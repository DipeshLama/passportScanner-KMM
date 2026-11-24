package io.github.dipeshlama.barcodescanner

import androidx.compose.runtime.Immutable

/**
 * Behaviour options for [BarcodeScanner].
 *
 * @property formats the symbologies to look for. Fewer formats scan faster and avoid picking up an
 *   unrelated code nearby. Must not be empty.
 * @property frameAspectRatio width / height of the scan frame. `1f` (the default) is a square, which
 *   suits QR codes; use a wider frame such as `2f` for linear barcodes. Must be greater than 0.
 * @property allowGallery show the button for picking a photo from the gallery.
 * @property cropGalleryImage after a photo is picked from the gallery, let the user crop it to the
 *   code before it's read. Useful when the photo has more than one code. Off by default.
 * @property showTorch show the flashlight button when the device has one.
 * @property hapticFeedback vibrate briefly when a code is read.
 */
@Immutable
public data class BarcodeScannerConfig(
    val formats: Set<BarcodeFormat> = BarcodeFormat.All,
    val frameAspectRatio: Float = 1f,
    val allowGallery: Boolean = true,
    val cropGalleryImage: Boolean = false,
    val showTorch: Boolean = true,
    val hapticFeedback: Boolean = true,
) {
    init {
        require(formats.isNotEmpty()) { "formats must not be empty" }
        require(frameAspectRatio > 0f) { "frameAspectRatio must be greater than 0" }
    }
}

/** Text shown by [BarcodeScanner]. Override any value to change or translate it. */
@Immutable
public data class BarcodeScannerStrings(
    val title: String = "Scan code",
    val instruction: String = "Point the camera at a QR code or barcode",
    val back: String = "Back",
    val gallery: String = "Upload from gallery",
    val processing: String = "Looking for a code…",
    val noCodeFound: String = "No QR code or barcode found in this photo",
    val permissionDescription: String =
        "Allow camera access to scan codes, or upload a photo from your gallery.",
    val allowCamera: String = "Allow camera",
    val torchOn: String = "Turn on flashlight",
    val torchOff: String = "Turn off flashlight",

    val cropTitle: String = "Crop photo",
    val cropInstruction: String = "Drag the corners around the code",
    val cropCancel: String = "Cancel",
    val cropReset: String = "Reset",
    val cropConfirm: String = "Continue",
)
