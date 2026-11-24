package io.github.dipeshlama.barcodescanner.internal

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import io.github.dipeshlama.barcodescanner.BarcodeFormat
import io.github.dipeshlama.barcodescanner.BarcodeScanResult
import io.github.dipeshlama.barcodescanner.BarcodeScannerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScannerStateTest {
    private val code = BarcodeScanResult("https://example.com", BarcodeFormat.QrCode)

    private fun state(config: BarcodeScannerConfig = BarcodeScannerConfig()) = ScannerState(config = { config })

    private object FakeImage : PickedImage {
        override val width = 100
        override val height = 100
        override fun toImageBitmap(): ImageBitmap? = null
        override suspend fun read(formats: Set<BarcodeFormat>, crop: IntRect?): BarcodeScanResult? = null
    }

    @Test
    fun cameraResultIsDeliveredOnce() {
        val state = state()
        assertEquals(code, state.onCameraResult(code))
        assertTrue(state.isFinished)
        assertFalse(state.isAutoScanEnabled)
        assertNull(state.onCameraResult(code))
    }

    @Test
    fun cameraResultIsIgnoredWhileReadingGalleryPhoto() {
        val state = state()
        state.onProcessingStarted()
        assertNull(state.onCameraResult(code))
        assertFalse(state.isFinished)
    }

    @Test
    fun galleryPhotoWithoutCodeShowsMessageAndKeepsScanning() {
        val state = state()
        state.onProcessingStarted()
        assertNull(state.onGalleryResult(null))
        assertTrue(state.isNotFoundVisible)
        assertFalse(state.isProcessing)
        assertTrue(state.isAutoScanEnabled)

        state.onProcessingStarted()
        assertFalse(state.isNotFoundVisible)
    }

    @Test
    fun galleryCodeIsDelivered() {
        val state = state()
        state.onProcessingStarted()
        assertEquals(code, state.onGalleryResult(code))
        assertTrue(state.isFinished)
        assertNull(state.onGalleryResult(code))
    }

    @Test
    fun galleryPhotoIsReadRightAwayWithoutCrop() {
        val state = state()
        state.onProcessingStarted()
        assertSame(FakeImage, state.onGalleryPicked(FakeImage))
        assertNull(state.cropImage)
    }

    @Test
    fun galleryPhotoWaitsForCropWhenEnabled() {
        val state = state(BarcodeScannerConfig(cropGalleryImage = true))
        state.onProcessingStarted()
        assertNull(state.onGalleryPicked(FakeImage))
        assertSame(FakeImage, state.cropImage)
        assertFalse(state.isProcessing)
        assertFalse(state.isAutoScanEnabled)

        assertSame(FakeImage, state.confirmCrop())
        assertNull(state.cropImage)
        assertTrue(state.isProcessing)
    }

    @Test
    fun cancellingCropResumesScanning() {
        val state = state(BarcodeScannerConfig(cropGalleryImage = true))
        state.onGalleryPicked(FakeImage)
        state.cancelCrop()
        assertNull(state.cropImage)
        assertTrue(state.isAutoScanEnabled)
    }

    @Test
    fun cancelledPickerStopsProcessing() {
        val state = state()
        state.onProcessingStarted()
        state.onGalleryCancelled()
        assertFalse(state.isProcessing)
    }
}
