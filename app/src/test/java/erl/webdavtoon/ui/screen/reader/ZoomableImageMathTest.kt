// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.reader

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomableImageMathTest {

    @Test
    fun `calculateClampedOffset returns zero at scale 1`() {
        val clamped = calculateClampedOffset(
            offset = Offset(100f, 100f),
            scale = 1.0f,
            containerWidth = 1080f,
            containerHeight = 1920f
        )
        assertEquals(0f, clamped.x, 0.001f)
        assertEquals(0f, clamped.y, 0.001f)
    }

    @Test
    fun `calculateClampedOffset clamps when offset exceeds boundaries`() {
        val containerWidth = 1000f
        val containerHeight = 2000f
        val scale = 2.0f
        // maxX = (1000 * 1) / 2 = 500f, maxY = (2000 * 1) / 2 = 1000f

        val within = calculateClampedOffset(
            offset = Offset(200f, -400f),
            scale = scale,
            containerWidth = containerWidth,
            containerHeight = containerHeight
        )
        assertEquals(200f, within.x, 0.001f)
        assertEquals(-400f, within.y, 0.001f)

        val exceeded = calculateClampedOffset(
            offset = Offset(800f, -1500f),
            scale = scale,
            containerWidth = containerWidth,
            containerHeight = containerHeight
        )
        assertEquals(500f, exceeded.x, 0.001f)
        assertEquals(-1000f, exceeded.y, 0.001f)
    }

    @Test
    fun `shouldConsumeDrag returns true only when scale exceeds 1_05`() {
        assertFalse(shouldConsumeDrag(1.0f))
        assertFalse(shouldConsumeDrag(1.04f))
        assertFalse(shouldConsumeDrag(1.05f))
        assertTrue(shouldConsumeDrag(1.06f))
        assertTrue(shouldConsumeDrag(2.5f))
        assertTrue(shouldConsumeDrag(4.0f))
    }

    @Test
    fun `calculateDoubleTapTarget toggles between 1_0 and 2_5`() {
        val containerWidth = 1000f
        val containerHeight = 1000f

        // When zoomed at 2.5f -> double tap resets to 1.0f and Offset.Zero
        val (resetScale, resetOffset) = calculateDoubleTapTarget(
            currentScale = 2.5f,
            tapOffset = Offset(300f, 300f),
            containerWidth = containerWidth,
            containerHeight = containerHeight
        )
        assertEquals(1.0f, resetScale, 0.001f)
        assertEquals(0f, resetOffset.x, 0.001f)
        assertEquals(0f, resetOffset.y, 0.001f)

        // When at 1.0f -> double tap zooms to 2.5f
        val (zoomScale, zoomOffset) = calculateDoubleTapTarget(
            currentScale = 1.0f,
            tapOffset = Offset(500f, 500f), // center tap
            containerWidth = containerWidth,
            containerHeight = containerHeight
        )
        assertEquals(2.5f, zoomScale, 0.001f)
        assertEquals(0f, zoomOffset.x, 0.001f)
        assertEquals(0f, zoomOffset.y, 0.001f)
    }

    @Test
    fun `calculatePinchScale bounds within 1_0 and maxScale`() {
        val scaleBelow = calculatePinchScale(currentScale = 1.0f, zoomChange = 0.5f, maxScale = 4.0f)
        assertEquals(1.0f, scaleBelow, 0.001f)

        val scaleNormal = calculatePinchScale(currentScale = 2.0f, zoomChange = 1.5f, maxScale = 4.0f)
        assertEquals(3.0f, scaleNormal, 0.001f)

        val scaleAbove = calculatePinchScale(currentScale = 3.0f, zoomChange = 2.0f, maxScale = 4.0f)
        assertEquals(4.0f, scaleAbove, 0.001f)
    }

    @Test
    fun `calculatePinchOffset returns Zero when newScale is 1_0 or below`() {
        val offset = calculatePinchOffset(
            currentOffset = Offset(100f, 100f),
            panChange = Offset(10f, 10f),
            centroid = Offset(500f, 500f),
            currentScale = 1.2f,
            newScale = 1.0f,
            containerWidth = 1000f,
            containerHeight = 1000f
        )
        assertEquals(0f, offset.x, 0.001f)
        assertEquals(0f, offset.y, 0.001f)
    }
}
