package io.github.dipeshlama.passportscanner

import io.github.dipeshlama.passportscanner.mrz.PassportMrzData

/** Result returned by [PassportScanner]. */
sealed interface PassportScanResult {
    /** JPEG of the passport page (longest edge at most 1600 px), if one was captured. */
    val image: ByteArray?

    /** The MRZ was read and all check digits are valid. */
    class Success(
        val data: PassportMrzData,
        override val image: ByteArray?
    ) : PassportScanResult {
        override fun toString(): String = "Success(data=$data, image=${image?.size} bytes)"
    }

    /** The MRZ couldn't be read and the user chose to keep the photo. */
    class ImageOnly(
        override val image: ByteArray
    ) : PassportScanResult {
        override fun toString(): String = "ImageOnly(image=${image.size} bytes)"
    }
}
