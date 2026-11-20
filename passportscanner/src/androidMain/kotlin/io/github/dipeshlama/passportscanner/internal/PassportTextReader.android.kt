package io.github.dipeshlama.passportscanner.internal

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.graphics.toComposeRect
import io.github.dipeshlama.passportscanner.mrz.MrzParser
import io.github.dipeshlama.passportscanner.mrz.OcrLine
import io.github.dipeshlama.passportscanner.mrz.PassportMrzData
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val PASSPORT_IMAGE_MAX_EDGE = 1600
private const val PASSPORT_IMAGE_JPEG_QUALITY = 85

internal object PassportTextReader {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun process(image: InputImage): Task<Text> = recognizer.process(image)

    suspend fun readMrz(image: InputImage): PassportMrzData? =
        MrzParser.find(process(image).await().ocrLines())?.data
}

internal fun Text.ocrLines(): List<OcrLine> =
    textBlocks.flatMap { it.lines }
        .sortedBy { it.boundingBox?.top ?: 0 }
        .map { OcrLine(text = it.text, bounds = it.boundingBox?.toComposeRect() ?: Rect.Zero) }

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

internal fun Bitmap.uprightAndScaled(rotationDegrees: Int, maxEdge: Int = PASSPORT_IMAGE_MAX_EDGE): Bitmap {
    val scale = minOf(1f, maxEdge.toFloat() / maxOf(width, height))
    if (rotationDegrees == 0 && scale == 1f) return this
    val matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
        postScale(scale, scale)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

internal fun Bitmap.uprightRegion(rotationDegrees: Int, uprightRect: IntRect): Bitmap {
    val r = uprightRect
    val source = when ((rotationDegrees % 360 + 360) % 360) {
        90 -> android.graphics.Rect(r.top, height - r.right, r.bottom, height - r.left)
        180 -> android.graphics.Rect(width - r.right, height - r.bottom, width - r.left, height - r.top)
        270 -> android.graphics.Rect(width - r.bottom, r.left, width - r.top, r.right)
        else -> android.graphics.Rect(r.left, r.top, r.right, r.bottom)
    }
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(this, source.left, source.top, source.width(), source.height(), matrix, true)
}

internal fun Bitmap.toPassportPhoto(rotationDegrees: Int, crop: PreviewCrop?): Bitmap {
    val upright = uprightAndScaled(rotationDegrees, maxEdge = Int.MAX_VALUE)
    val cropped = crop?.toImageRect(upright.width, upright.height)
        ?.let { Bitmap.createBitmap(upright, it.left, it.top, it.width, it.height) }
        ?: upright
    return cropped.uprightAndScaled(rotationDegrees = 0)
}

internal fun Bitmap.toJpegBytes(): ByteArray =
    ByteArrayOutputStream().use { stream ->
        compress(Bitmap.CompressFormat.JPEG, PASSPORT_IMAGE_JPEG_QUALITY, stream)
        stream.toByteArray()
    }
