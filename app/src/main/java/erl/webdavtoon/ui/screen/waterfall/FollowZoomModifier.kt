package erl.webdavtoon.ui.screen.waterfall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FollowZoomState(
    currentColumns: Int = 2,
    minColumns: Int = 1,
    maxColumns: Int = 5,
    onColumnsChanged: (Int) -> Unit = {},
    onVirtualColumnsChanged: ((Float, Boolean) -> Unit)? = null,
    private var coroutineScope: CoroutineScope? = null
) {
    var minColumns: Int = minColumns
        internal set
    var maxColumns: Int = maxColumns
        internal set

    var currentColumns: Int by mutableIntStateOf(currentColumns.coerceIn(minColumns, maxColumns))
        internal set

    var scale: Float by mutableFloatStateOf(1.0f)
        private set

    var transformOrigin: TransformOrigin by mutableStateOf(TransformOrigin.Center)
        private set

    var isZooming: Boolean by mutableStateOf(false)
        private set

    var virtualColumns: Float by mutableFloatStateOf(
        currentColumns.toFloat().coerceIn(minColumns.toFloat(), maxColumns.toFloat())
    )
        private set

    /**
     * The integer column count the grid should re-layout to while the pinch is
     * in progress, so the user can preview the result before releasing.
     */
    var previewColumns: Int by mutableIntStateOf(
        currentColumns.coerceIn(minColumns, maxColumns)
    )
        private set

    var onColumnsChanged: (Int) -> Unit = onColumnsChanged
        internal set

    var onVirtualColumnsChanged: ((Float, Boolean) -> Unit)? = onVirtualColumnsChanged
        internal set

    private var startColumns: Float = currentColumns.toFloat()
    private var gestureScale: Float = 1.0f
    private var targetVirtualColumns: Float = currentColumns.toFloat()
    private val animatable = Animatable(1.0f)
    private var snapJob: Job? = null

    internal fun attachCoroutineScope(scope: CoroutineScope) {
        this.coroutineScope = scope
    }

    internal fun updateColumnsIfIdle(columns: Int) {
        if (!isZooming && snapJob?.isActive != true) {
            val clamped = columns.coerceIn(minColumns, maxColumns)
            currentColumns = clamped
            virtualColumns = clamped.toFloat()
            previewColumns = clamped
        }
    }

    fun onPinchStart(
        startCentroid: Offset = Offset.Zero,
        containerSize: IntSize = IntSize.Zero
    ) {
        snapJob?.cancel()
        snapJob = null
        startColumns = currentColumns.toFloat().coerceIn(minColumns.toFloat(), maxColumns.toFloat())
        gestureScale = 1.0f
        targetVirtualColumns = startColumns
        scale = 1.0f
        virtualColumns = startColumns
        previewColumns = startColumns.roundToInt().coerceIn(minColumns, maxColumns)
        isZooming = true

        if (containerSize.width > 0 && containerSize.height > 0) {
            val pivotX = (startCentroid.x / containerSize.width).coerceIn(0f, 1f)
            val pivotY = (startCentroid.y / containerSize.height).coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(pivotX, pivotY)
        } else {
            transformOrigin = TransformOrigin.Center
        }

        onVirtualColumnsChanged?.invoke(startColumns, true)
    }

    fun onPinch(
        centroid: Offset,
        zoomChange: Float,
        containerSize: IntSize
    ) {
        if (!isZooming) return
        gestureScale *= zoomChange

        val rawVirtual = startColumns / gestureScale
        targetVirtualColumns = rawVirtual.coerceIn(minColumns.toFloat(), maxColumns.toFloat())
        virtualColumns = targetVirtualColumns
        previewColumns = targetVirtualColumns.roundToInt().coerceIn(minColumns, maxColumns)

        // Kept in sync for the snap animation; no longer drives a full-layer scale.
        scale = calculateDampenedScale(gestureScale)

        if (containerSize.width > 0 && containerSize.height > 0) {
            val pivotX = (centroid.x / containerSize.width).coerceIn(0f, 1f)
            val pivotY = (centroid.y / containerSize.height).coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(pivotX, pivotY)
        }

        onVirtualColumnsChanged?.invoke(targetVirtualColumns, true)
    }

    fun onPinchEnd() {
        if (!isZooming) return

        val targetColumns = targetVirtualColumns.roundToInt().coerceIn(minColumns, maxColumns)
        val columnsChanged = targetColumns != currentColumns

        currentColumns = targetColumns
        previewColumns = targetColumns
        if (columnsChanged) {
            onColumnsChanged(targetColumns)
        }

        val scope = coroutineScope
        if (scope != null) {
            snapJob?.cancel()
            snapJob = scope.launch {
                try {
                    animatable.snapTo(scale)
                    animatable.animateTo(
                        targetValue = 1.0f,
                        animationSpec = tween(
                            durationMillis = 200,
                            easing = FastOutSlowInEasing
                        )
                    ) {
                        scale = this.value
                    }
                    scale = 1.0f
                    virtualColumns = targetColumns.toFloat()
                    if (!columnsChanged) {
                        onColumnsChanged(targetColumns)
                    }
                    onVirtualColumnsChanged?.invoke(virtualColumns, false)
                    isZooming = false
                } catch (e: CancellationException) {
                    throw e
                }
            }
        } else {
            scale = 1.0f
            virtualColumns = targetColumns.toFloat()
            if (!columnsChanged) {
                onColumnsChanged(targetColumns)
            }
            onVirtualColumnsChanged?.invoke(virtualColumns, false)
            isZooming = false
        }
    }

    fun reset() {
        snapJob?.cancel()
        snapJob = null
        scale = 1.0f
        isZooming = false
        transformOrigin = TransformOrigin.Center
        virtualColumns = currentColumns.toFloat()
        previewColumns = currentColumns.coerceIn(minColumns, maxColumns)
        onVirtualColumnsChanged?.invoke(virtualColumns, false)
    }

    private fun calculateDampenedScale(gestureScale: Float): Float {
        return if (gestureScale >= 1.0f) {
            1.0f + (gestureScale - 1.0f) * 0.75f
        } else {
            1.0f - (1.0f - gestureScale) * 0.75f
        }.coerceIn(0.5f, 2.5f)
    }
}

@Composable
fun rememberFollowZoomState(
    currentColumns: Int = 2,
    minColumns: Int = 1,
    maxColumns: Int = 5,
    onColumnsChanged: (Int) -> Unit = {},
    onVirtualColumnsChanged: ((Float, Boolean) -> Unit)? = null
): FollowZoomState {
    val coroutineScope = rememberCoroutineScope()
    val updatedOnColumnsChanged by rememberUpdatedState(onColumnsChanged)
    val updatedOnVirtualColumnsChanged by rememberUpdatedState(onVirtualColumnsChanged)

    val state = remember(minColumns, maxColumns) {
        FollowZoomState(
            currentColumns = currentColumns,
            minColumns = minColumns,
            maxColumns = maxColumns,
            onColumnsChanged = { updatedOnColumnsChanged(it) },
            onVirtualColumnsChanged = { virtual, zooming ->
                updatedOnVirtualColumnsChanged?.invoke(virtual, zooming)
            },
            coroutineScope = coroutineScope
        )
    }

    SideEffect {
        state.attachCoroutineScope(coroutineScope)
        state.minColumns = minColumns
        state.maxColumns = maxColumns
        state.onColumnsChanged = updatedOnColumnsChanged
        state.onVirtualColumnsChanged = updatedOnVirtualColumnsChanged
        state.updateColumnsIfIdle(currentColumns)
    }

    return state
}

suspend fun PointerInputScope.detectFollowZoomGestures(
    onGestureStart: (centroid: Offset) -> Unit,
    onGesture: (centroid: Offset, zoomChange: Float) -> Unit,
    onGestureEnd: () -> Unit
) {
    var isZooming = false
    try {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)

            do {
                val event = awaitPointerEvent()
                val pressedPointers = event.changes.filter { it.pressed }
                val pointerCount = pressedPointers.size

                if (pointerCount >= 2) {
                    val zoomChange = event.calculateZoom()
                    val centroid = event.calculateCentroid(useCurrent = false)

                    if (!isZooming) {
                        if (zoomChange != 1.0f) {
                            isZooming = true
                            onGestureStart(centroid)
                            onGesture(centroid, zoomChange)
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    } else {
                        if (zoomChange != 1.0f) {
                            onGesture(centroid, zoomChange)
                        }
                        event.changes.forEach { change ->
                            if (change.positionChanged()) {
                                change.consume()
                            }
                        }
                    }
                } else {
                    if (isZooming) {
                        isZooming = false
                        onGestureEnd()
                    }
                }
            } while (event.changes.any { it.pressed })

            if (isZooming) {
                isZooming = false
                onGestureEnd()
            }
        }
    } finally {
        if (isZooming) {
            isZooming = false
            onGestureEnd()
        }
    }
}

fun Modifier.followZoom(state: FollowZoomState): Modifier {
    return this
        .pointerInput(state) {
            detectFollowZoomGestures(
                onGestureStart = { centroid ->
                    state.onPinchStart(centroid, size)
                },
                onGesture = { centroid, zoomChange ->
                    state.onPinch(centroid, zoomChange, size)
                },
                onGestureEnd = {
                    state.onPinchEnd()
                }
            )
        }
}

@Composable
fun Modifier.followZoom(
    currentColumns: Int = 2,
    minColumns: Int = 1,
    maxColumns: Int = 5,
    onColumnsChanged: (Int) -> Unit = {},
    onVirtualColumnsChanged: ((Float, Boolean) -> Unit)? = null
): Modifier {
    val state = rememberFollowZoomState(
        currentColumns = currentColumns,
        minColumns = minColumns,
        maxColumns = maxColumns,
        onColumnsChanged = onColumnsChanged,
        onVirtualColumnsChanged = onVirtualColumnsChanged
    )
    return followZoom(state)
}
