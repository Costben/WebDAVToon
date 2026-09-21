package erl.webdavtoon.ui.screen.waterfall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.webdavtoon.FollowZoomWaterfallLayoutEngine
import erl.webdavtoon.WaterfallItemFrame
import erl.webdavtoon.WaterfallLayoutResult
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Uniform margin between a card's content and its edge, matching the folder card's inner padding so
 * a single image sits in the card exactly like a folder's preview mosaic does.
 * It is passed to [FollowZoomWaterfallLayout] as `itemHorizontalPadding`, so the engine sizes the
 * content height from `laneWidth - 2 * margin` while the frame keeps the full lane width. The image
 * then fills a container of exactly its own ratio and the rounded clip trims all four corners.
 */
val MediaCardImageMargin = 8.dp

/** Fixed height reserved for the media card's single-line filename label. */
val MediaCardLabelHeight = 18.dp

/** The caption block below the image: the 6dp gap above the label plus the label height. */
val MediaCardLabelBlock = 6.dp + MediaCardLabelHeight

/**
 * Extra cell height for a media card: the image's top and bottom margins plus the caption block.
 * The engine computes the image height as `(laneWidth - 2 * [MediaCardImageMargin]) / ratio`, so
 * image + margins + caption fills the cell exactly and no letterbox band is left inside the
 * rounded image container.
 */
val MediaCardExtraHeight = MediaCardImageMargin * 2 + MediaCardLabelBlock

/**
 * The folder card's label block: the name row (6dp top padding + a 20dp line) plus the count row
 * (2dp + one footnote line + 2dp). Measured on device at the default font scale, so the square
 * mosaic plus this block fills the cell exactly.
 */
val FolderCardLabelBlock = 49.dp

/**
 * Extra cell height for a folder card, chosen so its 2x2 preview mosaic comes out exactly square.
 * The card pads its content by [MediaCardImageMargin] on every side, and the engine reserves the
 * same margin horizontally, so the frame height is `(laneWidth - 2 * margin) + extra`; with
 * `extra = margin * 2 + labelBlock` the square mosaic and the label block fill the cell exactly.
 */
val FolderCardExtraHeight = MediaCardImageMargin * 2 + FolderCardLabelBlock

/** Extra cell height for a folder card that hides its labels: only its two image margins. */
val FolderCardExtraHeightBare = MediaCardImageMargin * 2

/**
 * State for [FollowZoomWaterfallLayout].
 *
 * Holds the continuous (fractional) column count and the scroll offset, and owns
 * the pinch anchoring math. The layout writes its measured frames back into the
 * plain fields so the gesture handler can anchor against the currently rendered
 * layout without triggering recomposition.
 */
@Stable
class FollowZoomGridState(
    initialColumns: Int,
    private val minColumns: Int,
    private val maxColumns: Int,
) {
    /** Fractional column count that drives the live layout. */
    var virtualColumns: Float by mutableFloatStateOf(initialColumns.coerceIn(minColumns, maxColumns).toFloat())
        private set

    /** Vertical scroll offset in content pixels. */
    var scrollOffset: Int by mutableIntStateOf(0)
        private set

    var isZooming: Boolean by mutableStateOf(false)
        private set

    internal var committedColumns: Int = initialColumns.coerceIn(minColumns, maxColumns)
    internal var onColumnsChanged: (Int) -> Unit = {}

    // Layout snapshot (plain fields written during measure, read by the gesture).
    internal var layoutProvider: ((Float) -> WaterfallLayoutResult)? = null
    internal var viewportHeight: Int = 0
    internal var topPadding: Int = 0
    internal var bottomPadding: Int = 0
    internal var itemsContentHeight: Int = 0
    internal var itemCount: Int = 0
    internal var lastFrames: List<WaterfallItemFrame> = emptyList()
    internal var lastVisibleIndex: Int = 0

    private var cachedLayoutKey: LayoutCacheKey? = null
    private var cachedLayout: CachedWaterfallLayout? = null

    private var anchorIndex = -1
    private var anchorYFraction = 0f
    private var anchorFocusY = 0f
    private var startColumns = 1f
    private var gestureScale = 1f
    private var settleJob: Job? = null
    private var scope: CoroutineScope? = null
    private var isSettling = false

    internal fun attachScope(scope: CoroutineScope) {
        this.scope = scope
    }

    /** Adopt the persisted integer column count while the user is not pinching. */
    internal fun syncColumns(columns: Int) {
        val clamped = columns.coerceIn(minColumns, maxColumns)
        committedColumns = clamped
        if (!isZooming && !isSettling) {
            virtualColumns = clamped.toFloat()
        }
    }

    internal fun clampScroll() {
        val max = scrollRange()
        if (scrollOffset > max) scrollOffset = max
        if (scrollOffset < 0) scrollOffset = 0
    }

    /**
     * Returns the layout for [virtualColumns], reusing the previous result when the inputs are
     * unchanged. Scrolling keeps every input constant, so this turns the per-frame O(n) layout
     * (and its object churn) into a single cached lookup.
     */
    internal fun obtainLayout(
        ratios: FloatArray,
        containerWidth: Int,
        spacing: Int,
        extraHeight: Int,
        horizontalPadding: Int,
        extraHeights: IntArray?,
        virtualColumns: Float,
    ): CachedWaterfallLayout {
        val key = LayoutCacheKey(
            ratios = ratios,
            containerWidth = containerWidth,
            spacing = spacing,
            extraHeight = extraHeight,
            horizontalPadding = horizontalPadding,
            extraHeights = extraHeights,
            virtualColumns = virtualColumns,
        )
        cachedLayout?.let { if (cachedLayoutKey == key) return it }

        val previous = cachedLayout
        val result = FollowZoomWaterfallLayoutEngine.computeLayout(
            aspectRatios = ratios,
            containerWidth = containerWidth,
            spacing = spacing,
            virtualColumns = virtualColumns,
            itemExtraHeight = extraHeight,
            itemHorizontalPadding = horizontalPadding,
            itemExtraHeights = extraHeights,
        )
        val frames = result.frames
        val sortedByTop = frames.indices.sortedBy { frames[it].top }.toIntArray()
        val maxFrameHeight = frames.maxOfOrNull { it.height } ?: 0
        val built = CachedWaterfallLayout(result, sortedByTop, maxFrameHeight)
        if (previous != null && !isZooming && !isSettling) {
            reanchorScroll(previous, built)
        }
        cachedLayoutKey = key
        cachedLayout = built
        return built
    }

    /**
     * Keeps the item under the viewport top in place when the layout changes because a previously
     * unseen item resolved its real aspect ratio. Items are laid out from the top, so an item above
     * the viewport that grows taller pushes the whole visible window down by the same amount; without
     * this correction the content under the finger jumps backwards - the exact opposite of a fling
     * that scrolls back towards the start - which reads as the fling stalling, and can also clamp the
     * offset and end the fling early. The pinch reflow anchors itself in [applyColumns], so skip it.
     */
    private fun reanchorScroll(previous: CachedWaterfallLayout, next: CachedWaterfallLayout) {
        val oldFrames = previous.result.frames
        val newFrames = next.result.frames
        if (oldFrames.isEmpty() || oldFrames.size != newFrames.size) return
        val offset = scrollOffset
        val anchor = previous.topVisibleIndex(offset)
        if (anchor < 0) return
        val oldTop = oldFrames[anchor].top
        val newTop = newFrames[anchor].top
        if (oldTop == newTop) return
        val max = (next.result.contentHeight + topPadding + bottomPadding - viewportHeight)
            .coerceAtLeast(0)
        scrollOffset = (offset + (newTop - oldTop)).coerceIn(0, max)
    }

    fun scrollBy(delta: Float): Float {
        if (isZooming || isSettling) return 0f
        val max = scrollRange()
        val current = scrollOffset
        // `Modifier.scrollable` reports drag deltas opposite to the content scroll direction
        // (dragging up is negative), so subtract to make the content follow the finger.
        val targetFloat = current - delta
        val target = targetFloat.roundToInt().coerceIn(0, max)
        if (target == current) return 0f
        scrollOffset = target
        // Report the exact (unrounded) consumed delta when the whole delta fit inside the range.
        // The default fling behavior cancels the animation as soon as
        // `abs(delta - consumed) > 0.5f`, so returning the integer-rounded offset change would
        // randomly kill flings on any frame whose rounding error lands above 0.5px.
        return if (targetFloat >= 0f && targetFloat <= max.toFloat()) {
            delta
        } else {
            (current - target).toFloat()
        }
    }

    private fun scrollRange(): Int =
        (itemsContentHeight + topPadding + bottomPadding - viewportHeight).coerceAtLeast(0)

    internal fun onPinchStart(centroid: Offset) {
        settleJob?.cancel()
        settleJob = null
        isSettling = false
        startColumns = virtualColumns
        gestureScale = 1f
        isZooming = true
        captureAnchor(centroid)
    }

    internal fun onPinch(zoomChange: Float) {
        if (!isZooming) return
        gestureScale *= zoomChange
        if (gestureScale <= 0.0001f) return
        val target = (startColumns / gestureScale)
            .coerceIn(minColumns.toFloat(), maxColumns.toFloat())
        applyColumns(target)
    }

    internal fun onPinchEnd() {
        if (!isZooming) return
        val target = virtualColumns.roundToInt().coerceIn(minColumns, maxColumns)
        val changed = target != committedColumns
        val activeScope = scope
        if (activeScope == null) {
            finishSettle(target, changed)
            return
        }
        settleJob?.cancel()
        isSettling = true
        settleJob = activeScope.launch {
            try {
                val start = virtualColumns
                if (abs(start - target) > 0.001f) {
                    Animatable(start).animateTo(
                        targetValue = target.toFloat(),
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    ) {
                        applyColumns(this.value)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (isSettling) {
                    finishSettle(target, changed)
                }
            }
        }
    }

    private fun finishSettle(target: Int, changed: Boolean) {
        virtualColumns = target.toFloat()
        isZooming = false
        isSettling = false
        anchorIndex = -1
        if (changed) {
            committedColumns = target
            onColumnsChanged(target)
        }
    }

    private fun captureAnchor(centroid: Offset) {
        val frames = lastFrames
        if (frames.isEmpty() || viewportHeight <= 0) {
            anchorIndex = -1
            return
        }
        val contentY = scrollOffset + centroid.y - topPadding
        var target = -1
        for (index in frames.indices) {
            val frame = frames[index]
            if (contentY >= frame.top && contentY < frame.top + frame.height) {
                target = index
                break
            }
        }
        if (target < 0) {
            var bestDistance = Float.MAX_VALUE
            for (index in frames.indices) {
                val frame = frames[index]
                val distance = abs(contentY - (frame.top + frame.height / 2f))
                if (distance < bestDistance) {
                    bestDistance = distance
                    target = index
                }
            }
        }
        if (target < 0) {
            anchorIndex = -1
            return
        }
        val frame = frames[target]
        anchorIndex = target
        anchorYFraction = if (frame.height <= 0) 0.5f else ((contentY - frame.top) / frame.height).coerceIn(0f, 1f)
        anchorFocusY = (centroid.y - topPadding).coerceIn(0f, viewportHeight.toFloat())
    }

    private fun applyColumns(target: Float) {
        val provider = layoutProvider
        if (provider == null || anchorIndex < 0) {
            virtualColumns = target
            return
        }
        val result = provider(target)
        val frame = result.frames.getOrNull(anchorIndex)
        if (frame != null) {
            val anchoredContentY = frame.top + frame.height * anchorYFraction
            val max = (result.contentHeight + topPadding + bottomPadding - viewportHeight).coerceAtLeast(0)
            scrollOffset = (anchoredContentY - anchorFocusY).roundToInt().coerceIn(0, max)
        }
        virtualColumns = target
    }
}

@Composable
fun rememberFollowZoomGridState(
    columns: Int,
    minColumns: Int,
    maxColumns: Int,
    onColumnsChanged: (Int) -> Unit,
): FollowZoomGridState {
    val scope = rememberCoroutineScope()
    val state = remember(minColumns, maxColumns) {
        FollowZoomGridState(columns, minColumns, maxColumns)
    }
    SideEffect {
        state.attachScope(scope)
        state.onColumnsChanged = onColumnsChanged
        state.syncColumns(columns)
        state.clampScroll()
    }
    return state
}

/**
 * Compose-native follow-zoom waterfall.
 *
 * Unlike `LazyVerticalStaggeredGrid`, this lays items out itself from
 * [FollowZoomWaterfallLayoutEngine] frames, so the fractional [FollowZoomGridState.virtualColumns]
 * continuously reflows the visible window while the user pinches. Only the visible
 * window (plus a buffer) is subcomposed, so long lists stay lazy.
 */
@Composable
fun FollowZoomWaterfallLayout(
    itemCount: Int,
    aspectRatios: List<Float>,
    columns: Int,
    minColumns: Int,
    maxColumns: Int,
    onColumnsChanged: (Int) -> Unit,
    spacing: Dp,
    contentPadding: PaddingValues,
    state: FollowZoomGridState,
    modifier: Modifier = Modifier,
    itemExtraHeight: Dp = 0.dp,
    itemExtraHeights: List<Dp>? = null,
    itemHorizontalPadding: Dp = 0.dp,
    itemContent: @Composable (index: Int, widthPx: Int, heightPx: Int) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val spacingPx = with(density) { spacing.roundToPx() }
    val extraPx = with(density) { itemExtraHeight.roundToPx() }
    val extraHeightsPx = remember(itemExtraHeights, density) {
        itemExtraHeights?.let { list ->
            IntArray(list.size) { with(density) { list[it].roundToPx() } }
        }
    }
    val hPadPx = with(density) { itemHorizontalPadding.roundToPx() }
    val padLeft = with(density) { contentPadding.calculateLeftPadding(layoutDirection).roundToPx() }
    val padRight = with(density) { contentPadding.calculateRightPadding(layoutDirection).roundToPx() }
    val padTop = with(density) { contentPadding.calculateTopPadding().roundToPx() }
    val padBottom = with(density) { contentPadding.calculateBottomPadding().roundToPx() }
    val ratios = remember(aspectRatios) { aspectRatios.toFloatArray() }

    // `SubcomposeLayout` re-sets the content lambda on every measure pass, so passing a
    // freshly-created lambda re-runs composition for every visible item on every scroll
    // frame. Keep one lambda instance per index so the subcomposition is actually skipped.
    val latestItemContent = rememberUpdatedState(itemContent)
    val itemContentCache = remember { ItemContentCache() }

    SubcomposeLayout(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectFollowZoomGestures(
                    onGestureStart = { centroid -> state.onPinchStart(centroid) },
                    onGesture = { _, zoomChange -> state.onPinch(zoomChange) },
                    onGestureEnd = { state.onPinchEnd() },
                )
            }
            .scrollable(
                state = rememberScrollableState { delta -> state.scrollBy(delta) },
                orientation = Orientation.Vertical,
                flingBehavior = ScrollableDefaults.flingBehavior(),
            ),
    ) { constraints ->
        val width = constraints.maxWidth
        val viewportHeight = constraints.maxHeight
        val containerWidth = (width - padLeft - padRight).coerceAtLeast(0)

        state.layoutProvider = { cols ->
            FollowZoomWaterfallLayoutEngine.computeLayout(
                aspectRatios = ratios,
                containerWidth = containerWidth,
                spacing = spacingPx,
                virtualColumns = cols,
                itemExtraHeight = extraPx,
                itemHorizontalPadding = hPadPx,
                itemExtraHeights = extraHeightsPx,
            )
        }
        state.viewportHeight = viewportHeight
        state.topPadding = padTop
        state.bottomPadding = padBottom

        val cached = state.obtainLayout(
            ratios = ratios,
            containerWidth = containerWidth,
            spacing = spacingPx,
            extraHeight = extraPx,
            horizontalPadding = hPadPx,
            extraHeights = extraHeightsPx,
            virtualColumns = state.virtualColumns,
        )
        val result = cached.result
        state.itemsContentHeight = result.contentHeight
        state.lastFrames = result.frames
        state.itemCount = itemCount

        val maxScroll = (result.contentHeight + padTop + padBottom - viewportHeight).coerceAtLeast(0)
        val effectiveScroll = state.scrollOffset.coerceIn(0, maxScroll)

        val visible = cached.visibleIndices(
            scrollOffset = effectiveScroll,
            viewportHeight = viewportHeight,
            extraBuffer = viewportHeight / 2,
        )
        var lastVisible = 0
        for (index in visible) {
            if (index > lastVisible) lastVisible = index
        }
        state.lastVisibleIndex = lastVisible


        val placeables = ArrayList<PlaceableFrame>(visible.size)
        for (index in visible) {
            // Skip indices the current item list no longer provides (the cached layout can be
            // one frame ahead of a shrinking list, e.g. while filtering).
            if (index >= itemCount) continue
            val frame = result.frames.getOrNull(index) ?: continue
            val itemWidth = frame.width.coerceAtLeast(1)
            val itemHeight = frame.height.coerceAtLeast(1)
            val placeable = subcompose(
                index,
                itemContentCache.contentFor(index, itemWidth, itemHeight, latestItemContent),
            )
                .firstOrNull()
                ?.measure(Constraints.fixed(itemWidth, itemHeight))
                ?: continue
            placeables.add(PlaceableFrame(frame, placeable))
        }

        layout(width, viewportHeight) {
            placeables.forEach { (frame, placeable) ->
                placeable.place(
                    x = padLeft + frame.left,
                    y = padTop + frame.top - effectiveScroll,
                )
            }
        }
    }
}

private data class PlaceableFrame(
    val frame: WaterfallItemFrame,
    val placeable: androidx.compose.ui.layout.Placeable,
)

/**
 * Keeps one content lambda instance per item index. `SubcomposeLayoutState.subcompose`
 * re-sets the content whenever the lambda instance differs, which re-runs composition for
 * the item on every measure pass; reusing the instance lets Compose skip unchanged items
 * while scrolling. The lambda reads [latest] so item data updates still propagate, and is
 * rebuilt when the item's measured size changes (pinch reflow).
 */
private class ItemContentCache {
    private companion object {
        /**
         * Only the visible window (plus a little slack) is ever requested, so a small
         * access-ordered cache covers it without growing with the whole list.
         */
        const val MAX_ENTRIES = 256
    }

    private class Entry(
        val heightPx: Int,
        val lambda: @Composable () -> Unit,
    )

    private val lambdas = object : LinkedHashMap<Int, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    fun contentFor(
        index: Int,
        widthPx: Int,
        heightPx: Int,
        latest: State<@Composable (Int, Int, Int) -> Unit>,
    ): @Composable () -> Unit {
        val existing = lambdas[index]
        if (existing != null && existing.heightPx == heightPx) return existing.lambda
        val entry = Entry(heightPx) { latest.value(index, widthPx, heightPx) }
        lambdas[index] = entry
        return entry.lambda
    }
}

/**
 * A layout plus a sort-by-top index so the visible window can be found with a binary search
 * instead of scanning every item on each scroll frame.
 */
internal class CachedWaterfallLayout(
    val result: WaterfallLayoutResult,
    private val sortedByTop: IntArray,
    private val maxFrameHeight: Int,
) {
    fun visibleIndices(scrollOffset: Int, viewportHeight: Int, extraBuffer: Int): IntArray {
        if (result.frames.isEmpty() || viewportHeight <= 0) return IntArray(0)

        val topBound = scrollOffset - extraBuffer
        val bottomBound = scrollOffset + viewportHeight + extraBuffer
        val startTop = topBound - maxFrameHeight

        var low = 0
        var high = sortedByTop.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (result.frames[sortedByTop[mid]].top < startTop) low = mid + 1 else high = mid
        }

        val visible = ArrayList<Int>()
        var i = low
        while (i < sortedByTop.size) {
            val index = sortedByTop[i]
            val frame = result.frames[index]
            if (frame.top > bottomBound) break
            if (frame.top + frame.height >= topBound) visible.add(index)
            i++
        }
        return visible.toIntArray()
    }

    /**
     * Index of the innermost item frame containing [scrollOffset], or -1 when no frame covers it.
     * Used to keep the item under the viewport top at the same screen position when the layout
     * changes because a previously unseen item resolved its real aspect ratio.
     */
    fun topVisibleIndex(scrollOffset: Int): Int {
        val frames = result.frames
        if (frames.isEmpty()) return -1
        val startTop = scrollOffset - maxFrameHeight
        var low = 0
        var high = sortedByTop.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (frames[sortedByTop[mid]].top < startTop) low = mid + 1 else high = mid
        }
        var best = -1
        var bestTop = Int.MIN_VALUE
        var i = low
        while (i < sortedByTop.size) {
            val index = sortedByTop[i]
            val frame = frames[index]
            if (frame.top > scrollOffset) break
            if (frame.top > bestTop) {
                bestTop = frame.top
                best = index
            }
            i++
        }
        return best
    }
}

private class LayoutCacheKey(
    val ratios: FloatArray,
    val containerWidth: Int,
    val spacing: Int,
    val extraHeight: Int,
    val horizontalPadding: Int,
    val extraHeights: IntArray?,
    val virtualColumns: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayoutCacheKey) return false
        return ratios === other.ratios &&
            extraHeights === other.extraHeights &&
            containerWidth == other.containerWidth &&
            spacing == other.spacing &&
            extraHeight == other.extraHeight &&
            horizontalPadding == other.horizontalPadding &&
            virtualColumns == other.virtualColumns
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(ratios)
        result = 31 * result + System.identityHashCode(extraHeights)
        result = 31 * result + containerWidth
        result = 31 * result + spacing
        result = 31 * result + extraHeight
        result = 31 * result + horizontalPadding
        result = 31 * result + virtualColumns.hashCode()
        return result
    }
}
