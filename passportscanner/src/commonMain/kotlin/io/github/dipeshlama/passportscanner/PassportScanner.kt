package io.github.dipeshlama.passportscanner

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import io.github.dipeshlama.passportscanner.internal.CameraScan
import io.github.dipeshlama.passportscanner.internal.ExpiredSheet
import io.github.dipeshlama.passportscanner.internal.ImageCropScreen
import io.github.dipeshlama.passportscanner.internal.PassportCameraController
import io.github.dipeshlama.passportscanner.internal.PassportCameraView
import io.github.dipeshlama.passportscanner.internal.PickedImage
import io.github.dipeshlama.passportscanner.internal.PreviewCrop
import io.github.dipeshlama.passportscanner.internal.ReviewSheet
import io.github.dipeshlama.passportscanner.internal.ScannerIcons
import io.github.dipeshlama.passportscanner.internal.ScannerState
import io.github.dipeshlama.passportscanner.internal.SheetStyle
import io.github.dipeshlama.passportscanner.internal.TipsSheet
import io.github.dipeshlama.passportscanner.internal.UnreadableSheet
import io.github.dipeshlama.passportscanner.internal.rememberCameraPermission
import io.github.dipeshlama.passportscanner.internal.rememberPassportGalleryPicker

private const val FRAME_ASPECT_RATIO = 125f / 88f
private const val FRAME_WIDTH_FRACTION = 0.88f
private val FrameCornerRadius = 16.dp
private val FrameBracketLength = 32.dp
private val FrameBracketWidth = 3.dp
private val ScanLineBandHeight = 47.dp

/**
 * Full-screen passport scanner with live auto-scan, manual capture and gallery import, with optional
 * cropping of gallery photos ([PassportScannerConfig.cropGalleryImage]).
 *
 * [onResult] is called once when a scan is complete; remove the scanner from composition after
 * that. [onCancel] is called when the user goes back, from the top bar or with system back.
 *
 * On iOS, add `NSCameraUsageDescription` to the app's Info.plist.
 *
 * @param config behaviour options.
 * @param strings text shown by the scanner.
 * @param colors colours of the camera overlay, including the scan line.
 * @param sheetColors colours of the review, unreadable and tips sheets.
 * @param sheetTypography text styles (and fonts) of the review, unreadable and tips sheets.
 * @param title replaces the top bar title. Null uses [PassportScannerStrings.title].
 * @param instruction replaces the message above the frame. Null uses [PassportScannerStrings.instruction].
 * @param galleryButton replaces the gallery button. Null uses [PassportScannerDefaults.GalleryButton].
 * @param captureButton replaces the capture button. Null uses [PassportScannerDefaults.CaptureButton].
 * @param captureHint replaces the message under the capture button. Null uses [PassportScannerStrings.captureHint].
 */
@Composable
public fun PassportScanner(
    onResult: (PassportScanResult) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    config: PassportScannerConfig = PassportScannerConfig(),
    strings: PassportScannerStrings = PassportScannerStrings(),
    colors: PassportScannerColors = PassportScannerDefaults.colors(),
    sheetColors: PassportScannerSheetColors = PassportScannerDefaults.sheetColors(),
    sheetTypography: PassportScannerSheetTypography = PassportScannerDefaults.sheetTypography(),
    title: (@Composable () -> Unit)? = null,
    instruction: (@Composable () -> Unit)? = null,
    galleryButton: (@Composable (onClick: () -> Unit, enabled: Boolean) -> Unit)? = null,
    captureButton: (@Composable (onClick: () -> Unit, enabled: Boolean) -> Unit)? = null,
    captureHint: (@Composable () -> Unit)? = null,
) {
    val currentConfig by rememberUpdatedState(config)
    val currentOnResult by rememberUpdatedState(onResult)
    val state = remember { ScannerState(config = { currentConfig }) }
    val deliver: (PassportScanResult?) -> Unit = { result -> result?.let(currentOnResult) }

    val currentOnCancel by rememberUpdatedState(onCancel)
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        // The crop screen handles back itself, to return to the camera.
        isBackEnabled = !state.isFinished && state.cropImage == null,
        onBackCompleted = { currentOnCancel() }
    )

    val cameraController = remember { PassportCameraController() }
    val cameraPermission = rememberCameraPermission()
    val isCameraGranted = cameraPermission.isGranted

    LaunchedEffect(Unit) {
        if (!cameraPermission.isGranted) cameraPermission.request()
    }

    val scope = rememberCoroutineScope()
    val readPicked: (PickedImage, IntRect?) -> Unit = { image, crop ->
        scope.launch { deliver(state.onScan(image.read(crop))) }
    }
    val launchGallery = rememberPassportGalleryPicker(
        onLoading = state::onProcessingStarted,
        onPicked = { image ->
            if (image == null) {
                state.onGalleryCancelled()
            } else {
                state.onGalleryPicked(image)?.let { readPicked(it, null) }
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
            PassportCameraView(
                controller = cameraController,
                isAutoScanEnabled = state.isAutoScanEnabled,
                crop = previewCrop,
                onCaptureStarted = state::onProcessingStarted,
                onResult = { scan: CameraScan -> deliver(state.onScan(scan)) },
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
                    onBack = onCancel,
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
                    if (title != null) title() else PassportScannerDefaults.Title(strings.title)
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
                        PassportScannerDefaults.Instruction(strings.instruction, backgroundColor = colors.chipBackground)
                    }

                    ScanFrame(
                        isScanning = isCameraGranted && state.isAutoScanEnabled,
                        scanLineColor = colors.scanLine,
                        cornerColor = colors.frameCorners,
                        modifier = Modifier
                            .fillMaxWidth(FRAME_WIDTH_FRACTION)
                            .aspectRatio(FRAME_ASPECT_RATIO)
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

                    Row(
                        modifier = Modifier.fillMaxWidth(FRAME_WIDTH_FRACTION),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (config.allowGallery) {
                            val galleryEnabled = !state.isProcessing && !state.isFinished
                            if (galleryButton != null) {
                                galleryButton(launchGallery, galleryEnabled)
                            } else {
                                PassportScannerDefaults.GalleryButton(
                                    text = strings.gallery,
                                    onClick = launchGallery,
                                    enabled = galleryEnabled,
                                    backgroundColor = colors.chipBackground
                                )
                            }
                        } else {
                            Spacer(Modifier)
                        }
                        if (config.showTips) {
                            IconButton(onClick = { state.showTips(true) }) {
                                Icon(ScannerIcons.Info, contentDescription = strings.tips)
                            }
                        }
                    }
                }

                val captureEnabled = isCameraGranted && !state.isProcessing && !state.isFinished
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (captureButton != null) {
                        captureButton(cameraController::capture, captureEnabled)
                    } else {
                        PassportScannerDefaults.CaptureButton(
                            onClick = cameraController::capture,
                            enabled = captureEnabled,
                            contentDescription = strings.capture
                        )
                    }
                    if (captureHint != null) captureHint() else PassportScannerDefaults.CaptureHint(strings.captureHint)
                }
            }
        }

        state.cropImage?.let { image ->
            ImageCropScreen(
                image = image,
                strings = strings,
                colors = colors,
                onCancel = state::cancelCrop,
                onConfirm = { crop -> state.confirmCrop()?.let { readPicked(it, crop) } }
            )
        }

        if (state.isProcessing) {
            ProcessingOverlay(text = strings.processing, colors = colors)
        }
    }

    val sheetStyle = remember(sheetColors, sheetTypography) { SheetStyle(sheetColors, sheetTypography) }

    UnreadableSheet(
        scan = state.unreadableScan,
        strings = strings,
        style = sheetStyle,
        allowImageOnly = config.allowImageOnlyResult,
        onRetry = state::retry,
        onUsePhoto = { deliver(state.useUnreadablePhoto()) }
    )

    ExpiredSheet(
        scan = state.expiredScan,
        strings = strings,
        style = sheetStyle,
        onScanAnother = state::dismissExpired
    )

    ReviewSheet(
        data = state.reviewScan?.data,
        strings = strings,
        style = sheetStyle,
        onRescan = state::rescan,
        onConfirm = { deliver(state.confirmReview()) }
    )

    if (config.showTips) {
        TipsSheet(
            isVisible = state.isTipsVisible,
            strings = strings,
            style = sheetStyle,
            onDismiss = { state.showTips(false) }
        )
    }
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
    val band = ScanLineBandHeight.toPx()
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
    colors: PassportScannerColors
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
private fun ProcessingOverlay(text: String, colors: PassportScannerColors) {
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
