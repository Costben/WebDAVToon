package erl.webdavtoon.ui.screen.folder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import erl.webdavtoon.R
import erl.webdavtoon.SettingsManager
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.icon.extended.Sort

/** Material 3 Expressive top bar for the folder screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderTopBarMaterial(
    uiState: FolderUiState,
    actions: FolderTopBarActions,
    modifier: Modifier = Modifier,
) {
    var searchExpanded by remember { mutableStateOf(uiState.isSearching) }
    var menuExpanded by remember { mutableStateOf(false) }
    val colors = if (uiState.isSelectionMode) {
        TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    } else {
        TopAppBarDefaults.topAppBarColors()
    }

    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Text(
                    if (uiState.isSelectionMode) stringResource(R.string.selected_count, uiState.selectedCount)
                    else stringResource(R.string.app_name),
                )
            },
            navigationIcon = {
                IconButton(onClick = if (uiState.isSelectionMode) actions.onClearSelection else actions.onOpenDrawer) {
                    Icon(
                        if (uiState.isSelectionMode) MiuixIcons.Light.Close else MiuixIcons.Light.Sidebar,
                        contentDescription = stringResource(if (uiState.isSelectionMode) R.string.cancel else R.string.navigation_drawer_open),
                    )
                }
            },
            actions = {
                if (uiState.isSelectionMode) {
                    IconButton(onClick = actions.onSelectAll) {
                        Icon(MiuixIcons.Light.SelectAll, contentDescription = "Select all")
                    }
                    IconButton(onClick = actions.onDeleteSelected) {
                        Icon(MiuixIcons.Light.Delete, contentDescription = stringResource(R.string.delete), tint = Color.Red)
                    }
                } else {
                    MaterialRefreshStatus(uiState.refreshStatus, actions.onRefresh)
                    IconButton(onClick = {
                        searchExpanded = true
                        actions.onToggleSearch()
                    }) {
                        Icon(MiuixIcons.Light.Search, contentDescription = stringResource(R.string.search_folders))
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(MiuixIcons.Light.More, contentDescription = stringResource(R.string.more))
                    }
                    FolderMaterialMenu(menuExpanded, uiState, actions) { menuExpanded = false }
                }
            },
            colors = colors,
        )
        if (!uiState.isSelectionMode && searchExpanded) {
            OutlinedTextField(
                value = uiState.searchKeyword,
                onValueChange = actions.onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                label = { Text(stringResource(R.string.search_folders)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        actions.onClearSearch()
                        actions.onSearchQueryChange("")
                        searchExpanded = false
                    }) {
                        Icon(MiuixIcons.Light.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun MaterialRefreshStatus(status: RefreshStatus, onRefresh: () -> Unit) {
    when (status) {
        RefreshStatus.Refreshing -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            CircularProgressIndicator(modifier = Modifier.padding(6.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.refresh_status_refreshing), style = MaterialTheme.typography.labelSmall)
        }
        RefreshStatus.Completed -> TextButton(onClick = onRefresh) {
            Text("✓ ${stringResource(R.string.refresh_status_completed)}")
        }
        RefreshStatus.Idle -> IconButton(onClick = onRefresh) {
            Icon(MiuixIcons.Light.Refresh, contentDescription = stringResource(R.string.refresh_status_refreshing))
        }
    }
}

@Composable
private fun FolderMaterialMenu(
    expanded: Boolean,
    uiState: FolderUiState,
    actions: FolderTopBarActions,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.sort_order)) }, enabled = false, onClick = {})
        materialSortItems().forEach { (order, label) ->
            DropdownMenuItem(
                text = { Text(if (uiState.sortOrder == order) "✓ $label" else label) },
                leadingIcon = if (uiState.sortOrder == order) ({ Icon(MiuixIcons.Light.Sort, null) }) else null,
                onClick = { actions.onSetSortOrder(order); onDismiss() },
            )
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.grid_columns)) }, enabled = false, onClick = {})
        (1..4).forEach { columns ->
            val label = stringResource(R.string.columns_suffix, columns)
            DropdownMenuItem(
                text = { Text(if (uiState.gridColumns == columns) "✓ $label" else label) },
                leadingIcon = if (uiState.gridColumns == columns) ({ Icon(MiuixIcons.Light.GridView, null) }) else null,
                onClick = { actions.onSetGridColumns(columns); onDismiss() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rotation_lock)) },
            trailingIcon = { Text(if (uiState.rotationLocked) "On" else "Off") },
            onClick = { actions.onToggleRotationLock(); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.settings)) },
            leadingIcon = { Icon(MiuixIcons.Light.Settings, null) },
            onClick = { actions.onOpenSettings(); onDismiss() },
        )
    }
}

@Composable
private fun materialSortItems(): List<Pair<Int, String>> = listOf(
    SettingsManager.SORT_NAME_ASC to stringResource(R.string.sort_name_asc),
    SettingsManager.SORT_NAME_DESC to stringResource(R.string.sort_name_desc),
    SettingsManager.SORT_DATE_DESC to stringResource(R.string.sort_date_desc),
    SettingsManager.SORT_DATE_ASC to stringResource(R.string.sort_date_asc),
    SettingsManager.SORT_RANDOM_FOLDERS to stringResource(R.string.sort_random_folders),
)

