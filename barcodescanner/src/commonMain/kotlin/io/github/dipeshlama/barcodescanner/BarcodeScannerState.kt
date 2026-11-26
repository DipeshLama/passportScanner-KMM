package io.github.dipeshlama.barcodescanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import io.github.dipeshlama.barcodescanner.internal.BarcodeCameraController
import io.github.dipeshlama.barcodescanner.internal.ScannerState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** What the scanner is doing right now. See [BarcodeScannerState.phase]. */
public enum class BarcodeScanPhase {
    /** Looking for codes in the camera preview. */
    Scanning,

    /** Not reading codes from the camera, after [BarcodeScannerState.pause]. */
    Paused,

    /** Reading a photo picked from the gallery. */
    Processing,

    /** Showing a gallery photo for the user to crop ([BarcodeScannerConfig.cropGalleryImage]). */
    Cropping,

    /** Done: a code was read in single mode, or the user tapped Done in continuous mode. */
    Finished,
}

/** One-off events from a [BarcodeScanner] driven by a [BarcodeScannerState]. */
public sealed interface BarcodeScanEvent {
    /**
     * A code was read. In [BarcodeScanMode.Single] this is the only result and the scanner
     * finishes; in [BarcodeScanMode.Continuous] it's sent for each new code.
     */
    public class Scanned(public val result: BarcodeScanResult) : BarcodeScanEvent {
        override fun toString(): String = "Scanned(result=$result)"
    }

    /**
     * A continuous session ended, from the Done button or [BarcodeScannerState.finish], with every
     * code read in it, oldest first.
     */
    public class Done(public val results: List<BarcodeScanResult>) : BarcodeScanEvent {
        override fun toString(): String = "Done(results=$results)"
    }

    /** The user went back, from the top bar or with system back. */
    public data object Cancelled : BarcodeScanEvent

    /** A photo picked from the gallery had no code in it. The scanner shows a message and keeps scanning. */
    public data object NoCodeFound : BarcodeScanEvent
}

/**
 * State holder for [BarcodeScanner], for observing and controlling the scanner from outside it:
 * pausing it, turning on the flashlight, zooming, or receiving results as a [Flow].
 *
 * Create it with [rememberBarcodeScannerState] and pass it to the `BarcodeScanner(state = ...)`
 * overload. Use one state for one scanner at a time.
 *
 * ```
 * val scanner = rememberBarcodeScannerState()
 * LaunchedEffect(scanner) {
 *     scanner.events.collect { event ->
 *         when (event) {
 *             is BarcodeScanEvent.Scanned -> onCode(event.result)
 *             BarcodeScanEvent.Cancelled -> navigateBack()
 *             else -> Unit
 *         }
 *     }
 * }
 * BarcodeScanner(state = scanner)
 * ```
 */
@Stable
public class BarcodeScannerState internal constructor(private val emitsEvents: Boolean) {
    internal var config: BarcodeScannerConfig = BarcodeScannerConfig()
    internal val scanner: ScannerState = ScannerState(config = { config })
    internal val camera: BarcodeCameraController = BarcodeCameraController()

    private val eventChannel = Channel<BarcodeScanEvent>(Channel.UNLIMITED)

    /**
     * One-off scanner events. Each event is delivered once, to one collector, and events sent
     * before collection starts are buffered, so none are lost while the scanner starts up.
     */
    public val events: Flow<BarcodeScanEvent> = eventChannel.receiveAsFlow()

    /** The last code read, or null when none has been read yet. Cleared by [restart]. */
    public val result: BarcodeScanResult? get() = scanner.results.lastOrNull()

    /** Every code read since the scanner started or [restart]ed, oldest first. */
    public val results: List<BarcodeScanResult> get() = scanner.results

    /** What the scanner is doing right now. */
    public val phase: BarcodeScanPhase
        get() = with(scanner) {
            when {
                isFinished -> BarcodeScanPhase.Finished
                isProcessing -> BarcodeScanPhase.Processing
                cropImage != null -> BarcodeScanPhase.Cropping
                isPaused -> BarcodeScanPhase.Paused
                else -> BarcodeScanPhase.Scanning
            }
        }

    /** Whether reading codes from the camera is paused. */
    public val isPaused: Boolean get() = scanner.isPaused

    /** Stops reading codes from the camera; the preview stays on. Gallery import still works. */
    public fun pause() {
        scanner.pause()
    }

    /** Starts reading codes from the camera again after [pause]. */
    public fun resume() {
        scanner.resume()
    }

    /** Forgets every code read and starts scanning again, also after the scanner finished. */
    public fun restart() {
        scanner.restart()
    }

    /**
     * Ends a [BarcodeScanMode.Continuous] session like the Done button, sending
     * [BarcodeScanEvent.Done]. Does nothing in single mode or once the scanner has finished.
     */
    public fun finish() {
        onDone()
    }

    /** Whether the active camera has a flashlight. False until the camera has started. */
    public val hasTorch: Boolean get() = camera.hasTorch

    /** Whether the flashlight is on. */
    public val isTorchOn: Boolean get() = camera.isTorchOn

    /** Turns the flashlight on or off, if the camera has one. */
    public fun setTorch(on: Boolean) {
        camera.setTorch(on)
    }

    /** The camera's current zoom, where 1 is no zoom. Updated by pinching too. */
    public val zoomRatio: Float get() = camera.zoomRatio

    /** The smallest zoom [setZoomRatio] accepts. 1 until the camera has started. */
    public val minZoomRatio: Float get() = camera.minZoomRatio

    /** The largest zoom [setZoomRatio] accepts. 1 until the camera has started. */
    public val maxZoomRatio: Float get() = camera.maxZoomRatio

    /** Zooms the camera, clamped to [minZoomRatio]..[maxZoomRatio]. */
    public fun setZoomRatio(ratio: Float) {
        camera.setZoomRatio(ratio)
    }

    /** Sends [BarcodeScanEvent.Scanned] for a code that should be delivered, and returns it. */
    internal fun onScanned(result: BarcodeScanResult?): BarcodeScanResult? {
        if (result != null) send(BarcodeScanEvent.Scanned(result))
        return result
    }

    internal fun onNoCodeFound() {
        send(BarcodeScanEvent.NoCodeFound)
    }

    /** Ends a continuous session. Returns the codes read, or null when there's no session to end. */
    internal fun onDone(): List<BarcodeScanResult>? {
        if (config.scanMode !is BarcodeScanMode.Continuous) return null
        val results = scanner.finishSession() ?: return null
        send(BarcodeScanEvent.Done(results))
        return results
    }

    internal fun onCancelled() {
        send(BarcodeScanEvent.Cancelled)
    }

    private fun send(event: BarcodeScanEvent) {
        if (emitsEvents) eventChannel.trySend(event)
    }
}

/** Creates and remembers a [BarcodeScannerState] for `BarcodeScanner(state = ...)`. */
@Composable
public fun rememberBarcodeScannerState(): BarcodeScannerState =
    remember { BarcodeScannerState(emitsEvents = true) }
