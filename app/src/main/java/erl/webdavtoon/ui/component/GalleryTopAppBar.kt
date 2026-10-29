// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState

/**
 * The app's top app bar: COUI's [TopAppBar] wearing the ColorOS 17 gradient glass, laid out at
 * [GalleryChrome] density.
 *
 * The gallery's toolbar measures 182px on the PKT110, i.e. COUI's 52dp row at the gallery's
 * 3.5 px/dp, while the system's 620dpi would draw 201px. Pinning the row — not only its buttons —
 * is what keeps the bar the same height as the gallery's and centres the 40dp keys in it.
 */
@Composable
fun GalleryTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    GalleryChrome {
        TopAppBar(
            title = title,
            modifier = modifier,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
            gradientGlass = true,
            showDivider = false,
        )
    }
}

/**
 * The scroll behavior for a [GalleryTopAppBar] that owns a large title.
 *
 * `expandOnContentScroll = false` is COUI's "snap" mode: the title stays in the toolbar row while
 * the list is scrolled and only returns to the large-title row at the top, instead of tracking the
 * finger anywhere in the list the way Material 3's continuous behavior does.
 */
@Composable
fun rememberGalleryScrollBehavior(): ScrollBehavior = COUIScrollBehavior(
    state = rememberTopAppBarState(),
    expandOnContentScroll = false,
)

/**
 * The nested-scroll connection that tells [GalleryTopAppBar] where the list is.
 *
 * COUI's own connection accumulates the same displacement, but then zeroes it whenever the
 * scrollable reports an unconsumed downward delta — on the assumption that only a list already at
 * its top can leave a downward drag unconsumed. Our `FollowZoomWaterfallLayout` (a bare
 * `Modifier.scrollable`, not a `LazyLayout`) leaves part of every downward drag unconsumed, so the
 * offset was being zeroed mid-list and the large title slid back in from anywhere, which is the
 * one thing the snap mode exists to prevent.
 *
 * This connection keeps only the accumulation, and clears it on [atTop] instead — the caller's own
 * answer to "is the list at its top", which is the condition the title is actually meant to follow.
 *
 * Apply it to the scrollable: `Modifier.nestedScroll(connection)`.
 *
 * @param scrollBehavior the behavior handed to [GalleryTopAppBar].
 * @param atTop whether the list is at the top right now. Read after every scroll dispatch, i.e.
 *   with the delta already applied.
 */
@Composable
fun rememberGalleryTopBarScrollConnection(
    scrollBehavior: ScrollBehavior,
    atTop: () -> Boolean,
): NestedScrollConnection {
    val currentAtTop by rememberUpdatedState(atTop)
    return remember(scrollBehavior) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val state = scrollBehavior.state
                // `consumed.y` is the whole dispatch's displacement, and negative on the way down
                // the list, so the offset doubles as the bar's "content scrolled under me" depth.
                state.contentOffset = (state.contentOffset + consumed.y).coerceAtMost(0f)
                // Landing on the top is the one moment the accumulated depth must be forgotten:
                // the sum carries a few pixels of rounding per fling, and the title only comes back
                // when the offset reads as exactly zero.
                if (currentAtTop()) state.contentOffset = 0f
                return Offset.Zero
            }
        }
    }
}
