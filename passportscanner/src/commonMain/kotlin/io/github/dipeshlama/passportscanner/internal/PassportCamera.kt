package io.github.dipeshlama.passportscanner.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import io.github.dipeshlama.passportscanner.mrz.PassportMrzData
import kotlin.math.max
import kotlin.math.roundToInt

private const val READING_MARGIN_RATIO = 0.04f

/** Output of the camera or gallery. [data] is null when the MRZ couldn't be read. */
internal class CameraScan(
    val data: PassportMrzData?,
    val image: ByteArray?
)

/** Position of the scan frame over the camera preview, in preview pixels. */
internal data class PreviewCrop(
    val previewWidth: Float,
    val previewHeight: Float,
    val frame: Rect
)

internal fun PreviewCrop.toImageRect(imageWidth: Int, imageHeight: Int): IntRect? =
    mapToImage(imageWidth, imageHeight, marginRatio = 0f)

internal fun PreviewCrop.toReadingRect(imageWidth: Int, imageHeight: Int): IntRect? =
    mapToImage(imageWidth, imageHeight, marginRatio = READING_MARGIN_RATIO)

private fun PreviewCrop.mapToImage(imageWidth: Int, imageHeight: Int, marginRatio: Float): IntRect? {
    if (previewWidth <= 0f || previewHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) return null
    val scale = max(previewWidth / imageWidth, previewHeight / imageHeight)
    val overflowX = (imageWidth * scale - previewWidth) / 2
    val overflowY = (imageHeight * scale - previewHeight) / 2
    val marginX = frame.width * marginRatio
    val marginY = frame.height * marginRatio

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

internal class PassportCameraController {
    internal var captureHandler: (() -> Unit)? = null
    internal var torchHandler: ((Boolean) -> Unit)? = null

    /** Set by the platform camera once it knows whether the active camera has a flashlight. */
    var hasTorch by mutableStateOf(false)
        internal set

    /** Set by the platform camera whenever the flashlight turns on or off. */
    var isTorchOn by mutableStateOf(false)
        internal set

    fun capture() {
        captureHandler?.invoke()
    }

    fun setTorch(on: Boolean) {
        torchHandler?.invoke(on)
    }
}

@Composable
internal expect fun PassportCameraView(
    controller: PassportCameraController,
    isAutoScanEnabled: Boolean,
    crop: PreviewCrop?,
    onCaptureStarted: () -> Unit,
    onResult: (CameraScan) -> Unit,
    modifier: Modifier = Modifier
)

/** A photo picked from the gallery, already upright and scaled down, not yet read. */
internal interface PickedImage {
    val width: Int
    val height: Int

    /** For showing the photo in the crop screen. */
    fun toImageBitmap(): ImageBitmap?

    /** Reads the MRZ from the photo, or from [crop] of it (in pixels) when given. */
    suspend fun read(crop: IntRect? = null): CameraScan

    /** Reads only the MRZ from the whole photo, without encoding a JPEG. */
    suspend fun readMrz(): PassportMrzData?
}

/** Decodes an encoded image (JPEG, PNG, HEIC…), upright and scaled down, or null if it can't be decoded. */
internal expect fun decodePassportImage(bytes: ByteArray): PickedImage?

/**
 * Returns a function that opens the system photo picker. [onLoading] is called once a photo is
 * chosen and is being loaded; [onPicked] is called with the photo, or with null when the user
 * cancels or the photo can't be loaded.
 */
@Composable
internal expect fun rememberPassportGalleryPicker(
    onLoading: () -> Unit,
    onPicked: (PickedImage?) -> Unit
): () -> Unit
