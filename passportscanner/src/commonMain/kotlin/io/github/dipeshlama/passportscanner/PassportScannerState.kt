package io.github.dipeshlama.passportscanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.dipeshlama.passportscanner.internal.CameraScan
import io.github.dipeshlama.passportscanner.internal.PassportCameraController
import io.github.dipeshlama.passportscanner.internal.ScannerState
import io.github.dipeshlama.passportscanner.mrz.PassportMrzData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** What the scanner is doing right now. See [PassportScannerState.phase]. */
public enum class PassportScanPhase {
    /** Looking for a passport in the camera preview. */
    Scanning,

    /** Reading a captured or picked photo. */
    Processing,

    /** Showing a gallery photo for the user to crop ([PassportScannerConfig.cropGalleryImage]). */
    Cropping,

    /** The MRZ couldn't be read; the unreadable sheet is shown. */
    Unreadable,

    /** The passport has expired; shown only with [PassportScannerConfig.rejectExpired]. */
    Expired,

    /** The details are shown for confirmation ([PassportScannerConfig.reviewBeforeReturning]). */
    Review,

    /** A result was returned. Call [PassportScannerState.restart] to scan again. */
    Finished,
}

/** One-off events from a [PassportScanner] driven by a [PassportScannerState]. */
public sealed interface PassportScanEvent {
    /** A scan finished with [result]. Also available as [PassportScannerState.result]. */
    public class Completed(public val result: PassportScanResult) : PassportScanEvent {
        override fun toString(): String = "Completed(result=$result)"
    }

    /** The user went back, from the top bar or with system back. */
    public data object Cancelled : PassportScanEvent

    /** A photo was read but no valid MRZ was found. The scanner shows the unreadable sheet. */
    public data object Unreadable : PassportScanEvent

    /** An expired passport was scanned and held back ([PassportScannerConfig.rejectExpired]). */
    public class Expired(public val data: PassportMrzData) : PassportScanEvent {
        override fun toString(): String = "Expired(data=$data)"
    }
}

/**
 * State holder for [PassportScanner], for observing and controlling the scanner from outside it,
 * such as from a ViewModel-style presenter, a custom capture button or tests of your screen.
 *
 * Create it with [rememberPassportScannerState] and pass it to the `PassportScanner(state = ...)`
 * overload. Use one state for one scanner at a time.
 *
 * Observe [phase] and [result] as Compose state, or collect [events] for one-off events:
 * ```
 * val scanner = rememberPassportScannerState()
 * LaunchedEffect(scanner) {
 *     scanner.events.collect { event ->
 *         when (event) {
 *             is PassportScanEvent.Completed -> onPassport(event.result)
 *             PassportScanEvent.Cancelled -> navigateBack()
 *             else -> Unit
 *         }
 *     }
 * }
 * PassportScanner(state = scanner)
 * ```
 */
@Stable
public class PassportScannerState internal constructor(private val emitsEvents: Boolean) {
    internal var config: PassportScannerConfig = PassportScannerConfig()
    internal val scanner: ScannerState = ScannerState(config = { config })
    internal val camera: PassportCameraController = PassportCameraController()

    /** Set by [PassportScanner] once camera permission is granted. */
    internal var isCameraGranted: Boolean by mutableStateOf(false)

    private val eventChannel = Channel<PassportScanEvent>(Channel.UNLIMITED)

    /**
     * One-off scanner events. Each event is delivered once, to one collector, and events sent
     * before collection starts are buffered, so none are lost while the scanner starts up.
     */
    public val events: Flow<PassportScanEvent> = eventChannel.receiveAsFlow()

    /** The result of the last finished scan, or null while scanning. Cleared by [restart]. */
    public var result: PassportScanResult? by mutableStateOf(null)
        private set

    /** What the scanner is doing right now. */
    public val phase: PassportScanPhase
        get() = with(scanner) {
            when {
                isFinished -> PassportScanPhase.Finished
                isProcessing -> PassportScanPhase.Processing
                reviewScan != null -> PassportScanPhase.Review
                expiredScan != null -> PassportScanPhase.Expired
                unreadableScan != null -> PassportScanPhase.Unreadable
                cropImage != null -> PassportScanPhase.Cropping
                else -> PassportScanPhase.Scanning
            }
        }

    /** Whether the active camera has a flashlight. False until the camera has started. */
    public val hasTorch: Boolean get() = camera.hasTorch

    /** Whether the flashlight is on. */
    public val isTorchOn: Boolean get() = camera.isTorchOn

    /** Whether [capture] would take a photo now. */
    public val canCapture: Boolean
        get() = isCameraGranted && !scanner.isProcessing && !scanner.isFinished

    /** Takes a photo and reads it, like the capture button. Does nothing when [canCapture] is false. */
    public fun capture() {
        if (canCapture) camera.capture()
    }

    /** Turns the flashlight on or off, if the camera has one. */
    public fun setTorch(on: Boolean) {
        camera.setTorch(on)
    }

    /** Clears [result] and any open sheet, and starts scanning again. */
    public fun restart() {
        scanner.restart()
        result = null
    }

    internal fun onScan(scan: CameraScan): PassportScanResult? {
        val result = scanner.onScan(scan)
        if (result == null) {
            if (scanner.unreadableScan === scan) send(PassportScanEvent.Unreadable)
            val expired = scanner.expiredScan
            if (expired === scan && expired.data != null) send(PassportScanEvent.Expired(expired.data))
        }
        return complete(result)
    }

    /** Records a finished [result] (null when there is none) and returns it. */
    internal fun complete(result: PassportScanResult?): PassportScanResult? {
        if (result != null) {
            this.result = result
            send(PassportScanEvent.Completed(result))
        }
        return result
    }

    internal fun onCancelled() {
        send(PassportScanEvent.Cancelled)
    }

    private fun send(event: PassportScanEvent) {
        if (emitsEvents) eventChannel.trySend(event)
    }
}

/** Creates and remembers a [PassportScannerState] for `PassportScanner(state = ...)`. */
@Composable
public fun rememberPassportScannerState(): PassportScannerState =
    remember { PassportScannerState(emitsEvents = true) }
