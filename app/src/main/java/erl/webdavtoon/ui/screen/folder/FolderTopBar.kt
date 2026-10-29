// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.folder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import erl.webdavtoon.ui.component.AnimatedSearchField
import erl.webdavtoon.ui.component.CouiCascadingMenuPopup
import erl.webdavtoon.ui.component.GalleryTopAppBar
import erl.webdavtoon.ui.component.HighlightedTopBarAction
import erl.webdavtoon.ui.component.TopBarIcons
import erl.webdavtoon.ui.component.rememberStrongHaptic
import erl.webdavtoon.ui.component.rememberTapHaptic
import erl.webdavtoon.R
import erl.webdavtoon.SettingsManager
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text as MiuixText
import io.github.suqi8.coui.kmp.basic.couiGlassHeader
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.SelectAll
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.icon.extended.Sort
import io.github.suqi8.coui.kmp.theme.COUITheme

/** Callbacks shared by the Miuix and Material folder top bars. */
data class FolderTopBarActions(
    val onOpenDrawer: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onToggleSearch: () -> Unit,
    val onSetSortOrder: (Int) -> Unit,
    val onSetGridColumns: (Int) -> Unit,
    val onToggleRotationLock: () -> Unit,
    val onRefresh: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onClearSelection: () -> Unit,
    val onSelectAll: () -> Unit,
    val onDeleteSelected: () -> Unit,
)

/** HyperOS-inspired top bar for the folder screen. */
@Composable
fun FolderTopBarMiuix(
    uiState: FolderUiState,
    actions: FolderTopBarActions,
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null,
) {
    var searchExpanded by remember { mutableStateOf(uiState.isSearching) }
    val tapHaptic = rememberTapHaptic()
    val strongHaptic = rememberStrongHaptic()

    Column(modifier = modifier) {
        if (uiState.isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HighlightedTopBarAction(
                    icon = COUIIcons.Light.Close,
                    contentDescription = stringResource(R.string.cancel),
                    onClick = {
                        tapHaptic()
                        actions.onClearSelection()
                    },
                )
                MiuixText(
                    text = stringResource(R.string.selected_count, uiState.selectedCount),
                    modifier = Modifier.weight(1f),
                    style = COUITheme.textStyles.title2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
                HighlightedTopBarAction(
                    icon = COUIIcons.Light.SelectAll,
                    contentDescription = "Select all",
                    onClick = {
                        tapHaptic()
                        actions.onSelectAll()
                    },
                )
                HighlightedTopBarAction(
                    icon = COUIIcons.Light.Delete,
                    contentDescription = stringResource(R.string.delete),
                    onClick = {
                        strongHaptic()
                        actions.onDeleteSelected()
                    },
                    iconTint = COUITheme.colorScheme.error,
                )
            }
        } else {
            GalleryTopAppBar(
                title = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                actions = {
                    // COUI keeps the top-left slot for back/cancel only; a root page leaves it empty
                    // and puts every action in the top-right group (see the COUI example app's
                    // Home page). The server-panel switcher is an action, so it lives here.
                    HighlightedTopBarAction(
                        icon = TopBarIcons.Panel,
                        contentDescription = stringResource(R.string.navigation_drawer_open),
                        onClick = actions.onOpenDrawer,
                    )
                    HighlightedTopBarAction(
                        icon = TopBarIcons.Filter,
                        contentDescription = stringResource(R.string.filter),
                        onClick = {
                            searchExpanded = true
                            actions.onToggleSearch()
                        },
                    )
                    FolderMiuixMenuButton(uiState = uiState, actions = actions)
                },
            )
        }
        // The expanded search band is part of the bar, so it wears the same glass instead of
        // letting the list show through the gap around the field.
        Column(modifier = Modifier.couiGlassHeader(LocalBackdrop.current, COUITheme.colorScheme.surface)) {
            AnimatedSearchField(
                value = uiState.searchKeyword,
                onValueChange = actions.onSearchQueryChange,
                placeholder = stringResource(R.string.search_folders),
                visible = !uiState.isSelectionMode && searchExpanded,
                onClose = {
                    actions.onClearSearch()
                    actions.onSearchQueryChange("")
                    searchExpanded = false
                },
            )
        }
    }
}

/**
 * The "more" action renders Miuix's native cascading dropdown: the top level
 * holds the four groups, and "sort order" / "grid columns" expand a second
 * level of options. Leaf selection state is drawn by the component itself.
 */
@Composable
private fun FolderMiuixMenuButton(
    uiState: FolderUiState,
    actions: FolderTopBarActions,
) {
    var showMenu by remember { mutableStateOf(false) }
    val sortItems = folderSortItems().map { (order, label) ->
        DropdownItem(
            text = label,
            selected = uiState.sortOrder == order,
            onClick = { actions.onSetSortOrder(order) },
        )
    }
    val columnItems = (1..4).map { columns ->
        DropdownItem(
            text = stringResource(R.string.columns_suffix, columns),
            selected = uiState.gridColumns == columns,
            onClick = { actions.onSetGridColumns(columns) },
        )
    }

    Box {
        HighlightedTopBarAction(
            icon = TopBarIcons.More,
            contentDescription = stringResource(R.string.more),
            onClick = { showMenu = true },
        )
        CouiCascadingMenuPopup(
            show = showMenu,
            entries = listOf(
                DropdownEntry(
                    items = listOf(
                        DropdownItem(
                            text = stringResource(R.string.sort_order),
                            icon = { modifier -> Icon(COUIIcons.Light.Sort, null, modifier) },
                            children = sortItems,
                        ),
                        DropdownItem(
                            text = stringResource(R.string.grid_columns),
                            icon = { modifier -> Icon(COUIIcons.Light.GridView, null, modifier) },
                            children = columnItems,
                        ),
                        DropdownItem(
                            text = stringResource(R.string.refresh),
                            icon = { modifier -> Icon(COUIIcons.Light.Refresh, null, modifier) },
                            onClick = actions.onRefresh,
                        ),
                        DropdownItem(
                            text = stringResource(R.string.settings),
                            icon = { modifier -> Icon(COUIIcons.Light.Settings, null, modifier) },
                            onClick = actions.onOpenSettings,
                        ),
                        DropdownItem(
                            text = stringResource(R.string.rotation_lock),
                            summary = if (uiState.rotationLocked) "On" else "Off",
                            icon = { modifier -> Icon(COUIIcons.Light.Lock, null, modifier) },
                            onClick = actions.onToggleRotationLock,
                        ),
                    ),
                ),
            ),
            onDismissRequest = { showMenu = false },
        )
    }
}

@Composable
private fun folderSortItems(): List<Pair<Int, String>> = listOf(
    SettingsManager.SORT_NAME_ASC to stringResource(R.string.sort_name_asc),
    SettingsManager.SORT_NAME_DESC to stringResource(R.string.sort_name_desc),
    SettingsManager.SORT_DATE_DESC to stringResource(R.string.sort_date_desc),
    SettingsManager.SORT_DATE_ASC to stringResource(R.string.sort_date_asc),
    SettingsManager.SORT_RANDOM_FOLDERS to stringResource(R.string.sort_random_folders),
)

