package erl.webdavtoon

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

class FollowZoomWaterfallLayoutManager(
    private val spacingPx: Int,
    private val aspectRatioProvider: (Int) -> Float
) : RecyclerView.LayoutManager() {

    private data class LayoutAnchor(
        val position: Int,
        val xFraction: Float,
        val yFraction: Float,
        val focusY: Float
    )

    private data class PreviewChildState(
        val view: View,
        val position: Int,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    )

    private var virtualColumns: Float = 2f
    private var verticalScrollOffset: Int = 0
    private var contentHeight: Int = 0
    private var frames: List<WaterfallItemFrame> = emptyList()
    private var interpolationFrames: List<WaterfallItemFrame>? = null
    private var interpolationProgress: Float = 0f
    private var aspectRatios: FloatArray = FloatArray(0)
    private var discreteLayouts: Array<WaterfallLayoutResult?> = arrayOfNulls(5)
    private var sortedFrameIndicesByTop: IntArray = IntArray(0)
    private var sortedFrameIndexDirty = true
    private var maxFrameHeight: Int = 0
    private var forceFullRelayout = true
    private var geometryDirty = true
    private var columnsDirty = true
    private var lastLayoutItemCount = RecyclerView.NO_POSITION
    private var lastLayoutWidth = RecyclerView.NO_POSITION
    private var lastLayoutColumns = Float.NaN
    private var interactiveAnchor: LayoutAnchor? = null
    private var previewChildStates: List<PreviewChildState> = emptyList()
    private var hasActivePreviewTransforms = false
    private var pendingAnchor: LayoutAnchor? = null
    private var pendingScrollPosition: Int = RecyclerView.NO_POSITION
    private var pendingScrollOffset: Int = 0

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
    }

    override fun canScrollVertically(): Boolean = true

    override fun isAutoMeasureEnabled(): Boolean = true

    override fun supportsPredictiveItemAnimations(): Boolean = false

    fun getVirtualColumns(): Float = virtualColumns

    fun beginInteractiveZoom(
        focusX: Float? = null,
        focusY: Float? = null,
        touchPoints: List<Pair<Float, Float>> = emptyList()
    ) {
        interactiveAnchor = captureAnchor(focusX, focusY, touchPoints) ?: captureLeadingAnchor()
        previewChildStates = capturePreviewChildStates()
    }

    fun endInteractiveZoom() {
        interactiveAnchor = null
        if (!hasActivePreviewTransforms) {
            previewChildStates = emptyList()
        }
        if (frames.isNotEmpty() && sortedFrameIndicesByTop.size != frames.size) {
            sortedFrameIndexDirty = true
            requestLayout()
        }
    }

    fun setVirtualColumns(columns: Float, focusX: Float? = null, focusY: Float? = null) {
        val clamped = columns.coerceIn(1f, 4f)
        if (abs(virtualColumns - clamped) < 0.0001f && pendingAnchor == null) return
        pendingAnchor = interactiveAnchor ?: captureAnchor(focusX, focusY) ?: captureLeadingAnchor()
        virtualColumns = clamped
        columnsDirty = true
        requestLayout()
    }

    fun previewVirtualColumns(columns: Float, focusX: Float? = null, focusY: Float? = null) {
        if (previewChildStates.isEmpty()) {
            setVirtualColumns(columns, focusX, focusY)
            return
        }

        val width = contentWidth
        if (width <= 0 || aspectRatios.isEmpty()) {
            setVirtualColumns(columns, focusX, focusY)
            return
        }

        val clamped = columns.coerceIn(1f, 4f)
        virtualColumns = clamped
        val lowerColumns = floor(clamped).toInt().coerceIn(1, 4)
        val upperColumns = ceil(clamped).toInt().coerceIn(1, 4)
        val lower = getDiscreteLayout(width, lowerColumns)
        val upper = if (lowerColumns == upperColumns) lower else getDiscreteLayout(width, upperColumns)
        val progress = clamped - lowerColumns
        val previewContentHeight = if (lowerColumns == upperColumns) {
            lower.contentHeight
        } else {
            computeInterpolatedContentHeight(lower.frames, upper.frames, progress)
        }
        val anchor = interactiveAnchor ?: captureAnchor(focusX, focusY) ?: captureLeadingAnchor()
        val previewScrollOffset = anchor?.let { captured ->
            val frame = interpolateFrameAt(captured.position, lower.frames, upper.frames, progress)
                ?: return@let verticalScrollOffset
            FollowZoomWaterfallLayoutEngine.computeAnchoredScrollOffset(
                frame = frame,
                anchorYFraction = captured.yFraction,
                focusY = captured.focusY,
                viewportHeight = viewportHeight,
                contentHeight = previewContentHeight
            )
        } ?: verticalScrollOffset

        for (state in previewChildStates) {
            val frame = interpolateFrameAt(state.position, lower.frames, upper.frames, progress) ?: continue
            val targetLeft = paddingLeft + frame.left
            val targetTop = paddingTop + frame.top - previewScrollOffset
            state.view.pivotX = 0f
            state.view.pivotY = 0f
            state.view.translationX = (targetLeft - state.left).toFloat()
            state.view.translationY = (targetTop - state.top).toFloat()
            state.view.scaleX = frame.width.toFloat() / state.width.coerceAtLeast(1).toFloat()
            state.view.scaleY = frame.height.toFloat() / state.height.coerceAtLeast(1).toFloat()
            if (!hasActivePreviewTransforms) {
                state.view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
        }
        hasActivePreviewTransforms = true
    }

    fun commitInteractiveZoom(columns: Float, focusX: Float? = null, focusY: Float? = null) {
        val clamped = columns.coerceIn(1f, 4f)
        pendingAnchor = interactiveAnchor ?: captureAnchor(focusX, focusY) ?: captureLeadingAnchor()
        virtualColumns = clamped
        columnsDirty = true
        requestLayout()
    }

    fun notifyAspectRatiosChanged() {
        pendingAnchor = captureLeadingAnchor()
        markLayoutDirty()
        requestLayout()
    }

    fun getFirstVisibleAdapterPosition(): Int {
        if (childCount == 0) return RecyclerView.NO_POSITION
        var first = RecyclerView.NO_POSITION
        for (index in 0 until childCount) {
            val position = getPosition(getChildAt(index) ?: continue)
            if (position == RecyclerView.NO_POSITION) continue
            first = if (first == RecyclerView.NO_POSITION) position else minOf(first, position)
        }
        return first
    }

    fun getLastVisibleAdapterPosition(): Int {
        if (childCount == 0) return RecyclerView.NO_POSITION
        var last = RecyclerView.NO_POSITION
        for (index in 0 until childCount) {
            val position = getPosition(getChildAt(index) ?: continue)
            if (position == RecyclerView.NO_POSITION) continue
            last = max(last, position)
        }
        return last
    }

    fun scrollToPositionWithOffset(position: Int, offset: Int) {
        pendingScrollPosition = position
        pendingScrollOffset = offset
        requestLayout()
    }

    override fun scrollToPosition(position: Int) {
        scrollToPositionWithOffset(position, 0)
    }

    override fun onItemsChanged(recyclerView: RecyclerView) {
        markLayoutDirty()
        requestLayout()
    }

    override fun onItemsAdded(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        markLayoutDirty()
        requestLayout()
    }

    override fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        markLayoutDirty()
        requestLayout()
    }

    override fun onItemsUpdated(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        markLayoutDirty()
        requestLayout()
    }

    override fun onItemsMoved(
        recyclerView: RecyclerView,
        from: Int,
        to: Int,
        itemCount: Int
    ) {
        markLayoutDirty()
        requestLayout()
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (state.itemCount <= 0) {
            detachAndScrapAttachedViews(recycler)
            verticalScrollOffset = 0
            contentHeight = 0
            frames = emptyList()
            interpolationFrames = null
            interpolationProgress = 0f
            aspectRatios = FloatArray(0)
            discreteLayouts = arrayOfNulls(5)
            sortedFrameIndicesByTop = IntArray(0)
            sortedFrameIndexDirty = false
            maxFrameHeight = 0
            interactiveAnchor = null
            markLayoutDirty()
            return
        }

        val framesChanged = rebuildLayoutIfNeeded(state.itemCount)
        val scrollOffsetBeforePendingState = verticalScrollOffset
        consumePendingPositionState()
        consumePendingAnchor()
        clampScrollOffset()
        val scrollOffsetChanged = verticalScrollOffset != scrollOffsetBeforePendingState

        val shouldFullRelayout = forceFullRelayout || state.didStructureChange() || childCount == 0
        if (shouldFullRelayout) {
            detachAndScrapAttachedViews(recycler)
        }
        val isInteractiveLayout = interactiveAnchor != null
        fill(
            recycler = recycler,
            recycleOutsideWindow = !shouldFullRelayout && !isInteractiveLayout,
            relayoutExistingChildren = shouldFullRelayout || framesChanged || scrollOffsetChanged
        )
        if (hasActivePreviewTransforms) {
            clearPreviewTransforms()
            previewChildStates = emptyList()
        }
        forceFullRelayout = false
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        if (dy == 0 || state.itemCount == 0) return 0
        val maxScroll = max(contentHeight - viewportHeight, 0)
        val newOffset = (verticalScrollOffset + dy).coerceIn(0, maxScroll)
        val consumed = newOffset - verticalScrollOffset
        if (consumed == 0) return 0

        verticalScrollOffset = newOffset
        offsetChildrenVertical(-consumed)
        fill(recycler, recycleOutsideWindow = true, relayoutExistingChildren = false)
        return consumed
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int = verticalScrollOffset

    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int = viewportHeight

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = contentHeight

    private fun markLayoutDirty() {
        geometryDirty = true
        columnsDirty = true
        sortedFrameIndexDirty = true
        forceFullRelayout = true
    }

    private fun rebuildLayoutIfNeeded(itemCount: Int): Boolean {
        val width = contentWidth
        if (width <= 0 || itemCount <= 0) {
            frames = emptyList()
            interpolationFrames = null
            interpolationProgress = 0f
            contentHeight = 0
            discreteLayouts = arrayOfNulls(5)
            sortedFrameIndicesByTop = IntArray(0)
            sortedFrameIndexDirty = false
            geometryDirty = false
            columnsDirty = false
            lastLayoutItemCount = itemCount
            lastLayoutWidth = width
            lastLayoutColumns = virtualColumns
            return true
        }
        if (
            !geometryDirty &&
            !columnsDirty &&
            !sortedFrameIndexDirty &&
            itemCount == lastLayoutItemCount &&
            width == lastLayoutWidth &&
            abs(virtualColumns - lastLayoutColumns) < 0.0001f
        ) {
            return false
        }

        val framesWereDirty = geometryDirty || columnsDirty ||
            itemCount != lastLayoutItemCount ||
            width != lastLayoutWidth ||
            abs(virtualColumns - lastLayoutColumns) >= 0.0001f

        if (geometryDirty || itemCount != lastLayoutItemCount || width != lastLayoutWidth) {
            if (aspectRatios.size != itemCount) {
                aspectRatios = FloatArray(itemCount)
            }
            for (index in 0 until itemCount) {
                aspectRatios[index] = aspectRatioProvider(index)
            }
            discreteLayouts = arrayOfNulls(5)
        }

        applyLayoutFromCache(width)
        updateVisibleSearchIndex()
        geometryDirty = false
        columnsDirty = false
        sortedFrameIndexDirty = false
        lastLayoutItemCount = itemCount
        lastLayoutWidth = width
        lastLayoutColumns = virtualColumns
        return framesWereDirty
    }

    private fun applyLayoutFromCache(width: Int) {
        val clampedColumns = virtualColumns.coerceIn(1f, 4f)
        val lowerColumns = floor(clampedColumns).toInt().coerceIn(1, 4)
        val upperColumns = ceil(clampedColumns).toInt().coerceIn(1, 4)
        val lower = getDiscreteLayout(width, lowerColumns)
        if (lowerColumns == upperColumns) {
            frames = lower.frames
            interpolationFrames = null
            interpolationProgress = 0f
            contentHeight = lower.contentHeight
            maxFrameHeight = frames.maxOfOrNull { it.height } ?: 0
            return
        }

        val upper = getDiscreteLayout(width, upperColumns)
        frames = lower.frames
        interpolationFrames = upper.frames
        interpolationProgress = clampedColumns - lowerColumns
        contentHeight = computeInterpolatedContentHeight(frames, upper.frames, interpolationProgress)
        maxFrameHeight = computeInterpolatedMaxFrameHeight(frames, upper.frames, interpolationProgress)
    }

    private fun getDiscreteLayout(width: Int, columns: Int): WaterfallLayoutResult {
        discreteLayouts[columns]?.let { return it }
        return FollowZoomWaterfallLayoutEngine.computeDiscreteLayout(
            aspectRatios = aspectRatios,
            containerWidth = width,
            spacing = spacingPx,
            columns = columns
        ).also { layout ->
            discreteLayouts[columns] = layout
        }
    }

    private fun computeInterpolatedContentHeight(
        lowerFrames: List<WaterfallItemFrame>,
        upperFrames: List<WaterfallItemFrame>,
        progress: Float
    ): Int {
        if (lowerFrames.size != upperFrames.size) {
            return if (progress < 0.5f) {
                lowerFrames.maxOfOrNull { it.top + it.height } ?: 0
            } else {
                upperFrames.maxOfOrNull { it.top + it.height } ?: 0
            }
        }
        var maxBottom = 0
        for (index in lowerFrames.indices) {
            val from = lowerFrames[index]
            val to = upperFrames[index]
            val top = lerpInt(from.top, to.top, progress)
            val height = lerpInt(from.height, to.height, progress)
            maxBottom = max(maxBottom, top + height)
        }
        return maxBottom
    }

    private fun computeInterpolatedMaxFrameHeight(
        lowerFrames: List<WaterfallItemFrame>,
        upperFrames: List<WaterfallItemFrame>,
        progress: Float
    ): Int {
        if (lowerFrames.size != upperFrames.size) {
            return if (progress < 0.5f) {
                lowerFrames.maxOfOrNull { it.height } ?: 0
            } else {
                upperFrames.maxOfOrNull { it.height } ?: 0
            }
        }
        var maxHeight = 0
        for (index in lowerFrames.indices) {
            val from = lowerFrames[index]
            val to = upperFrames[index]
            maxHeight = max(maxHeight, lerpInt(from.height, to.height, progress))
        }
        return maxHeight
    }

    private fun frameAt(position: Int): WaterfallItemFrame? {
        val lower = frames.getOrNull(position) ?: return null
        val upper = interpolationFrames?.getOrNull(position) ?: return lower
        return FollowZoomWaterfallLayoutEngine.interpolateFrame(
            lower = lower,
            upper = upper,
            progress = interpolationProgress
        )
    }

    private fun interpolateFrameAt(
        position: Int,
        lowerFrames: List<WaterfallItemFrame>,
        upperFrames: List<WaterfallItemFrame>,
        progress: Float
    ): WaterfallItemFrame? {
        val lower = lowerFrames.getOrNull(position) ?: return null
        val upper = upperFrames.getOrNull(position) ?: lower
        return FollowZoomWaterfallLayoutEngine.interpolateFrame(lower, upper, progress)
    }

    private fun frameIntersectsWindow(position: Int, topBound: Int, bottomBound: Int): Boolean {
        val lower = frames.getOrNull(position) ?: return false
        val upper = interpolationFrames?.getOrNull(position)
        if (upper == null) {
            return lower.top + lower.height >= topBound && lower.top <= bottomBound
        }
        val top = lerpInt(lower.top, upper.top, interpolationProgress)
        val height = lerpInt(lower.height, upper.height, interpolationProgress)
        return top + height >= topBound && top <= bottomBound
    }

    private fun lerpInt(start: Int, end: Int, progress: Float): Int {
        return (start + (end - start) * progress).roundToInt()
    }

    private fun fill(
        recycler: RecyclerView.Recycler,
        recycleOutsideWindow: Boolean,
        relayoutExistingChildren: Boolean
    ) {
        if (frames.isEmpty()) return

        val visiblePositions = findVisiblePositions(
            scrollOffset = verticalScrollOffset,
            viewportHeight = viewportHeight,
            extraBuffer = viewportHeight / 3
        )

        if (recycleOutsideWindow) {
            recycleViewsOutsideWindow(recycler, visiblePositions.toHashSet())
        }

        visiblePositions.forEach { position ->
            val frame = frameAt(position) ?: return@forEach
            val existingView = findViewByPosition(position)
            val view = if (existingView != null) {
                if (!relayoutExistingChildren) {
                    return@forEach
                }
                existingView
            } else {
                recycler.getViewForPosition(position).also { child ->
                    addView(child)
                }
            }
            measureChildExactly(view, frame.width, frame.height)
            layoutDecorated(
                view,
                paddingLeft + frame.left,
                paddingTop + frame.top - verticalScrollOffset,
                paddingLeft + frame.left + frame.width,
                paddingTop + frame.top - verticalScrollOffset + frame.height
            )
        }
    }

    private fun findVisiblePositions(
        scrollOffset: Int,
        viewportHeight: Int,
        extraBuffer: Int
    ): List<Int> {
        if (frames.isEmpty() || viewportHeight <= 0) return emptyList()

        if (sortedFrameIndicesByTop.size != frames.size) {
            return findVisiblePositionsByLinearScan(scrollOffset, viewportHeight, extraBuffer)
        }

        val topBound = scrollOffset - extraBuffer
        val bottomBound = scrollOffset + viewportHeight + extraBuffer
        val startTop = topBound - maxFrameHeight
        var low = 0
        var high = sortedFrameIndicesByTop.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (frames[sortedFrameIndicesByTop[mid]].top < startTop) {
                low = mid + 1
            } else {
                high = mid
            }
        }

        val result = ArrayList<Int>()
        var index = low
        while (index < sortedFrameIndicesByTop.size) {
            val position = sortedFrameIndicesByTop[index]
            val frame = frames[position]
            if (frame.top > bottomBound) break
            if (frame.top + frame.height >= topBound) {
                result.add(position)
            }
            index++
        }
        return result
    }

    private fun updateVisibleSearchIndex() {
        if (!shouldBuildSortedFrameIndex()) {
            sortedFrameIndicesByTop = IntArray(0)
            return
        }
        sortedFrameIndicesByTop = frames.indices
            .sortedWith(compareBy<Int> { frames[it].top }.thenBy { frames[it].left })
            .toIntArray()
    }

    private fun shouldBuildSortedFrameIndex(): Boolean {
        val roundedColumns = virtualColumns.roundToInt().coerceIn(1, 4)
        return interactiveAnchor == null && abs(virtualColumns - roundedColumns.toFloat()) < 0.001f
    }

    private fun findVisiblePositionsByLinearScan(
        scrollOffset: Int,
        viewportHeight: Int,
        extraBuffer: Int
    ): List<Int> {
        val topBound = scrollOffset - extraBuffer
        val bottomBound = scrollOffset + viewportHeight + extraBuffer
        val result = ArrayList<Int>()
        for (position in frames.indices) {
            if (frameIntersectsWindow(position, topBound, bottomBound)) {
                result.add(position)
            }
        }
        return result
    }

    private fun recycleViewsOutsideWindow(
        recycler: RecyclerView.Recycler,
        visiblePositions: Set<Int>
    ) {
        for (index in childCount - 1 downTo 0) {
            val child = getChildAt(index) ?: continue
            val position = getPosition(child)
            if (position !in visiblePositions) {
                removeAndRecycleView(child, recycler)
            }
        }
    }

    private fun measureChildExactly(view: View, width: Int, height: Int) {
        val layoutParams = view.layoutParams as RecyclerView.LayoutParams
        if (layoutParams.width != width || layoutParams.height != height) {
            layoutParams.width = width
            layoutParams.height = height
        }
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
    }

    private fun capturePreviewChildStates(): List<PreviewChildState> {
        if (childCount == 0) return emptyList()
        val states = ArrayList<PreviewChildState>(childCount)
        for (index in 0 until childCount) {
            val child = getChildAt(index) ?: continue
            val position = getPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            states.add(
                PreviewChildState(
                    view = child,
                    position = position,
                    left = getDecoratedLeft(child),
                    top = getDecoratedTop(child),
                    width = getDecoratedMeasuredWidth(child).coerceAtLeast(1),
                    height = getDecoratedMeasuredHeight(child).coerceAtLeast(1)
                )
            )
        }
        return states
    }

    private fun clearPreviewTransforms() {
        for (state in previewChildStates) {
            state.view.translationX = 0f
            state.view.translationY = 0f
            state.view.scaleX = 1f
            state.view.scaleY = 1f
            state.view.pivotX = 0f
            state.view.pivotY = 0f
            state.view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        hasActivePreviewTransforms = false
    }

    private fun consumePendingPositionState() {
        if (pendingScrollPosition == RecyclerView.NO_POSITION) return
        val frame = frameAt(pendingScrollPosition) ?: return
        val maxScroll = max(contentHeight - viewportHeight, 0)
        verticalScrollOffset = (frame.top - pendingScrollOffset).coerceIn(0, maxScroll)
        pendingScrollPosition = RecyclerView.NO_POSITION
        pendingScrollOffset = 0
    }

    private fun consumePendingAnchor() {
        val anchor = pendingAnchor ?: return
        val frame = frameAt(anchor.position) ?: run {
            pendingAnchor = null
            return
        }
        verticalScrollOffset = FollowZoomWaterfallLayoutEngine.computeAnchoredScrollOffset(
            frame = frame,
            anchorYFraction = anchor.yFraction,
            focusY = anchor.focusY,
            viewportHeight = viewportHeight,
            contentHeight = contentHeight
        )
        pendingAnchor = null
    }

    private fun captureAnchor(
        focusX: Float?,
        focusY: Float?,
        touchPoints: List<Pair<Float, Float>> = emptyList()
    ): LayoutAnchor? {
        if (focusX == null || focusY == null || frames.isEmpty() || childCount == 0) return null

        val targetChild = findAnchorChild(focusX, focusY, touchPoints) ?: return null
        val position = getPosition(targetChild)
        val target = frameAt(position) ?: return null
        val childLeft = getDecoratedLeft(targetChild).toFloat()
        val childTop = getDecoratedTop(targetChild).toFloat()
        val childWidth = max(getDecoratedMeasuredWidth(targetChild), 1)
        val childHeight = max(getDecoratedMeasuredHeight(targetChild), 1)

        return LayoutAnchor(
            position = target.index,
            xFraction = ((focusX - childLeft) / childWidth).coerceIn(0f, 1f),
            yFraction = ((focusY - childTop) / childHeight).coerceIn(0f, 1f),
            focusY = (focusY - paddingTop).coerceIn(0f, viewportHeight.toFloat())
        )
    }

    private fun findAnchorChild(
        focusX: Float,
        focusY: Float,
        touchPoints: List<Pair<Float, Float>>
    ): View? {
        findChildContainingAnyTouchPoint(touchPoints)?.let { return it }
        return findClosestAttachedChild(focusX, focusY)
    }

    private fun findChildContainingAnyTouchPoint(touchPoints: List<Pair<Float, Float>>): View? {
        if (touchPoints.isEmpty()) return null
        for (point in touchPoints) {
            val child = findAttachedChildContaining(point.first, point.second)
            if (child != null) {
                return child
            }
        }
        return null
    }

    private fun findAttachedChildContaining(x: Float, y: Float): View? {
        for (index in 0 until childCount) {
            val child = getChildAt(index) ?: continue
            val left = getDecoratedLeft(child).toFloat()
            val top = getDecoratedTop(child).toFloat()
            val right = getDecoratedRight(child).toFloat()
            val bottom = getDecoratedBottom(child).toFloat()
            if (x in left..right && y in top..bottom) {
                return child
            }
        }
        return null
    }

    private fun captureLeadingAnchor(): LayoutAnchor? {
        if (frames.isEmpty()) return null
        val frame = findLeadingFrame() ?: return null
        val topOffsetWithinItem = (verticalScrollOffset - frame.top).coerceAtLeast(0)
        val yFraction = if (frame.height <= 0) 0f else topOffsetWithinItem.toFloat() / frame.height.toFloat()
        val focusY = (frame.top - verticalScrollOffset).coerceAtLeast(0).toFloat()
        return LayoutAnchor(
            position = frame.index,
            xFraction = 0.5f,
            yFraction = yFraction.coerceIn(0f, 1f),
            focusY = focusY.coerceIn(0f, viewportHeight.toFloat())
        )
    }

    private fun findLeadingFrame(): WaterfallItemFrame? {
        for (index in frames.indices) {
            val frame = frameAt(index) ?: continue
            if (frame.top + frame.height > verticalScrollOffset) {
                return frame
            }
        }
        for (index in frames.indices.reversed()) {
            return frameAt(index)
        }
        return null
    }

    private fun distanceToFrame(frame: WaterfallItemFrame, x: Float, y: Float): Float {
        val dx = when {
            x < frame.left -> frame.left - x
            x > frame.left + frame.width -> x - (frame.left + frame.width)
            else -> 0f
        }
        val dy = when {
            y < frame.top -> frame.top - y
            y > frame.top + frame.height -> y - (frame.top + frame.height)
            else -> 0f
        }
        return dx * dx + dy * dy
    }

    private fun findClosestAttachedChild(focusX: Float, focusY: Float): View? {
        var targetChild: View? = null
        var bestDistance = Float.MAX_VALUE
        for (index in 0 until childCount) {
            val child = getChildAt(index) ?: continue
            val distance = distanceToChild(child, focusX, focusY)
            if (distance < bestDistance) {
                bestDistance = distance
                targetChild = child
                if (distance == 0f) break
            }
        }
        return targetChild
    }

    private fun distanceToChild(child: View, x: Float, y: Float): Float {
        val left = getDecoratedLeft(child).toFloat()
        val top = getDecoratedTop(child).toFloat()
        val right = getDecoratedRight(child).toFloat()
        val bottom = getDecoratedBottom(child).toFloat()
        val dx = when {
            x < left -> left - x
            x > right -> x - right
            else -> 0f
        }
        val dy = when {
            y < top -> top - y
            y > bottom -> y - bottom
            else -> 0f
        }
        return dx * dx + dy * dy
    }

    private fun clampScrollOffset() {
        val maxScroll = max(contentHeight - viewportHeight, 0)
        verticalScrollOffset = verticalScrollOffset.coerceIn(0, maxScroll)
    }

    private val contentWidth: Int
        get() = max(width - paddingLeft - paddingRight, 0)

    private val viewportHeight: Int
        get() = max(height - paddingTop - paddingBottom, 0)
}
