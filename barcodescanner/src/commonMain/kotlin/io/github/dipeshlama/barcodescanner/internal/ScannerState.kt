package io.github.dipeshlama.barcodescanner.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dipeshlama.barcodescanner.BarcodeScanResult
import io.github.dipeshlama.barcodescanner.BarcodeScannerConfig

internal class ScannerState(private val config: () -> BarcodeScannerConfig) {
    var isProcessing by mutableStateOf(false)
        private set

    var isFinished by mutableStateOf(false)
        private set

    /** Gallery photo waiting for the user to crop it, when [BarcodeScannerConfig.cropGalleryImage] is on. */
    var cropImage by mutableStateOf<PickedImage?>(null)
        private set

    /** True after a gallery photo had no code in it, until [dismissNotFound]. */
    var isNotFoundVisible by mutableStateOf(false)
        private set

    val isAutoScanEnabled: Boolean
        get() = !isProcessing && cropImage == null && !isFinished

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
        return finish(result)
    }

    /** The outcome of reading a gallery photo: a code, or null when it had none. */
    fun onGalleryResult(result: BarcodeScanResult?): BarcodeScanResult? {
        if (isFinished) return null
        isProcessing = false
        if (result == null) {
            isNotFoundVisible = true
            return null
        }
        return finish(result)
    }

    fun dismissNotFound() {
        isNotFoundVisible = false
    }

    private fun finish(result: BarcodeScanResult): BarcodeScanResult {
        isFinished = true
        isProcessing = false
        isNotFoundVisible = false
        cropImage = null
        return result
    }
}
