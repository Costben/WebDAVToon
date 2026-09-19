package erl.webdavtoon.ui.screen.waterfall

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowZoomStateTest {

    @Test
    fun `initial state reflects configured columns`() {
        val state = FollowZoomState(
            currentColumns = 2,
            minColumns = 1,
            maxColumns = 5
        )

        assertEquals(2, state.currentColumns)
        assertEquals(2.0f, state.virtualColumns, 0.001f)
        assertEquals(1.0f, state.scale, 0.001f)
        assertEquals(2, state.previewColumns)
        assertFalse(state.isZooming)
    }

    @Test
    fun `preview columns tracks the rounded target while pinching`() {
        val state = FollowZoomState(
            currentColumns = 2,
            minColumns = 1,
            maxColumns = 5
        )

        state.onPinchStart()
        // 2 / 1.5 = 1.33 -> preview rounds to 1 column mid-gesture
        state.onPinch(Offset.Zero, 1.5f, IntSize(1000, 1000))
        assertEquals(1, state.previewColumns)
        assertTrue(state.isZooming)

        // 2 / 0.6 = 3.33 -> preview rounds to 3 columns mid-gesture
        state.onPinch(Offset.Zero, 0.6f / 1.5f, IntSize(1000, 1000))
        assertEquals(3, state.previewColumns)

        state.onPinchEnd()
        assertEquals(3, state.previewColumns)
        assertFalse(state.isZooming)
    }

    @Test
    fun `preview columns clamps to configured bounds`() {
        val state = FollowZoomState(
            currentColumns = 2,
            minColumns = 1,
            maxColumns = 4
        )

        state.onPinchStart()
        state.onPinch(Offset.Zero, 0.05f, IntSize(1000, 1000))
        assertEquals(4, state.previewColumns)

        state.onPinchEnd()
        assertEquals(4, state.previewColumns)
        assertEquals(4, state.currentColumns)
    }

    @Test
    fun `pinch expand decreases virtual columns and updates scale`() {
        var reportedVirtual = 0f
        var reportedZooming = false

        val state = FollowZoomState(
            currentColumns = 2,
            minColumns = 1,
            maxColumns = 5,
            onVirtualColumnsChanged = { v, zooming ->
                reportedVirtual = v
                reportedZooming = zooming
            }
        )

        state.onPinchStart(
            startCentroid = Offset(500f, 500f),
            containerSize = IntSize(1000, 1000)
        )

        assertTrue(state.isZooming)
        assertEquals(2.0f, state.virtualColumns, 0.001f)
        assertEquals(0.5f, state.transformOrigin.pivotFractionX, 0.001f)
        assertEquals(0.5f, state.transformOrigin.pivotFractionY, 0.001f)

        // Expand fingers by 1.5x -> 2.0 / 1.5 = 1.33 columns
        state.onPinch(
            centroid = Offset(600f, 600f),
            zoomChange = 1.5f,
            containerSize = IntSize(1000, 1000)
        )

        assertEquals(1.333f, state.virtualColumns, 0.01f)
        assertEquals(1.333f, reportedVirtual, 0.01f)
        assertTrue(reportedZooming)
        assertTrue(state.scale > 1.0f)
        assertEquals(0.6f, state.transformOrigin.pivotFractionX, 0.001f)
        assertEquals(0.6f, state.transformOrigin.pivotFractionY, 0.001f)
    }

    @Test
    fun `pinch inward increases virtual columns and clamps to max`() {
        val state = FollowZoomState(
            currentColumns = 2,
            minColumns = 1,
            maxColumns = 4
        )

        state.onPinchStart()
        // Pinch inward significantly (zoomChange = 0.3) -> 2 / 0.3 = 6.67, clamped to maxColumns (4)
        state.onPinch(
            centroid = Offset.Zero,
            zoomChange = 0.3f,
            containerSize = IntSize(1000, 1000)
        )

        assertEquals(4.0f, state.virtualColumns, 0.001f)
        assertTrue(state.scale < 1.0f)
    }

    @Test
    fun `pinch end snaps to nearest roundToInt columns`() {
        var changedColumns = 0
        val state = FollowZoomState(
            currentColumns = 2,
            minColumns = 1,
            maxColumns = 5,
            onColumnsChanged = { changedColumns = it }
        )

        state.onPinchStart()
        // 2 / 1.5 = 1.33 -> roundToInt = 1
        state.onPinch(
            centroid = Offset.Zero,
            zoomChange = 1.5f,
            containerSize = IntSize(1000, 1000)
        )
        state.onPinchEnd()

        assertEquals(1, changedColumns)
        assertEquals(1, state.currentColumns)
        assertEquals(1.0f, state.virtualColumns, 0.001f)
        assertEquals(1.0f, state.scale, 0.001f)
        assertFalse(state.isZooming)
    }

    @Test
    fun `reset restores neutral zoom state`() {
        val state = FollowZoomState(
            currentColumns = 3,
            minColumns = 1,
            maxColumns = 5
        )

        state.onPinchStart()
        state.onPinch(Offset.Zero, 1.4f, IntSize(100, 100))
        assertTrue(state.isZooming)

        state.reset()
        assertFalse(state.isZooming)
        assertEquals(1.0f, state.scale, 0.001f)
        assertEquals(3.0f, state.virtualColumns, 0.001f)
    }
}
