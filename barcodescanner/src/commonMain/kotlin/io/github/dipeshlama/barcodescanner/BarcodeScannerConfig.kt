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
 * @property beepOnScan play a short beep when a code is read. Off by default. It follows the
 *   device's notification volume on Android and the silent switch on iOS.
 * @property scanMode read one code and stop ([BarcodeScanMode.Single], the default), or keep reading
 *   codes until the user taps Done ([BarcodeScanMode.Continuous]).
 * @property pinchToZoom let the user zoom the camera with two fingers.
 * @property autoZoom zoom in on its own when a code is too far away to read. Android only (ML Kit's
 *   zoom suggestion); ignored on iOS. Off by default.
 */
@Immutable
public data class BarcodeScannerConfig(
    val formats: Set<BarcodeFormat> = BarcodeFormat.All,
    val frameAspectRatio: Float = 1f,
    val allowGallery: Boolean = true,
    val cropGalleryImage: Boolean = false,
    val showTorch: Boolean = true,
    val hapticFeedback: Boolean = true,
    val beepOnScan: Boolean = false,
    val scanMode: BarcodeScanMode = BarcodeScanMode.Single,
    val pinchToZoom: Boolean = true,
    val autoZoom: Boolean = false,
) {
    init {
        require(formats.isNotEmpty()) { "formats must not be empty" }
        require(frameAspectRatio > 0f) { "frameAspectRatio must be greater than 0" }
    }
}

/** Whether [BarcodeScanner] stops after one code or keeps reading. See [BarcodeScannerConfig.scanMode]. */
public sealed interface BarcodeScanMode {
    /** Read one code, deliver it and stop. */
    public data object Single : BarcodeScanMode

    /**
     * Keep reading codes until the user taps Done, which returns every code read. Each code is also
     * delivered as soon as it's read.
     *
     * @property delayMillis how long to wait after a code is read before reading the next one, so
     *   the user has time to move to the next code. Must not be negative.
     * @property skipDuplicates ignore a code (same value and format) that was already read in this
     *   session, so a code left in view isn't read again and again.
     */
    public data class Continuous(
        val delayMillis: Long = 1000,
        val skipDuplicates: Boolean = true,
    ) : BarcodeScanMode {
        init {
            require(delayMillis >= 0) { "delayMillis must not be negative" }
        }
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
    val done: String = "Done",

    val cropTitle: String = "Crop photo",
    val cropInstruction: String = "Drag the corners around the code",
    val cropCancel: String = "Cancel",
    val cropReset: String = "Reset",
    val cropConfirm: String = "Continue",
)
