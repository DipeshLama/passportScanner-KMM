package io.github.dipeshlama.passportscanner.internal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreviewCropTest {
    @Test
    fun mapsFrameThroughCenterCrop() {
        val crop = PreviewCrop(
            previewWidth = 1000f,
            previewHeight = 1000f,
            frame = Rect(left = 100f, top = 400f, right = 900f, bottom = 600f)
        )
        assertEquals(IntRect(left = 108, top = 852, right = 972, bottom = 1068), crop.toImageRect(1080, 1920))
        assertEquals(IntRect(left = 73, top = 843, right = 1007, bottom = 1077), crop.toReadingRect(1080, 1920))
    }

    @Test
    fun clampsToImageBounds() {
        val crop = PreviewCrop(1080f, 1920f, Rect(0f, 0f, 1080f, 1920f))
        assertEquals(IntRect(0, 0, 1080, 1920), crop.toImageRect(1080, 1920))
    }

    @Test
    fun returnsNullBeforeLayout() {
        assertNull(PreviewCrop(0f, 0f, Rect.Zero).toImageRect(1080, 1920))
    }
}
