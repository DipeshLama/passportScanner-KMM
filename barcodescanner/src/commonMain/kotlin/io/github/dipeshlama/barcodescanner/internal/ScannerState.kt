package io.github.dipeshlama.barcodescanner.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dipeshlama.barcodescanner.BarcodeScanMode
import io.github.dipeshlama.barcodescanner.BarcodeScanResult
import io.github.dipeshlama.barcodescanner.BarcodeScannerConfig
import kotlin.concurrent.Volatile

internal class ScannerState(private val config: () -> BarcodeScannerConfig) {
    var isProcessing by mutableStateOf(false)
        private set

    var isFinished by mutableStateOf(false)
        private set

    /** Set by [pause]: the camera stops reading codes until [resume]. */
    var isPaused by mutableStateOf(false)
        private set

    /** Waiting out [BarcodeScanMode.Continuous.delayMillis] after a code was read, until [endCooldown]. */
    var isCoolingDown by mutableStateOf(false)
        private set

    /** Gallery photo waiting for the user to crop it, when [BarcodeScannerConfig.cropGalleryImage] is on. */
    var cropImage by mutableStateOf<PickedImage?>(null)
        private set

    /** True after a gallery photo had no code in it, until [dismissNotFound]. */
    var isNotFoundVisible by mutableStateOf(false)
        private set

    /** Codes read since the scanner started or [restart]ed, oldest first. */
    var results by mutableStateOf<List<BarcodeScanResult>>(emptyList())
        private set

    /** In continuous mode, the code just read, shown briefly until [dismissRecentScan]. */
    var recentScan by mutableStateOf<BarcodeScanResult?>(null)
        private set

    /** Changes each time [recentScan] is set, even to an equal code, to restart its timer. */
    var recentScanId by mutableIntStateOf(0)
        private set

    // Read by the camera off the main thread, so it's a plain immutable set, not snapshot state.
    @Volatile
    private var skipped: Set<BarcodeScanResult> = emptySet()

    /** Whether the scanner is looking for codes (the scan line runs), including during a cooldown. */
    val isLive: Boolean
        get() = !isProcessing && cropImage == null && !isFinished && !isPaused

    /** Whether the camera should read codes right now. */
    val isAutoScanEnabled: Boolean
        get() = isLive && !isCoolingDown

    /**
     * Whether the camera should report [result], or skip it and keep looking, as for a code already
     * read with [BarcodeScanMode.Continuous.skipDuplicates]. Safe to call from any thread.
     */
    fun accepts(result: BarcodeScanResult): Boolean = result !in skipped

    fun pause() {
        if (!isFinished) isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun onProcessingStarted() {
        if (isFinished) return
        isProcessing = true
        isNotFoundVisible = false
    }

    fun onGalleryCancelled() {
        isProcessing = false
    }

    /**
     * Called with a photo picked from the gallery. Returns the photo when it should be read right
     * away, or null when it's held in [cropImage] for the user to crop first.
     */
    fun onGalleryPicked(image: PickedImage): PickedImage? {
        if (isFinished) return null
        if (!config().cropGalleryImage) return image
        isProcessing = false
        cropImage = image
        return null
    }

    /** Leaves the crop screen and returns the photo to read, which the caller then crops. */
    fun confirmCrop(): PickedImage? {
        val image = cropImage ?: return null
        cropImage = null
        onProcessingStarted()
        return image
    }

    fun cancelCrop() {
        cropImage = null
    }

    /** A code read by the live camera. Returns it when it should be delivered. */
    fun onCameraResult(result: BarcodeScanResult): BarcodeScanResult? {
        if (!isAutoScanEnabled) return null
        return record(result)
    }

    /** The outcome of reading a gallery photo: a code, or null when it had none. */
    fun onGalleryResult(result: BarcodeScanResult?): BarcodeScanResult? {
        if (isFinished) return null
        isProcessing = false
        if (result == null) {
            isNotFoundVisible = true
            return null
        }
        return record(result)
    }

    fun dismissNotFound() {
        isNotFoundVisible = false
    }

    fun endCooldown() {
        isCoolingDown = false
    }

    fun dismissRecentScan() {
        recentScan = null
    }

    /**
     * Ends a continuous session, as the Done button does. Returns every code read, or null when the
     * scanner had already finished.
     */
    fun finishSession(): List<BarcodeScanResult>? {
        if (isFinished) return null
        stop()
        return results
    }

    /** Forgets every code read and starts scanning again. */
    fun restart() {
        isFinished = false
        isProcessing = false
        isPaused = false
        isCoolingDown = false
        isNotFoundVisible = false
        cropImage = null
        recentScan = null
        results = emptyList()
        skipped = emptySet()
    }

    /** Records a code that was read. Returns it when it should be delivered. */
    private fun record(result: BarcodeScanResult): BarcodeScanResult? {
        val mode = config().scanMode
        if (mode !is BarcodeScanMode.Continuous) {
            results = listOf(result)
            stop()
            return result
        }

        isNotFoundVisible = false
        recentScan = result
        recentScanId++
        // A gallery photo of a code already read; the camera skips these on its own.
        if (mode.skipDuplicates && result in results) return null

        results = results + result
        if (mode.skipDuplicates) skipped = skipped + result
        isCoolingDown = true
        return result
    }

    private fun stop() {
        isFinished = true
        isProcessing = false
        isCoolingDown = false
        isNotFoundVisible = false
        cropImage = null
        recentScan = null
    }
}
