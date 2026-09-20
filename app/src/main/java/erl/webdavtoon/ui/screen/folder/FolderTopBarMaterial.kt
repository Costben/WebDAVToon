package erl.webdavtoon.ui.screen.folder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import erl.webdavtoon.ui.UiMode
import erl.webdavtoon.ui.component.AnimatedSearchField
import erl.webdavtoon.ui.component.FunnelIcon
import erl.webdavtoon.R
import erl.webdavtoon.SettingsManager
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
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

    Column(modifier = modifier.statusBarsPadding()) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = if (uiState.isSelectionMode) stringResource(R.string.selected_count, uiState.selectedCount)
                    else stringResource(R.string.app_name),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                    IconButton(onClick = {
                        searchExpanded = true
                        actions.onToggleSearch()
                    }) {
                        Icon(FunnelIcon, contentDescription = stringResource(R.string.filter))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(MiuixIcons.Light.More, contentDescription = stringResource(R.string.more))
                        }
                        FolderMaterialMenu(menuExpanded, uiState, actions) { menuExpanded = false }
                    }
                }
            },
            colors = colors,
        )
        AnimatedSearchField(
            value = uiState.searchKeyword,
            onValueChange = actions.onSearchQueryChange,
            placeholder = stringResource(R.string.search_folders),
            uiMode = UiMode.Material,
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
 * Two-level menu. Material3 has no native cascading popup (a nested
 * `DropdownMenu` overlaps its parent), so the second level is a drill-down:
 * the top level lists the four groups, and "sort order" / "grid columns"
 * replace the menu body with their options plus a back row.
 */
private enum class MaterialMenuLevel { Root, Sort, Columns }

@Composable
private fun FolderMaterialMenu(
    expanded: Boolean,
    uiState: FolderUiState,
    actions: FolderTopBarActions,
    onDismiss: () -> Unit,
) {
    val selectedTint = MaterialTheme.colorScheme.primary
    val idleTint = MaterialTheme.colorScheme.onSurfaceVariant
    var level by remember { mutableStateOf(MaterialMenuLevel.Root) }

    // Reset to the top level whenever the menu is reopened.
    if (!expanded && level != MaterialMenuLevel.Root) {
        level = MaterialMenuLevel.Root
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (level) {
            MaterialMenuLevel.Root -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_order)) },
                    leadingIcon = { Icon(MiuixIcons.Light.Sort, null, tint = idleTint) },
                    trailingIcon = { Icon(MiuixIcons.Light.ChevronForward, null, tint = idleTint) },
                    onClick = { level = MaterialMenuLevel.Sort },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.grid_columns)) },
                    leadingIcon = { Icon(MiuixIcons.Light.GridView, null, tint = idleTint) },
                    trailingIcon = { Icon(MiuixIcons.Light.ChevronForward, null, tint = idleTint) },
                    onClick = { level = MaterialMenuLevel.Columns },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.refresh)) },
                    leadingIcon = { Icon(MiuixIcons.Light.Refresh, null, tint = idleTint) },
                    onClick = { actions.onRefresh(); onDismiss() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings)) },
                    leadingIcon = { Icon(MiuixIcons.Light.Settings, null, tint = idleTint) },
                    onClick = { actions.onOpenSettings(); onDismiss() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rotation_lock)) },
                    leadingIcon = {
                        Icon(
                            MiuixIcons.Light.Lock,
                            null,
                            tint = if (uiState.rotationLocked) selectedTint else idleTint,
                        )
                    },
                    trailingIcon = { Text(if (uiState.rotationLocked) "On" else "Off") },
                    onClick = { actions.onToggleRotationLock(); onDismiss() },
                )
            }

            MaterialMenuLevel.Sort -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_order), color = selectedTint) },
                    leadingIcon = { Icon(MiuixIcons.Light.ChevronBackward, null, tint = selectedTint) },
                    onClick = { level = MaterialMenuLevel.Root },
                )
                materialSortItems().forEach { (order, label) ->
                    val selected = uiState.sortOrder == order
                    DropdownMenuItem(
                        text = { Text(label, color = if (selected) selectedTint else Color.Unspecified) },
                        onClick = { actions.onSetSortOrder(order); onDismiss() },
                    )
                }
            }

            MaterialMenuLevel.Columns -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.grid_columns), color = selectedTint) },
                    leadingIcon = { Icon(MiuixIcons.Light.ChevronBackward, null, tint = selectedTint) },
                    onClick = { level = MaterialMenuLevel.Root },
                )
                (1..4).forEach { columns ->
                    val selected = uiState.gridColumns == columns
                    val label = stringResource(R.string.columns_suffix, columns)
                    DropdownMenuItem(
                        text = { Text(label, color = if (selected) selectedTint else Color.Unspecified) },
                        onClick = { actions.onSetGridColumns(columns); onDismiss() },
                    )
                }
            }
        }
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

