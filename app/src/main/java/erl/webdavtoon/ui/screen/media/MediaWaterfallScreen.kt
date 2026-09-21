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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import erl.webdavtoon.ui.component.AppAdaptiveNavigationScaffold
import erl.webdavtoon.ui.component.ServerSheetActions
import erl.webdavtoon.ui.screen.waterfall.MediaCardMiuix
import erl.webdavtoon.ui.screen.waterfall.MediaCardExtraHeight
import erl.webdavtoon.ui.screen.waterfall.FollowZoomWaterfallLayout
import erl.webdavtoon.ui.screen.waterfall.SelectionBottomBarMiuix
import erl.webdavtoon.ui.screen.waterfall.rememberFollowZoomGridState
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon as MiuixIcon
import io.github.suqi8.coui.kmp.basic.IconButton as MiuixIconButton
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text as MiuixText
import io.github.suqi8.coui.kmp.basic.TopAppBar as MiuixTopAppBar
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Info
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.icon.extended.Sidebar
import io.github.suqi8.coui.kmp.icon.extended.Sort
import erl.webdavtoon.ui.component.CouiCascadingMenu
import io.github.suqi8.coui.kmp.theme.COUITheme

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

@Composable
fun MediaWaterfallScreen(
    uiState: MediaWaterfallUiState,
    actions: MediaWaterfallActions,
    modifier: Modifier = Modifier,
) {
    var showServerSheet by remember { mutableStateOf(false) }
    val serverSheetActions = ServerSheetActions(
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
    val topAppBarScrollBehavior = COUIScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(zoomState, visibleItems.size) {
        snapshotFlow { zoomState.scrollOffset }.collect {
            val total = visibleItems.size
            if (total > 0 && zoomState.lastVisibleIndex >= total - 24) actions.onLoadMore()
        }
    }

    AppAdaptiveNavigationScaffold(
        showServerSheet = showServerSheet,
        onOpenServerSheet = { showServerSheet = true },
        onDismissServerSheet = { showServerSheet = false },
        slots = uiState.slots,
        isPrivacyMode = uiState.isPrivacyMode,
        serverSheetEdgeWidthPercent = uiState.drawerEdgeWidthPercent,
        actions = serverSheetActions,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(COUITheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                MediaTopBarMiuix(
                    uiState = uiState,
                    actions = actions,
                    scrollBehavior = topAppBarScrollBehavior,
                    onOpenDrawer = { showServerSheet = true },
                )

                PullToRefresh(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = actions.onRefresh,
                    modifier = Modifier.weight(1f),
                ) {
                    when {
                        uiState.loading && uiState.items.isEmpty() -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiuixCircularProgressIndicator()
                        }
                        !uiState.loading && uiState.visibleItems.isEmpty() -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val text = uiState.error?.let { stringResource(R.string.error_prefix, it) }
                                ?: stringResource(R.string.no_photos_found)
                            MiuixText(
                                text = text,
                                style = COUITheme.textStyles.body1,
                                color = COUITheme.colorScheme.disabledOnSurface,
                            )
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
                                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                        ) { index, widthPx, heightPx ->
                            // A filtered list can shrink while a stale subcomposition is still
                            // alive, so guard the index instead of crashing the composer.
                            val item = visibleItems.getOrNull(index) ?: return@FollowZoomWaterfallLayout
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
                SelectionBottomBarMiuix(
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
                        if (uiState.isSelectionMode) COUIIcons.Light.Close else COUIIcons.Light.Sidebar,
                        contentDescription = stringResource(
                            if (uiState.isSelectionMode) R.string.cancel else R.string.navigation_drawer_open
                        ),
                    )
                }
            },
            actions = {
                if (uiState.isSelectionMode) {
                    MiuixIconButton(onClick = actions.onInfoClick) {
                        MiuixIcon(COUIIcons.Light.Info, contentDescription = stringResource(R.string.photo_details))
                    }
                    MiuixIconButton(onClick = actions.onEditClick) {
                        MiuixIcon(COUIIcons.Light.Edit, contentDescription = stringResource(R.string.edit))
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

    CouiCascadingMenu(
        entries = listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = stringResource(R.string.sort_order),
                        icon = { modifier -> MiuixIcon(COUIIcons.Light.Sort, null, modifier) },
                        children = sortItems,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.grid_columns),
                        icon = { modifier -> MiuixIcon(COUIIcons.Light.GridView, null, modifier) },
                        children = columnItems,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.randomize_photos),
                        summary = if (shuffleAllPhotos) "On" else "Off",
                        icon = { modifier -> MiuixIcon(COUIIcons.Light.Sort, null, modifier) },
                        onClick = actions.onToggleRandomizePhotos,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.refresh),
                        icon = { modifier -> MiuixIcon(COUIIcons.Light.Refresh, null, modifier) },
                        onClick = actions.onRefresh,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.settings),
                        icon = { modifier -> MiuixIcon(COUIIcons.Light.Settings, null, modifier) },
                        onClick = actions.onOpenSettings,
                    ),
                    DropdownItem(
                        text = stringResource(R.string.rotation_lock),
                        summary = if (uiState.rotationLocked) "On" else "Off",
                        icon = { modifier -> MiuixIcon(COUIIcons.Light.Lock, null, modifier) },
                        onClick = actions.onToggleRotationLock,
                    ),
                ),
            ),
        ),
    ) {
        MiuixIcon(COUIIcons.Light.More, contentDescription = stringResource(R.string.more))
    }
}
