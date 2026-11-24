package io.github.dipeshlama.passportscanner.internal

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.dipeshlama.passportscanner.mrz.MrzParser
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val ANALYSIS_RESOLUTION = Size(1920, 1080)

@Composable
internal actual fun PassportCameraView(
    controller: PassportCameraController,
    isAutoScanEnabled: Boolean,
    crop: PreviewCrop?,
    onCaptureStarted: () -> Unit,
    onResult: (CameraScan) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val autoScanEnabled by rememberUpdatedState(isAutoScanEnabled)
    val currentCrop by rememberUpdatedState(crop)
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnCaptureStarted by rememberUpdatedState(onCaptureStarted)

    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val hasDelivered = remember { AtomicBoolean(false) }
    val autoScanGate = remember { AutoScanGate() }

    val cameraController = remember(context) {
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    ANALYSIS_RESOLUTION,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            previewResolutionSelector = resolutionSelector
            imageAnalysisResolutionSelector = resolutionSelector
            imageCaptureResolutionSelector = resolutionSelector
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }

    LaunchedEffect(isAutoScanEnabled) {
        if (isAutoScanEnabled) hasDelivered.set(false)
    }

    DisposableEffect(cameraController, lifecycleOwner) {
        cameraController.setImageAnalysisAnalyzer(analysisExecutor) { proxy ->
            analyzeFrame(
                proxy = proxy,
                shouldAnalyze = { autoScanEnabled && !hasDelivered.get() },
                crop = { currentCrop },
                gate = autoScanGate,
                onMrzFound = { result ->
                    if (hasDelivered.compareAndSet(false, true)) {
                        mainExecutor.execute { currentOnResult(result) }
                    }
                }
            )
        }
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
        }
    }

    DisposableEffect(controller, cameraController) {
        controller.captureHandler = {
            currentOnCaptureStarted()
            cameraController.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val result = readCapturedStill(image, currentCrop)
                        mainExecutor.execute { currentOnResult(result) }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        mainExecutor.execute { currentOnResult(CameraScan(data = null, image = null)) }
                    }
                }
            )
        }
        onDispose { controller.captureHandler = null }
    }

    DisposableEffect(controller, cameraController, lifecycleOwner) {
        controller.hasTorch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        controller.torchHandler = { on -> cameraController.enableTorch(on) }
        // The torch also turns off on its own, e.g. when the app goes to the background.
        val torchObserver = Observer<Int> { controller.isTorchOn = it == TorchState.ON }
        cameraController.torchState.observe(lifecycleOwner, torchObserver)
        onDispose {
            cameraController.torchState.removeObserver(torchObserver)
            controller.torchHandler = null
            controller.isTorchOn = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this.controller = cameraController
            }
        }
    )
}

private fun analyzeFrame(
    proxy: ImageProxy,
    shouldAnalyze: () -> Boolean,
    crop: () -> PreviewCrop?,
    gate: AutoScanGate,
    onMrzFound: (CameraScan) -> Unit
) {
    proxy.use {
        if (!shouldAnalyze()) {
            gate.reset()
            return
        }
        val rotation = proxy.imageInfo.rotationDegrees
        val frame = runCatching { proxy.toBitmap() }.getOrNull() ?: return
        val isSideways = rotation % 180 != 0
        val uprightWidth = if (isSideways) frame.height else frame.width
        val uprightHeight = if (isSideways) frame.width else frame.height

        val frameCrop = crop()
        val region = frameCrop?.toReadingRect(uprightWidth, uprightHeight)
            ?: IntRect(0, 0, uprightWidth, uprightHeight)
        val regionBitmap = frame.uprightRegion(rotation, region)
        if (regionBitmap !== frame) frame.recycle()

        // Blocking is fine: with KEEP_ONLY_LATEST, frames arriving meanwhile are dropped.
        val text = runCatching {
            Tasks.await(PassportTextReader.process(InputImage.fromBitmap(regionBitmap, 0)))
        }.getOrNull() ?: return
        val offset = Offset(region.left.toFloat(), region.top.toFloat())
        val lines = text.ocrLines().map { it.copy(bounds = it.bounds.translate(offset)) }
        val match = MrzParser.find(lines)
        if (!gate.accept(match, uprightWidth, uprightHeight, frameCrop) || match == null) return

        val photo = runCatching {
            val box = frameCrop?.toImageRect(uprightWidth, uprightHeight)
            val photoBitmap = if (box == null) regionBitmap else Bitmap.createBitmap(
                regionBitmap,
                box.left - region.left,
                box.top - region.top,
                box.width.coerceAtMost(regionBitmap.width - (box.left - region.left)),
                box.height.coerceAtMost(regionBitmap.height - (box.top - region.top))
            )
            photoBitmap.uprightAndScaled(rotationDegrees = 0).toJpegBytes()
        }.getOrNull()
        onMrzFound(CameraScan(data = match.data, image = photo))
    }
}

private fun readCapturedStill(image: ImageProxy, crop: PreviewCrop?): CameraScan {
    val (forOcr, photo) = image.use { proxy ->
        runCatching {
            val bitmap = proxy.toBitmap()
            val rotation = proxy.imageInfo.rotationDegrees
            bitmap.uprightAndScaled(rotation) to bitmap.toPassportPhoto(rotation, crop)
        }.getOrNull()
    } ?: return CameraScan(data = null, image = null)
    val data = runCatching {
        Tasks.await(PassportTextReader.process(InputImage.fromBitmap(forOcr, 0)))
    }.getOrNull()?.let { MrzParser.find(it.ocrLines())?.data }
    return CameraScan(data = data, image = photo.toJpegBytes())
}

@Composable
internal actual fun rememberPassportGalleryPicker(
    onLoading: () -> Unit,
    onPicked: (PickedImage?) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnLoading by rememberUpdatedState(onLoading)
    val currentOnPicked by rememberUpdatedState(onPicked)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            currentOnPicked(null)
            return@rememberLauncherForActivityResult
        }
        currentOnLoading()
        scope.launch {
            val image = withContext(Dispatchers.IO) { loadUprightBitmap(context, uri) }
            currentOnPicked(image?.let(::AndroidPickedImage))
        }
    }

    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

private class AndroidPickedImage(private val bitmap: Bitmap) : PickedImage {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun toImageBitmap(): ImageBitmap = bitmap.asImageBitmap()

    override suspend fun read(crop: IntRect?): CameraScan = withContext(Dispatchers.Default) {
        val source = runCatching {
            crop?.let { Bitmap.createBitmap(bitmap, it.left, it.top, it.width, it.height) } ?: bitmap
        }.getOrDefault(bitmap)
        val data = runCatching { PassportTextReader.readMrz(InputImage.fromBitmap(source, 0)) }.getOrNull()
        CameraScan(data = data, image = source.toJpegBytes())
    }
}

private fun loadUprightBitmap(context: Context, uri: Uri): Bitmap? =
    runCatching {
        decodeSampledBitmap(context, uri)?.uprightAndScaled(readExifRotation(context, uri))
    }.getOrNull()

private fun decodeSampledBitmap(context: Context, uri: Uri) = context.contentResolver.run {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (longestEdge <= 0) return@run null

    var sampleSize = 1
    while (longestEdge / (sampleSize * 2) >= PASSPORT_IMAGE_MAX_EDGE) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun readExifRotation(context: Context, uri: Uri): Int =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees }
    }.getOrNull() ?: 0
