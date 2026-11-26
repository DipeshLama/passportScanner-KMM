package io.github.dipeshlama.barcodescanner

import io.github.dipeshlama.barcodescanner.internal.decodeBarcodeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a QR code or barcode from an image without any UI, for images that come from somewhere
 * other than [BarcodeScanner] (a file, a download, another camera library) or for background work.
 *
 * Codes are read on the device with the same engine as [BarcodeScanner]: ML Kit on Android and
 * Apple Vision on iOS.
 */
public object BarcodeImageScanner {
    /**
     * Reads the first code of [formats] in [image], an encoded image such as a JPEG or PNG. EXIF
     * orientation is applied, and the image is scaled down to at most 2048 px on its longest edge
     * before reading.
     *
     * Runs off the main thread, so it's safe to call from any coroutine.
     *
     * @return the code, or null when the image holds no code of [formats].
     * @throws IllegalArgumentException if [image] can't be decoded as an image, or [formats] is empty.
     */
    public suspend fun scanImage(
        image: ByteArray,
        formats: Set<BarcodeFormat> = BarcodeFormat.All,
    ): BarcodeScanResult? = withContext(Dispatchers.Default) {
        require(formats.isNotEmpty()) { "formats must not be empty" }
        val decoded = requireNotNull(decodeBarcodeImage(image)) { "The bytes can't be decoded as an image." }
        decoded.read(formats)
    }
}
