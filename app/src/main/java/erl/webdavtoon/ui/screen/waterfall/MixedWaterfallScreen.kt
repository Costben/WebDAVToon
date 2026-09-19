package erl.webdavtoon.ui.screen.waterfall

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.CircularProgressIndicator as M3CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold as M3Scaffold
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TopAppBar as M3TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import erl.webdavtoon.Folder
import erl.webdavtoon.R
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.ui.component.AnimatedSearchField
import erl.webdavtoon.ui.UiMode
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.menu.WindowIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class MixedWaterfallActions(
    val onBackClick: () -> Unit,
    val onItemClick: (MixedWaterfallItemUi) -> Unit,
    val onItemLongClick: (MixedWaterfallItemUi) -> Unit,
    val onRefresh: () -> Unit,
    val onColumnsChange: (Int) -> Unit,
    val onSearchQueryChange: (String) -> Unit = {},
    val onClearSearch: () -> Unit = {},
    val onSetSortOrder: (Int) -> Unit = {},
    val onToggleRotationLock: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenRecursiveBrowser: () -> Unit = {},
    val onToggleSelectAll: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onDeleteClick: () -> Unit,
    val onShareClick: () -> Unit,
    val onExitSelectionMode: () -> Unit,
    val onDimensionsResolved: (photoId: String, width: Int, height: Int) -> Unit,
    val onFolderVisibilityChanged: (folder: Folder, visible: Boolean) -> Unit = { _, _ -> },
    val onFolderPreviewsRequested: () -> Unit = {},
)

@Composable
fun MixedWaterfallScreen(
    uiState: MixedWaterfallUiState,
    actions: MixedWaterfallActions,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = uiState.isSelectionMode) {
        actions.onExitSelectionMode()
    }

    val zoomState = rememberFollowZoomState(
        currentColumns = uiState.columns,
        minColumns = 1,
        maxColumns = 5,
        onColumnsChanged = actions.onColumnsChange,
    )

    val effectiveColumns = if (zoomState.isZooming) {
        zoomState.previewColumns
    } else {
        uiState.columns.coerceIn(1, 5)
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (uiState.uiMode == UiMode.Miuix) MiuixTheme.colorScheme.background
                else MaterialTheme.colorScheme.background
            )
    ) {
        if (uiState.uiMode == UiMode.Miuix) {
            MixedWaterfallTopBarMiuix(
                uiState = uiState,
                actions = actions,
                scrollBehavior = topAppBarScrollBehavior,
            )
        } else {
            MixedWaterfallTopBarMaterial(uiState = uiState, actions = actions)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                uiState.loading && uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.uiMode == UiMode.Miuix) {
                            MiuixCircularProgressIndicator()
                        } else {
                            M3CircularProgressIndicator()
                        }
                    }
                }
                !uiState.loading && uiState.visibleItems.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.uiMode == UiMode.Miuix) {
                            MiuixText(
                                text = stringResource(R.string.no_photos_found),
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.disabledOnSurface,
                            )
                        } else {
                            M3Text(
                                text = stringResource(R.string.no_photos_found),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(effectiveColumns),
                        modifier = Modifier
                            .fillMaxSize()
                            .followZoom(zoomState)
                            .then(
                                if (uiState.uiMode == UiMode.Miuix) {
                                    Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                } else {
                                    Modifier
                                }
                            ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalItemSpacing = 8.dp,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.visibleItems, key = { it.key }) { item ->
                            if (uiState.uiMode == UiMode.Miuix) {
                                MediaCardMiuix(
                                    item = item,
                                    showFilename = uiState.showFilenames,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onClick = { actions.onItemClick(item) },
                                    onLongClick = { actions.onItemLongClick(item) },
                                    onDimensionsResolved = actions.onDimensionsResolved,
                                    onFolderVisibilityChanged = { folder, visible ->
                                        actions.onFolderVisibilityChanged(folder, visible)
                                        if (visible) actions.onFolderPreviewsRequested()
                                    },
                                )
                            } else {
                                MediaCardMaterial(
                                    item = item,
                                    showFilename = uiState.showFilenames,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onClick = { actions.onItemClick(item) },
                                    onLongClick = { actions.onItemLongClick(item) },
                                    onDimensionsResolved = actions.onDimensionsResolved,
                                    onFolderVisibilityChanged = { folder, visible ->
                                        actions.onFolderVisibilityChanged(folder, visible)
                                        if (visible) actions.onFolderPreviewsRequested()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (!uiState.isSelectionMode && !uiState.isFavorites) {
                FloatingActionButton(
                    onClick = actions.onOpenRecursiveBrowser,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(20.dp),
                ) {
                    M3Text("+")
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                if (uiState.uiMode == UiMode.Miuix) {
                    SelectionBottomBarMiuix(
                        selectedCount = uiState.selectedCount,
                        isAllSelected = uiState.isAllSelected,
                        onToggleSelectAll = actions.onToggleSelectAll,
                        onToggleFavorite = actions.onToggleFavorite,
                        onDelete = actions.onDeleteClick,
                        onShare = actions.onShareClick,
                        onDismiss = actions.onExitSelectionMode,
                    )
                } else {
                    SelectionBottomBarMaterial(
                        selectedCount = uiState.selectedCount,
                        isAllSelected = uiState.isAllSelected,
                        onToggleSelectAll = actions.onToggleSelectAll,
                        onToggleFavorite = actions.onToggleFavorite,
                        onDelete = actions.onDeleteClick,
                        onShare = actions.onShareClick,
                        onDismiss = actions.onExitSelectionMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun MixedWaterfallTopBarMiuix(
    uiState: MixedWaterfallUiState,
    actions: MixedWaterfallActions,
    scrollBehavior: ScrollBehavior? = null,
    modifier: Modifier = Modifier,
) {
    var searchExpanded by remember { mutableStateOf(uiState.isSearching) }

    val titleText = if (uiState.isSelectionMode) {
        stringResource(R.string.selected_count, uiState.selectedCount)
    } else {
        uiState.title.ifEmpty { stringResource(R.string.app_name) }
    }

    val onNavClick = {
        if (uiState.isSelectionMode) {
            actions.onExitSelectionMode()
        } else {
            actions.onBackClick()
        }
    }

    Column(modifier = modifier.statusBarsPadding()) {
        MiuixTopAppBar(
            title = titleText,
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                MiuixIconButton(onClick = onNavClick) {
                    if (uiState.isSelectionMode) {
                        MiuixIcon(
                            MiuixIcons.Light.Close,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    } else {
                        MiuixIcon(
                            MiuixIcons.Light.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                }
            },
            actions = {
                if (!uiState.isSelectionMode) {
                    MiuixIconButton(onClick = { searchExpanded = true }) {
                        MiuixIcon(
                            MiuixIcons.Light.Search,
                            contentDescription = stringResource(R.string.search_photos),
                        )
                    }
                    MixedWaterfallMiuixMenuButton(uiState = uiState, actions = actions)
                }
            },
        )
        AnimatedSearchField(
            value = uiState.searchKeyword,
            onValueChange = actions.onSearchQueryChange,
            placeholder = stringResource(R.string.search_photos),
            uiMode = UiMode.Miuix,
            visible = !uiState.isSelectionMode && searchExpanded,
            onClose = {
                actions.onClearSearch()
                searchExpanded = false
            },
        )
    }
}

/**
 * The inner-page "more" menu: sort order and grid columns expand a second
 * level, while refresh / settings / rotation lock are direct actions.
 */
@Composable
private fun MixedWaterfallMiuixMenuButton(
    uiState: MixedWaterfallUiState,
    actions: MixedWaterfallActions,
) {
    val sortItems = mixedSortItems().map { (order, label) ->
        DropdownItem(
            text = label,
            selected = uiState.sortOrder == order,
            onClick = { actions.onSetSortOrder(order) },
        )
    }
    val columnItems = (1..5).map { columns ->
        DropdownItem(
            text = stringResource(R.string.columns_suffix, columns),
            selected = uiState.columns == columns,
            onClick = { actions.onColumnsChange(columns) },
        )
    }

    WindowIconCascadingDropdownMenu(
        entries = listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = stringResource(R.string.sort_order),
                        icon = { modifier -> MiuixIcon(MiuixIcons.Light.Sort, null, modifier) },
                        children = sortItems,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.grid_columns),
                        icon = { modifier -> MiuixIcon(MiuixIcons.Light.GridView, null, modifier) },
                        children = columnItems,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.refresh),
                        icon = { modifier -> MiuixIcon(MiuixIcons.Light.Refresh, null, modifier) },
                        onClick = actions.onRefresh,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.settings),
                        icon = { modifier -> MiuixIcon(MiuixIcons.Light.Settings, null, modifier) },
                        onClick = actions.onOpenSettings,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.rotation_lock),
                        summary = if (uiState.rotationLocked) "On" else "Off",
                        icon = { modifier -> MiuixIcon(MiuixIcons.Light.Lock, null, modifier) },
                        onClick = actions.onToggleRotationLock,
                    ),
                ),
            ),
        ),
    ) {
        MiuixIcon(MiuixIcons.Light.More, contentDescription = stringResource(R.string.more))
    }
}

@Composable
private fun mixedSortItems(): List<Pair<Int, String>> = listOf(
    SettingsManager.SORT_NAME_ASC to stringResource(R.string.sort_name_asc),
    SettingsManager.SORT_NAME_DESC to stringResource(R.string.sort_name_desc),
    SettingsManager.SORT_DATE_DESC to stringResource(R.string.sort_date_desc),
    SettingsManager.SORT_DATE_ASC to stringResource(R.string.sort_date_asc),
    SettingsManager.SORT_RANDOM_FOLDERS to stringResource(R.string.sort_random_folders),
    SettingsManager.SORT_RANDOM_PHOTOS to stringResource(R.string.sort_random_photos),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MixedWaterfallTopBarMaterial(
    uiState: MixedWaterfallUiState,
    actions: MixedWaterfallActions,
    modifier: Modifier = Modifier,
) {
    var searchExpanded by remember { mutableStateOf(uiState.isSearching) }
    var menuExpanded by remember { mutableStateOf(false) }

    val titleText = if (uiState.isSelectionMode) {
        stringResource(R.string.selected_count, uiState.selectedCount)
    } else {
        uiState.title.ifEmpty { stringResource(R.string.app_name) }
    }

    val onNavClick = {
        if (uiState.isSelectionMode) {
            actions.onExitSelectionMode()
        } else {
            actions.onBackClick()
        }
    }

    val colors = if (uiState.isSelectionMode) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    } else {
        TopAppBarDefaults.topAppBarColors()
    }

    Column(modifier = modifier.statusBarsPadding()) {
        M3TopAppBar(
            title = {
                M3Text(
                    text = titleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                M3IconButton(onClick = onNavClick) {
                    if (uiState.isSelectionMode) {
                        M3Icon(
                            MiuixIcons.Light.Close,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    } else {
                        M3Icon(
                            MiuixIcons.Light.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                }
            },
            actions = {
                if (!uiState.isSelectionMode) {
                    M3IconButton(onClick = { searchExpanded = true }) {
                        M3Icon(
                            MiuixIcons.Light.Search,
                            contentDescription = stringResource(R.string.search_photos),
                        )
                    }

                    Box {
                        M3IconButton(onClick = { menuExpanded = true }) {
                            M3Icon(
                                MiuixIcons.Light.More,
                                contentDescription = stringResource(R.string.more),
                            )
                        }
                        MixedWaterfallMaterialMenu(menuExpanded, uiState, actions) { menuExpanded = false }
                    }
                }
            },
            colors = colors,
        )
        AnimatedSearchField(
            value = uiState.searchKeyword,
            onValueChange = actions.onSearchQueryChange,
            placeholder = stringResource(R.string.search_photos),
            uiMode = UiMode.Material,
            visible = !uiState.isSelectionMode && searchExpanded,
            onClose = {
                actions.onClearSearch()
                searchExpanded = false
            },
        )
    }
}

/** Material drill-down menu for the inner page (sort / columns / refresh / settings / rotation). */
private enum class MixedMaterialMenuLevel { Root, Sort, Columns }

@Composable
private fun MixedWaterfallMaterialMenu(
    expanded: Boolean,
    uiState: MixedWaterfallUiState,
    actions: MixedWaterfallActions,
    onDismiss: () -> Unit,
) {
    val selectedTint = MaterialTheme.colorScheme.primary
    val idleTint = MaterialTheme.colorScheme.onSurfaceVariant
    var level by remember { mutableStateOf(MixedMaterialMenuLevel.Root) }

    if (!expanded && level != MixedMaterialMenuLevel.Root) {
        level = MixedMaterialMenuLevel.Root
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (level) {
            MixedMaterialMenuLevel.Root -> {
                DropdownMenuItem(
                    text = { M3Text(stringResource(R.string.sort_order)) },
                    leadingIcon = { M3Icon(MiuixIcons.Light.Sort, null, tint = idleTint) },
                    trailingIcon = { M3Icon(MiuixIcons.Light.ChevronForward, null, tint = idleTint) },
                    onClick = { level = MixedMaterialMenuLevel.Sort },
                )
                DropdownMenuItem(
                    text = { M3Text(stringResource(R.string.grid_columns)) },
                    leadingIcon = { M3Icon(MiuixIcons.Light.GridView, null, tint = idleTint) },
                    trailingIcon = { M3Icon(MiuixIcons.Light.ChevronForward, null, tint = idleTint) },
                    onClick = { level = MixedMaterialMenuLevel.Columns },
                )
                DropdownMenuItem(
                    text = { M3Text(stringResource(R.string.refresh)) },
                    leadingIcon = { M3Icon(MiuixIcons.Light.Refresh, null, tint = idleTint) },
                    onClick = { actions.onRefresh(); onDismiss() },
                )
                DropdownMenuItem(
                    text = { M3Text(stringResource(R.string.settings)) },
                    leadingIcon = { M3Icon(MiuixIcons.Light.Settings, null, tint = idleTint) },
                    onClick = { actions.onOpenSettings(); onDismiss() },
                )
                DropdownMenuItem(
                    text = { M3Text(stringResource(R.string.rotation_lock)) },
                    leadingIcon = {
                        M3Icon(
                            MiuixIcons.Light.Lock,
                            null,
                            tint = if (uiState.rotationLocked) selectedTint else idleTint,
                        )
                    },
                    trailingIcon = { M3Text(if (uiState.rotationLocked) "On" else "Off") },
                    onClick = { actions.onToggleRotationLock(); onDismiss() },
                )
            }

            MixedMaterialMenuLevel.Sort -> {
                DropdownMenuItem(
                    text = { M3Text(stringResource(R.string.sort_order), color = selectedTint) },
                    leadingIcon = { M3Icon(MiuixIcons.Light.ChevronBackward, null, tint = selectedTint) },
                    onClick = { level = MixedMaterialMenuLevel.Root },
                )
                mixedSortItems().forEach { (order, label) ->
                    val selected = uiState.sortOrder == order
                    DropdownMenuItem(
                        text = { M3Text(label, color = if (selected) selectedTint else Color.Unspecified) },
                        onClick = { actions.onSetSortOrder(order); onDismiss() },
                    )
                }
            }

            MixedMaterialMenuLevel.Columns -> {
                DropdownMenuItem(
                    text = { M3Text(stringResource(R.string.grid_columns), color = selectedTint) },
                    leadingIcon = { M3Icon(MiuixIcons.Light.ChevronBackward, null, tint = selectedTint) },
                    onClick = { level = MixedMaterialMenuLevel.Root },
                )
                (1..5).forEach { columns ->
                    val selected = uiState.columns == columns
                    val label = stringResource(R.string.columns_suffix, columns)
                    DropdownMenuItem(
                        text = { M3Text(label, color = if (selected) selectedTint else Color.Unspecified) },
                        onClick = { actions.onColumnsChange(columns); onDismiss() },
                    )
                }
            }
        }
    }
}
