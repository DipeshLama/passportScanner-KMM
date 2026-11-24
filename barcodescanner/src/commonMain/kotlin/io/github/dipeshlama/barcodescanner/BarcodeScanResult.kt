package io.github.dipeshlama.barcodescanner

import androidx.compose.runtime.Immutable

/**
 * A code read by [BarcodeScanner].
 *
 * @property value the decoded text. For a UPC-A code it's the 12 digits, without the leading `0`
 *   some readers add when they treat it as EAN-13.
 * @property format the symbology of the code.
 */
@Immutable
public data class BarcodeScanResult(
    val value: String,
    val format: BarcodeFormat,
)

/** Symbologies [BarcodeScanner] can read, on both Android and iOS. */
public enum class BarcodeFormat {
    QrCode,
    Aztec,
    DataMatrix,
    Pdf417,
    Code128,
    Code39,
    Code93,
    Codabar,
    Ean13,
    Ean8,
    Itf,
    UpcA,
    UpcE;

    /** True for square and stacked codes (QR, Aztec, Data Matrix, PDF417); false for linear barcodes. */
    public val is2D: Boolean
        get() = this == QrCode || this == Aztec || this == DataMatrix || this == Pdf417

    public companion object {
        /** Every format. */
        public val All: Set<BarcodeFormat> = entries.toSet()

        /** QR, Aztec, Data Matrix and PDF417. */
        public val TwoDimensional: Set<BarcodeFormat> = entries.filter { it.is2D }.toSet()

        /** Linear (1D) barcodes such as EAN-13, UPC-A and Code 128. */
        public val Linear: Set<BarcodeFormat> = entries.filterNot { it.is2D }.toSet()
    }
}
