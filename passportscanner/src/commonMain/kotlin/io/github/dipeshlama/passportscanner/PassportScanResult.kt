package io.github.dipeshlama.passportscanner

import io.github.dipeshlama.passportscanner.mrz.PassportMrzData

/** Result returned by [PassportScanner]. */
public sealed interface PassportScanResult {
    /** JPEG of the passport page (longest edge at most 1600 px), if one was captured. */
    public val image: ByteArray?

    /** The MRZ was read and all check digits are valid. */
    public class Success(
        public val data: PassportMrzData,
        override val image: ByteArray?
    ) : PassportScanResult {
        override fun toString(): String = "Success(data=$data, image=${image?.size} bytes)"
    }

    /** The MRZ couldn't be read and the user chose to keep the photo. */
    public class ImageOnly(
        override val image: ByteArray
    ) : PassportScanResult {
        override fun toString(): String = "ImageOnly(image=${image.size} bytes)"
    }
}
