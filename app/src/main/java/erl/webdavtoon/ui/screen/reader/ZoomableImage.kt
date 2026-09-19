package erl.webdavtoon.ui.screen.reader

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import erl.webdavtoon.Photo
import erl.webdavtoon.WebDavImageLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Zoomable image viewer component supporting pinch-to-zoom, double-tap zoom, and gesture conflict resolution.
 */
@Composable
fun ZoomableImage(
    photo: Photo,
    modifier: Modifier = Modifier,
    maxScale: Float = 4.0f,
    onSingleTap: () -> Unit = {},
    onDoubleTap: (() -> Unit)? = null,
    isCurrentPage: Boolean = true
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val coroutineScope = rememberCoroutineScope()
    var animationJob: Job? by remember { mutableStateOf(null) }

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            val startScale = scale
            val startOffset = offset
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 250)
            ) { frac, _ ->
                scale = startScale + (targetScale - startScale) * frac
                offset = Offset(
                    x = startOffset.x + (targetOffset.x - startOffset.x) * frac,
                    y = startOffset.y + (targetOffset.y - startOffset.y) * frac
                )
            }
        }
    }

    // When not current page, reset zoom and pan immediately
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            animationJob?.cancel()
            scale = 1.0f
            offset = Offset.Zero
        }
    }

    // Reset when photo changes
    LaunchedEffect(photo.id) {
        animationJob?.cancel()
        scale = 1.0f
        offset = Offset.Zero
    }

    // Loading & retry state.
    // These are keyed on (photo.id, retryTrigger) so the reset happens during
    // composition, *before* AndroidView.update runs. Resetting them in a
    // LaunchedEffect instead would race the Glide callback: a synchronous
    // memory-cache hit delivers onDimensionsReady (setting isLoaded=true)
    // during update, and the later effect would overwrite it back to
    // isLoading=true with no further callback to clear it — leaving a spinner
    // forever after switching from Webtoon to Card mode.
    var retryTrigger by remember { mutableIntStateOf(0) }
    var isLoading by remember(photo.id, retryTrigger) { mutableStateOf(true) }
    var isError by remember(photo.id, retryTrigger) { mutableStateOf(false) }
    var isLoaded by remember(photo.id, retryTrigger) { mutableStateOf(false) }

    val context = LocalContext.current

    val progressProxy = remember(photo.id, retryTrigger, context) {
        object : ProgressBar(context) {
            override fun setVisibility(v: Int) {
                super.setVisibility(v)
                if (v == View.GONE) {
                    post {
                        if (!isLoaded) {
                            isLoading = false
                            isError = true
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(photo.id, maxScale) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val containerWidth = containerSize.width.toFloat().coerceAtLeast(1f)
                        val containerHeight = containerSize.height.toFloat().coerceAtLeast(1f)
                        val (targetScale, targetOffset) = calculateDoubleTapTarget(
                            currentScale = scale,
                            tapOffset = tapOffset,
                            containerWidth = containerWidth,
                            containerHeight = containerHeight,
                            maxScale = maxScale
                        )
                        animateTo(targetScale, targetOffset)
                        onDoubleTap?.invoke()
                    },
                    onTap = {
                        onSingleTap()
                    }
                )
            }
            .pointerInput(photo.id, maxScale) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    animationJob?.cancel()

                    var pastTouchSlop = false
                    var panAccumulator = Offset.Zero
                    val touchSlop = viewConfiguration.touchSlop

                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break

                        val activePointers = event.changes.filter { it.pressed }
                        val pointerCount = activePointers.size

                        if (pointerCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)

                            if (!pastTouchSlop) {
                                panAccumulator += panChange
                                val zoomDelta = kotlin.math.abs(1f - zoomChange)
                                if (zoomDelta > 0.02f || panAccumulator.getDistance() > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop) {
                                val containerWidth = containerSize.width.toFloat().coerceAtLeast(1f)
                                val containerHeight = containerSize.height.toFloat().coerceAtLeast(1f)
                                val newScale = calculatePinchScale(scale, zoomChange, maxScale)
                                val newOffset = calculatePinchOffset(
                                    currentOffset = offset,
                                    panChange = panChange,
                                    centroid = centroid,
                                    currentScale = scale,
                                    newScale = newScale,
                                    containerWidth = containerWidth,
                                    containerHeight = containerHeight
                                )

                                scale = newScale
                                offset = newOffset

                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            }
                        } else if (pointerCount == 1) {
                            if (shouldConsumeDrag(scale)) {
                                val panDelta = event.calculatePan()

                                if (!pastTouchSlop) {
                                    panAccumulator += panDelta
                                    if (panAccumulator.getDistance() > touchSlop) {
                                        pastTouchSlop = true
                                    }
                                }

                                if (pastTouchSlop) {
                                    val containerWidth = containerSize.width.toFloat().coerceAtLeast(1f)
                                    val containerHeight = containerSize.height.toFloat().coerceAtLeast(1f)
                                    offset = calculateClampedOffset(
                                        offset = Offset(offset.x + panDelta.x, offset.y + panDelta.y),
                                        scale = scale,
                                        containerWidth = containerWidth,
                                        containerHeight = containerHeight
                                    )

                                    event.changes.forEach { c ->
                                        if (c.positionChanged()) {
                                            c.consume()
                                        }
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (scale <= 1.05f) {
                        scale = 1.0f
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    isClickable = false
                    isFocusable = false
                }
            },
            update = { imageView ->
                @Suppress("UNUSED_EXPRESSION")
                retryTrigger

                if (photo.isLocal) {
                    WebDavImageLoader.loadLocalImage(
                        context = imageView.context,
                        imageUri = photo.imageUri,
                        imageView = imageView,
                        progressBar = progressProxy,
                        limitSize = false,
                        isWebtoonReader = true,
                        onDimensionsReady = { _, _ ->
                            isLoaded = true
                            isLoading = false
                            isError = false
                        }
                    )
                } else {
                    WebDavImageLoader.loadWebDavImage(
                        context = imageView.context,
                        imageUri = photo.imageUri,
                        imageView = imageView,
                        progressBar = progressProxy,
                        limitSize = false,
                        isWebtoonReader = true,
                        onDimensionsReady = { _, _ ->
                            isLoaded = true
                            isLoading = false
                            isError = false
                        }
                    )
                }
            },
            onRelease = WebDavImageLoader::clear
        )

        if (isLoading && !isError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            }
        }

        if (isError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Failed to load image",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { retryTrigger++ }
                    ) {
                        Text(text = "Retry", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * Clamp offset so zoomed image does not slide too far outside viewport.
 */
internal fun calculateClampedOffset(
    offset: Offset,
    scale: Float,
    containerWidth: Float,
    containerHeight: Float
): Offset {
    val maxX = (containerWidth * (scale - 1f)).coerceAtLeast(0f) / 2f
    val maxY = (containerHeight * (scale - 1f)).coerceAtLeast(0f) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

/**
 * Determine whether single-finger drag should be consumed by image pan vs yielded to horizontal pager.
 */
internal fun shouldConsumeDrag(scale: Float): Boolean = scale > 1.05f

/**
 * Calculate target scale and offset for double-tap zoom.
 */
internal fun calculateDoubleTapTarget(
    currentScale: Float,
    tapOffset: Offset,
    containerWidth: Float,
    containerHeight: Float,
    maxScale: Float = 4.0f
): Pair<Float, Offset> {
    return if (currentScale > 1.05f) {
        Pair(1.0f, Offset.Zero)
    } else {
        val targetScale = 2.5f.coerceAtMost(maxScale)
        val containerCenter = Offset(containerWidth / 2f, containerHeight / 2f)
        val rawX = (containerCenter.x - tapOffset.x) * (targetScale - 1f)
        val rawY = (containerCenter.y - tapOffset.y) * (targetScale - 1f)
        val clamped = calculateClampedOffset(
            offset = Offset(rawX, rawY),
            scale = targetScale,
            containerWidth = containerWidth,
            containerHeight = containerHeight
        )
        Pair(targetScale, clamped)
    }
}

/**
 * Calculate pinch scale clamped within 1.0f..maxScale.
 */
internal fun calculatePinchScale(
    currentScale: Float,
    zoomChange: Float,
    maxScale: Float = 4.0f
): Float {
    return (currentScale * zoomChange).coerceIn(1.0f, maxScale)
}

/**
 * Calculate pinch offset centered around focal centroid and clamped to boundaries.
 */
internal fun calculatePinchOffset(
    currentOffset: Offset,
    panChange: Offset,
    centroid: Offset,
    currentScale: Float,
    newScale: Float,
    containerWidth: Float,
    containerHeight: Float
): Offset {
    if (newScale <= 1.0f) return Offset.Zero
    val containerCenter = Offset(containerWidth / 2f, containerHeight / 2f)
    val zoomFactor = if (currentScale > 0f) newScale / currentScale else 1f
    val centroidOffset = centroid - containerCenter - currentOffset
    val zoomOffsetDelta = centroidOffset * (1f - zoomFactor)
    val targetX = currentOffset.x + panChange.x + zoomOffsetDelta.x
    val targetY = currentOffset.y + panChange.y + zoomOffsetDelta.y
    return calculateClampedOffset(
        offset = Offset(targetX, targetY),
        scale = newScale,
        containerWidth = containerWidth,
        containerHeight = containerHeight
    )
}
