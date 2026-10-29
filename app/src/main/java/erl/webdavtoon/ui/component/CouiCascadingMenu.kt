// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup

/**
 * COUI's own cascading dropdown, for the overflow entries that carry a second level.
 *
 * COUI 1.2 ships the whole cascade in [OverlayCascadingListPopup]: the primary panel shrinks
 * toward its top-right corner and dims while the secondary card grows out of the tapped row's
 * band, on the `SmallScreenAnimationController` springs — the motion ColorOS Gallery runs when
 * "sort order" is expanded.
 *
 * Building the drill-down by hand (swap the row list inside one `ListPopupColumn`) can only ever
 * hard-cut: the rows must stay the column's *direct* children for its gesture host to register
 * them, so no layout node can sit between the two levels and carry a transition.
 *
 * The popup anchors to the nearest enclosing `Box`, exactly as [OverlayListPopup] does, so a
 * caller places this next to its trigger inside one `Box`.
 *
 * @param show whether the popup is shown.
 * @param entries grouped entries; items with non-empty [DropdownItem.children] open the second level.
 * @param onDismissRequest invoked when the popup wants to be dismissed.
 */
@Composable
fun CouiCascadingMenuPopup(
    show: Boolean,
    entries: List<DropdownEntry>,
    onDismissRequest: () -> Unit,
) {
    OverlayCascadingListPopup(
        show = show,
        entries = entries,
        onDismissRequest = onDismissRequest,
        alignment = PopupPositionProvider.Align.End,
    )
}

/**
 * Single-level COUI dropdown, for callers that own their own trigger (for example a `TextButton`).
 */
@Composable
fun CouiFlatMenu(
    expanded: Boolean,
    entries: List<DropdownEntry>,
    onDismissRequest: () -> Unit,
) {
    OverlayListPopup(
        show = expanded,
        alignment = PopupPositionProvider.Align.End,
        onDismissRequest = onDismissRequest,
    ) {
        Box(modifier = Modifier.consumeTapUp()) {
            ListPopupColumn {
                PrimaryRows(entries) { item, children ->
                    if (children == null) {
                        item.onClick?.invoke()
                        onDismissRequest()
                    }
                }
            }
        }
    }
}

/**
 * Swallows the UP of a tap inside the popup so the enclosing dismiss gesture does not fire.
 * A drag (scroll) is left untouched, so `ListPopupColumn` keeps working when the list scrolls.
 */
private fun Modifier.consumeTapUp(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitFirstDown(requireUnconsumed = false)
            waitForUpOrCancellation()?.consume()
        }
    }
}

/**
 * The group every row of a flat menu declares.
 *
 * `ListPopupColumn` reserves a 12dp divider band between any two adjacent rows whose group key
 * differs, and a `null` key never equals another `null` — so leaving it unset drew a hairline
 * between *every* pair of rows. One shared key keeps the whole menu a single group, which is what
 * ColorOS shows: a flat menu has no separators inside it.
 */
private val FlatMenuGroup = Any()

/** Top level: every item of every entry, in order. */
@Composable
private fun PrimaryRows(
    entries: List<DropdownEntry>,
    onItemSelected: (DropdownItem, List<DropdownItem>?) -> Unit,
) {
    val rowCount = entries.sumOf { it.items.size }
    var index = 0
    entries.forEach { entry ->
        entry.items.forEach { item ->
            key(item) {
                val children = item.children?.takeIf { it.isNotEmpty() }
                DropdownImpl(
                    item = item,
                    optionSize = rowCount,
                    isSelected = item.selected,
                    index = index,
                    enabled = item.enabled,
                    hasSubmenu = children != null,
                    isFirst = index == 0,
                    isLast = index == rowCount - 1,
                    groupKey = FlatMenuGroup,
                    onSelectedIndexChange = { onItemSelected(item, children) },
                )
            }
            index++
        }
    }
}
