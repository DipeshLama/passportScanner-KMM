package io.github.dipeshlama.passportscanner

import io.github.dipeshlama.passportscanner.internal.CameraScan
import io.github.dipeshlama.passportscanner.mrz.MrzGender
import io.github.dipeshlama.passportscanner.mrz.PassportMrzData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PassportScannerStateTest {
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

    private val valid = passport(expiry = LocalDate(2099, 1, 1))
    private val expired = passport(expiry = LocalDate(2001, 1, 1))

    private fun state(config: PassportScannerConfig) =
        PassportScannerState(emitsEvents = true).also { it.config = config }

    @Test
    fun completedScanUpdatesResultPhaseAndEvents() = runTest {
        val state = state(PassportScannerConfig(reviewBeforeReturning = false))
        assertEquals(PassportScanPhase.Scanning, state.phase)

        val result = assertIs<PassportScanResult.Success>(state.onScan(CameraScan(valid, image)))
        assertSame(result, state.result)
        assertEquals(PassportScanPhase.Finished, state.phase)

        val event = assertIs<PassportScanEvent.Completed>(state.events.first())
        assertSame(result, event.result)
    }

    @Test
    fun reviewIsReportedAsPhaseAndCompletesOnConfirm() = runTest {
        val state = state(PassportScannerConfig(reviewBeforeReturning = true))
        assertNull(state.onScan(CameraScan(valid, image)))
        assertEquals(PassportScanPhase.Review, state.phase)
        assertNull(state.result)

        state.complete(state.scanner.confirmReview())
        assertIs<PassportScanResult.Success>(state.result)
        assertIs<PassportScanEvent.Completed>(state.events.first())
    }

    @Test
    fun unreadableAndExpiredAreSentAsEvents() = runTest {
        val state = state(PassportScannerConfig(reviewBeforeReturning = false, rejectExpired = true))
        state.onScan(CameraScan(null, image))
        assertEquals(PassportScanPhase.Unreadable, state.phase)
        state.scanner.retry()
        state.onScan(CameraScan(expired, image))
        assertEquals(PassportScanPhase.Expired, state.phase)
        state.onCancelled()

        val events = state.events.take(3).toList()
        assertEquals(PassportScanEvent.Unreadable, events[0])
        assertEquals(expired, assertIs<PassportScanEvent.Expired>(events[1]).data)
        assertEquals(PassportScanEvent.Cancelled, events[2])
    }

    @Test
    fun restartClearsResultAndScansAgain() {
        val state = state(PassportScannerConfig(reviewBeforeReturning = false))
        state.onScan(CameraScan(valid, image))
        state.restart()
        assertNull(state.result)
        assertEquals(PassportScanPhase.Scanning, state.phase)
        assertTrue(state.scanner.isAutoScanEnabled)
        assertIs<PassportScanResult.Success>(state.onScan(CameraScan(valid, image)))
    }

    @Test
    fun captureNeedsCameraPermission() {
        val state = state(PassportScannerConfig())
        assertFalse(state.canCapture)
        state.isCameraGranted = true
        assertTrue(state.canCapture)
        state.onScan(CameraScan(valid, image))
        state.complete(state.scanner.confirmReview())
        assertFalse(state.canCapture)
    }
}
