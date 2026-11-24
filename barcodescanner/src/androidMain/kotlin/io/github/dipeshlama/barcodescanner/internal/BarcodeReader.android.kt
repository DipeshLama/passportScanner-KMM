package io.github.dipeshlama.barcodescanner.internal

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import io.github.dipeshlama.barcodescanner.BarcodeFormat
import io.github.dipeshlama.barcodescanner.BarcodeScanResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** An ML Kit scanner that only looks for [formats]. Close it when done. */
internal fun barcodeClient(formats: Set<BarcodeFormat>): BarcodeScanner {
    val codes = formats.map { it.toMlKit() }
    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(codes.first(), *codes.drop(1).toIntArray())
        .build()
    return BarcodeScanning.getClient(options)
}

internal fun Barcode.toResult(formats: Set<BarcodeFormat>): BarcodeScanResult? {
    val value = rawValue ?: return null
    val format = mlKitFormat(format) ?: return null
    return if (format in formats) BarcodeScanResult(value, format) else null
}

private fun BarcodeFormat.toMlKit(): Int = when (this) {
    BarcodeFormat.QrCode -> Barcode.FORMAT_QR_CODE
    BarcodeFormat.Aztec -> Barcode.FORMAT_AZTEC
    BarcodeFormat.DataMatrix -> Barcode.FORMAT_DATA_MATRIX
    BarcodeFormat.Pdf417 -> Barcode.FORMAT_PDF417
    BarcodeFormat.Code128 -> Barcode.FORMAT_CODE_128
    BarcodeFormat.Code39 -> Barcode.FORMAT_CODE_39
    BarcodeFormat.Code93 -> Barcode.FORMAT_CODE_93
    BarcodeFormat.Codabar -> Barcode.FORMAT_CODABAR
    BarcodeFormat.Ean13 -> Barcode.FORMAT_EAN_13
    BarcodeFormat.Ean8 -> Barcode.FORMAT_EAN_8
    BarcodeFormat.Itf -> Barcode.FORMAT_ITF
    BarcodeFormat.UpcA -> Barcode.FORMAT_UPC_A
    BarcodeFormat.UpcE -> Barcode.FORMAT_UPC_E
}

private fun mlKitFormat(format: Int): BarcodeFormat? = when (format) {
    Barcode.FORMAT_QR_CODE -> BarcodeFormat.QrCode
    Barcode.FORMAT_AZTEC -> BarcodeFormat.Aztec
    Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DataMatrix
    Barcode.FORMAT_PDF417 -> BarcodeFormat.Pdf417
    Barcode.FORMAT_CODE_128 -> BarcodeFormat.Code128
    Barcode.FORMAT_CODE_39 -> BarcodeFormat.Code39
    Barcode.FORMAT_CODE_93 -> BarcodeFormat.Code93
    Barcode.FORMAT_CODABAR -> BarcodeFormat.Codabar
    Barcode.FORMAT_EAN_13 -> BarcodeFormat.Ean13
    Barcode.FORMAT_EAN_8 -> BarcodeFormat.Ean8
    Barcode.FORMAT_ITF -> BarcodeFormat.Itf
    Barcode.FORMAT_UPC_A -> BarcodeFormat.UpcA
    Barcode.FORMAT_UPC_E -> BarcodeFormat.UpcE
    else -> null
}

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

internal fun Bitmap.uprightAndScaled(rotationDegrees: Int, maxEdge: Int = GALLERY_IMAGE_MAX_EDGE): Bitmap {
    val scale = minOf(1f, maxEdge.toFloat() / maxOf(width, height))
    if (rotationDegrees == 0 && scale == 1f) return this
    val matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
        postScale(scale, scale)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
