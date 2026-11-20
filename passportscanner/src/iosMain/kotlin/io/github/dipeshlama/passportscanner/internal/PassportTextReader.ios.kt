@file:OptIn(ExperimentalForeignApi::class)

package io.github.dipeshlama.passportscanner.internal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import io.github.dipeshlama.passportscanner.mrz.MrzMatch
import io.github.dipeshlama.passportscanner.mrz.MrzParser
import io.github.dipeshlama.passportscanner.mrz.OcrLine
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.posix.memcpy

internal const val PASSPORT_IMAGE_MAX_EDGE = 1600.0
private const val PASSPORT_IMAGE_JPEG_QUALITY = 0.85

internal object PassportTextReader {
    fun findMrz(
        handler: VNImageRequestHandler,
        imageWidth: Int,
        imageHeight: Int,
        region: IntRect? = null
    ): MrzMatch? {
        val area = region ?: IntRect(0, 0, imageWidth, imageHeight)
        val request = VNRecognizeTextRequest().apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            usesLanguageCorrection = false
            recognitionLanguages = listOf("en-US")
            regionOfInterest = CGRectMake(
                x = area.left.toDouble() / imageWidth,
                y = 1.0 - area.bottom.toDouble() / imageHeight,
                width = area.width.toDouble() / imageWidth,
                height = area.height.toDouble() / imageHeight
            )
        }
        if (!handler.performRequests(listOf(request), null)) return null

        val lines = request.results.orEmpty()
            .filterIsInstance<VNRecognizedTextObservation>()
            .mapNotNull { observation ->
                val text = (observation.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string
                    ?: return@mapNotNull null
                OcrLine(text = text, bounds = observation.pixelBounds(area))
            }
            .sortedBy { it.bounds.top }
        return MrzParser.find(lines)
    }

    private fun VNRecognizedTextObservation.pixelBounds(area: IntRect): Rect =
        boundingBox.useContents {
            val left = area.left + origin.x * area.width
            val top = area.top + (1 - origin.y - size.height) * area.height
            Rect(
                left = left.toFloat(),
                top = top.toFloat(),
                right = (left + size.width * area.width).toFloat(),
                bottom = (top + size.height * area.height).toFloat()
            )
        }
}

internal fun UIImage.uprightAndScaled(): UIImage {
    val (pointWidth, pointHeight) = size.useContents { width to height }
    val pixelWidth = pointWidth * scale
    val pixelHeight = pointHeight * scale
    val factor = minOf(1.0, PASSPORT_IMAGE_MAX_EDGE / maxOf(pixelWidth, pixelHeight))
    val targetWidth = pixelWidth * factor
    val targetHeight = pixelHeight * factor

    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(targetWidth, targetHeight), format = format)
    return renderer.imageWithActions { _ ->
        drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    }
}

internal fun UIImage.toJpegBytes(): ByteArray? =
    UIImageJPEGRepresentation(this, PASSPORT_IMAGE_JPEG_QUALITY)?.toByteArray()

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { memcpy(it.addressOf(0), bytes, length) }
    }
}
