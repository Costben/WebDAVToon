// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.waterfall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import erl.webdavtoon.R
import erl.webdavtoon.ui.component.GalleryChrome
import erl.webdavtoon.ui.component.rememberStrongHaptic
import erl.webdavtoon.ui.component.rememberTapHaptic
import io.github.suqi8.coui.kmp.anim.COUIInEase
import io.github.suqi8.coui.kmp.anim.COUIOutEase
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingScrollToolbar
import io.github.suqi8.coui.kmp.basic.BottomFloatingScrollToolbarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingScrollToolbarItem
import io.github.suqi8.coui.kmp.basic.NavigationBarMotion
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.FavoritesFill
import io.github.suqi8.coui.kmp.icon.extended.SelectAll
import io.github.suqi8.coui.kmp.icon.extended.Share
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The COUI scrolling tool bar (滚动工具栏) shown while items are selected.
 *
 * This is [BottomFloatingScrollToolbar], the pill the ColorOS Photos editor raises for its tools: a
 * 56dp capsule with 28dp corners, one icon over a short label per item, centred and 16dp in from
 * each side. The app keeps five bulk actions, so the pill does not scroll at rest, but it fades and
 * scrolls the same way the native one does as soon as the item widths outgrow the screen.
 *
 * It is a floating pill over the grid rather than a bar pinned to the edge, so it is placed in the
 * `bottomBar` slot of `GlassTopBarScaffold` (outside the content's backdrop layer) and the list
 * scrolls behind it. The navigation-bar inset and the 16dp above it are carried by the padding
 * below the pill, as [GalleryChrome]'s other capsules do.
 *
 * @param visible whether the selection mode is active.
 * @param selectedCount the number of selected items; actions other than select-all and dismiss are
 *   disabled while it is zero.
 * @param isAllSelected whether every item is selected, which tints the select-all item as the
 *   active tool.
 * @param onEditClick sends the selection to the local ComfyUI workflow. The item is left out when
 *   the host has no ComfyUI flow wired, as on the sub-folder screen.
 */
@Composable
fun SelectionBottomBar(
    visible: Boolean,
    selectedCount: Int,
    isAllSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null,
) {
    val hasSelection = selectedCount > 0
    val editAction = onEditClick
    val tapHaptic = rememberTapHaptic()
    val strongHaptic = rememberStrongHaptic()
    // The Photos editor's pill is always dark; here it follows the page instead, so a light page
    // gets the same light capsule the gallery uses for its own bottom chrome.
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val colors = if (isDark) {
        BottomFloatingScrollToolbarDefaults.colors()
    } else {
        BottomFloatingScrollToolbarDefaults.colors(
            containerColor = Color(0xF5F5F5F7),
            strokeColor = Color(0x1A000000),
            contentColor = Color(0xB3000000),
            disabledContentColor = Color(0x33000000),
        )
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        // COUINavigationView.show() / hide(): the bar slides by its own height, without a fade.
        enter = slideInVertically(
            tween(NavigationBarMotion.ShowTranslateDurationMillis, easing = COUIInEase),
        ) { it },
        exit = slideOutVertically(
            tween(NavigationBarMotion.HideTranslateDurationMillis, easing = COUIOutEase),
        ) { it },
    ) {
        GalleryChrome {
            BottomFloatingScrollToolbar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = BottomFloatingNavigationBarDefaults.navigationBarPadding() + 16.dp,
                    ),
                colors = colors,
            ) {
                // A tool bar has no current tool: every action draws in the idle colour, except
                // select-all, which reads as active once the whole page is selected.
                BottomFloatingScrollToolbarItem(
                    onClick = {
                        tapHaptic()
                        onToggleSelectAll()
                    },
                    icon = COUIIcons.SelectAll,
                    label = stringResource(
                        if (isAllSelected) R.string.unselect_all else R.string.select_all
                    ),
                    selected = isAllSelected,
                )
                BottomFloatingScrollToolbarItem(
                    onClick = {
                        tapHaptic()
                        onToggleFavorite()
                    },
                    icon = COUIIcons.Light.FavoritesFill,
                    label = stringResource(R.string.favorite),
                    enabled = hasSelection,
                )
                BottomFloatingScrollToolbarItem(
                    onClick = {
                        tapHaptic()
                        onShare()
                    },
                    icon = COUIIcons.Light.Share,
                    label = stringResource(R.string.share),
                    enabled = hasSelection,
                )
                // The label cell is 49dp at 8sp, so the pill keeps the product name and leaves the
                // full sentence to the content description.
                if (editAction != null) {
                    BottomFloatingScrollToolbarItem(
                        onClick = {
                            tapHaptic()
                            editAction()
                        },
                        icon = COUIIcons.Edit,
                        label = stringResource(R.string.comfyui_toolbar_label),
                        enabled = hasSelection,
                    )
                }
                BottomFloatingScrollToolbarItem(
                    onClick = {
                        strongHaptic()
                        onDelete()
                    },
                    icon = COUIIcons.Delete,
                    label = stringResource(R.string.delete),
                    enabled = hasSelection,
                )
                BottomFloatingScrollToolbarItem(
                    onClick = {
                        tapHaptic()
                        onDismiss()
                    },
                    icon = COUIIcons.Close,
                    label = stringResource(R.string.cancel),
                )
            }
        }
    }
}
