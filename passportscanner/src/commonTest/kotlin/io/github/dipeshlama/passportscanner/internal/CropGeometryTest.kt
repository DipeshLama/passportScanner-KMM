package io.github.dipeshlama.passportscanner.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CropGeometryTest {
    private val minSize = Size(0.1f, 0.1f)

    private fun assertRect(expected: Rect, actual: Rect) {
        val close = abs(expected.left - actual.left) < 1e-4f && abs(expected.top - actual.top) < 1e-4f &&
            abs(expected.right - actual.right) < 1e-4f && abs(expected.bottom - actual.bottom) < 1e-4f
        assertTrue(close, "expected $expected but was $actual")
    }

    @Test
    fun fitsWideImageInTallContainer() {
        assertEquals(Rect(0f, 350f, 400f, 650f), fitImage(Size(800f, 600f), Size(400f, 1000f)))
    }

    @Test
    fun fitsTallImageInWideContainer() {
        assertEquals(Rect(350f, 0f, 650f, 400f), fitImage(Size(600f, 800f), Size(1000f, 400f)))
    }

    @Test
    fun fitReturnsZeroForEmptySizes() {
        assertEquals(Rect.Zero, fitImage(Size.Zero, Size(100f, 100f)))
        assertEquals(Rect.Zero, fitImage(Size(100f, 100f), Size.Zero))
    }

    @Test
    fun mapsNormalizedCropToScreen() {
        val bounds = Rect(10f, 20f, 210f, 120f)
        assertEquals(Rect(60f, 45f, 160f, 95f), Rect(0.25f, 0.25f, 0.75f, 0.75f).toScreen(bounds))
    }

    @Test
    fun hitTestPrefersCornersThenEdgesThenMove() {
        val crop = Rect(100f, 100f, 300f, 300f)
        assertEquals(CropHandle.TopLeft, hitTestCrop(crop, Offset(105f, 95f), 20f))
        assertEquals(CropHandle.BottomRight, hitTestCrop(crop, Offset(310f, 290f), 20f))
        assertEquals(CropHandle.Top, hitTestCrop(crop, Offset(200f, 110f), 20f))
        assertEquals(CropHandle.Right, hitTestCrop(crop, Offset(295f, 200f), 20f))
        assertEquals(CropHandle.Move, hitTestCrop(crop, Offset(200f, 200f), 20f))
        assertNull(hitTestCrop(crop, Offset(50f, 200f), 20f))
        assertNull(hitTestCrop(crop, Offset(200f, 400f), 20f))
    }

    @Test
    fun cornerDragResizesOnlyItsEdges() {
        val result = Rect(0.2f, 0.2f, 0.8f, 0.8f).dragBy(CropHandle.TopLeft, Offset(0.1f, -0.1f), minSize)
        assertRect(Rect(0.3f, 0.1f, 0.8f, 0.8f), result)
    }

    @Test
    fun edgeDragMovesOneEdge() {
        val result = Rect(0.2f, 0.2f, 0.8f, 0.8f).dragBy(CropHandle.Bottom, Offset(0.3f, 0.1f), minSize)
        assertRect(Rect(0.2f, 0.2f, 0.8f, 0.9f), result)
    }

    @Test
    fun resizeStaysInsideImage() {
        val result = Rect(0.2f, 0.2f, 0.8f, 0.8f).dragBy(CropHandle.BottomRight, Offset(1f, 1f), minSize)
        assertRect(Rect(0.2f, 0.2f, 1f, 1f), result)
    }

    @Test
    fun resizeKeepsMinimumSize() {
        val result = Rect(0.2f, 0.2f, 0.8f, 0.8f).dragBy(CropHandle.TopLeft, Offset(1f, 1f), minSize)
        assertRect(Rect(0.7f, 0.7f, 0.8f, 0.8f), result)
    }

    @Test
    fun moveKeepsSizeAndStaysInsideImage() {
        val result = Rect(0.2f, 0.2f, 0.6f, 0.5f).dragBy(CropHandle.Move, Offset(1f, -1f), minSize)
        assertRect(Rect(0.6f, 0f, 1f, 0.3f), result)
    }

    @Test
    fun convertsToPixels() {
        assertEquals(IntRect(100, 50, 300, 150), Rect(0.25f, 0.25f, 0.75f, 0.75f).toPixelCrop(400, 200))
    }

    @Test
    fun fullCropMeansNoCrop() {
        assertNull(FullCrop.toPixelCrop(400, 200))
        assertNull(Rect(0.5f, 0.5f, 0.5f, 0.6f).toPixelCrop(400, 200))
        assertNull(FullCrop.toPixelCrop(0, 0))
    }
}
