// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import erl.webdavtoon.R
import erl.webdavtoon.ui.component.GalleryChrome
import erl.webdavtoon.ui.component.HighlightedTopBarAction
import erl.webdavtoon.ui.component.rememberTapHaptic
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingScrollToolbar
import io.github.suqi8.coui.kmp.basic.BottomFloatingScrollToolbarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingScrollToolbarItem
import io.github.suqi8.coui.kmp.basic.BottomFloatingToolbarDefaults
import io.github.suqi8.coui.kmp.basic.GlassContainer
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.couiGlassSurface
import io.github.suqi8.coui.kmp.basic.rememberGlassHighlight
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.FavoritesFill
import io.github.suqi8.coui.kmp.icon.extended.Image
import io.github.suqi8.coui.kmp.icon.extended.ListView
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.RotateLeft
import io.github.suqi8.coui.kmp.icon.extended.Share
import io.github.suqi8.coui.kmp.icon.extended.Tune
import kotlin.math.roundToInt

/** The favourite disc's amber, kept off the theme so it reads the same over every photo. */
private val FavoriteTint = Color(0xFFFFB300)

private val ProgressCapsuleCorner = 22.dp
private val ProgressCapsuleMargin = PaddingValues(horizontal = 16.dp)

/** The gap between the progress capsule and the tool pill below it. */
private val BottomStackSpacing = 10.dp

/** How far the bottom stack sits above the navigation bar, matching the gallery's own chrome. */
private val BottomStackInset = 16.dp

/**
 * HyperOS / Miuix styled floating overlay for the immersive reader.
 *
 * The top is the app's own [GalleryTopAppBar] — the same ColorOS 17 glass row every other page
 * wears — so the back key keeps its halo light and the album name sits where the gallery puts it,
 * with favourite and share as the end actions. The bottom is split in two the way ColorOS 17's
 * viewer does it: the seek slider in a capsule of its own, and under it
 * [BottomFloatingScrollToolbar], the Photos editor's scrolling tool pill. The page counter is gone
 * on purpose: the slider already tells where the reader is, and the number only crowded the bar.
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
    canResubmit: Boolean,
    onBack: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleMode: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onToggleOrientationLock: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveImage: () -> Unit,
    onOpenGestureControl: () -> Unit,
    onShare: () -> Unit,
    onReedit: () -> Unit,
    onResubmit: () -> Unit,
    onDeleteTask: () -> Unit,
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

    val lastIndex = maxOf(totalCount - 1, 0)
    val backdrop = LocalBackdrop.current
    val squircleEnabled = isSquircleEnabled()
    val toolbarShape = remember(squircleEnabled) {
        SquircleShape(
            cornerRadius = BottomFloatingScrollToolbarDefaults.CornerRadius,
            squircleEnabled = squircleEnabled,
        )
    }
    val toolColors = BottomFloatingScrollToolbarDefaults.colors(
        containerColor = Color.Transparent,
        strokeColor = Color.Transparent,
        contentColor = Color(0xCCFFFFFF),
    )
    val tapHaptic = rememberTapHaptic()

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            // The reader keeps a transparent top bar with no gradient blur band; the circular
            // dark-mode buttons blur the photo inside their own 40dp discs and the title stays white.
            GalleryChrome {
                SmallTopAppBar(
                    title = title,
                    color = Color.Transparent,
                    titleColor = Color.White,
                    navigationIcon = {
                        HighlightedTopBarAction(
                            icon = COUIIcons.Light.Back,
                            contentDescription = stringResource(R.string.back),
                            onClick = onBack,
                            iconTint = Color.White,
                            backdrop = backdrop,
                        )
                    },
                    actions = {
                        HighlightedTopBarAction(
                            icon = if (isFavorite) COUIIcons.Light.FavoritesFill else COUIIcons.Light.Favorites,
                            contentDescription = stringResource(R.string.favorite),
                            onClick = onToggleFavorite,
                            iconTint = if (isFavorite) FavoriteTint else Color.White,
                            backdrop = backdrop,
                        )
                        HighlightedTopBarAction(
                            icon = COUIIcons.Light.Share,
                            contentDescription = stringResource(R.string.share),
                            onClick = onShare,
                            iconTint = Color.White,
                            backdrop = backdrop,
                        )
                    },
                    gradientGlass = false,
                    showDivider = false,
                )
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    bottom = BottomFloatingNavigationBarDefaults.navigationBarPadding() + BottomStackInset,
                ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ReaderProgressCapsule(
                    value = if (totalCount <= 1) 0f else sliderPosition.coerceIn(0f, maxSliderValue),
                    maxValue = maxSliderValue,
                    enabled = totalCount > 1,
                    onValueChange = { value ->
                        isDragging = true
                        sliderPosition = value
                        onSeek(value.roundToInt().coerceIn(0, lastIndex))
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        onSeek(sliderPosition.roundToInt().coerceIn(0, lastIndex))
                    },
                )

                Spacer(Modifier.height(BottomStackSpacing))

                GalleryChrome {
                    val itemCount = if (canResubmit) 8 else 5
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val maxPillWidth = minOf(
                            (maxWidth - BottomFloatingScrollToolbarDefaults.MarginHorizontal * 2)
                                .coerceAtLeast(0.dp),
                            BottomFloatingScrollToolbarDefaults.MaxWidth,
                        )
                        val naturalPillWidth =
                            BottomFloatingScrollToolbarDefaults.ContentPadding * 2 +
                                BottomFloatingScrollToolbarDefaults.ItemWidth * itemCount +
                                BottomFloatingScrollToolbarDefaults.ItemSpacing *
                                (itemCount - 1).coerceAtLeast(0)
                        val pillWidth = minOf(naturalPillWidth, maxPillWidth)

                        Box(contentAlignment = Alignment.Center) {
                            // BottomFloatingScrollToolbar paints an opaque fill of its own and
                            // does not read LocalBackdrop, so we place the frosted glass surface
                            // at the exact pill bounds underneath and keep the toolbar transparent.
                            Box(
                                modifier = Modifier
                                    .width(pillWidth)
                                    .height(BottomFloatingScrollToolbarDefaults.Height)
                                    .dropShadow(
                                        shape = toolbarShape,
                                        shadow = Shadow(
                                            radius = 10.dp,
                                            color = Color.Black,
                                            alpha = 0.18f,
                                        ),
                                    )
                                    .couiGlassSurface(
                                        backdrop = backdrop,
                                        shape = toolbarShape,
                                        containerColor = BottomFloatingScrollToolbarDefaults.ContainerColor,
                                        blurRadius = BottomFloatingToolbarDefaults.BlurRadius,
                                        blendColors = BottomFloatingToolbarDefaults.BlurColorsDark,
                                        fallback = Modifier.background(
                                            BottomFloatingScrollToolbarDefaults.ContainerColor,
                                            toolbarShape,
                                        ),
                                    ),
                            )

                            BottomFloatingScrollToolbar(
                                modifier = Modifier.width(
                                    pillWidth + BottomFloatingScrollToolbarDefaults.MarginHorizontal * 2,
                                ),
                                colors = toolColors,
                            ) {
                                // Only AutoWorkflow output images carry a generation task; when
                                // present, re-edit and resubmit sit at the front of the toolbar.
                                if (canResubmit) {
                                    BottomFloatingScrollToolbarItem(
                                        onClick = {
                                            tapHaptic()
                                            onReedit()
                                        },
                                        icon = COUIIcons.Light.Edit,
                                        label = stringResource(R.string.autoworkflow_resubmit_edit),
                                    )
                                    BottomFloatingScrollToolbarItem(
                                        onClick = {
                                            tapHaptic()
                                            onResubmit()
                                        },
                                        icon = COUIIcons.Light.Refresh,
                                        label = stringResource(R.string.autoworkflow_resubmit),
                                    )
                                    BottomFloatingScrollToolbarItem(
                                        onClick = {
                                            tapHaptic()
                                            onDeleteTask()
                                        },
                                        icon = COUIIcons.Delete,
                                        label = stringResource(R.string.autoworkflow_delete_task),
                                    )
                                }
                                BottomFloatingScrollToolbarItem(
                                    onClick = {
                                        tapHaptic()
                                        onToggleMode()
                                    },
                                    icon = if (readingMode == ReadingMode.WEBTOON) COUIIcons.Light.ListView else COUIIcons.Light.Image,
                                    label = stringResource(
                                        if (readingMode == ReadingMode.WEBTOON) {
                                            R.string.default_reader_mode_webtoon
                                        } else {
                                            R.string.default_reader_mode_card
                                        },
                                    ),
                                    selected = readingMode == ReadingMode.WEBTOON,
                                )
                                BottomFloatingScrollToolbarItem(
                                    onClick = {
                                        tapHaptic()
                                        onToggleSlideshow()
                                    },
                                    icon = if (isSlideshowPlaying) COUIIcons.Light.Pause else COUIIcons.Light.Play,
                                    label = stringResource(
                                        if (isSlideshowPlaying) R.string.slideshow_pause else R.string.slideshow,
                                    ),
                                    selected = isSlideshowPlaying,
                                )
                                BottomFloatingScrollToolbarItem(
                                    onClick = {
                                        tapHaptic()
                                        onToggleOrientationLock()
                                    },
                                    icon = if (isOrientationLocked) COUIIcons.Light.Lock else COUIIcons.Light.RotateLeft,
                                    label = stringResource(
                                        if (isOrientationLocked) R.string.rotation_locked else R.string.rotation_lock,
                                    ),
                                    selected = isOrientationLocked,
                                )
                                BottomFloatingScrollToolbarItem(
                                    onClick = {
                                        tapHaptic()
                                        onOpenGestureControl()
                                    },
                                    icon = COUIIcons.Light.Tune,
                                    label = stringResource(R.string.gesture_control),
                                )
                                BottomFloatingScrollToolbarItem(
                                    onClick = {
                                        tapHaptic()
                                        onSaveImage()
                                    },
                                    icon = COUIIcons.Light.Download,
                                    label = stringResource(R.string.save),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The seek slider in a frosted glass capsule of its own, split off from the tool pill underneath.
 *
 * Keeping the two apart is what lets the tool row scroll sideways without moving the thumb, and
 * gives the slider the full width instead of the share it used to get next to four buttons.
 */
@Composable
private fun ReaderProgressCapsule(
    value: Float,
    maxValue: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    GalleryChrome {
        val backdrop = LocalBackdrop.current
        val squircleEnabled = isSquircleEnabled()
        val shape = remember(squircleEnabled) {
            SquircleShape(cornerRadius = ProgressCapsuleCorner, squircleEnabled = squircleEnabled)
        }
        val highlight = rememberGlassHighlight(
            container = GlassContainer.Medium,
            backdrop = backdrop,
        )
        val fallback = remember(shape) {
            Modifier.background(BottomFloatingScrollToolbarDefaults.ContainerColor, shape)
        }
        Box(
            modifier = Modifier
                .padding(ProgressCapsuleMargin)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 10.dp,
                        color = Color.Black,
                        alpha = 0.18f,
                    ),
                )
                .couiGlassSurface(
                    backdrop = backdrop,
                    shape = shape,
                    containerColor = BottomFloatingScrollToolbarDefaults.ContainerColor,
                    blurRadius = BottomFloatingToolbarDefaults.BlurRadius,
                    blendColors = BottomFloatingToolbarDefaults.BlurColorsDark,
                    highlight = highlight,
                    fallback = fallback,
                )
                .pointerInput(Unit) {
                    detectTapGestures { /* Consume click */ }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = 0f..maxValue,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
