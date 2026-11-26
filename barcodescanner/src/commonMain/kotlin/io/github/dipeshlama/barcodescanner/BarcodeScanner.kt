package io.github.dipeshlama.barcodescanner

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.dipeshlama.barcodescanner.internal.BarcodeCameraView
import io.github.dipeshlama.barcodescanner.internal.ImageCropScreen
import io.github.dipeshlama.barcodescanner.internal.PickedImage
import io.github.dipeshlama.barcodescanner.internal.PreviewCrop
import io.github.dipeshlama.barcodescanner.internal.ScannerIcons
import io.github.dipeshlama.barcodescanner.internal.rememberBeep
import io.github.dipeshlama.barcodescanner.internal.rememberCameraPermission
import io.github.dipeshlama.barcodescanner.internal.rememberGalleryPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SQUARE_FRAME_WIDTH_FRACTION = 0.72f
private const val WIDE_FRAME_WIDTH_FRACTION = 0.88f
private const val WIDE_FRAME_MIN_ASPECT_RATIO = 1.25f
private const val NOT_FOUND_MESSAGE_MILLIS = 3000L
private const val RECENT_SCAN_MESSAGE_MILLIS = 1500L
private val FrameCornerRadius = 16.dp
private val FrameBracketLength = 32.dp
private val FrameBracketWidth = 3.dp
private val ScanLineBandHeight = 47.dp

/**
 * Full-screen QR code and barcode scanner with live scanning inside a frame, a flashlight button,
 * pinch-to-zoom and gallery import, with optional cropping of gallery photos
 * ([BarcodeScannerConfig.cropGalleryImage]).
 *
 * In [BarcodeScanMode.Single] (the default), [onResult] is called once with the first code read;
 * remove the scanner from composition after that. In [BarcodeScanMode.Continuous], [onResult] is
 * called for each new code and [onDone] with every code read when the user taps Done.
 * [onCancel] is called when the user goes back, from the top bar or with system back.
 *
 * To observe or control the scanner from outside (pause it, zoom, turn on the flashlight), or to
 * receive results as a [kotlinx.coroutines.flow.Flow], use the overload that takes a
 * [BarcodeScannerState] instead.
 *
 * On iOS, add `NSCameraUsageDescription` to the app's Info.plist.
 *
 * @param onDone called with every code read, oldest first, when the user taps Done in
 *   [BarcodeScanMode.Continuous]. Not called in single mode.
 * @param config behaviour options, including which formats to read, the frame shape and the scan mode.
 * @param strings text shown by the scanner.
 * @param colors colours of the camera overlay, including the scan line.
 * @param title replaces the top bar title. Null uses [BarcodeScannerStrings.title].
 * @param instruction replaces the message above the frame. Null uses [BarcodeScannerStrings.instruction].
 * @param galleryButton replaces the gallery button. Null uses [BarcodeScannerDefaults.GalleryButton].
 */
@Composable
public fun BarcodeScanner(
    onResult: (BarcodeScanResult) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onDone: (List<BarcodeScanResult>) -> Unit = {},
    config: BarcodeScannerConfig = BarcodeScannerConfig(),
    strings: BarcodeScannerStrings = BarcodeScannerStrings(),
    colors: BarcodeScannerColors = BarcodeScannerDefaults.colors(),
    title: (@Composable () -> Unit)? = null,
    instruction: (@Composable () -> Unit)? = null,
    galleryButton: (@Composable (onClick: () -> Unit, enabled: Boolean) -> Unit)? = null,
) {
    val state = remember { BarcodeScannerState(emitsEvents = false) }
    BarcodeScannerContent(
        state = state,
        onResult = onResult,
        onDone = onDone,
        onCancel = onCancel,
        modifier = modifier,
        config = config,
        strings = strings,
        colors = colors,
        title = title,
        instruction = instruction,
        galleryButton = galleryButton,
    )
}

/** The 1.0 signature, kept so code compiled against it still links. */
@Deprecated("Kept for binary compatibility.", level = DeprecationLevel.HIDDEN)
@Composable
public fun BarcodeScanner(
    onResult: (BarcodeScanResult) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    config: BarcodeScannerConfig = BarcodeScannerConfig(),
    strings: BarcodeScannerStrings = BarcodeScannerStrings(),
    colors: BarcodeScannerColors = BarcodeScannerDefaults.colors(),
    title: (@Composable () -> Unit)? = null,
    instruction: (@Composable () -> Unit)? = null,
    galleryButton: (@Composable (onClick: () -> Unit, enabled: Boolean) -> Unit)? = null,
) {
    BarcodeScanner(
        onResult = onResult,
        onCancel = onCancel,
        modifier = modifier,
        onDone = {},
        config = config,
        strings = strings,
        colors = colors,
        title = title,
        instruction = instruction,
        galleryButton = galleryButton,
    )
}

/**
 * Full-screen QR code and barcode scanner driven by a [BarcodeScannerState], the same scanner as
 * the `BarcodeScanner(onResult, onCancel)` overload.
 *
 * Codes and back presses are reported through [BarcodeScannerState.events] (and
 * [BarcodeScannerState.result]), and the scanner can be controlled with
 * [BarcodeScannerState.pause], [BarcodeScannerState.resume], [BarcodeScannerState.setTorch],
 * [BarcodeScannerState.setZoomRatio], [BarcodeScannerState.finish] and [BarcodeScannerState.restart].
 * Collect [BarcodeScannerState.events] to handle [BarcodeScanEvent.Cancelled]; the scanner consumes
 * system back while it's shown.
 *
 * Once finished ([BarcodeScanPhase.Finished]), remove the scanner from composition, or call
 * [BarcodeScannerState.restart] to scan again.
 *
 * The other parameters are the same as in the `BarcodeScanner(onResult, onCancel)` overload.
 */
@Composable
public fun BarcodeScanner(
    state: BarcodeScannerState,
    modifier: Modifier = Modifier,
    config: BarcodeScannerConfig = BarcodeScannerConfig(),
    strings: BarcodeScannerStrings = BarcodeScannerStrings(),
    colors: BarcodeScannerColors = BarcodeScannerDefaults.colors(),
    title: (@Composable () -> Unit)? = null,
    instruction: (@Composable () -> Unit)? = null,
    galleryButton: (@Composable (onClick: () -> Unit, enabled: Boolean) -> Unit)? = null,
) {
    BarcodeScannerContent(
        state = state,
        onResult = {},
        onDone = {},
        onCancel = {},
        modifier = modifier,
        config = config,
        strings = strings,
        colors = colors,
        title = title,
        instruction = instruction,
        galleryButton = galleryButton,
    )
}

@Composable
private fun BarcodeScannerContent(
    state: BarcodeScannerState,
    onResult: (BarcodeScanResult) -> Unit,
    onDone: (List<BarcodeScanResult>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
    config: BarcodeScannerConfig,
    strings: BarcodeScannerStrings,
    colors: BarcodeScannerColors,
    title: (@Composable () -> Unit)?,
    instruction: (@Composable () -> Unit)?,
    galleryButton: (@Composable (onClick: () -> Unit, enabled: Boolean) -> Unit)?,
) {
    state.config = config
    val scanner = state.scanner
    val currentConfig by rememberUpdatedState(config)
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val haptics = LocalHapticFeedback.current
    val beep = rememberBeep()
    val deliver: (BarcodeScanResult?) -> Unit = { result ->
        state.onScanned(result)?.let {
            if (currentConfig.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            if (currentConfig.beepOnScan) beep()
            currentOnResult(it)
        }
    }
    val cancel = {
        state.onCancelled()
        currentOnCancel()
    }
    val done = {
        state.onDone()?.let { currentOnDone(it) }
        Unit
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        // The crop screen handles back itself, to return to the camera.
        isBackEnabled = !scanner.isFinished && scanner.cropImage == null,
        onBackCompleted = cancel
    )

    val cameraController = state.camera
    val cameraPermission = rememberCameraPermission()
    val isCameraGranted = cameraPermission.isGranted

    LaunchedEffect(Unit) {
        if (!cameraPermission.isGranted) cameraPermission.request()
    }

    LaunchedEffect(scanner.isNotFoundVisible) {
        if (scanner.isNotFoundVisible) {
            delay(NOT_FOUND_MESSAGE_MILLIS)
            scanner.dismissNotFound()
        }
    }

    LaunchedEffect(scanner.isCoolingDown) {
        if (scanner.isCoolingDown) {
            delay((currentConfig.scanMode as? BarcodeScanMode.Continuous)?.delayMillis ?: 0L)
            scanner.endCooldown()
        }
    }

    LaunchedEffect(scanner.recentScanId) {
        if (scanner.recentScan != null) {
            delay(RECENT_SCAN_MESSAGE_MILLIS)
            scanner.dismissRecentScan()
        }
    }

    val scope = rememberCoroutineScope()
    val readPicked: (PickedImage, IntRect?) -> Unit = { image, crop ->
        scope.launch {
            val result = runCatching { image.read(currentConfig.formats, crop) }.getOrNull()
            if (result == null && !scanner.isFinished) state.onNoCodeFound()
            deliver(scanner.onGalleryResult(result))
        }
    }
    val launchGallery = rememberGalleryPicker(
        onLoading = scanner::onProcessingStarted,
        onPicked = { image ->
            if (image == null) {
                scanner.onGalleryCancelled()
            } else {
                scanner.onGalleryPicked(image)?.let { readPicked(it, null) }
            }
        }
    )

    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var frameInWindow by remember { mutableStateOf<Rect?>(null) }
    val frame = frameInWindow?.translate(-rootOffset)
    val previewCrop = remember(frame, rootSize) {
        frame?.let { PreviewCrop(rootSize.width.toFloat(), rootSize.height.toFloat(), it) }
    }
    val frameWidthFraction =
        if (config.frameAspectRatio >= WIDE_FRAME_MIN_ASPECT_RATIO) WIDE_FRAME_WIDTH_FRACTION else SQUARE_FRAME_WIDTH_FRACTION
    val isContinuous = config.scanMode is BarcodeScanMode.Continuous

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned {
                rootOffset = it.positionInWindow()
                rootSize = it.size
            }
    ) {
        if (isCameraGranted) {
            BarcodeCameraView(
                controller = cameraController,
                formats = config.formats,
                isAutoScanEnabled = scanner.isAutoScanEnabled,
                crop = previewCrop,
                accept = scanner::accepts,
                pinchToZoom = config.pinchToZoom,
                autoZoom = config.autoZoom,
                onResult = { result -> deliver(scanner.onCameraResult(result)) },
                modifier = Modifier.fillMaxSize()
            )
        }

        ScannerScrim(frame = frame, color = colors.scrim)

        CompositionLocalProvider(LocalContentColor provides colors.content) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                ScannerTopBar(
                    backDescription = strings.back,
                    onBack = cancel,
                    action = {
                        if (config.showTorch && isCameraGranted && cameraController.hasTorch) {
                            val isOn = cameraController.isTorchOn
                            IconButton(onClick = { cameraController.setTorch(!isOn) }) {
                                Icon(
                                    if (isOn) ScannerIcons.FlashOff else ScannerIcons.FlashOn,
                                    contentDescription = if (isOn) strings.torchOff else strings.torchOn
                                )
                            }
                        }
                    }
                ) {
                    if (title != null) title() else BarcodeScannerDefaults.Title(strings.title)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (instruction != null) {
                        instruction()
                    } else {
                        BarcodeScannerDefaults.Instruction(strings.instruction, backgroundColor = colors.chipBackground)
                    }

                    ScanFrame(
                        isScanning = isCameraGranted && scanner.isLive,
                        scanLineColor = colors.scanLine,
                        cornerColor = colors.frameCorners,
                        modifier = Modifier
                            .fillMaxWidth(frameWidthFraction)
                            .aspectRatio(config.frameAspectRatio)
                            .onGloballyPositioned { frameInWindow = it.boundsInWindow() }
                    ) {
                        if (!isCameraGranted) {
                            CameraPermissionPrompt(
                                description = strings.permissionDescription,
                                buttonText = strings.allowCamera,
                                onAllowClick = cameraPermission::request,
                                colors = colors
                            )
                        }
                    }

                    // Space is kept for the messages so the frame doesn't move when they show.
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = scanner.isNotFoundVisible,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            BarcodeScannerDefaults.Instruction(strings.noCodeFound, backgroundColor = colors.chipBackground)
                        }
                        val recent = scanner.recentScan
                        androidx.compose.animation.AnimatedVisibility(
                            visible = recent != null && !scanner.isNotFoundVisible,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            // Keeps the last value while fading out.
                            var shown by remember { mutableStateOf("") }
                            if (recent != null) shown = recent.value
                            RecentScanChip(text = shown, backgroundColor = colors.chipBackground)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (config.allowGallery) {
                        val galleryEnabled = !scanner.isProcessing && !scanner.isFinished
                        if (galleryButton != null) {
                            galleryButton(launchGallery, galleryEnabled)
                        } else {
                            BarcodeScannerDefaults.GalleryButton(
                                text = strings.gallery,
                                onClick = launchGallery,
                                enabled = galleryEnabled,
                                backgroundColor = colors.chipBackground
                            )
                        }
                    }
                    if (isContinuous) {
                        val count = scanner.results.size
                        Button(
                            onClick = done,
                            enabled = count > 0 && !scanner.isProcessing && !scanner.isFinished,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.permissionButtonContainer,
                                contentColor = colors.permissionButtonContent
                            )
                        ) { Text(if (count > 0) "${strings.done} ($count)" else strings.done) }
                    }
                }
            }
        }

        scanner.cropImage?.let { image ->
            ImageCropScreen(
                image = image,
                strings = strings,
                colors = colors,
                onCancel = scanner::cancelCrop,
                onConfirm = { crop -> scanner.confirmCrop()?.let { readPicked(it, crop) } }
            )
        }

        if (scanner.isProcessing) {
            ProcessingOverlay(text = strings.processing, colors = colors)
        }
    }
}

@Composable
private fun RecentScanChip(text: String, backgroundColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = LocalContentColor.current,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ScannerTopBar(
    backDescription: String,
    onBack: () -> Unit,
    action: @Composable () -> Unit,
    title: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(ScannerIcons.ArrowBack, contentDescription = backDescription)
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { title() }
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) { action() }
    }
}

@Composable
private fun ScannerScrim(frame: Rect?, color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // Offscreen so BlendMode.Clear cuts through to the camera preview.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        drawRect(color)
        if (frame != null) {
            drawRoundRect(
                color = Color.Black,
                topLeft = frame.topLeft,
                size = frame.size,
                cornerRadius = CornerRadius(FrameCornerRadius.toPx()),
                blendMode = BlendMode.Clear
            )
        }
    }
}

@Composable
private fun ScanFrame(
    isScanning: Boolean,
    scanLineColor: Color,
    cornerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val progress = if (isScanning) {
        val transition = rememberInfiniteTransition()
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart)
        )
    } else {
        null
    }

    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            progress?.let { drawScanLine(it.value, scanLineColor) }
            drawCornerBrackets(cornerColor)
        },
        contentAlignment = Alignment.Center,
        content = content
    )
}

private fun DrawScope.drawScanLine(progress: Float, color: Color) {
    val band = ScanLineBandHeight.toPx().coerceAtMost(size.height / 3)
    val y = (size.height - band) * progress
    val frameShape = Path().apply {
        addRoundRect(RoundRect(rect = Rect(Offset.Zero, size), cornerRadius = CornerRadius(FrameCornerRadius.toPx())))
    }
    clipPath(frameShape) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.45f), Color.Transparent),
                startY = y,
                endY = y + band
            ),
            topLeft = Offset(0f, y),
            size = Size(size.width, band)
        )
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
    }
}

private fun DrawScope.drawCornerBrackets(color: Color) {
    val stroke = FrameBracketWidth.toPx()
    val inset = stroke / 2
    val radius = FrameCornerRadius.toPx() - inset
    val arm = FrameBracketLength.toPx()
    val bracket = Path().apply {
        moveTo(inset, arm)
        lineTo(inset, inset + radius)
        arcTo(Rect(Offset(inset, inset), Size(radius * 2, radius * 2)), 180f, 90f, false)
        lineTo(arm, inset)
    }
    val style = Stroke(width = stroke, cap = StrokeCap.Round)
    listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f).forEach { (scaleX, scaleY) ->
        withTransform({ scale(scaleX, scaleY, pivot = center) }) {
            drawPath(bracket, color, style = style)
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    description: String,
    buttonText: String,
    onAllowClick: () -> Unit,
    colors: BarcodeScannerColors
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.permissionText,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onAllowClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.permissionButtonContainer,
                contentColor = colors.permissionButtonContent
            )
        ) { Text(buttonText) }
    }
}

@Composable
private fun ProcessingOverlay(text: String, colors: BarcodeScannerColors) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.processingScrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = colors.processingIndicator)
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = colors.processingText)
        }
    }
}
