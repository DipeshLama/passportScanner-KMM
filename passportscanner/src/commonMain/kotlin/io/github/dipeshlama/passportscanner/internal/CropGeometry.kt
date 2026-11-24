package io.github.dipeshlama.passportscanner.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** The whole image, in normalized (0..1) image coordinates. */
internal val FullCrop = Rect(0f, 0f, 1f, 1f)

/** What a drag on the crop overlay moves. */
internal enum class CropHandle(val movesLeft: Boolean, val movesTop: Boolean, val movesRight: Boolean, val movesBottom: Boolean) {
    TopLeft(true, true, false, false),
    TopRight(false, true, true, false),
    BottomLeft(true, false, false, true),
    BottomRight(false, false, true, true),
    Left(true, false, false, false),
    Top(false, true, false, false),
    Right(false, false, true, false),
    Bottom(false, false, false, true),
    Move(true, true, true, true),
}

/** Where an image of [imageSize] is drawn inside [containerSize] with `ContentScale.Fit`, centered. */
internal fun fitImage(imageSize: Size, containerSize: Size): Rect {
    if (imageSize.width <= 0f || imageSize.height <= 0f || containerSize.width <= 0f || containerSize.height <= 0f) {
        return Rect.Zero
    }
    val scale = min(containerSize.width / imageSize.width, containerSize.height / imageSize.height)
    val width = imageSize.width * scale
    val height = imageSize.height * scale
    val left = (containerSize.width - width) / 2
    val top = (containerSize.height - height) / 2
    return Rect(left, top, left + width, top + height)
}

/** Maps a normalized crop onto [bounds], the on-screen rect the image is drawn in. */
internal fun Rect.toScreen(bounds: Rect): Rect = Rect(
    left = bounds.left + left * bounds.width,
    top = bounds.top + top * bounds.height,
    right = bounds.left + right * bounds.width,
    bottom = bounds.top + bottom * bounds.height,
)

/**
 * Which handle a touch at [point] grabs, for a crop drawn at [crop] on screen.
 * Corners win over edges, and edges over moving. Null when the touch misses the crop.
 */
internal fun hitTestCrop(crop: Rect, point: Offset, touchSlop: Float): CropHandle? {
    val nearLeft = abs(point.x - crop.left) <= touchSlop
    val nearRight = abs(point.x - crop.right) <= touchSlop
    val nearTop = abs(point.y - crop.top) <= touchSlop
    val nearBottom = abs(point.y - crop.bottom) <= touchSlop
    val withinX = point.x in (crop.left - touchSlop)..(crop.right + touchSlop)
    val withinY = point.y in (crop.top - touchSlop)..(crop.bottom + touchSlop)
    if (!withinX || !withinY) return null
    return when {
        nearLeft && nearTop -> CropHandle.TopLeft
        nearRight && nearTop -> CropHandle.TopRight
        nearLeft && nearBottom -> CropHandle.BottomLeft
        nearRight && nearBottom -> CropHandle.BottomRight
        nearLeft -> CropHandle.Left
        nearRight -> CropHandle.Right
        nearTop -> CropHandle.Top
        nearBottom -> CropHandle.Bottom
        crop.contains(point) -> CropHandle.Move
        else -> null
    }
}

/**
 * Moves the [handle] of a normalized crop by [delta] (also normalized), keeping it inside the image
 * and at least [minSize] (normalized) wide and tall.
 */
internal fun Rect.dragBy(handle: CropHandle, delta: Offset, minSize: Size): Rect {
    val minWidth = minSize.width.coerceIn(0f, 1f)
    val minHeight = minSize.height.coerceIn(0f, 1f)
    if (handle == CropHandle.Move) {
        val dx = delta.x.coerceIn(-left, 1f - right)
        val dy = delta.y.coerceIn(-top, 1f - bottom)
        return translate(dx, dy)
    }
    val newLeft = if (handle.movesLeft) (left + delta.x).coerceIn(0f, right - minWidth) else left
    val newRight = if (handle.movesRight) (right + delta.x).coerceIn(left + minWidth, 1f) else right
    val newTop = if (handle.movesTop) (top + delta.y).coerceIn(0f, bottom - minHeight) else top
    val newBottom = if (handle.movesBottom) (bottom + delta.y).coerceIn(top + minHeight, 1f) else bottom
    return Rect(newLeft, newTop, newRight, newBottom)
}

/** Converts a normalized crop to pixels of an image of [width] x [height]. Null if it covers the whole image. */
internal fun Rect.toPixelCrop(width: Int, height: Int): IntRect? {
    if (width <= 0 || height <= 0) return null
    val rect = IntRect(
        left = (left.coerceIn(0f, 1f) * width).roundToInt(),
        top = (top.coerceIn(0f, 1f) * height).roundToInt(),
        right = (right.coerceIn(0f, 1f) * width).roundToInt(),
        bottom = (bottom.coerceIn(0f, 1f) * height).roundToInt(),
    )
    if (rect.width <= 0 || rect.height <= 0) return null
    if (rect.left == 0 && rect.top == 0 && rect.right == width && rect.bottom == height) return null
    return rect
}
