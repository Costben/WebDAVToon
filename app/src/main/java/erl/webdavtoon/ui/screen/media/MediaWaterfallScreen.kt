package erl.webdavtoon.ui.screen.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator as M3CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TopAppBar as M3TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import erl.webdavtoon.R
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.ui.component.AnimatedSearchField
import erl.webdavtoon.ui.component.FunnelIcon
import erl.webdavtoon.ui.UiMode
import erl.webdavtoon.ui.component.AppAdaptiveNavigationScaffold
import erl.webdavtoon.ui.component.NavigationDrawerActions
import erl.webdavtoon.ui.screen.waterfall.MediaCardMaterial
import erl.webdavtoon.ui.screen.waterfall.MediaCardMiuix
import erl.webdavtoon.ui.screen.waterfall.MediaCardExtraHeight
import erl.webdavtoon.ui.screen.waterfall.FollowZoomWaterfallLayout
import erl.webdavtoon.ui.screen.waterfall.SelectionBottomBarMaterial
import erl.webdavtoon.ui.screen.waterfall.SelectionBottomBarMiuix
import erl.webdavtoon.ui.screen.waterfall.rememberFollowZoomGridState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.menu.WindowIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class MediaWaterfallActions(
    val onBackClick: () -> Unit,
    val onItemClick: (MediaWaterfallItemUi) -> Unit,
    val onItemLongClick: (MediaWaterfallItemUi) -> Unit,
    val onRefresh: () -> Unit,
    val onLoadMore: () -> Unit = {},
    val onColumnsChange: (Int) -> Unit,
    val onSearchQueryChange: (String) -> Unit = {},
    val onClearSearch: () -> Unit = {},
    val onSetSortOrder: (Int) -> Unit = {},
    val onToggleRandomizePhotos: () -> Unit = {},
    val onToggleRotationLock: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onToggleSelectAll: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onDeleteClick: () -> Unit,
    val onShareClick: () -> Unit,
    val onInfoClick: () -> Unit = {},
    val onEditClick: () -> Unit = {},
    val onExitSelectionMode: () -> Unit,
    val onDimensionsResolved: (photoId: String, width: Int, height: Int) -> Unit,
    val onSelectSlot: (Int) -> Unit = {},
    val onEditSlot: (Int) -> Unit = {},
    val onDuplicateSlot: (Int) -> Unit = {},
    val onAddSlot: () -> Unit = {},
    val onEnterPrivacy: () -> Unit = {},
    val onExitPrivacy: () -> Unit = {},
    val onOpenFavorites: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaWaterfallScreen(
    uiState: MediaWaterfallUiState,
    actions: MediaWaterfallActions,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val drawerActions = NavigationDrawerActions(
        onSelectSlot = actions.onSelectSlot,
        onEditSlot = actions.onEditSlot,
        onDuplicateSlot = actions.onDuplicateSlot,
        onAddSlot = actions.onAddSlot,
        onLongClickAddSlot = actions.onEnterPrivacy,
        onExitPrivacy = actions.onExitPrivacy,
        onOpenFavorites = actions.onOpenFavorites,
        onOpenSettings = actions.onOpenSettings,
    )

    BackHandler {
        if (uiState.isSelectionMode) actions.onExitSelectionMode() else actions.onBackClick()
    }

    val visibleItems = uiState.visibleItems
    val zoomState = rememberFollowZoomGridState(
        columns = uiState.columns,
        minColumns = 1,
        maxColumns = 4,
        onColumnsChanged = actions.onColumnsChange,
    )
    val aspectRatios = remember(visibleItems) { visibleItems.map { it.aspectRatio } }
    val extraHeights = remember(visibleItems, uiState.showFilenames) {
        if (!uiState.showFilenames) List(visibleItems.size) { 0.dp }
        else List(visibleItems.size) { MediaCardExtraHeight }
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(zoomState, visibleItems.size) {
        snapshotFlow { zoomState.scrollOffset }.collect {
            val total = visibleItems.size
            if (total > 0 && zoomState.lastVisibleIndex >= total - 24) actions.onLoadMore()
        }
    }

    AppAdaptiveNavigationScaffold(
        drawerState = drawerState,
        slots = uiState.slots,
        isPrivacyMode = uiState.isPrivacyMode,
        drawerEdgeWidthPercent = uiState.drawerEdgeWidthPercent,
        actions = drawerActions,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (uiState.uiMode == UiMode.Miuix) MiuixTheme.colorScheme.background
                    else MaterialTheme.colorScheme.background
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.uiMode == UiMode.Miuix) {
                    MediaTopBarMiuix(
                        uiState = uiState,
                        actions = actions,
                        scrollBehavior = topAppBarScrollBehavior,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                    )
                } else {
                    MediaTopBarMaterial(
                        uiState = uiState,
                        actions = actions,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                    )
                }

                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = actions.onRefresh,
                    modifier = Modifier.weight(1f),
                ) {
                    when {
                        uiState.loading && uiState.items.isEmpty() -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (uiState.uiMode == UiMode.Miuix) MiuixCircularProgressIndicator() else M3CircularProgressIndicator()
                        }
                        !uiState.loading && uiState.visibleItems.isEmpty() -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val text = uiState.error?.let { stringResource(R.string.error_prefix, it) }
                                ?: stringResource(R.string.no_photos_found)
                            if (uiState.uiMode == UiMode.Miuix) {
                                MiuixText(
                                    text = text,
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.disabledOnSurface,
                                )
                            } else {
                                M3Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        else -> FollowZoomWaterfallLayout(
                            itemCount = visibleItems.size,
                            aspectRatios = aspectRatios,
                            columns = uiState.columns,
                            minColumns = 1,
                            maxColumns = 4,
                            onColumnsChanged = actions.onColumnsChange,
                            spacing = 8.dp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            state = zoomState,
                            itemExtraHeights = extraHeights,
                            itemHorizontalPadding = 8.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (uiState.uiMode == UiMode.Miuix) {
                                        Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                    } else {
                                        Modifier
                                    }
                                ),
                        ) { index, widthPx, heightPx ->
                            // A filtered list can shrink while a stale subcomposition is still
                            // alive, so guard the index instead of crashing the composer.
                            val item = visibleItems.getOrNull(index) ?: return@FollowZoomWaterfallLayout
                            if (uiState.uiMode == UiMode.Miuix) {
                                MediaCardMiuix(
                                    item = item,
                                    showFilename = uiState.showFilenames,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onClick = { actions.onItemClick(item) },
                                    onLongClick = { actions.onItemLongClick(item) },
                                    onDimensionsResolved = actions.onDimensionsResolved,
                                    targetWidthPx = widthPx,
                                    targetHeightPx = heightPx,
                                    fillHeight = true,
                                )
                            } else {
                                MediaCardMaterial(
                                    item = item,
                                    showFilename = uiState.showFilenames,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onClick = { actions.onItemClick(item) },
                                    onLongClick = { actions.onItemLongClick(item) },
                                    onDimensionsResolved = actions.onDimensionsResolved,
                                    targetWidthPx = widthPx,
                                    targetHeightPx = heightPx,
                                    fillHeight = true,
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
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
private fun mediaSortItems(): List<Pair<Int, String>> = listOf(
    SettingsManager.SORT_NAME_ASC to stringResource(R.string.sort_name_asc),
    SettingsManager.SORT_NAME_DESC to stringResource(R.string.sort_name_desc),
    SettingsManager.SORT_DATE_DESC to stringResource(R.string.sort_date_desc),
    SettingsManager.SORT_DATE_ASC to stringResource(R.string.sort_date_asc),
    SettingsManager.SORT_RANDOM_FOLDERS to stringResource(R.string.sort_random_folders),
    SettingsManager.SORT_RANDOM_PHOTOS_GROUPED to stringResource(R.string.sort_random_photos_grouped),
    SettingsManager.SORT_RANDOM_PHOTOS to stringResource(R.string.sort_random_photos),
)

@Composable
private fun MediaTopBarMiuix(
    uiState: MediaWaterfallUiState,
    actions: MediaWaterfallActions,
    scrollBehavior: ScrollBehavior?,
    onOpenDrawer: () -> Unit,
) {
    var searchExpanded by remember { mutableStateOf(uiState.isSearching) }
    val titleText = if (uiState.isSelectionMode) {
        stringResource(R.string.selected_count, uiState.selectedCount)
    } else {
        uiState.title.ifEmpty { stringResource(R.string.app_name) }
    }

    Column(modifier = Modifier.statusBarsPadding()) {
        MiuixTopAppBar(
            title = titleText,
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                MiuixIconButton(
                    onClick = if (uiState.isSelectionMode) actions.onExitSelectionMode else onOpenDrawer
                ) {
                    MiuixIcon(
                        if (uiState.isSelectionMode) MiuixIcons.Light.Close else MiuixIcons.Light.Sidebar,
                        contentDescription = stringResource(
                            if (uiState.isSelectionMode) R.string.cancel else R.string.navigation_drawer_open
                        ),
                    )
                }
            },
            actions = {
                if (uiState.isSelectionMode) {
                    MiuixIconButton(onClick = actions.onInfoClick) {
                        MiuixIcon(MiuixIcons.Light.Info, contentDescription = stringResource(R.string.photo_details))
                    }
                    MiuixIconButton(onClick = actions.onEditClick) {
                        MiuixIcon(MiuixIcons.Light.Edit, contentDescription = stringResource(R.string.edit))
                    }
                } else {
                    MiuixIconButton(onClick = { searchExpanded = true }) {
                        MiuixIcon(FunnelIcon, contentDescription = stringResource(R.string.filter))
                    }
                    MediaMiuixMenuButton(uiState = uiState, actions = actions)
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

@Composable
private fun MediaMiuixMenuButton(uiState: MediaWaterfallUiState, actions: MediaWaterfallActions) {
    val shuffleAllPhotos = uiState.sortOrder == SettingsManager.SORT_RANDOM_PHOTOS
    val sortItems = mediaSortItems().map { (order, label) ->
        DropdownItem(text = label, selected = uiState.sortOrder == order, onClick = { actions.onSetSortOrder(order) })
    }
    val columnItems = (1..4).map { columns ->
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
                        text = stringResource(R.string.randomize_photos),
                        summary = if (shuffleAllPhotos) "On" else "Off",
                        icon = { modifier -> MiuixIcon(MiuixIcons.Light.Sort, null, modifier) },
                        onClick = actions.onToggleRandomizePhotos,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaTopBarMaterial(
    uiState: MediaWaterfallUiState,
    actions: MediaWaterfallActions,
    onOpenDrawer: () -> Unit,
) {
    var searchExpanded by remember { mutableStateOf(uiState.isSearching) }
    var menuExpanded by remember { mutableStateOf(false) }
    val titleText = if (uiState.isSelectionMode) {
        stringResource(R.string.selected_count, uiState.selectedCount)
    } else {
        uiState.title.ifEmpty { stringResource(R.string.app_name) }
    }
    val colors = if (uiState.isSelectionMode) {
        TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    } else {
        TopAppBarDefaults.topAppBarColors()
    }

    Column(modifier = Modifier.statusBarsPadding()) {
        M3TopAppBar(
            title = { M3Text(text = titleText, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                M3IconButton(
                    onClick = if (uiState.isSelectionMode) actions.onExitSelectionMode else onOpenDrawer
                ) {
                    M3Icon(
                        if (uiState.isSelectionMode) MiuixIcons.Light.Close else MiuixIcons.Light.Sidebar,
                        contentDescription = stringResource(
                            if (uiState.isSelectionMode) R.string.cancel else R.string.navigation_drawer_open
                        ),
                    )
                }
            },
            actions = {
                if (uiState.isSelectionMode) {
                    M3IconButton(onClick = actions.onInfoClick) {
                        M3Icon(MiuixIcons.Light.Info, contentDescription = stringResource(R.string.photo_details))
                    }
                    M3IconButton(onClick = actions.onEditClick) {
                        M3Icon(MiuixIcons.Light.Edit, contentDescription = stringResource(R.string.edit))
                    }
                } else {
                    M3IconButton(onClick = { searchExpanded = true }) {
                        M3Icon(FunnelIcon, contentDescription = stringResource(R.string.filter))
                    }
                    Box {
                        M3IconButton(onClick = { menuExpanded = true }) {
                            M3Icon(MiuixIcons.Light.More, contentDescription = stringResource(R.string.more))
                        }
                        MediaMaterialMenu(menuExpanded, uiState, actions) { menuExpanded = false }
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

private enum class MediaMaterialMenuLevel { Root, Sort, Columns }

@Composable
private fun MediaMaterialMenu(
    expanded: Boolean,
    uiState: MediaWaterfallUiState,
    actions: MediaWaterfallActions,
    onDismiss: () -> Unit,
) {
    val selectedTint = MaterialTheme.colorScheme.primary
    val idleTint = MaterialTheme.colorScheme.onSurfaceVariant
    val shuffleAllPhotos = uiState.sortOrder == SettingsManager.SORT_RANDOM_PHOTOS
    var level by remember { mutableStateOf(MediaMaterialMenuLevel.Root) }
    if (!expanded && level != MediaMaterialMenuLevel.Root) level = MediaMaterialMenuLevel.Root

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (level) {
            MediaMaterialMenuLevel.Root -> {
                DropdownMenuItem(
                    text = { M3Text(stringResource(R.string.sort_order)) },
                    leadingIcon = { M3Icon(MiuixIcons.Light.Sort, null, tint = idleTint) },
                    trailingIcon = { M3Icon(MiuixIcons.Light.ChevronForward, null, tint = idleTint) },
                    onClick = { level = MediaMaterialMenuLevel.Sort },
                )
                DropdownMenuItem(
                    text = { M3Text(stringResource(R.string.grid_columns)) },
                    leadingIcon = { M3Icon(MiuixIcons.Light.GridView, null, tint = idleTint) },
                    trailingIcon = { M3Icon(MiuixIcons.Light.ChevronForward, null, tint = idleTint) },
                    onClick = { level = MediaMaterialMenuLevel.Columns },
                )
                DropdownMenuItem(
                    text = { M3Text(stringResource(R.string.randomize_photos)) },
                    leadingIcon = {
                        M3Icon(
                            MiuixIcons.Light.Sort,
                            null,
                            tint = if (shuffleAllPhotos) selectedTint else idleTint,
                        )
                    },
                    trailingIcon = { M3Text(if (shuffleAllPhotos) "On" else "Off") },
                    onClick = { actions.onToggleRandomizePhotos(); onDismiss() },
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

            MediaMaterialMenuLevel.Sort -> {
                DropdownMenuItem(
                    text = { M3Text(stringResource(R.string.sort_order), color = selectedTint) },
                    leadingIcon = { M3Icon(MiuixIcons.Light.ChevronBackward, null, tint = selectedTint) },
                    onClick = { level = MediaMaterialMenuLevel.Root },
                )
                mediaSortItems().forEach { (order, label) ->
                    val selected = uiState.sortOrder == order
                    DropdownMenuItem(
                        text = { M3Text(label, color = if (selected) selectedTint else Color.Unspecified) },
                        onClick = { actions.onSetSortOrder(order); onDismiss() },
                    )
                }
            }

            MediaMaterialMenuLevel.Columns -> {
                DropdownMenuItem(
                    text = { M3Text(stringResource(R.string.grid_columns), color = selectedTint) },
                    leadingIcon = { M3Icon(MiuixIcons.Light.ChevronBackward, null, tint = selectedTint) },
                    onClick = { level = MediaMaterialMenuLevel.Root },
                )
                (1..4).forEach { columns ->
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
