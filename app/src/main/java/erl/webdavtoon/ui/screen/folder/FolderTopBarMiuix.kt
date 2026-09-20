package erl.webdavtoon.ui.screen.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import erl.webdavtoon.ui.UiMode
import erl.webdavtoon.ui.component.AnimatedSearchField
import erl.webdavtoon.ui.component.FunnelIcon
import erl.webdavtoon.R
import erl.webdavtoon.SettingsManager
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.menu.WindowIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

    Column(modifier = modifier.background(MiuixTheme.colorScheme.background).statusBarsPadding()) {
        if (uiState.isSelectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.onClearSelection) {
                    Icon(MiuixIcons.Light.Close, contentDescription = stringResource(R.string.cancel))
                }
                Text(
                    text = stringResource(R.string.selected_count, uiState.selectedCount),
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.title2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
                IconButton(onClick = actions.onSelectAll) {
                    Icon(MiuixIcons.Light.SelectAll, contentDescription = "Select all")
                }
                IconButton(onClick = actions.onDeleteSelected) {
                    Icon(MiuixIcons.Light.Delete, contentDescription = stringResource(R.string.delete), tint = Color.Red)
                }
            }
        } else {
            MiuixTopAppBar(
                title = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = actions.onOpenDrawer) {
                        Icon(MiuixIcons.Light.Sidebar, contentDescription = stringResource(R.string.navigation_drawer_open))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchExpanded = true
                        actions.onToggleSearch()
                    }) {
                        Icon(FunnelIcon, contentDescription = stringResource(R.string.filter))
                    }
                    FolderMiuixMenuButton(uiState = uiState, actions = actions)
                },
            )
        }
        AnimatedSearchField(
            value = uiState.searchKeyword,
            onValueChange = actions.onSearchQueryChange,
            placeholder = stringResource(R.string.search_folders),
            uiMode = UiMode.Miuix,
            visible = !uiState.isSelectionMode && searchExpanded,
            onClose = {
                actions.onClearSearch()
                actions.onSearchQueryChange("")
                searchExpanded = false
            },
        )
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

    WindowIconCascadingDropdownMenu(
        entries = listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = stringResource(R.string.sort_order),
                        icon = { modifier -> Icon(MiuixIcons.Light.Sort, null, modifier) },
                        children = sortItems,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.grid_columns),
                        icon = { modifier -> Icon(MiuixIcons.Light.GridView, null, modifier) },
                        children = columnItems,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.refresh),
                        icon = { modifier -> Icon(MiuixIcons.Light.Refresh, null, modifier) },
                        onClick = actions.onRefresh,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.settings),
                        icon = { modifier -> Icon(MiuixIcons.Light.Settings, null, modifier) },
                        onClick = actions.onOpenSettings,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.rotation_lock),
                        summary = if (uiState.rotationLocked) "On" else "Off",
                        icon = { modifier -> Icon(MiuixIcons.Light.Lock, null, modifier) },
                        onClick = actions.onToggleRotationLock,
                    ),
                ),
            ),
        ),
    ) {
        Icon(MiuixIcons.Light.More, contentDescription = stringResource(R.string.more))
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

