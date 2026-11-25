package io.github.dipeshlama.passportscanner.internal

import io.github.dipeshlama.passportscanner.PassportScanResult
import io.github.dipeshlama.passportscanner.PassportScannerConfig
import io.github.dipeshlama.passportscanner.mrz.MrzGender
import io.github.dipeshlama.passportscanner.mrz.PassportMrzData
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScannerStateTest {
    private val today = LocalDate(2026, 6, 1)
    private val valid = passport(expiry = LocalDate(2030, 1, 1))
    private val expired = passport(expiry = LocalDate(2025, 1, 1))
    private val image = byteArrayOf(1, 2, 3)

    private fun passport(expiry: LocalDate) = PassportMrzData(
        documentNumber = "L898902C3",
        surname = "ERIKSSON",
        givenNames = "ANNA MARIA",
        issuingCountry = "UTO",
        nationality = "UTO",
        dateOfBirth = LocalDate(1974, 8, 12),
        expiryDate = expiry,
        gender = MrzGender.FEMALE
    )

    private fun state(config: PassportScannerConfig) = ScannerState(config = { config }, today = { today })

    @Test
    fun returnsValidPassportDirectlyWithoutReview() {
        val state = state(PassportScannerConfig(reviewBeforeReturning = false))
        val result = assertIs<PassportScanResult.Success>(state.onScan(CameraScan(valid, image)))
        assertEquals(valid, result.data)
        assertTrue(state.isFinished)
        assertFalse(state.isAutoScanEnabled)
    }

    @Test
    fun reviewThenConfirm() {
        val state = state(PassportScannerConfig(reviewBeforeReturning = true))
        assertNull(state.onScan(CameraScan(valid, image)))
        assertNotNull(state.reviewScan)
        assertFalse(state.isAutoScanEnabled)
        assertIs<PassportScanResult.Success>(state.confirmReview())
        assertNull(state.reviewScan)
    }

    @Test
    fun reviewThenRescanResumesAutoScan() {
        val state = state(PassportScannerConfig(reviewBeforeReturning = true))
        state.onScan(CameraScan(valid, image))
        state.rescan()
        assertTrue(state.isAutoScanEnabled)
        assertFalse(state.isFinished)
    }

    @Test
    fun expiredPassportIsReturnedWhenRejectExpiredIsOff() {
        val state = state(PassportScannerConfig(reviewBeforeReturning = false, rejectExpired = false))
        assertIs<PassportScanResult.Success>(state.onScan(CameraScan(expired, image)))
    }

    @Test
    fun expiredPassportIsHeldBackWhenRejectExpiredIsOn() {
        val state = state(PassportScannerConfig(reviewBeforeReturning = false, rejectExpired = true))
        assertNull(state.onScan(CameraScan(expired, image)))
        assertNotNull(state.expiredScan)
        assertFalse(state.isAutoScanEnabled)
        // Frames arriving while the sheet is open are ignored.
        assertNull(state.onScan(CameraScan(valid, image)))

        state.dismissExpired()
        assertNull(state.expiredScan)
        assertTrue(state.isAutoScanEnabled)
        assertIs<PassportScanResult.Success>(state.onScan(CameraScan(valid, image)))
    }

    @Test
    fun expiredPassportIsRejectedBeforeReview() {
        val state = state(PassportScannerConfig(reviewBeforeReturning = true, rejectExpired = true))
        state.onScan(CameraScan(expired, image))
        assertNotNull(state.expiredScan)
        assertNull(state.reviewScan)
    }

    @Test
    fun unreadableScanOffersPhotoOnlyWhenAllowed() {
        val allowed = state(PassportScannerConfig(allowImageOnlyResult = true))
        allowed.onScan(CameraScan(data = null, image = image))
        assertIs<PassportScanResult.ImageOnly>(allowed.useUnreadablePhoto())

        val notAllowed = state(PassportScannerConfig(allowImageOnlyResult = false))
        notAllowed.onScan(CameraScan(data = null, image = image))
        assertNull(notAllowed.useUnreadablePhoto())
        notAllowed.retry()
        assertTrue(notAllowed.isAutoScanEnabled)
    }

    @Test
    fun nothingIsDeliveredAfterFinishing() {
        val state = state(PassportScannerConfig(reviewBeforeReturning = false))
        state.onScan(CameraScan(valid, image))
        assertNull(state.onScan(CameraScan(valid, image)))
        assertNull(state.confirmReview())
    }

    private class FakePickedImage : PickedImage {
        override val width = 100
        override val height = 100
        override fun toImageBitmap(): ImageBitmap? = null
        override suspend fun read(crop: IntRect?) = CameraScan(data = null, image = null)
        override suspend fun readMrz(): PassportMrzData? = null
    }

    @Test
    fun galleryPhotoIsReadDirectlyWhenCroppingIsOff() {
        val state = state(PassportScannerConfig(cropGalleryImage = false))
        val picked = FakePickedImage()
        state.onProcessingStarted()
        assertSame(picked, state.onGalleryPicked(picked))
        assertNull(state.cropImage)
        assertTrue(state.isProcessing)
    }

    @Test
    fun galleryPhotoWaitsForCropWhenCroppingIsOn() {
        val state = state(PassportScannerConfig(cropGalleryImage = true))
        val picked = FakePickedImage()
        state.onProcessingStarted()
        assertNull(state.onGalleryPicked(picked))
        assertSame(picked, state.cropImage)
        assertFalse(state.isProcessing)
        assertFalse(state.isAutoScanEnabled)

        assertSame(picked, state.confirmCrop())
        assertNull(state.cropImage)
        assertTrue(state.isProcessing)
        assertNull(state.confirmCrop())
    }

    @Test
    fun cancellingCropResumesAutoScan() {
        val state = state(PassportScannerConfig(cropGalleryImage = true))
        state.onGalleryPicked(FakePickedImage())
        state.cancelCrop()
        assertNull(state.cropImage)
        assertTrue(state.isAutoScanEnabled)
    }

    @Test
    fun galleryPhotoIgnoredAfterFinishing() {
        val state = state(PassportScannerConfig(reviewBeforeReturning = false, cropGalleryImage = true))
        state.onScan(CameraScan(valid, image))
        assertNull(state.onGalleryPicked(FakePickedImage()))
        assertNull(state.cropImage)
    }
}
