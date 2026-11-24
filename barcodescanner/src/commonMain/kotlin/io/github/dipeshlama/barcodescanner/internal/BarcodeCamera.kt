package io.github.dipeshlama.barcodescanner.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import io.github.dipeshlama.barcodescanner.BarcodeFormat
import io.github.dipeshlama.barcodescanner.BarcodeScanResult
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest edge, in pixels, that gallery photos are scaled down to before they're read. */
internal const val GALLERY_IMAGE_MAX_EDGE = 2048

/**
 * How far past the frame, as a share of its size, a code may reach and still be read live. Codes are
 * rarely held perfectly inside the frame, and a frame-sized code overlaps it a little.
 */
private const val READING_MARGIN_RATIO = 0.1f

/** Position of the scan frame over the camera preview, in preview pixels. */
internal data class PreviewCrop(
    val previewWidth: Float,
    val previewHeight: Float,
    val frame: Rect
)

/** The area of an upright camera image of [imageWidth] x [imageHeight] in which codes are read. */
internal fun PreviewCrop.toReadingRect(imageWidth: Int, imageHeight: Int): IntRect? {
    if (previewWidth <= 0f || previewHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) return null
    // The preview fills the screen and is center-cropped, like ContentScale.Crop.
    val scale = max(previewWidth / imageWidth, previewHeight / imageHeight)
    val overflowX = (imageWidth * scale - previewWidth) / 2
    val overflowY = (imageHeight * scale - previewHeight) / 2
    val marginX = frame.width * READING_MARGIN_RATIO
    val marginY = frame.height * READING_MARGIN_RATIO

    fun toImageX(x: Float) = ((x + overflowX) / scale).coerceIn(0f, imageWidth.toFloat()).roundToInt()
    fun toImageY(y: Float) = ((y + overflowY) / scale).coerceIn(0f, imageHeight.toFloat()).roundToInt()

    val rect = IntRect(
        left = toImageX(frame.left - marginX),
        top = toImageY(frame.top - marginY),
        right = toImageX(frame.right + marginX),
        bottom = toImageY(frame.bottom + marginY)
    )
    return rect.takeIf { it.width > 0 && it.height > 0 }
}

/** True when a code found at [bounds] (upright image pixels) lies inside the reading area. */
internal fun PreviewCrop?.contains(bounds: Rect, imageWidth: Int, imageHeight: Int): Boolean {
    val area = this?.toReadingRect(imageWidth, imageHeight) ?: return true
    return bounds.left >= area.left && bounds.top >= area.top &&
        bounds.right <= area.right && bounds.bottom <= area.bottom
}

internal class BarcodeCameraController {
    internal var torchHandler: ((Boolean) -> Unit)? = null

    /** Set by the platform camera once it knows whether the active camera has a flashlight. */
    var hasTorch by mutableStateOf(false)
        internal set

    /** Set by the platform camera whenever the flashlight turns on or off. */
    var isTorchOn by mutableStateOf(false)
        internal set

    fun setTorch(on: Boolean) {
        torchHandler?.invoke(on)
    }
}

/**
 * Live camera preview that reads codes of [formats] inside [crop] while [isAutoScanEnabled], and
 * calls [onResult] once per enabled period with the first one found.
 */
@Composable
internal expect fun BarcodeCameraView(
    controller: BarcodeCameraController,
    formats: Set<BarcodeFormat>,
    isAutoScanEnabled: Boolean,
    crop: PreviewCrop?,
    onResult: (BarcodeScanResult) -> Unit,
    modifier: Modifier = Modifier
)

/** A photo picked from the gallery, already upright and scaled down, not yet read. */
internal interface PickedImage {
    val width: Int
    val height: Int

    /** For showing the photo in the crop screen. */
    fun toImageBitmap(): ImageBitmap?

    /** Reads the first code of [formats] in the photo, or in [crop] of it (in pixels) when given. */
    suspend fun read(formats: Set<BarcodeFormat>, crop: IntRect? = null): BarcodeScanResult?
}

/**
 * Returns a function that opens the system photo picker. [onLoading] is called once a photo is
 * chosen and is being loaded; [onPicked] is called with the photo, or with null when the user
 * cancels or the photo can't be loaded.
 */
@Composable
internal expect fun rememberGalleryPicker(
    onLoading: () -> Unit,
    onPicked: (PickedImage?) -> Unit
): () -> Unit
