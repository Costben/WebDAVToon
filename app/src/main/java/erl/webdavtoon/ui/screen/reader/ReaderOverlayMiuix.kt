package erl.webdavtoon.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.sp
import erl.webdavtoon.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * HyperOS / Miuix styled floating translucent overlay for the immersive reader.
 */
@Composable
fun ReaderOverlayMiuix(
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
                shape = RoundedCornerShape(20.dp),
                color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.2f)),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Light.Back,
                            contentDescription = stringResource(R.string.back),
                            tint = MiuixTheme.colorScheme.onSurface,
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
                            style = MiuixTheme.textStyles.title3,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pageDisplay,
                            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                    }

                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            painter = painterResource(
                                if (isFavorite) R.drawable.ic_ior_star_solid else R.drawable.ic_ior_star
                            ),
                            contentDescription = stringResource(R.string.favorite),
                            tint = if (isFavorite) Color(0xFFFFB300) else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    IconButton(onClick = onShare) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ior_share),
                            contentDescription = stringResource(R.string.share),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
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
                shape = RoundedCornerShape(24.dp),
                color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.2f)),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Upper Row: Progress Slider + Page indicator
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
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = pageDisplay,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurface,
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
                        MiuixBottomActionItem(
                            iconRes = if (isWebtoon) R.drawable.ic_card_mode_outlined else R.drawable.ic_webtoon_mode_outlined,
                            label = if (isWebtoon) stringResource(R.string.default_reader_mode_card) else stringResource(R.string.default_reader_mode_webtoon),
                            tint = MiuixTheme.colorScheme.onSurface,
                            onClick = onToggleMode,
                        )

                        // 2. Slideshow
                        MiuixBottomActionItem(
                            iconRes = if (isSlideshowPlaying) R.drawable.ic_ior_pause_solid else R.drawable.ic_ior_play_solid,
                            label = if (isSlideshowPlaying) "暂停" else stringResource(R.string.slideshow),
                            tint = if (isSlideshowPlaying) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            onClick = onToggleSlideshow,
                        )

                        // 3. Orientation Lock
                        MiuixBottomActionItem(
                            iconRes = if (isOrientationLocked) R.drawable.ic_lock else R.drawable.ic_screen_rotation,
                            label = if (isOrientationLocked) "已锁定" else stringResource(R.string.rotation_lock),
                            tint = if (isOrientationLocked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            onClick = onToggleOrientationLock,
                        )

                        // 4. Save to Album
                        MiuixBottomActionItem(
                            iconRes = R.drawable.ic_ior_download,
                            label = stringResource(R.string.save),
                            tint = MiuixTheme.colorScheme.onSurface,
                            onClick = onSaveImage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.MiuixBottomActionItem(
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
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
