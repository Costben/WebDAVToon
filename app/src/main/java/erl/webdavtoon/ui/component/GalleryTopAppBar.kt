// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.couiGlassHeader
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The app's top app bar: COUI's app bar wearing the ColorOS 17 gradient glass, laid out at
 * [GalleryChrome] density.
 *
 * The gallery's toolbar measures 182px on the PKT110, i.e. COUI's 52dp row at the gallery's
 * 3.5 px/dp, while the system's 620dpi would draw 201px. Pinning the row — not only its buttons —
 * is what keeps the bar the same height as the gallery's and centres the 40dp keys in it.
 *
 * Two shapes, both one row high:
 * - [SmallTopAppBar] by default: a 24dp title in the 52dp toolbar row, which is what a page with a
 *   back key gets.
 * - [largeTitle]: the 32dp home title on the same row as the action keys, the way ColorOS 17 draws
 *   the Photos home page. COUI's own `TopAppBar` would put the keys on a row of their own and hang
 *   the title underneath, so this shape is a plain [Row] over the same glass.
 *
 * Neither shape goes through a scroll behavior. The bar height used to follow the scroll offset,
 * and that height feeds back into the scaffold's top padding, the scrollable's viewport and the next
 * scroll dispatch; on a short list the loop never settles, which kept re-triggering the bottom
 * overscroll while the title twitched. A fixed height has nothing to feed back.
 *
 * @param largeTitle draws the title at home-page size on the action row instead of the toolbar size.
 */
@Composable
fun GalleryTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    GalleryChrome {
        if (largeTitle) {
            LargeTitleRow(title = title, modifier = modifier, navigationIcon = navigationIcon, actions = actions)
        } else {
            SmallTopAppBar(
                title = title,
                modifier = modifier,
                navigationIcon = navigationIcon,
                actions = actions,
                gradientGlass = true,
                showDivider = false,
            )
        }
    }
}

/**
 * COUI's expanded title, moved onto the action row.
 *
 * The 32dp / SemiBold figure is `TopAppBarDefaults`' large title (`coui_appbar_title_*` text size),
 * converted to sp through the composition's density so it keeps the size the collapsed bar used at
 * any system font scale.
 */
@Composable
private fun LargeTitleRow(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .couiGlassHeader(LocalBackdrop.current, COUITheme.colorScheme.surface)
            .statusBarsPadding()
            .heightIn(min = 60.dp)
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationIcon()
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = COUITheme.colorScheme.onSurface,
            fontSize = with(LocalDensity.current) { LargeTitleSize.toSp() },
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

private val LargeTitleSize = 28.dp
