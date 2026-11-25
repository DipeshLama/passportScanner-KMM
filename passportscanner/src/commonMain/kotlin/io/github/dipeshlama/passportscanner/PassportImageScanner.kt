package io.github.dipeshlama.passportscanner

import io.github.dipeshlama.passportscanner.internal.decodePassportImage
import io.github.dipeshlama.passportscanner.mrz.MrzParser
import io.github.dipeshlama.passportscanner.mrz.PassportMrzData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a passport MRZ from an image without any UI, for batch processing, background work or
 * images that come from somewhere other than [PassportScanner] (a file, a download, another camera
 * library).
 *
 * OCR runs on the device with the same engine as [PassportScanner]: ML Kit on Android and Apple
 * Vision on iOS. To parse MRZ text you already have, use [MrzParser] directly.
 */
public object PassportImageScanner {
    /**
     * Reads the MRZ from [image], an encoded image such as a JPEG or PNG. EXIF orientation is
     * applied, and the image is scaled down to at most 1600 px on its longest edge before reading.
     *
     * Runs off the main thread, so it's safe to call from any coroutine.
     *
     * @return the passport details when an MRZ was found and every check digit is valid, or null
     *   when the image holds no readable MRZ.
     * @throws IllegalArgumentException if [image] can't be decoded as an image.
     */
    public suspend fun scanImage(image: ByteArray): PassportMrzData? = withContext(Dispatchers.Default) {
        val decoded = requireNotNull(decodePassportImage(image)) { "The bytes can't be decoded as an image." }
        decoded.readMrz()
    }
}
