// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.waterfall

import androidx.compose.ui.geometry.Offset
import erl.webdavtoon.WaterfallItemFrame
import erl.webdavtoon.WaterfallLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class FollowZoomStateTest {

    private fun fakeLayout(columns: Float, count: Int = 12): WaterfallLayoutResult {
        val cols = ceil(columns).toInt().coerceIn(1, 5)
        val width = (1000 / cols).coerceAtLeast(1)
        val frames = List(count) { index ->
            WaterfallItemFrame(
                index = index,
                left = (index % cols) * width,
                top = (index / cols) * 100,
                width = width,
                height = 100,
            )
        }
        return WaterfallLayoutResult(frames, (count / cols + 1) * 100)
    }

    private fun zoomState(
        columns: Int = 2,
        min: Int = 1,
        max: Int = 5,
        onColumnsChanged: (Int) -> Unit = {},
    ): FollowZoomGridState {
        val state = FollowZoomGridState(columns, min, max)
        state.onColumnsChanged = onColumnsChanged
        state.viewportHeight = 800
        state.topPadding = 0
        state.bottomPadding = 0
        state.layoutProvider = { cols -> fakeLayout(cols) }
        state.lastFrames = fakeLayout(columns.toFloat()).frames
        state.itemsContentHeight = 1200
        return state
    }

    @Test
    fun `initial state reflects configured columns`() {
        val state = zoomState()
        assertEquals(2f, state.virtualColumns, 0.001f)
        assertFalse(state.isZooming)
    }

    @Test
    fun `pinch expand decreases virtual columns continuously`() {
        val state = zoomState()
        state.onPinchStart(Offset(100f, 300f))
        assertTrue(state.isZooming)

        // 2 / 1.5 = 1.333, kept fractional instead of snapping to an integer.
        state.onPinch(1.5f)
        assertEquals(1.333f, state.virtualColumns, 0.01f)
    }

    @Test
    fun `pinch inward clamps to max columns`() {
        val state = zoomState()
        state.onPinchStart(Offset.Zero)
        state.onPinch(0.1f)
        assertEquals(5f, state.virtualColumns, 0.001f)
    }

    @Test
    fun `pinch end settles to nearest integer and reports the change`() {
        var changed = -1
        val state = zoomState(onColumnsChanged = { changed = it })

        state.onPinchStart(Offset(100f, 300f))
        state.onPinch(1.5f)
        state.onPinchEnd()

        assertEquals(1f, state.virtualColumns, 0.001f)
        assertEquals(1, changed)
        assertFalse(state.isZooming)
    }

    @Test
    fun `pinch end without a scope settles immediately`() {
        val state = zoomState(columns = 3)
        state.onPinchStart(Offset.Zero)
        state.onPinch(2.4f) // 3 / 2.4 = 1.25 -> rounds to 1
        state.onPinchEnd()
        assertEquals(1f, state.virtualColumns, 0.001f)
    }

    @Test
    fun `anchor keeps the pinched point stable across column changes`() {
        val state = zoomState(columns = 2)
        val provider = requireNotNull(state.layoutProvider)
        val framesTwo = provider(2f).frames
        val anchorIndex = framesTwo.indexOfFirst { 300f >= it.top && 300f < it.top + it.height }
        assertTrue(anchorIndex >= 0)

        state.onPinchStart(Offset(50f, 300f))
        state.onPinch(2f) // 2 / 2 = 1 column
        assertEquals(1f, state.virtualColumns, 0.001f)

        val framesOne = provider(1f).frames
        val anchoredY = framesOne[anchorIndex].top - state.scrollOffset
        assertEquals(300f, anchoredY.toFloat(), 2f)
    }

    @Test
    fun `scrollBy clamps to the content range`() {
        val state = zoomState()
        val consumed = state.scrollBy(-10_000f)
        assertEquals(-400f, consumed, 0.001f)
        assertEquals(400, state.scrollOffset)
    }

    @Test
    fun `scrollBy follows the finger direction`() {
        val state = zoomState()
        assertEquals(-120f, state.scrollBy(-120f), 0.001f)
        assertEquals(120, state.scrollOffset)

        assertEquals(120f, state.scrollBy(120f), 0.001f)
        assertEquals(0, state.scrollOffset)

        assertEquals(0f, state.scrollBy(120f), 0.001f)
        assertEquals(0, state.scrollOffset)
    }

    @Test
    fun `scrollBy reports unrounded consumption so flings are not cancelled`() {
        val state = zoomState()
        // The default fling behavior cancels the animation as soon as it sees
        // `abs(delta - consumed) > 0.5f`, so consumption must not be snapped to the integer offset.
        assertEquals(-191.4f, state.scrollBy(-191.4f), 0f)
        assertEquals(191, state.scrollOffset)
        assertEquals(-180.9f, state.scrollBy(-180.9f), 0f)
        assertEquals(372, state.scrollOffset)
    }

    @Test
    fun `resolving a taller item above the viewport keeps the visible window in place`() {
        val state = zoomState()
        val ratios = FloatArray(12) { 1f }
        val before = state.obtainLayout(ratios, 1000, 0, 0, 0, null, 2f)
        state.itemsContentHeight = 6000
        state.scrollBy(-900f)
        assertEquals(900, state.scrollOffset)
        val anchor = before.topVisibleIndex(900)
        assertTrue(anchor >= 0)

        // both items above the viewport grow from 500px to 1000px tall
        val tallerFirstItems = ratios.clone()
        tallerFirstItems[0] = 0.5f
        tallerFirstItems[1] = 0.5f
        val after = state.obtainLayout(tallerFirstItems, 1000, 0, 0, 0, null, 2f)

        val screenBefore = before.result.frames[anchor].top - 900
        val screenAfter = after.result.frames[anchor].top - state.scrollOffset
        assertEquals(screenBefore, screenAfter)
        assertEquals(1400, state.scrollOffset)
    }

    @Test
    fun `syncColumns adopts the persisted value while idle`() {
        val state = zoomState()
        state.syncColumns(4)
        assertEquals(4f, state.virtualColumns, 0.001f)
    }
}
