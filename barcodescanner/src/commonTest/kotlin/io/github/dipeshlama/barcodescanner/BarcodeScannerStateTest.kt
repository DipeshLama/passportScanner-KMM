package io.github.dipeshlama.barcodescanner

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BarcodeScannerStateTest {
    private val code = BarcodeScanResult("https://example.com", BarcodeFormat.QrCode)
    private val other = BarcodeScanResult("4006381333931", BarcodeFormat.Ean13)

    private fun state(config: BarcodeScannerConfig = BarcodeScannerConfig()) =
        BarcodeScannerState(emitsEvents = true).apply { this.config = config }

    @Test
    fun singleScanFinishes() = runTest {
        val state = state()
        state.onScanned(state.scanner.onCameraResult(code))
        assertEquals(BarcodeScanPhase.Finished, state.phase)
        assertEquals(code, state.result)
        assertEquals(code, assertIs<BarcodeScanEvent.Scanned>(state.events.first()).result)

        // Done does nothing in single mode.
        state.finish()
        state.restart()
        assertEquals(BarcodeScanPhase.Scanning, state.phase)
        assertNull(state.result)
    }

    @Test
    fun continuousSessionSendsEachCodeThenDone() = runTest {
        val state = state(BarcodeScannerConfig(scanMode = BarcodeScanMode.Continuous()))
        state.onScanned(state.scanner.onCameraResult(code))
        state.scanner.endCooldown()
        state.onScanned(state.scanner.onCameraResult(other))
        assertEquals(BarcodeScanPhase.Scanning, state.phase)
        state.finish()
        assertEquals(BarcodeScanPhase.Finished, state.phase)

        val events = state.events.take(3).toList()
        assertEquals(code, assertIs<BarcodeScanEvent.Scanned>(events[0]).result)
        assertEquals(other, assertIs<BarcodeScanEvent.Scanned>(events[1]).result)
        assertEquals(listOf(code, other), assertIs<BarcodeScanEvent.Done>(events[2]).results)
    }

    @Test
    fun pauseShowsInPhase() {
        val state = state()
        state.pause()
        assertEquals(BarcodeScanPhase.Paused, state.phase)
        state.resume()
        assertEquals(BarcodeScanPhase.Scanning, state.phase)
    }

    @Test
    fun zoomIsIgnoredBeforeCameraStarts() {
        val state = state()
        state.setZoomRatio(3f)
        assertEquals(1f, state.zoomRatio)
    }
}
