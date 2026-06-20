package erl.webdavtoon

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class WaterfallItemFrame(
    val index: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

data class WaterfallLayoutResult(
    val frames: List<WaterfallItemFrame>,
    val contentHeight: Int
)

object FollowZoomWaterfallLayoutEngine {

    private const val MIN_COLUMNS = 1
    private const val MAX_COLUMNS = 4
    private const val DEFAULT_ASPECT_RATIO = 1f

    fun computeLayout(
        aspectRatios: List<Float>,
        containerWidth: Int,
        spacing: Int,
        virtualColumns: Float
    ): WaterfallLayoutResult {
        return computeLayout(
            aspectRatios = aspectRatios.toFloatArray(),
            containerWidth = containerWidth,
            spacing = spacing,
            virtualColumns = virtualColumns
        )
    }

    fun computeLayout(
        aspectRatios: FloatArray,
        containerWidth: Int,
        spacing: Int,
        virtualColumns: Float
    ): WaterfallLayoutResult {
        if (aspectRatios.isEmpty() || containerWidth <= 0) {
            return WaterfallLayoutResult(emptyList(), 0)
        }

        val clampedColumns = virtualColumns.coerceIn(MIN_COLUMNS.toFloat(), MAX_COLUMNS.toFloat())
        val lowerColumns = floor(clampedColumns).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val upperColumns = ceil(clampedColumns).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)

        if (lowerColumns == upperColumns) {
            return computeDiscreteLayout(aspectRatios, containerWidth, spacing, lowerColumns)
        }

        val progress = clampedColumns - lowerColumns
        val lower = computeDiscreteLayout(aspectRatios, containerWidth, spacing, lowerColumns)
        val upper = computeDiscreteLayout(aspectRatios, containerWidth, spacing, upperColumns)
        return interpolateLayout(lower, upper, progress)
    }

    fun computeDiscreteLayout(
        aspectRatios: FloatArray,
        containerWidth: Int,
        spacing: Int,
        columns: Int
    ): WaterfallLayoutResult {
        return buildDiscreteLayout(aspectRatios, containerWidth, spacing, columns)
    }

    fun interpolateLayout(
        lower: WaterfallLayoutResult,
        upper: WaterfallLayoutResult,
        progress: Float
    ): WaterfallLayoutResult {
        if (lower.frames.size != upper.frames.size) {
            return if (progress < 0.5f) lower else upper
        }
        if (progress <= 0f) return lower
        if (progress >= 1f) return upper

        val lowerFrames = lower.frames
        val upperFrames = upper.frames
        val frames = ArrayList<WaterfallItemFrame>(lowerFrames.size)
        for (index in lowerFrames.indices) {
            val from = lowerFrames[index]
            val to = upperFrames[index]
            frames.add(
                WaterfallItemFrame(
                    index = from.index,
                    left = lerpInt(from.left, to.left, progress),
                    top = lerpInt(from.top, to.top, progress),
                    width = lerpInt(from.width, to.width, progress),
                    height = lerpInt(from.height, to.height, progress)
                )
            )
        }
        return WaterfallLayoutResult(
            frames = frames,
            contentHeight = frames.maxOfOrNull { it.top + it.height } ?: 0
        )
    }

    fun interpolateFrame(
        lower: WaterfallItemFrame,
        upper: WaterfallItemFrame,
        progress: Float
    ): WaterfallItemFrame {
        if (progress <= 0f) return lower
        if (progress >= 1f) return upper
        return WaterfallItemFrame(
            index = lower.index,
            left = lerpInt(lower.left, upper.left, progress),
            top = lerpInt(lower.top, upper.top, progress),
            width = lerpInt(lower.width, upper.width, progress),
            height = lerpInt(lower.height, upper.height, progress)
        )
    }

    fun findVisibleItems(
        frames: List<WaterfallItemFrame>,
        scrollOffset: Int,
        viewportHeight: Int,
        extraBuffer: Int
    ): List<Int> {
        if (frames.isEmpty() || viewportHeight <= 0) return emptyList()

        val topBound = scrollOffset - extraBuffer
        val bottomBound = scrollOffset + viewportHeight + extraBuffer
        return frames.asSequence()
            .filter { frame -> frame.top + frame.height >= topBound && frame.top <= bottomBound }
            .map { it.index }
            .toList()
    }

    fun computeAnchoredScrollOffset(
        frame: WaterfallItemFrame,
        anchorYFraction: Float,
        focusY: Float,
        viewportHeight: Int,
        contentHeight: Int
    ): Int {
        val anchoredYInContent = frame.top + frame.height * anchorYFraction.coerceIn(0f, 1f)
        val target = (anchoredYInContent - focusY).roundToInt()
        val maxScroll = max(contentHeight - viewportHeight, 0)
        return target.coerceIn(0, maxScroll)
    }

    private fun buildDiscreteLayout(
        aspectRatios: FloatArray,
        containerWidth: Int,
        spacing: Int,
        columns: Int
    ): WaterfallLayoutResult {
        val columnCount = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val safeSpacing = spacing.coerceAtLeast(0)
        val usableWidth = max(containerWidth - safeSpacing * (columnCount - 1), columnCount)
        val columnWidth = usableWidth / columnCount
        val columnHeights = IntArray(columnCount)
        val frames = MutableList(aspectRatios.size) { index ->
            val column = index % columnCount
            val ratio = aspectRatios[index].takeIf { it > 0f } ?: DEFAULT_ASPECT_RATIO
            val left = column * (columnWidth + safeSpacing)
            val top = columnHeights[column]
            val height = max((columnWidth / ratio).roundToInt(), 1)
            columnHeights[column] = top + height + safeSpacing
            WaterfallItemFrame(
                index = index,
                left = left,
                top = top,
                width = columnWidth,
                height = height
            )
        }
        val contentHeight = max(columnHeights.maxOrNull()?.minus(safeSpacing) ?: 0, 0)
        return WaterfallLayoutResult(frames, contentHeight)
    }

    private fun lerpInt(start: Int, end: Int, progress: Float): Int {
        return (start + (end - start) * progress).roundToInt()
    }
}
