package io.github.dipeshlama.passportscanner.internal

import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Keeps camera frames flowing while they're read with Vision.
 *
 * Kotlin/Native releases an Objective-C object (such as a `VNImageRequestHandler`, which retains
 * the camera frame) only when its garbage collector runs, not when the last reference goes away.
 * The camera hands out frames from a small buffer pool, so frames kept alive that way can empty
 * the pool, and the preview then freezes until the next collection returns them. Scheduling a
 * collection regularly while frames are read gives them back in time.
 *
 * Call [onFrameRead] on the video queue after each frame. Not thread-safe.
 */
@OptIn(NativeRuntimeApi::class)
internal class CameraFrameReleaser(private val interval: Duration = 300.milliseconds) {
    private var lastCollection = TimeSource.Monotonic.markNow()

    fun onFrameRead() {
        if (lastCollection.elapsedNow() < interval) return
        lastCollection = TimeSource.Monotonic.markNow()
        GC.schedule()
    }
}
