package io.github.dipeshlama.barcodescanner.internal

import io.github.dipeshlama.barcodescanner.BarcodeFormat
import io.github.dipeshlama.barcodescanner.BarcodeScanResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppleSymbologyTest {
    private val all = BarcodeFormat.All

    @Test
    fun mapsVisionSymbologyNames() {
        assertEquals(BarcodeScanResult("hi", BarcodeFormat.QrCode), appleBarcode("VNBarcodeSymbologyQR", "hi", all))
        assertEquals(BarcodeFormat.Itf, appleBarcode("VNBarcodeSymbologyITF14", "1", all)?.format)
        assertEquals(BarcodeFormat.Itf, appleBarcode("VNBarcodeSymbologyI2of5", "1", all)?.format)
        assertEquals(BarcodeFormat.Code39, appleBarcode("VNBarcodeSymbologyCode39FullASCII", "A", all)?.format)
        assertEquals(BarcodeFormat.DataMatrix, appleBarcode("DataMatrix", "x", all)?.format)
    }

    @Test
    fun ignoresUnknownSymbologies() {
        assertNull(appleBarcode("VNBarcodeSymbologyGS1DataBar", "1", all))
    }

    @Test
    fun filtersByRequestedFormats() {
        assertNull(appleBarcode("VNBarcodeSymbologyQR", "hi", setOf(BarcodeFormat.Ean13)))
    }

    private val supported = listOf(
        "VNBarcodeSymbologyQR", "VNBarcodeSymbologyMicroQR", "VNBarcodeSymbologyAztec",
        "VNBarcodeSymbologyEAN13", "VNBarcodeSymbologyEAN8", "VNBarcodeSymbologyUPCE",
        "VNBarcodeSymbologyCode128", "VNBarcodeSymbologyITF14", "VNBarcodeSymbologyI2of5",
        "VNBarcodeSymbologyGS1DataBar",
    )

    @Test
    fun requestsOnlyTheSymbologiesOfTheChosenFormats() {
        assertEquals(
            listOf("VNBarcodeSymbologyQR", "VNBarcodeSymbologyMicroQR"),
            appleSymbologiesFor(supported, setOf(BarcodeFormat.QrCode))
        )
        assertEquals(
            listOf("VNBarcodeSymbologyITF14", "VNBarcodeSymbologyI2of5"),
            appleSymbologiesFor(supported, setOf(BarcodeFormat.Itf))
        )
    }

    @Test
    fun requestsEan13ForUpcA() {
        assertEquals(listOf("VNBarcodeSymbologyEAN13"), appleSymbologiesFor(supported, setOf(BarcodeFormat.UpcA)))
    }

    @Test
    fun requestsNothingForFormatsThisVersionCantRead() {
        assertEquals(emptyList(), appleSymbologiesFor(supported, setOf(BarcodeFormat.Codabar)))
    }

    @Test
    fun reportsEan13WithLeadingZeroAsUpcA() {
        assertEquals(
            BarcodeScanResult("036000291452", BarcodeFormat.UpcA),
            appleBarcode("VNBarcodeSymbologyEAN13", "0036000291452", all)
        )
        assertEquals(
            BarcodeScanResult("0036000291452", BarcodeFormat.Ean13),
            appleBarcode("VNBarcodeSymbologyEAN13", "0036000291452", setOf(BarcodeFormat.Ean13))
        )
        assertEquals(BarcodeFormat.Ean13, appleBarcode("VNBarcodeSymbologyEAN13", "4006381333931", all)?.format)
    }
}
