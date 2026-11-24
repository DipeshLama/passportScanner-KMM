package io.github.dipeshlama.passportscanner.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dipeshlama.passportscanner.PassportScanResult
import io.github.dipeshlama.passportscanner.PassportScannerConfig
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal class ScannerState(
    private val config: () -> PassportScannerConfig,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) {
    var isProcessing by mutableStateOf(false)
        private set

    var unreadableScan by mutableStateOf<CameraScan?>(null)
        private set

    var reviewScan by mutableStateOf<CameraScan?>(null)
        private set

    var expiredScan by mutableStateOf<CameraScan?>(null)
        private set

    var isTipsVisible by mutableStateOf(false)
        private set

    var isFinished by mutableStateOf(false)
        private set

    /** Gallery photo waiting for the user to crop it, when [PassportScannerConfig.cropGalleryImage] is on. */
    var cropImage by mutableStateOf<PickedImage?>(null)
        private set

    val isAutoScanEnabled: Boolean
        get() = !isProcessing && unreadableScan == null && reviewScan == null && expiredScan == null &&
            cropImage == null && !isTipsVisible && !isFinished

    fun onProcessingStarted() {
        if (!isFinished) isProcessing = true
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

    fun onScan(scan: CameraScan): PassportScanResult? {
        if (isFinished || reviewScan != null || expiredScan != null) return null
        isProcessing = false
        val data = scan.data
        return when {
            data == null -> {
                unreadableScan = scan
                null
            }
            config().rejectExpired && data.isExpired(today()) -> {
                expiredScan = scan
                null
            }
            config().reviewBeforeReturning -> {
                reviewScan = scan
                null
            }
            else -> finish(PassportScanResult.Success(data, scan.image))
        }
    }

    fun retry() {
        unreadableScan = null
    }

    fun rescan() {
        reviewScan = null
    }

    fun dismissExpired() {
        expiredScan = null
    }

    fun confirmReview(): PassportScanResult? {
        if (isFinished) return null
        val scan = reviewScan ?: return null
        val data = scan.data ?: return null
        return finish(PassportScanResult.Success(data, scan.image))
    }

    fun useUnreadablePhoto(): PassportScanResult? {
        if (isFinished || !config().allowImageOnlyResult) return null
        val image = unreadableScan?.image ?: return null
        return finish(PassportScanResult.ImageOnly(image))
    }

    fun showTips(visible: Boolean) {
        isTipsVisible = visible
    }

    private fun finish(result: PassportScanResult): PassportScanResult {
        isFinished = true
        isProcessing = false
        unreadableScan = null
        reviewScan = null
        expiredScan = null
        cropImage = null
        return result
    }
}
