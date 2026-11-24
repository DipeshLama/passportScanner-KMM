package io.github.dipeshlama.passportscanner.internal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.dipeshlama.passportscanner.PassportScannerColors
import io.github.dipeshlama.passportscanner.PassportScannerDefaults
import io.github.dipeshlama.passportscanner.PassportScannerStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HandleTouchSlop = 28.dp
private val MinCropSize = 64.dp
private val HandleLength = 22.dp
private val HandleWidth = 4.dp
private val BorderWidth = 1.5.dp
private val GridWidth = 1.dp

/**
 * Full-screen editor for cropping a gallery photo before it's read. [onConfirm] gets the crop in
 * the photo's pixels, or null when the whole photo is kept.
 */
@Composable
internal fun ImageCropScreen(
    image: PickedImage,
    strings: PassportScannerStrings,
    colors: PassportScannerColors,
    onCancel: () -> Unit,
    onConfirm: (crop: IntRect?) -> Unit,
) {
    val currentOnCancel by rememberUpdatedState(onCancel)
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = { currentOnCancel() }
    )

    val bitmap by produceState<ImageBitmap?>(initialValue = null, image) {
        value = withContext(Dispatchers.Default) { image.toImageBitmap() }
    }
    var crop by remember(image) { mutableStateOf(FullCrop) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Keeps touches from reaching the scanner underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(ScannerIcons.ArrowBack, contentDescription = strings.cropCancel)
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        PassportScannerDefaults.Title(strings.cropTitle)
                    }
                    TextButton(
                        onClick = { crop = FullCrop },
                        enabled = crop != FullCrop,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colors.content,
                            disabledContentColor = colors.content.copy(alpha = 0.38f)
                        )
                    ) { Text(strings.cropReset) }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PassportScannerDefaults.Instruction(strings.cropInstruction, backgroundColor = colors.chipBackground)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val loaded = bitmap
                    if (loaded == null) {
                        CircularProgressIndicator(color = colors.processingIndicator)
                    } else {
                        CropArea(
                            bitmap = loaded,
                            imageSize = Size(image.width.toFloat(), image.height.toFloat()),
                            crop = crop,
                            onCropChange = { crop = it },
                            colors = colors
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.content)
                    ) { Text(strings.cropCancel, textAlign = TextAlign.Center) }
                    Button(
                        onClick = { onConfirm(crop.toPixelCrop(image.width, image.height)) },
                        enabled = bitmap != null,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.permissionButtonContainer,
                            contentColor = colors.permissionButtonContent
                        )
                    ) { Text(strings.cropConfirm, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

@Composable
private fun CropArea(
    bitmap: ImageBitmap,
    imageSize: Size,
    crop: Rect,
    onCropChange: (Rect) -> Unit,
    colors: PassportScannerColors,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val bounds = remember(containerSize, imageSize) { fitImage(imageSize, containerSize.toSize()) }
    val currentCrop by rememberUpdatedState(crop)
    val currentOnCropChange by rememberUpdatedState(onCropChange)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bounds) {
                    if (bounds.width <= 0f || bounds.height <= 0f) return@pointerInput
                    val minSize = Size(MinCropSize.toPx() / bounds.width, MinCropSize.toPx() / bounds.height)
                    var handle: CropHandle? = null
                    detectDragGestures(
                        onDragStart = { start ->
                            handle = hitTestCrop(currentCrop.toScreen(bounds), start, HandleTouchSlop.toPx())
                        },
                        onDragEnd = { handle = null },
                        onDragCancel = { handle = null },
                        onDrag = { change, amount ->
                            val active = handle ?: return@detectDragGestures
                            change.consume()
                            val delta = Offset(amount.x / bounds.width, amount.y / bounds.height)
                            currentOnCropChange(currentCrop.dragBy(active, delta, minSize))
                        }
                    )
                }
        ) {
            if (bounds.width > 0f && bounds.height > 0f) {
                drawCropOverlay(crop.toScreen(bounds), bounds, colors)
            }
        }
    }
}

private fun DrawScope.drawCropOverlay(crop: Rect, bounds: Rect, colors: PassportScannerColors) {
    // Dim the parts of the image outside the crop: above, below, left and right of it.
    listOf(
        Rect(bounds.left, bounds.top, bounds.right, crop.top),
        Rect(bounds.left, crop.bottom, bounds.right, bounds.bottom),
        Rect(bounds.left, crop.top, crop.left, crop.bottom),
        Rect(crop.right, crop.top, bounds.right, crop.bottom),
    ).forEach { area ->
        if (area.width > 0f && area.height > 0f) drawRect(colors.scrim, topLeft = area.topLeft, size = area.size)
    }

    val gridColor = colors.content.copy(alpha = 0.4f)
    val gridStroke = GridWidth.toPx()
    for (i in 1..2) {
        val x = crop.left + crop.width * i / 3
        val y = crop.top + crop.height * i / 3
        drawLine(gridColor, Offset(x, crop.top), Offset(x, crop.bottom), gridStroke)
        drawLine(gridColor, Offset(crop.left, y), Offset(crop.right, y), gridStroke)
    }

    drawRect(colors.frameCorners, topLeft = crop.topLeft, size = crop.size, style = Stroke(BorderWidth.toPx()))

    val stroke = HandleWidth.toPx()
    val inset = stroke / 2
    val arm = HandleLength.toPx().coerceAtMost(minOf(crop.width, crop.height) / 2)
    val handle = Path().apply {
        moveTo(crop.left + inset, crop.top + arm)
        lineTo(crop.left + inset, crop.top + inset)
        lineTo(crop.left + arm, crop.top + inset)
    }
    val style = Stroke(width = stroke, cap = StrokeCap.Round)
    val pivot = crop.center
    listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f).forEach { (scaleX, scaleY) ->
        withTransform({ scale(scaleX, scaleY, pivot) }) {
            drawPath(handle, colors.frameCorners, style = style)
        }
    }
}
