@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.dipeshlama.barcodescanner.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.viewinterop.UIKitView
import io.github.dipeshlama.barcodescanner.BarcodeFormat
import io.github.dipeshlama.barcodescanner.BarcodeScanResult
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.AVFoundation.AVCaptureAutoFocusRangeRestrictionNear
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.autoFocusRangeRestriction
import platform.AVFoundation.autoFocusRangeRestrictionSupported
import platform.AVFoundation.focusMode
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.isTorchModeSupported
import platform.AVFoundation.minimumFocusDistance
import platform.AVFoundation.torchMode
import platform.AVFoundation.videoZoomFactor
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
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

/** Smallest code, in mm, the camera should be able to focus on while filling the frame. */
private const val MIN_CODE_SIZE_MM = 20.0
private const val CODE_FILL_RATIO = 0.5
private const val MAX_AUTO_ZOOM = 2.0

@Composable
internal actual fun BarcodeCameraView(
    controller: BarcodeCameraController,
    formats: Set<BarcodeFormat>,
    isAutoScanEnabled: Boolean,
    crop: PreviewCrop?,
    onResult: (BarcodeScanResult) -> Unit,
    modifier: Modifier
) {
    val currentOnResult by rememberUpdatedState(onResult)
    val scanner = remember {
        BarcodeCaptureSession(
            onResult = { result -> currentOnResult(result) },
            onTorchChanged = { hasTorch, isOn ->
                controller.hasTorch = hasTorch
                controller.isTorchOn = isOn
            }
        )
    }

    LaunchedEffect(isAutoScanEnabled) {
        scanner.setAutoScanEnabled(isAutoScanEnabled)
    }

    LaunchedEffect(crop) {
        scanner.crop.value = crop
    }

    LaunchedEffect(formats) {
        scanner.formats.value = formats
    }

    DisposableEffect(controller, scanner) {
        controller.torchHandler = scanner::setTorch
        onDispose {
            controller.torchHandler = null
            controller.isTorchOn = false
        }
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

private class BarcodeCaptureSession(
    private val onResult: (BarcodeScanResult) -> Unit,
    private val onTorchChanged: (hasTorch: Boolean, isOn: Boolean) -> Unit
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    private val session = AVCaptureSession()
    // Camera frames (and their OCR/decoding) run on [videoQueue]. Session setup and the torch use
    // [sessionQueue], so they don't wait behind frames that are still being read.
    private val videoQueue = dispatch_queue_create("io.github.dipeshlama.barcodescanner.video", null)
    private val sessionQueue = dispatch_queue_create("io.github.dipeshlama.barcodescanner.session", null)
    private val frameReleaser = CameraFrameReleaser()

    private val autoScanEnabled = AtomicInt(1)
    private val hasDelivered = AtomicInt(0)
    private var isConfigured = false

    // Only touched on [sessionQueue].
    private var device: AVCaptureDevice? = null

    val crop = AtomicReference<PreviewCrop?>(null)
    val formats = AtomicReference(BarcodeFormat.All)

    val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    fun setAutoScanEnabled(enabled: Boolean) {
        autoScanEnabled.value = if (enabled) 1 else 0
        if (enabled) hasDelivered.value = 0
    }

    fun start() {
        dispatch_async(sessionQueue) {
            if (!isConfigured) isConfigured = configure()
            if (isConfigured && !session.running) session.startRunning()
        }
    }

    fun stop() {
        dispatch_async(sessionQueue) {
            if (session.running) session.stopRunning()
        }
    }

    fun setTorch(on: Boolean) {
        dispatch_async(sessionQueue) {
            val device = device ?: return@dispatch_async
            val mode = if (on) AVCaptureTorchModeOn else AVCaptureTorchModeOff
            if (device.hasTorch && device.isTorchModeSupported(mode) && device.lockForConfiguration(null)) {
                device.torchMode = mode
                device.unlockForConfiguration()
            }
            reportTorch(device)
        }
    }

    private fun reportTorch(device: AVCaptureDevice) {
        val hasTorch = device.hasTorch
        val isOn = device.torchMode == AVCaptureTorchModeOn
        dispatch_async(dispatch_get_main_queue()) { onTorchChanged(hasTorch, isOn) }
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
            setSampleBufferDelegate(this@BarcodeCaptureSession, videoQueue)
        }
        if (session.canAddOutput(output)) session.addOutput(output)
        session.commitConfiguration()

        configureFocus(device)
        this.device = device
        reportTorch(device)
        return true
    }

    /**
     * Codes are often held close. On cameras that can't focus that close (recent Pro iPhones), zoom
     * in a little so the user holds the phone farther away, as Apple's barcode sample does.
     */
    private fun configureFocus(device: AVCaptureDevice) {
        if (!device.lockForConfiguration(null)) return
        if (device.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) {
            device.focusMode = AVCaptureFocusModeContinuousAutoFocus
        }
        if (device.autoFocusRangeRestrictionSupported) {
            device.autoFocusRangeRestriction = AVCaptureAutoFocusRangeRestrictionNear
        }
        val minimumFocusMm = device.minimumFocusDistance.toDouble()
        if (minimumFocusMm > 0) {
            val longSideFov = device.activeFormat.videoFieldOfView.toDouble() * PI / 180
            val shortSideHalfTan = tan(longSideFov / 2) * 9.0 / 16.0
            val subjectDistanceMm = (MIN_CODE_SIZE_MM / CODE_FILL_RATIO / 2) / shortSideHalfTan
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
        if (autoScanEnabled.value != 1 || hasDelivered.value != 0) return
        autoreleasepool { readFrame(didOutputSampleBuffer) }
        frameReleaser.onFrameRead()
    }

    private fun readFrame(didOutputSampleBuffer: CMSampleBufferRef?) {
        val pixelBuffer = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return

        // Camera frames are landscape; the UI is portrait.
        val handler = VNImageRequestHandler(
            cVPixelBuffer = pixelBuffer,
            orientation = kCGImagePropertyOrientationRight,
            options = emptyMap<Any?, Any?>()
        )
        val uprightWidth = CVPixelBufferGetHeight(pixelBuffer).toInt()
        val uprightHeight = CVPixelBufferGetWidth(pixelBuffer).toInt()
        val region = crop.value?.toReadingRect(uprightWidth, uprightHeight)
        val result = BarcodeReader.find(handler, formats.value, uprightWidth, uprightHeight, region) ?: return

        if (hasDelivered.compareAndSet(0, 1)) {
            dispatch_async(dispatch_get_main_queue()) { onResult(result) }
        }
    }
}

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
internal actual fun rememberGalleryPicker(
    onLoading: () -> Unit,
    onPicked: (PickedImage?) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnLoading by rememberUpdatedState(onLoading)
    val currentOnPicked by rememberUpdatedState(onPicked)

    // PHPickerViewController holds its delegate weakly.
    val delegate = remember {
        PickerDelegate(
            onCancelled = { currentOnPicked(null) },
            onPicked = { data ->
                currentOnLoading()
                scope.launch {
                    val image = withContext(Dispatchers.Default) { loadPickedImage(data) }
                    currentOnPicked(image)
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

private class PickerDelegate(
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

private fun loadPickedImage(data: NSData?): PickedImage? {
    // Rendered at scale 1, so the CGImage's pixels match the UIImage's points.
    val image = data?.let { UIImage.imageWithData(it) }?.uprightAndScaled() ?: return null
    val cgImage = image.CGImage ?: return null
    return IosPickedImage(image, cgImage)
}

private class IosPickedImage(
    private val image: UIImage,
    private val cgImage: CGImageRef
) : PickedImage {
    override val width: Int = CGImageGetWidth(cgImage).toInt()
    override val height: Int = CGImageGetHeight(cgImage).toInt()

    override fun toImageBitmap(): ImageBitmap? =
        image.toJpegBytes()?.let { runCatching { Image.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }

    override suspend fun read(formats: Set<BarcodeFormat>, crop: IntRect?): BarcodeScanResult? =
        withContext(Dispatchers.Default) {
            val croppedCg = crop?.let {
                CGImageCreateWithImageInRect(
                    cgImage,
                    CGRectMake(it.left.toDouble(), it.top.toDouble(), it.width.toDouble(), it.height.toDouble())
                )
            }
            try {
                val source = croppedCg ?: cgImage
                val handler = VNImageRequestHandler(cGImage = source, options = emptyMap<Any?, Any?>())
                BarcodeReader.find(
                    handler = handler,
                    formats = formats,
                    imageWidth = CGImageGetWidth(source).toInt(),
                    imageHeight = CGImageGetHeight(source).toInt()
                )
            } finally {
                croppedCg?.let { CGImageRelease(it) }
            }
        }
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
