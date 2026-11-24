@file:OptIn(ExperimentalForeignApi::class)

package io.github.dipeshlama.barcodescanner.internal

import androidx.compose.ui.unit.IntRect
import io.github.dipeshlama.barcodescanner.BarcodeFormat
import io.github.dipeshlama.barcodescanner.BarcodeScanResult
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
import platform.Vision.VNBarcodeObservation
import platform.Vision.VNDetectBarcodesRequest
import platform.Vision.VNDetectBarcodesRequestRevision1
import platform.Vision.VNImageRequestHandler
import platform.posix.memcpy

internal object BarcodeReader {
    /**
     * Returns the first code of [formats] in the image behind [handler], looking only inside
     * [region] (pixels of the upright image) when given.
     */
    fun find(
        handler: VNImageRequestHandler,
        formats: Set<BarcodeFormat>,
        imageWidth: Int,
        imageHeight: Int,
        region: IntRect? = null
    ): BarcodeScanResult? {
        fun request() = VNDetectBarcodesRequest().apply {
            if (region != null && imageWidth > 0 && imageHeight > 0) {
                // Vision's origin is bottom-left.
                regionOfInterest = CGRectMake(
                    x = region.left.toDouble() / imageWidth,
                    y = 1.0 - region.bottom.toDouble() / imageHeight,
                    width = region.width.toDouble() / imageWidth,
                    height = region.height.toDouble() / imageHeight
                )
            }
        }

        var request = request()
        if (!handler.performRequests(listOf(request), null)) {
            // Newer revisions fail on some simulators; revision 1 runs everywhere.
            request = request().apply { revision = VNDetectBarcodesRequestRevision1 }
            if (!handler.performRequests(listOf(request), null)) return null
        }

        return request.results.orEmpty()
            .filterIsInstance<VNBarcodeObservation>()
            .firstNotNullOfOrNull { observation ->
                val value = observation.payloadStringValue ?: return@firstNotNullOfOrNull null
                val symbology = observation.symbology ?: return@firstNotNullOfOrNull null
                appleBarcode(symbology, value, formats)
            }
    }
}

internal fun UIImage.uprightAndScaled(): UIImage {
    val (pointWidth, pointHeight) = size.useContents { width to height }
    val pixelWidth = pointWidth * scale
    val pixelHeight = pointHeight * scale
    val factor = minOf(1.0, GALLERY_IMAGE_MAX_EDGE / maxOf(pixelWidth, pixelHeight))
    val targetWidth = pixelWidth * factor
    val targetHeight = pixelHeight * factor

    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(targetWidth, targetHeight), format = format)
    return renderer.imageWithActions { _ ->
        drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    }
}

internal fun UIImage.toJpegBytes(): ByteArray? =
    UIImageJPEGRepresentation(this, 0.85)?.toByteArray()

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { memcpy(it.addressOf(0), bytes, length) }
    }
}
