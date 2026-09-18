package erl.webdavtoon.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class FastScrollerMathTest {

    @Test
    fun `calculateTargetIndex returns 0 when totalCount is 0 or 1`() {
        assertEquals(0, calculateTargetIndex(touchY = 100f, trackHeight = 500f, totalCount = 0))
        assertEquals(0, calculateTargetIndex(touchY = 100f, trackHeight = 500f, totalCount = 1))
    }

    @Test
    fun `calculateTargetIndex returns 0 when trackHeight is 0 or negative`() {
        assertEquals(0, calculateTargetIndex(touchY = 100f, trackHeight = 0f, totalCount = 10))
        assertEquals(0, calculateTargetIndex(touchY = 100f, trackHeight = -50f, totalCount = 10))
    }

    @Test
    fun `calculateTargetIndex clamps touchY above or below track bounds`() {
        val totalCount = 10
        val trackHeight = 1000f

        // Above top edge (negative Y)
        assertEquals(0, calculateTargetIndex(touchY = -100f, trackHeight = trackHeight, totalCount = totalCount))
        assertEquals(0, calculateTargetIndex(touchY = 0f, trackHeight = trackHeight, totalCount = totalCount))

        // Below bottom edge
        assertEquals(9, calculateTargetIndex(touchY = 1000f, trackHeight = trackHeight, totalCount = totalCount))
        assertEquals(9, calculateTargetIndex(touchY = 1200f, trackHeight = trackHeight, totalCount = totalCount))
    }

    @Test
    fun `calculateTargetIndex calculates correct intermediate indices`() {
        val trackHeight = 1000f
        val totalCount = 11 // indices: 0 to 10

        // Exact half
        assertEquals(5, calculateTargetIndex(touchY = 500f, trackHeight = trackHeight, totalCount = totalCount))

        // 10% -> 0.1 * 10 = 1
        assertEquals(1, calculateTargetIndex(touchY = 100f, trackHeight = trackHeight, totalCount = totalCount))

        // 90% -> 0.9 * 10 = 9
        assertEquals(9, calculateTargetIndex(touchY = 900f, trackHeight = trackHeight, totalCount = totalCount))

        // Rounding: 0.24 * 10 = 2.4 -> 2, 0.26 * 10 = 2.6 -> 3
        assertEquals(2, calculateTargetIndex(touchY = 240f, trackHeight = trackHeight, totalCount = totalCount))
        assertEquals(3, calculateTargetIndex(touchY = 260f, trackHeight = trackHeight, totalCount = totalCount))
    }

    @Test
    fun `calculateThumbOffsetY returns 0 when totalCount is 0 or 1`() {
        assertEquals(0f, calculateThumbOffsetY(currentIndex = 0, totalCount = 0, trackHeight = 800f, thumbHeight = 100f), 0.001f)
        assertEquals(0f, calculateThumbOffsetY(currentIndex = 0, totalCount = 1, trackHeight = 800f, thumbHeight = 100f), 0.001f)
    }

    @Test
    fun `calculateThumbOffsetY returns 0 when track is smaller than or equal to thumb`() {
        assertEquals(0f, calculateThumbOffsetY(currentIndex = 5, totalCount = 10, trackHeight = 100f, thumbHeight = 100f), 0.001f)
        assertEquals(0f, calculateThumbOffsetY(currentIndex = 5, totalCount = 10, trackHeight = 80f, thumbHeight = 100f), 0.001f)
    }

    @Test
    fun `calculateThumbOffsetY aligns thumb at top and bottom bounds`() {
        val trackHeight = 1000f
        val thumbHeight = 100f
        val totalCount = 11 // indices: 0 to 10
        val maxOffset = 900f

        // Top edge: currentIndex = 0
        assertEquals(0f, calculateThumbOffsetY(currentIndex = 0, totalCount = totalCount, trackHeight = trackHeight, thumbHeight = thumbHeight), 0.001f)

        // Bottom edge: currentIndex = 10
        assertEquals(maxOffset, calculateThumbOffsetY(currentIndex = 10, totalCount = totalCount, trackHeight = trackHeight, thumbHeight = thumbHeight), 0.001f)

        // Center: currentIndex = 5 -> 5/10 * 900 = 450
        assertEquals(450f, calculateThumbOffsetY(currentIndex = 5, totalCount = totalCount, trackHeight = trackHeight, thumbHeight = thumbHeight), 0.001f)
    }

    @Test
    fun `calculateThumbOffsetY clamps out-of-range currentIndex`() {
        val trackHeight = 1000f
        val thumbHeight = 100f
        val totalCount = 10 // indices 0 to 9
        val maxOffset = 900f

        // Below 0
        assertEquals(0f, calculateThumbOffsetY(currentIndex = -5, totalCount = totalCount, trackHeight = trackHeight, thumbHeight = thumbHeight), 0.001f)

        // Above totalCount - 1
        assertEquals(maxOffset, calculateThumbOffsetY(currentIndex = 20, totalCount = totalCount, trackHeight = trackHeight, thumbHeight = thumbHeight), 0.001f)
    }
}
