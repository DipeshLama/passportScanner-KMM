package io.github.dipeshlama.barcodescanner.internal

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.common.InputImage
import io.github.dipeshlama.barcodescanner.BarcodeFormat
import io.github.dipeshlama.barcodescanner.BarcodeScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val ANALYSIS_RESOLUTION = Size(1920, 1080)
private const val BEEP_VOLUME = 80
private const val BEEP_MILLIS = 150

@Composable
internal actual fun BarcodeCameraView(
    controller: BarcodeCameraController,
    formats: Set<BarcodeFormat>,
    isAutoScanEnabled: Boolean,
    crop: PreviewCrop?,
    accept: (BarcodeScanResult) -> Boolean,
    pinchToZoom: Boolean,
    autoZoom: Boolean,
    onResult: (BarcodeScanResult) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val autoScanEnabled by rememberUpdatedState(isAutoScanEnabled)
    val currentCrop by rememberUpdatedState(crop)
    val currentFormats by rememberUpdatedState(formats)
    val currentOnResult by rememberUpdatedState(onResult)
    val currentAccept by rememberUpdatedState(accept)

    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val hasDelivered = remember { AtomicBoolean(false) }

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
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            previewResolutionSelector = resolutionSelector
            imageAnalysisResolutionSelector = resolutionSelector
        }
    }

    // ML Kit asks for a zoom when it sees a code too small to read; the limit comes from the camera.
    val autoZoomLimit = if (autoZoom) controller.maxZoomRatio else 1f
    val reader = remember(formats, autoZoomLimit) {
        val zoom = if (autoZoomLimit > 1f) {
            ZoomSuggestionOptions.Builder { ratio ->
                mainExecutor.execute { cameraController.setZoomRatio(ratio) }
                true
            }.setMaxSupportedZoomRatio(autoZoomLimit).build()
        } else {
            null
        }
        barcodeClient(formats, zoom)
    }
    val currentReader by rememberUpdatedState(reader)
    DisposableEffect(reader) {
        onDispose { reader.close() }
    }

    LaunchedEffect(cameraController, pinchToZoom) {
        cameraController.isPinchToZoomEnabled = pinchToZoom
    }

    LaunchedEffect(isAutoScanEnabled) {
        if (isAutoScanEnabled) hasDelivered.set(false)
    }

    DisposableEffect(cameraController, lifecycleOwner) {
        cameraController.setImageAnalysisAnalyzer(analysisExecutor) { proxy ->
            analyzeFrame(
                proxy = proxy,
                shouldAnalyze = { autoScanEnabled && !hasDelivered.get() },
                reader = { currentReader },
                formats = { currentFormats },
                crop = { currentCrop },
                accept = { currentAccept(it) },
                onFound = { result ->
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

    DisposableEffect(controller, cameraController, lifecycleOwner) {
        controller.hasTorch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        controller.torchHandler = { on -> cameraController.enableTorch(on) }
        // The torch also turns off on its own, e.g. when the app goes to the background.
        val torchObserver = Observer<Int> { controller.isTorchOn = it == TorchState.ON }
        cameraController.torchState.observe(lifecycleOwner, torchObserver)

        controller.zoomHandler = { ratio -> cameraController.setZoomRatio(ratio) }
        val zoomObserver = Observer<ZoomState> { zoom ->
            controller.minZoomRatio = zoom.minZoomRatio
            controller.maxZoomRatio = zoom.maxZoomRatio
            controller.zoomRatio = zoom.zoomRatio
        }
        cameraController.zoomState.observe(lifecycleOwner, zoomObserver)
        onDispose {
            cameraController.torchState.removeObserver(torchObserver)
            cameraController.zoomState.removeObserver(zoomObserver)
            controller.torchHandler = null
            controller.zoomHandler = null
            controller.isTorchOn = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
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

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyzeFrame(
    proxy: ImageProxy,
    shouldAnalyze: () -> Boolean,
    reader: () -> BarcodeScanner,
    formats: () -> Set<BarcodeFormat>,
    crop: () -> PreviewCrop?,
    accept: (BarcodeScanResult) -> Boolean,
    onFound: (BarcodeScanResult) -> Unit
) {
    proxy.use {
        if (!shouldAnalyze()) return
        val mediaImage = proxy.image ?: return
        val rotation = proxy.imageInfo.rotationDegrees
        // ML Kit reports bounds in the upright image, after applying the rotation.
        val isSideways = rotation % 180 != 0
        val uprightWidth = if (isSideways) proxy.height else proxy.width
        val uprightHeight = if (isSideways) proxy.width else proxy.height

        // Blocking is fine: with KEEP_ONLY_LATEST, frames arriving meanwhile are dropped.
        val barcodes = runCatching {
            Tasks.await(reader().process(InputImage.fromMediaImage(mediaImage, rotation)))
        }.getOrNull() ?: return

        val frameCrop = crop()
        val wanted = formats()
        val result = barcodes.firstNotNullOfOrNull { barcode ->
            val bounds = barcode.boundingBox?.toComposeRect() ?: return@firstNotNullOfOrNull null
            if (!frameCrop.contains(bounds, uprightWidth, uprightHeight)) return@firstNotNullOfOrNull null
            barcode.toResult(wanted)?.takeIf(accept)
        } ?: return
        onFound(result)
    }
}

@Composable
internal actual fun rememberGalleryPicker(
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

    override suspend fun read(formats: Set<BarcodeFormat>, crop: IntRect?): BarcodeScanResult? =
        withContext(Dispatchers.Default) {
            val source = runCatching {
                crop?.let { Bitmap.createBitmap(bitmap, it.left, it.top, it.width, it.height) } ?: bitmap
            }.getOrDefault(bitmap)
            val reader = barcodeClient(formats)
            try {
                reader.process(InputImage.fromBitmap(source, 0)).await()
                    .firstNotNullOfOrNull { it.toResult(formats) }
            } finally {
                reader.close()
            }
        }
}

internal actual fun decodeBarcodeImage(bytes: ByteArray): PickedImage? =
    runCatching {
        val open = { ByteArrayInputStream(bytes) }
        val rotation = runCatching { open().use { ExifInterface(it).rotationDegrees } }.getOrDefault(0)
        decodeSampledBitmap(open)?.uprightAndScaled(rotation)
    }.getOrNull()?.let(::AndroidPickedImage)

@Composable
internal actual fun rememberBeep(): () -> Unit {
    val beeper = remember { Beeper() }
    DisposableEffect(beeper) {
        onDispose { beeper.release() }
    }
    return beeper::play
}

/** Plays the beep with a [ToneGenerator], created on first use since it holds an audio resource. */
private class Beeper {
    private var tone: ToneGenerator? = null

    fun play() {
        runCatching {
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, BEEP_VOLUME).also { tone = it }
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MILLIS)
        }
    }

    fun release() {
        tone?.release()
        tone = null
    }
}

private fun loadUprightBitmap(context: Context, uri: Uri): Bitmap? =
    runCatching {
        decodeSampledBitmap { context.contentResolver.openInputStream(uri) }
            ?.uprightAndScaled(readExifRotation(context, uri))
    }.getOrNull()

private fun decodeSampledBitmap(open: () -> InputStream?): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (longestEdge <= 0) return null

    var sampleSize = 1
    while (longestEdge / (sampleSize * 2) >= GALLERY_IMAGE_MAX_EDGE) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return open()?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun readExifRotation(context: Context, uri: Uri): Int =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees }
    }.getOrNull() ?: 0
