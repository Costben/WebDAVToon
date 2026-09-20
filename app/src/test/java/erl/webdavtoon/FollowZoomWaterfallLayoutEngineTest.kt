package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowZoomWaterfallLayoutEngineTest {

    @Test
    fun `integer two column layout keeps row major placement`() {
        val layout = FollowZoomWaterfallLayoutEngine.computeLayout(
            aspectRatios = List(6) { 1f },
            containerWidth = 208,
            spacing = 8,
            virtualColumns = 2f
        )

        assertEquals(6, layout.frames.size)
        assertEquals(0, layout.frames[0].left)
        assertEquals(108, layout.frames[1].left)
        assertEquals(0, layout.frames[0].top)
        assertEquals(0, layout.frames[1].top)
        assertEquals(108, layout.frames[2].top)
        assertEquals(108, layout.frames[3].top)
        assertEquals(316, layout.contentHeight)
    }

    @Test
    fun `virtual columns interpolate between adjacent integer layouts`() {
        val twoColumns = FollowZoomWaterfallLayoutEngine.computeLayout(
            aspectRatios = List(4) { 1f },
            containerWidth = 208,
            spacing = 8,
            virtualColumns = 2f
        )
        val threeColumns = FollowZoomWaterfallLayoutEngine.computeLayout(
            aspectRatios = List(4) { 1f },
            containerWidth = 208,
            spacing = 8,
            virtualColumns = 3f
        )
        val interpolated = FollowZoomWaterfallLayoutEngine.computeLayout(
            aspectRatios = List(4) { 1f },
            containerWidth = 208,
            spacing = 8,
            virtualColumns = 2.5f
        )

        assertEquals((twoColumns.frames[1].left + threeColumns.frames[1].left) / 2, interpolated.frames[1].left)
        assertEquals((twoColumns.frames[2].top + threeColumns.frames[2].top) / 2, interpolated.frames[2].top)
        assertTrue(interpolated.contentHeight in threeColumns.contentHeight..twoColumns.contentHeight)
    }

    @Test
    fun `virtual column content height matches actual max frame bottom`() {
        val interpolated = FollowZoomWaterfallLayoutEngine.computeLayout(
            aspectRatios = listOf(0.5f, 1f, 2f, 0.75f, 1.2f, 0.8f),
            containerWidth = 320,
            spacing = 8,
            virtualColumns = 2.5f
        )

        val expectedContentHeight = interpolated.frames.maxOf { it.top + it.height }
        assertEquals(expectedContentHeight, interpolated.contentHeight)
    }

    @Test
    fun `interpolating cached discrete layouts matches direct virtual layout`() {
        val aspectRatios = floatArrayOf(0.5f, 1f, 2f, 0.75f, 1.2f, 0.8f, 1.4f, 0.65f)
        val twoColumns = FollowZoomWaterfallLayoutEngine.computeDiscreteLayout(
            aspectRatios = aspectRatios,
            containerWidth = 320,
            spacing = 8,
            columns = 2
        )
        val threeColumns = FollowZoomWaterfallLayoutEngine.computeDiscreteLayout(
            aspectRatios = aspectRatios,
            containerWidth = 320,
            spacing = 8,
            columns = 3
        )
        val cachedPath = FollowZoomWaterfallLayoutEngine.interpolateLayout(
            lower = twoColumns,
            upper = threeColumns,
            progress = 0.5f
        )
        val directPath = FollowZoomWaterfallLayoutEngine.computeLayout(
            aspectRatios = aspectRatios,
            containerWidth = 320,
            spacing = 8,
            virtualColumns = 2.5f
        )

        assertEquals(directPath.contentHeight, cachedPath.contentHeight)
        assertEquals(directPath.frames, cachedPath.frames)
    }

    @Test
    fun `five column layout is supported and uses row major placement`() {
        val layout = FollowZoomWaterfallLayoutEngine.computeLayout(
            aspectRatios = List(10) { 1f },
            containerWidth = 572,
            spacing = 8,
            virtualColumns = 5f
        )

        assertEquals(10, layout.frames.size)
        assertEquals(0, layout.frames[0].left)
        assertEquals(0, layout.frames[4].top)
        assertEquals(layout.frames[0].width, layout.frames[4].width)
        assertEquals(108, layout.frames[0].width)
    }

    @Test
    fun `fractional interpolation between four and five columns is monotonic`() {
        val ratios = floatArrayOf(0.5f, 1f, 2f, 0.75f, 1.2f, 0.8f, 1.4f, 0.65f, 1f, 1f)
        val four = FollowZoomWaterfallLayoutEngine.computeDiscreteLayout(
            aspectRatios = ratios,
            containerWidth = 508,
            spacing = 8,
            columns = 4
        )
        val five = FollowZoomWaterfallLayoutEngine.computeDiscreteLayout(
            aspectRatios = ratios,
            containerWidth = 508,
            spacing = 8,
            columns = 5
        )
        val cached = FollowZoomWaterfallLayoutEngine.interpolateLayout(four, five, 0.5f)
        val direct = FollowZoomWaterfallLayoutEngine.computeLayout(
            aspectRatios = ratios,
            containerWidth = 508,
            spacing = 8,
            virtualColumns = 4.5f
        )

        assertEquals(direct.frames, cached.frames)
        assertEquals(direct.contentHeight, cached.contentHeight)
        assertTrue(cached.frames[0].width in five.frames[0].width..four.frames[0].width)
    }

    @Test
    fun `visible window clipping returns only intersecting items`() {
        val layout = FollowZoomWaterfallLayoutEngine.computeLayout(
            aspectRatios = List(12) { 1f },
            containerWidth = 208,
            spacing = 8,
            virtualColumns = 2f
        )

        val visible = FollowZoomWaterfallLayoutEngine.findVisibleItems(
            frames = layout.frames,
            scrollOffset = 100,
            viewportHeight = 180,
            extraBuffer = 0
        )

        assertEquals(listOf(0, 1, 2, 3, 4, 5), visible)
    }

    @Test
    fun `anchor helper preserves focal point within item`() {
        val layout = FollowZoomWaterfallLayoutEngine.computeLayout(
            aspectRatios = List(12) { 1f },
            containerWidth = 208,
            spacing = 8,
            virtualColumns = 2f
        )
        val frame = layout.frames[6]

        val targetScroll = FollowZoomWaterfallLayoutEngine.computeAnchoredScrollOffset(
            frame = frame,
            anchorYFraction = 0.4f,
            focusY = 150f,
            viewportHeight = 400,
            contentHeight = layout.contentHeight
        )

        val anchoredY = frame.top - targetScroll + frame.height * 0.4f
        assertEquals(150f, anchoredY, 1.0f)
    }
}
