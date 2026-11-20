@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.dipeshlama.passportscanner.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.focusMode
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.minimumFocusDistance
import platform.AVFoundation.videoZoomFactor
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferRef
import platform.Foundation.NSData
import platform.ImageIO.kCGImagePropertyOrientationRight
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.Vision.VNImageRequestHandler
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference
import kotlin.math.PI
import kotlin.math.tan

private const val PASSPORT_WIDTH_MM = 125.0
private const val PASSPORT_FILL_RATIO = 0.9
private const val MAX_AUTO_ZOOM = 2.0

@Composable
internal actual fun PassportCameraView(
    controller: PassportCameraController,
    isAutoScanEnabled: Boolean,
    crop: PreviewCrop?,
    onCaptureStarted: () -> Unit,
    onResult: (CameraScan) -> Unit,
    modifier: Modifier
) {
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnCaptureStarted by rememberUpdatedState(onCaptureStarted)
    val scanner = remember { PassportCaptureSession { result -> currentOnResult(result) } }

    LaunchedEffect(isAutoScanEnabled) {
        scanner.setAutoScanEnabled(isAutoScanEnabled)
    }

    LaunchedEffect(crop) {
        scanner.crop.value = crop
    }

    DisposableEffect(controller, scanner) {
        controller.captureHandler = {
            currentOnCaptureStarted()
            scanner.requestCapture()
        }
        onDispose { controller.captureHandler = null }
    }

    DisposableEffect(scanner) {
        scanner.start()
        onDispose { scanner.stop() }
    }

    UIKitView(
        factory = { CameraPreviewView(scanner.previewLayer) },
        modifier = modifier
    )
}

private class PassportCaptureSession(
    private val onResult: (CameraScan) -> Unit
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    private val session = AVCaptureSession()
    private val queue = dispatch_queue_create("io.github.dipeshlama.passportscanner", null)
    private val ciContext = CIContext()

    private val autoScanEnabled = AtomicInt(1)
    private val hasDelivered = AtomicInt(0)
    private val autoScanGate = AutoScanGate()
    private val captureRequested = AtomicInt(0)
    private var isConfigured = false

    val crop = AtomicReference<PreviewCrop?>(null)

    val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    fun setAutoScanEnabled(enabled: Boolean) {
        autoScanEnabled.value = if (enabled) 1 else 0
        if (enabled) hasDelivered.value = 0
    }

    fun requestCapture() {
        captureRequested.value = 1
    }

    fun start() {
        dispatch_async(queue) {
            if (!isConfigured) isConfigured = configure()
            if (isConfigured && !session.running) session.startRunning()
        }
    }

    fun stop() {
        dispatch_async(queue) {
            if (session.running) session.stopRunning()
        }
    }

    private fun configure(): Boolean {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return false
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return false

        session.beginConfiguration()
        if (session.canSetSessionPreset(AVCaptureSessionPreset1920x1080)) {
            session.sessionPreset = AVCaptureSessionPreset1920x1080
        }
        if (session.canAddInput(input)) session.addInput(input)
        val output = AVCaptureVideoDataOutput().apply {
            alwaysDiscardsLateVideoFrames = true
            setSampleBufferDelegate(this@PassportCaptureSession, queue)
        }
        if (session.canAddOutput(output)) session.addOutput(output)
        session.commitConfiguration()

        configureFocus(device)
        return true
    }

    private fun configureFocus(device: AVCaptureDevice) {
        if (!device.lockForConfiguration(null)) return
        if (device.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) {
            device.focusMode = AVCaptureFocusModeContinuousAutoFocus
        }
        val minimumFocusMm = device.minimumFocusDistance.toDouble()
        if (minimumFocusMm > 0) {
            val longSideFov = device.activeFormat.videoFieldOfView.toDouble() * PI / 180
            val shortSideHalfTan = tan(longSideFov / 2) * 9.0 / 16.0
            val subjectDistanceMm = (PASSPORT_WIDTH_MM / PASSPORT_FILL_RATIO / 2) / shortSideHalfTan
            if (subjectDistanceMm < minimumFocusMm) {
                val maxZoom = minOf(MAX_AUTO_ZOOM, device.activeFormat.videoMaxZoomFactor)
                device.videoZoomFactor = minOf(maxZoom, minimumFocusMm / subjectDistanceMm)
            }
        }
        device.unlockForConfiguration()
    }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection
    ) {
        val isManualCapture = captureRequested.compareAndSet(1, 0)
        val isAutoScan = autoScanEnabled.value == 1 && hasDelivered.value == 0
        if (!isManualCapture && !isAutoScan) {
            autoScanGate.reset()
            return
        }

        val pixelBuffer = CMSampleBufferGetImageBuffer(didOutputSampleBuffer)
        if (pixelBuffer == null) {
            if (isManualCapture) deliver(CameraScan(data = null, image = null))
            return
        }

        // Camera frames are landscape; the UI is portrait.
        val handler = VNImageRequestHandler(
            cVPixelBuffer = pixelBuffer,
            orientation = kCGImagePropertyOrientationRight,
            options = emptyMap<Any?, Any?>()
        )
        val uprightWidth = CVPixelBufferGetHeight(pixelBuffer).toInt()
        val uprightHeight = CVPixelBufferGetWidth(pixelBuffer).toInt()
        val region = if (isManualCapture) null else crop.value?.toReadingRect(uprightWidth, uprightHeight)
        val match = PassportTextReader.findMrz(handler, uprightWidth, uprightHeight, region)

        if (isManualCapture) {
            deliver(CameraScan(data = match?.data, image = jpegFrom(pixelBuffer)))
        } else if (
            autoScanGate.accept(match, uprightWidth, uprightHeight, crop.value) &&
            match != null &&
            hasDelivered.compareAndSet(0, 1)
        ) {
            deliver(CameraScan(data = match.data, image = jpegFrom(pixelBuffer)))
        }
    }

    private fun deliver(result: CameraScan) {
        dispatch_async(dispatch_get_main_queue()) { onResult(result) }
    }

    private fun jpegFrom(pixelBuffer: CVPixelBufferRef): ByteArray? {
        val upright = CIImage.imageWithCVPixelBuffer(pixelBuffer)
            .imageByApplyingCGOrientation(kCGImagePropertyOrientationRight)
        val cropped = cropToFrame(upright)
        val (width, height) = cropped.extent.size()
        val factor = minOf(1.0, PASSPORT_IMAGE_MAX_EDGE / maxOf(width, height))
        val scaled = cropped.imageByApplyingTransform(CGAffineTransformMakeScale(factor, factor))
        val cgImage = ciContext.createCGImage(scaled, fromRect = scaled.extent) ?: return null
        return try {
            UIImage.imageWithCGImage(cgImage).toJpegBytes()
        } finally {
            platform.CoreGraphics.CGImageRelease(cgImage)
        }
    }
}

private fun PassportCaptureSession.cropToFrame(image: CIImage): CIImage {
    val (originX, originY, width, height) = image.extent.useContents {
        listOf(origin.x, origin.y, size.width, size.height)
    }
    val rect = crop.value?.toImageRect(width.toInt(), height.toInt()) ?: return image
    val ciRect = CGRectMake(
        x = originX + rect.left,
        y = originY + height - rect.bottom,
        width = rect.width.toDouble(),
        height = rect.height.toDouble()
    )
    return image.imageByCroppingToRect(ciRect)
}

private fun CValue<CGRect>.size(): Pair<Double, Double> = useContents { size.width to size.height }

private class CameraPreviewView(
    private val previewLayer: AVCaptureVideoPreviewLayer
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
    }
}

@Composable
internal actual fun rememberPassportGalleryPicker(
    onProcessing: () -> Unit,
    onResult: (CameraScan?) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnProcessing by rememberUpdatedState(onProcessing)
    val currentOnResult by rememberUpdatedState(onResult)

    // PHPickerViewController holds its delegate weakly.
    val delegate = remember {
        PassportPickerDelegate(
            onCancelled = { currentOnResult(null) },
            onPicked = { data ->
                currentOnProcessing()
                scope.launch {
                    val result = withContext(Dispatchers.Default) { readPassportFromData(data) }
                    currentOnResult(result)
                }
            }
        )
    }

    return remember(delegate) {
        {
            val configuration = PHPickerConfiguration().apply {
                filter = PHPickerFilter.imagesFilter
                selectionLimit = 1
            }
            val picker = PHPickerViewController(configuration = configuration)
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PassportPickerDelegate(
    private val onCancelled: () -> Unit,
    private val onPicked: (NSData?) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onCancelled()
            return
        }
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            dispatch_async(dispatch_get_main_queue()) { onPicked(data) }
        }
    }
}

private fun readPassportFromData(data: NSData?): CameraScan {
    val image = data?.let { UIImage.imageWithData(it) }?.uprightAndScaled()
        ?: return CameraScan(data = null, image = null)
    val cgImage = image.CGImage ?: return CameraScan(data = null, image = image.toJpegBytes())
    val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any?>())
    val match = PassportTextReader.findMrz(
        handler = handler,
        imageWidth = CGImageGetWidth(cgImage).toInt(),
        imageHeight = CGImageGetHeight(cgImage).toInt()
    )
    return CameraScan(data = match?.data, image = image.toJpegBytes())
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
