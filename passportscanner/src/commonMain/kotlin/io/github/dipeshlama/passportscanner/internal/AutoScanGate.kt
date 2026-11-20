package io.github.dipeshlama.passportscanner.internal

import io.github.dipeshlama.passportscanner.mrz.MrzMatch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Decides when auto-scan may finish: after a short warm-up, the same MRZ must be read inside the
 * frame for [holdDuration] and at least [minReadings] times. Not thread-safe.
 */
internal class AutoScanGate(
    private val warmUp: Duration = 1500.milliseconds,
    private val holdDuration: Duration = 1000.milliseconds,
    private val minReadings: Int = 2,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {
    private var sessionStart: TimeMark? = null
    private var holdStart: TimeMark? = null
    private var lastKey: Any? = null
    private var readings = 0

    fun accept(match: MrzMatch?, imageWidth: Int, imageHeight: Int, crop: PreviewCrop?): Boolean {
        val session = sessionStart ?: timeSource.markNow().also { sessionStart = it }
        if (match == null) return false
        if (session.elapsedNow() < warmUp || !match.isInsideFrame(imageWidth, imageHeight, crop)) {
            clearHold()
            return false
        }
        val key = with(match.data) { Triple(documentNumber, dateOfBirth, expiryDate) }
        if (key != lastKey) {
            lastKey = key
            holdStart = timeSource.markNow()
            readings = 0
        }
        readings++
        val heldFor = holdStart?.elapsedNow() ?: Duration.ZERO
        return readings >= minReadings && heldFor >= holdDuration
    }

    fun reset() {
        sessionStart = null
        clearHold()
    }

    private fun clearHold() {
        holdStart = null
        lastKey = null
        readings = 0
    }
}

internal fun MrzMatch.isInsideFrame(imageWidth: Int, imageHeight: Int, crop: PreviewCrop?): Boolean {
    val frame = crop?.toReadingRect(imageWidth, imageHeight) ?: return true
    return bounds.left >= frame.left &&
        bounds.right <= frame.right + frame.width * MRZ_RIGHT_EDGE_TOLERANCE &&
        bounds.bottom <= frame.bottom &&
        bounds.top >= frame.top + frame.height * MRZ_MIN_TOP_RATIO &&
        bounds.width >= frame.width * MRZ_MIN_WIDTH_RATIO
}

private const val MRZ_MIN_TOP_RATIO = 0.5f
private const val MRZ_MIN_WIDTH_RATIO = 0.6f
private const val MRZ_RIGHT_EDGE_TOLERANCE = 0.1f
