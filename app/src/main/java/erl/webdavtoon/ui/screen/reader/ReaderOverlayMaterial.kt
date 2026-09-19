package erl.webdavtoon.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import erl.webdavtoon.R
import kotlin.math.roundToInt

/**
 * Material 3 Expressive styled floating translucent overlay for the immersive reader.
 */
@Composable
fun ReaderOverlayMaterial(
    visible: Boolean,
    title: String,
    currentIndex: Int,
    totalCount: Int,
    readingMode: ReadingMode,
    isSlideshowPlaying: Boolean,
    isOrientationLocked: Boolean,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleMode: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onToggleOrientationLock: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveImage: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxSliderValue = maxOf(totalCount - 1, 1).toFloat()
    var sliderPosition by remember(currentIndex) { mutableFloatStateOf(currentIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentIndex) {
        if (!isDragging) {
            sliderPosition = currentIndex.toFloat()
        }
    }

    val displayIndex = if (isDragging) {
        sliderPosition.roundToInt().coerceIn(0, maxOf(totalCount - 1, 0))
    } else {
        currentIndex.coerceIn(0, maxOf(totalCount - 1, 0))
    }
    val pageDisplay = if (totalCount <= 0) "0 / 0" else "${displayIndex + 1} / $totalCount"

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top Bar
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = title.ifBlank { stringResource(R.string.app_name) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pageDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }

                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            painter = painterResource(
                                if (isFavorite) R.drawable.ic_star_filled_md3 else R.drawable.ic_star_outlined
                            ),
                            contentDescription = stringResource(R.string.favorite),
                            tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    IconButton(onClick = onShare) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ior_share),
                            contentDescription = stringResource(R.string.share),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }

        // Bottom Control Island
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Upper Row: M3 Slider + Page indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Slider(
                            value = if (totalCount <= 1) 0f else sliderPosition.coerceIn(0f, maxSliderValue),
                            onValueChange = { value ->
                                isDragging = true
                                sliderPosition = value
                                onSeek(value.roundToInt().coerceIn(0, maxOf(totalCount - 1, 0)))
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                onSeek(sliderPosition.roundToInt().coerceIn(0, maxOf(totalCount - 1, 0)))
                            },
                            valueRange = 0f..maxSliderValue,
                            enabled = totalCount > 1,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = pageDisplay,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }

                    // Lower Row: Function Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 1. Reading Mode Toggle
                        val isWebtoon = readingMode == ReadingMode.WEBTOON
                        MaterialBottomActionItem(
                            iconRes = if (isWebtoon) R.drawable.ic_webtoon_mode_md3 else R.drawable.ic_card_mode_md3,
                            label = if (isWebtoon) stringResource(R.string.default_reader_mode_webtoon) else stringResource(R.string.default_reader_mode_card),
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = onToggleMode,
                        )

                        // 2. Slideshow
                        MaterialBottomActionItem(
                            iconRes = if (isSlideshowPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                            label = if (isSlideshowPlaying) "暂停" else stringResource(R.string.slideshow),
                            tint = if (isSlideshowPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            onClick = onToggleSlideshow,
                        )

                        // 3. Orientation Lock
                        MaterialBottomActionItem(
                            iconRes = if (isOrientationLocked) R.drawable.ic_lock else R.drawable.ic_screen_rotation,
                            label = if (isOrientationLocked) "已锁定" else stringResource(R.string.rotation_lock),
                            tint = if (isOrientationLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            onClick = onToggleOrientationLock,
                        )

                        // 4. Save to Album
                        MaterialBottomActionItem(
                            iconRes = R.drawable.ic_download_outlined,
                            label = stringResource(R.string.save),
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = onSaveImage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.MaterialBottomActionItem(
    iconRes: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
