package io.github.dipeshlama.barcodescanner.internal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewCropTest {
    // A 1000x1000 preview showing a 1080x1920 image, center-cropped: scale 0.926, 389 image px cut top and bottom.
    private val crop = PreviewCrop(
        previewWidth = 1000f,
        previewHeight = 1000f,
        frame = Rect(left = 250f, top = 250f, right = 750f, bottom = 750f)
    )

    @Test
    fun mapsFrameWithMarginThroughCenterCrop() {
        assertEquals(IntRect(left = 216, top = 636, right = 864, bottom = 1284), crop.toReadingRect(1080, 1920))
    }

    @Test
    fun acceptsCodesInsideTheFrame() {
        assertTrue(crop.contains(Rect(300f, 700f, 800f, 1200f), 1080, 1920))
    }

    @Test
    fun rejectsCodesReachingOutsideTheFrame() {
        assertFalse(crop.contains(Rect(0f, 700f, 500f, 1200f), 1080, 1920))
        assertFalse(crop.contains(Rect(300f, 100f, 800f, 600f), 1080, 1920))
    }

    @Test
    fun acceptsEverythingBeforeLayout() {
        val none: PreviewCrop? = null
        assertTrue(none.contains(Rect(0f, 0f, 10f, 10f), 1080, 1920))
        assertNull(PreviewCrop(0f, 0f, Rect.Zero).toReadingRect(1080, 1920))
    }
}
