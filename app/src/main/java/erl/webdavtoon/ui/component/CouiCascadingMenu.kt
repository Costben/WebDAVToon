package erl.webdavtoon.ui.component

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup

/**
 * COUI dropdown menu with a second level, built only from public COUI building blocks.
 *
 * COUI 1.1.0's own `WindowIconCascadingDropdownMenu` / `OverlayIconCascadingDropdownMenu` are
 * unusable: `CascadingPrimaryRow` and `CascadingSecondaryColumn` wrap `DropdownImpl` in an extra
 * `Box`, so the `Modifier.popupListItem` parent data never reaches `ListPopupColumn`'s measure
 * policy. Rows are then never registered with `PopupListGestureHost`, and every row click (and
 * drag-select) is silently swallowed by the surrounding dismiss gesture.
 *
 * This component keeps `DropdownImpl` a *direct* child of `ListPopupColumn` (no layout wrapper in
 * between) so the row registration works, and owns the expansion state itself so the second level
 * can be a drill-down inside the same popup.
 *
 * @param entries Grouped entries. Items with non-empty [DropdownItem.children] open the second
 *   level; every other item invokes its own `onClick`.
 * @param modifier Modifier applied to the anchor box.
 * @param content The anchor content, hosted in a COUI [IconButton].
 */
@Composable
fun CouiCascadingMenu(
    entries: List<DropdownEntry>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var expandedItem by remember { mutableStateOf<DropdownItem?>(null) }

    Box(modifier = modifier) {
        IconButton(
            onClick = {
                expandedItem = null
                show = true
            },
            holdDownState = show,
            content = content,
        )

        OverlayListPopup(
            show = show,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { show = false },
            onDismissFinished = { expandedItem = null },
        ) {
            // `OverlayListPopup` dismisses on ANY tap inside the popup surface, because the COUI
            // popup rows deliberately do not consume pointer events (the row gesture host observes
            // in the Initial pass and leaves the event unconsumed). That is fine for a flat menu,
            // but it would close this menu before a second level could ever show. Consuming the UP
            // here — strictly above `ListPopupColumn`, never between it and `DropdownImpl`, which
            // would break the row parent data again — keeps the popup open for row taps while
            // still letting an outside tap and the back gesture dismiss it.
            Box(modifier = Modifier.consumeTapUp()) {
                ListPopupColumn {
                    val expanded = expandedItem
                    if (expanded == null) {
                        PrimaryRows(entries) { item, children ->
                            if (children != null) {
                                expandedItem = item
                            } else {
                                item.onClick?.invoke()
                                show = false
                            }
                        }
                    } else {
                        SecondaryRows(
                            parent = expanded,
                            onBack = { expandedItem = null },
                            onLeafClick = { item ->
                                item.onClick?.invoke()
                                show = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single-level COUI dropdown, for callers that own their own trigger (for example a
 * `TextButton`). Shares the row registration and dismiss handling of [CouiCascadingMenu].
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

/** Top level: every item of every entry, in order, separated by group dividers. */
@Composable
private fun PrimaryRows(
    entries: List<DropdownEntry>,
    onItemSelected: (DropdownItem, List<DropdownItem>?) -> Unit,
) {
    val rowCount = entries.sumOf { it.items.size }
    var index = 0
    entries.forEachIndexed { entryIndex, entry ->
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
                    onSelectedIndexChange = { onItemSelected(item, children) },
                )
            }
            index++
        }
        if (entryIndex != entries.lastIndex) {
            key("groupDivider", entry) {
                HorizontalDivider()
            }
        }
    }
}

/**
 * Second level: the parent row doubles as the header (tapping it returns to the top level),
 * followed by the children. Mirrors COUI's cloned submenu header.
 */
@Composable
private fun SecondaryRows(
    parent: DropdownItem,
    onBack: () -> Unit,
    onLeafClick: (DropdownItem) -> Unit,
) {
    val children = parent.children.orEmpty()
    val rowCount = children.size + 1

    key("header", parent) {
        DropdownImpl(
            item = parent,
            optionSize = rowCount,
            isSelected = false,
            index = 0,
            enabled = true,
            hasSubmenu = true,
            isFirst = true,
            isLast = false,
            onSelectedIndexChange = { onBack() },
        )
    }
    key("headerDivider", parent) {
        HorizontalDivider()
    }
    children.forEachIndexed { childIndex, child ->
        key(child) {
            DropdownImpl(
                item = child,
                optionSize = rowCount,
                isSelected = child.selected,
                index = childIndex + 1,
                enabled = child.enabled,
                hasSubmenu = false,
                isFirst = false,
                isLast = childIndex == children.lastIndex,
                onSelectedIndexChange = { onLeafClick(child) },
            )
        }
    }
}
