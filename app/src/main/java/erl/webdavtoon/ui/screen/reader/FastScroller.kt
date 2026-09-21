package erl.webdavtoon.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Text as MiuixText
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Floating fast scroller for vertical Webtoon reader.
 * Features a minimalist translucent track, interactive pill thumb, and animated page bubble.
 */
@Composable
fun FastScroller(
    totalCount: Int,
    currentIndex: Int,
    onScrollToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isFastScrolling: Boolean = false,
    onFastScrollingChanged: ((Boolean) -> Unit)? = null
) {
    if (totalCount <= 1) return

    val density = LocalDensity.current
    val thumbHeight = 40.dp
    val thumbHeightPx = with(density) { thumbHeight.toPx() }

    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var touchAreaWidthPx by remember { mutableIntStateOf(0) }
    var bubbleHeightPx by remember { mutableFloatStateOf(0f) }

    var isDragging by remember { mutableStateOf(false) }
    val isInteracting = isDragging || isFastScrolling

    var isBubbleVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isInteracting) {
        if (isInteracting) {
            isBubbleVisible = true
        } else {
            delay(450)
            isBubbleVisible = false
        }
    }

    val thumbOffsetY = remember(currentIndex, totalCount, trackHeightPx, thumbHeightPx) {
        calculateThumbOffsetY(
            currentIndex = currentIndex,
            totalCount = totalCount,
            trackHeight = trackHeightPx,
            thumbHeight = thumbHeightPx
        )
    }

    Box(
        modifier = modifier
    ) {
        // Page Bubble (Indicator) to the left of the thumb
        AnimatedVisibility(
            visible = isBubbleVisible,
            enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(250)) + scaleOut(targetScale = 0.85f, animationSpec = tween(250)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val bubbleY = (thumbOffsetY - (bubbleHeightPx - thumbHeightPx) / 2f)
                        .coerceIn(0f, (trackHeightPx - bubbleHeightPx).coerceAtLeast(0f))
                    IntOffset(
                        x = -(touchAreaWidthPx + 8.dp.roundToPx()),
                        y = bubbleY.roundToInt()
                    )
                }
                .onSizeChanged { size ->
                    bubbleHeightPx = size.height.toFloat()
                }
        ) {
            Box(
                modifier = Modifier.background(
                    color = Color(0xCC1E1E1E),
                    shape = RoundedCornerShape(16.dp),
                )
            ) {
                MiuixText(
                    text = "${(currentIndex + 1).coerceIn(1, totalCount)} / $totalCount",
                    color = Color.White,
                    style = COUITheme.textStyles.footnote2,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Touch & Track Container
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(36.dp)
                .onSizeChanged { size ->
                    trackHeightPx = size.height.toFloat()
                    touchAreaWidthPx = size.width
                }
                .pointerInput(totalCount) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true
                        onFastScrollingChanged?.invoke(true)
                        val initialIndex = calculateTargetIndex(
                            touchY = down.position.y,
                            trackHeight = size.height.toFloat(),
                            totalCount = totalCount
                        )
                        onScrollToIndex(initialIndex)

                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                break
                            }
                            change.consume()
                            val newIndex = calculateTargetIndex(
                                touchY = change.position.y,
                                trackHeight = size.height.toFloat(),
                                totalCount = totalCount
                            )
                            onScrollToIndex(newIndex)
                        }
                        isDragging = false
                        onFastScrollingChanged?.invoke(false)
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            // Track: 4dp wide translucent pill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // Thumb: interactive pill with slight expansion and highlight on interaction
            val thumbWidth by animateDpAsState(
                targetValue = if (isInteracting) 8.dp else 6.dp,
                animationSpec = tween(150),
                label = "thumbWidth"
            )
            val thumbColor by animateColorAsState(
                targetValue = if (isInteracting) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.6f),
                animationSpec = tween(150),
                label = "thumbColor"
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = thumbOffsetY.roundToInt()) }
                    .size(width = thumbWidth, height = thumbHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(thumbColor)
            )
        }
    }
}

/**
 * Calculates target photo/page index from touch Y within track.
 */
fun calculateTargetIndex(
    touchY: Float,
    trackHeight: Float,
    totalCount: Int
): Int {
    if (totalCount <= 1 || trackHeight <= 0f) return 0
    val fraction = (touchY / trackHeight).coerceIn(0f, 1f)
    return (fraction * (totalCount - 1)).roundToInt().coerceIn(0, totalCount - 1)
}

/**
 * Calculates thumb vertical offset Y within track bounds.
 */
fun calculateThumbOffsetY(
    currentIndex: Int,
    totalCount: Int,
    trackHeight: Float,
    thumbHeight: Float
): Float {
    if (totalCount <= 1 || trackHeight <= thumbHeight) return 0f
    val maxOffset = trackHeight - thumbHeight
    val fraction = (currentIndex.toFloat() / (totalCount - 1).toFloat()).coerceIn(0f, 1f)
    return fraction * maxOffset
}
