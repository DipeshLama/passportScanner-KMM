package io.github.dipeshlama.barcodescanner.internal

import io.github.dipeshlama.barcodescanner.BarcodeFormat
import io.github.dipeshlama.barcodescanner.BarcodeScanResult

/**
 * Converts an Apple Vision barcode to a result, or null when it isn't one of [formats].
 *
 * [symbology] is matched by name ("VNBarcodeSymbologyQR" or "QR") instead of against Vision's
 * constants, because some constants don't exist on older iOS versions and referencing them would
 * stop the app from launching there. Lives in common code so it can be unit tested.
 */
internal fun appleBarcode(symbology: String, value: String, formats: Set<BarcodeFormat>): BarcodeScanResult? {
    val format = appleFormat(symbology) ?: return null
    // Vision reports UPC-A as EAN-13 with a leading 0; ML Kit reports it as UPC-A.
    if (format == BarcodeFormat.Ean13 && value.length == 13 && value.startsWith('0') && BarcodeFormat.UpcA in formats) {
        return BarcodeScanResult(value.drop(1), BarcodeFormat.UpcA)
    }
    return if (format in formats) BarcodeScanResult(value, format) else null
}

/**
 * The Vision symbologies to ask for so that only [formats] are detected, picked from [supported]
 * (what the running iOS version's request can read). EAN-13 is kept for UPC-A, which Vision
 * reports as EAN-13 with a leading 0.
 */
internal fun appleSymbologiesFor(supported: List<String>, formats: Set<BarcodeFormat>): List<String> {
    val wanted = if (BarcodeFormat.UpcA in formats) formats + BarcodeFormat.Ean13 else formats
    return supported.filter { appleFormat(it) in wanted }
}

private fun appleFormat(symbology: String): BarcodeFormat? =
    when (symbology.removePrefix("VNBarcodeSymbology").lowercase()) {
        "qr", "microqr" -> BarcodeFormat.QrCode
        "aztec" -> BarcodeFormat.Aztec
        "datamatrix" -> BarcodeFormat.DataMatrix
        "pdf417", "micropdf417" -> BarcodeFormat.Pdf417
        "code128" -> BarcodeFormat.Code128
        "code39", "code39checksum", "code39fullascii", "code39fullasciichecksum" -> BarcodeFormat.Code39
        "code93", "code93i" -> BarcodeFormat.Code93
        "codabar" -> BarcodeFormat.Codabar
        "ean13" -> BarcodeFormat.Ean13
        "ean8" -> BarcodeFormat.Ean8
        "itf14", "i2of5", "i2of5checksum" -> BarcodeFormat.Itf
        "upce" -> BarcodeFormat.UpcE
        else -> null
    }
