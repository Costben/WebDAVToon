// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.webdavtoon.R
import erl.webdavtoon.ui.component.CouiCascadingMenuPopup
import erl.webdavtoon.ui.theme.LocalDarkTheme
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.FloatingToolbar
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.FavoritesFill
import io.github.suqi8.coui.kmp.icon.extended.Image
import io.github.suqi8.coui.kmp.icon.extended.ListView
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.RotateLeft
import io.github.suqi8.coui.kmp.icon.extended.Share
import io.github.suqi8.coui.kmp.icon.extended.Tune
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

// Fixed skeleton for every bottom-bar slot. These are the only values the four function
// buttons are allowed to differ in, and they differ in none of them.
private val BottomActionRowInset = 8.dp
private val BottomActionSlotHeight = 46.dp
private val BottomActionIconSize = 22.dp
private val BottomActionIconLabelGap = 3.dp
private val BottomActionLabelSize = 11.sp
private val BottomActionLabelLineHeight = 13.sp

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
    onOpenGestureControl: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxSliderValue = maxOf(totalCount - 1, 1).toFloat()
    var sliderPosition by remember(currentIndex) { mutableFloatStateOf(currentIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

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
    val isDark = LocalDarkTheme.current || isSystemInDarkTheme()
    val toolbarBackgroundColor = if (isDark) {
        Color(0xF51C1C1E)
    } else {
        Color(0xF5F5F5F7)
    }

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
            FloatingToolbar(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                color = toolbarBackgroundColor,
                outSidePadding = PaddingValues(0.dp),
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = COUIIcons.Light.Back,
                            contentDescription = stringResource(R.string.back),
                            tint = COUITheme.colorScheme.onSurface,
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
                            style = COUITheme.textStyles.title3,
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pageDisplay,
                            style = COUITheme.textStyles.footnote2.copy(fontSize = 11.sp),
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                        )
                    }

                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) COUIIcons.Light.FavoritesFill else COUIIcons.Light.Favorites,
                            contentDescription = stringResource(R.string.favorite),
                            tint = if (isFavorite) Color(0xFFFFB300) else COUITheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = COUIIcons.Light.Share,
                            contentDescription = stringResource(R.string.share),
                            tint = COUITheme.colorScheme.onSurface,
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
            FloatingToolbar(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                color = toolbarBackgroundColor,
                outSidePadding = PaddingValues(0.dp),
                shadowElevation = 6.dp,
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
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }

                    // Lower Row: Function Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Inset the row first, then split what is left into four equal
                            // slots, so the items stay off the island's edges.
                            .padding(horizontal = BottomActionRowInset)
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 1. Reading Mode Toggle
                        val isWebtoon = readingMode == ReadingMode.WEBTOON
                        MiuixBottomActionItem(
                            modifier = Modifier.weight(1f),
                            icon = if (isWebtoon) COUIIcons.Light.ListView else COUIIcons.Light.Image,
                            label = if (isWebtoon) stringResource(R.string.default_reader_mode_webtoon) else stringResource(R.string.default_reader_mode_card),
                            tint = COUITheme.colorScheme.onSurface,
                            onClick = onToggleMode,
                        )

                        // 2. Slideshow
                        MiuixBottomActionItem(
                            modifier = Modifier.weight(1f),
                            icon = if (isSlideshowPlaying) COUIIcons.Light.Pause else COUIIcons.Light.Play,
                            label = if (isSlideshowPlaying) "暂停" else stringResource(R.string.slideshow),
                            tint = if (isSlideshowPlaying) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurface,
                            onClick = onToggleSlideshow,
                        )

                        // 3. Orientation Lock
                        MiuixBottomActionItem(
                            modifier = Modifier.weight(1f),
                            icon = if (isOrientationLocked) COUIIcons.Light.Lock else COUIIcons.Light.RotateLeft,
                            label = if (isOrientationLocked) "已锁定" else stringResource(R.string.rotation_lock),
                            tint = if (isOrientationLocked) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurface,
                            onClick = onToggleOrientationLock,
                        )

                        // 4. More: gesture control and save-to-album moved into one menu so the
                        // bar keeps four equal slots without giving up either action. The inner
                        // item needs its own fillMaxWidth to centre inside the slot: the Box is
                        // only the popup anchor, and a wrap-content child would hug its left edge.
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiuixBottomActionItem(
                                modifier = Modifier.fillMaxWidth(),
                                icon = COUIIcons.Light.More,
                                label = stringResource(R.string.more),
                                tint = COUITheme.colorScheme.onSurface,
                                onClick = { showMoreMenu = true },
                            )

                            CouiCascadingMenuPopup(
                                show = showMoreMenu,
                                entries = listOf(
                                    DropdownEntry(
                                        items = listOf(
                                            DropdownItem(
                                                text = stringResource(R.string.gesture_control),
                                                icon = { iconModifier ->
                                                    Icon(COUIIcons.Light.Tune, null, iconModifier)
                                                },
                                                onClick = onOpenGestureControl,
                                            ),
                                            DropdownItem(
                                                text = stringResource(R.string.save),
                                                icon = { iconModifier ->
                                                    Icon(COUIIcons.Light.Download, null, iconModifier)
                                                },
                                                onClick = onSaveImage,
                                            ),
                                        ),
                                    ),
                                ),
                                onDismissRequest = { showMoreMenu = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One bottom-bar slot. Every item goes through this skeleton so the four read as a single
 * unit: same slot height, same icon box, same label style, same icon-to-label gap. Nothing
 * here is derived from the label, so a two-character label sits exactly like a five-character
 * one and no slot drifts off the others' baseline.
 */
@Composable
private fun MiuixBottomActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = BottomActionSlotHeight)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(BottomActionIconSize),
            tint = tint,
        )
        Spacer(modifier = Modifier.height(BottomActionIconLabelGap))
        Text(
            text = label,
            style = COUITheme.textStyles.footnote2.copy(
                fontSize = BottomActionLabelSize,
                lineHeight = BottomActionLabelLineHeight,
            ),
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
