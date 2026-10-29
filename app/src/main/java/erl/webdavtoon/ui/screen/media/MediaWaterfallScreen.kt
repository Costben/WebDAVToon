// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import erl.webdavtoon.ui.component.AppAdaptiveNavigationScaffold
import erl.webdavtoon.ui.component.GalleryTopAppBar
import erl.webdavtoon.ui.component.GlassTopBarScaffold
import erl.webdavtoon.ui.component.rememberGalleryScrollBehavior
import erl.webdavtoon.ui.component.rememberGalleryTopBarScrollConnection
import erl.webdavtoon.ui.component.HighlightedTopBarAction
import erl.webdavtoon.ui.component.ServerSheetActions
import erl.webdavtoon.ui.screen.waterfall.MediaCardMiuix
import erl.webdavtoon.ui.screen.waterfall.MediaCardExtraHeight
import erl.webdavtoon.ui.screen.waterfall.MediaCardImageMargin
import erl.webdavtoon.ui.screen.waterfall.FollowZoomWaterfallLayout
import erl.webdavtoon.ui.screen.waterfall.SelectionBottomBarMiuix
import erl.webdavtoon.ui.screen.waterfall.rememberFollowZoomGridState
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon as MiuixIcon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text as MiuixText
import io.github.suqi8.coui.kmp.basic.couiGlassHeader
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Info
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.icon.extended.Sort
import erl.webdavtoon.ui.component.CouiCascadingMenuPopup
import erl.webdavtoon.ui.component.TopBarIcons
import erl.webdavtoon.ui.component.rememberTapHaptic
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
    val onSetAsHomeFolder: (() -> Unit)? = null,
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
        if (!uiState.showFilenames) List(visibleItems.size) { MediaCardImageMargin * 2 }
        else List(visibleItems.size) { MediaCardExtraHeight }
    }
    // Pins the title to the toolbar row while scrolled down and expands only at the list's top.
    val topAppBarScrollBehavior = rememberGalleryScrollBehavior()
    val topAppBarScrollConnection = rememberGalleryTopBarScrollConnection(
        scrollBehavior = topAppBarScrollBehavior,
        atTop = { zoomState.scrollOffset == 0 },
    )

    LaunchedEffect(zoomState, visibleItems.size) {
        snapshotFlow { zoomState.scrollOffset }.collect {
            val total = visibleItems.size
            if (total > 0 && zoomState.lastVisibleIndex >= total - 24) actions.onLoadMore()
        }
    }

    AppAdaptiveNavigationScaffold(
        showServerSheet = showServerSheet,
        onDismissServerSheet = { showServerSheet = false },
        slots = uiState.slots,
        isPrivacyMode = uiState.isPrivacyMode,
        actions = serverSheetActions,
        modifier = modifier,
    ) {
        GlassTopBarScaffold(
            topBar = {
                MediaTopBarMiuix(
                    uiState = uiState,
                    actions = actions,
                    scrollBehavior = topAppBarScrollBehavior,
                    onOpenDrawer = { showServerSheet = true },
                )
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefresh(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = actions.onRefresh,
                    modifier = Modifier.fillMaxSize(),
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
                                .padding(top = padding.calculateTopPadding())
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
                            spacing = 12.dp,
                            // The top inset is the app bar's height: the grid starts below the bar
                            // and slides under its glass while scrolling.
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = padding.calculateTopPadding() + 12.dp,
                                end = 16.dp,
                                bottom = 12.dp,
                            ),
                            state = zoomState,
                            itemExtraHeights = extraHeights,
                            itemHorizontalPadding = MediaCardImageMargin,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(topAppBarScrollConnection),
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
                                isZooming = zoomState.isZoomActive,
                            )
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
    val tapHaptic = rememberTapHaptic()
    val titleText = if (uiState.isSelectionMode) {
        stringResource(R.string.selected_count, uiState.selectedCount)
    } else {
        uiState.title.ifEmpty { stringResource(R.string.app_name) }
    }

    Column {
        GalleryTopAppBar(
            title = titleText,
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                // Selection mode's "close" is a cancel/back action, and COUI reserves the top-left
                // slot for exactly that. Outside selection mode the slot stays empty.
                if (uiState.isSelectionMode) {
                    HighlightedTopBarAction(
                        icon = COUIIcons.Light.Close,
                        contentDescription = stringResource(R.string.cancel),
                        onClick = {
                            tapHaptic()
                            actions.onExitSelectionMode()
                        },
                    )
                }
            },
            actions = {
                if (uiState.isSelectionMode) {
                    HighlightedTopBarAction(
                        icon = COUIIcons.Light.Info,
                        contentDescription = stringResource(R.string.photo_details),
                        onClick = actions.onInfoClick,
                    )
                    HighlightedTopBarAction(
                        icon = COUIIcons.Light.Edit,
                        contentDescription = stringResource(R.string.edit),
                        onClick = actions.onEditClick,
                    )
                } else {
                    // COUI keeps the top-left slot for back/cancel only; root pages put every action
                    // in the top-right group (see the COUI example app's Home page).
                    HighlightedTopBarAction(
                        icon = TopBarIcons.Panel,
                        contentDescription = stringResource(R.string.navigation_drawer_open),
                        onClick = onOpenDrawer,
                    )
                    HighlightedTopBarAction(
                        icon = TopBarIcons.Filter,
                        contentDescription = stringResource(R.string.filter),
                        onClick = { searchExpanded = true },
                    )
                    MediaMiuixMenuButton(uiState = uiState, actions = actions)
                }
            },
        )
        // The expanded search band wears the same glass as the bar above it.
        Column(modifier = Modifier.couiGlassHeader(LocalBackdrop.current, COUITheme.colorScheme.surface)) {
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
}

@Composable
private fun MediaMiuixMenuButton(uiState: MediaWaterfallUiState, actions: MediaWaterfallActions) {
    var showMenu by remember { mutableStateOf(false) }
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

    val menuItems = buildList {
        add(
            DropdownItem(
                text = stringResource(R.string.sort_order),
                icon = { modifier -> MiuixIcon(COUIIcons.Light.Sort, null, modifier) },
                children = sortItems,
            )
        )
        add(
            DropdownItem(
                text = stringResource(R.string.grid_columns),
                icon = { modifier -> MiuixIcon(COUIIcons.Light.GridView, null, modifier) },
                children = columnItems,
            )
        )
        add(
            DropdownItem(
                text = stringResource(R.string.randomize_photos),
                summary = if (shuffleAllPhotos) "On" else "Off",
                icon = { modifier -> MiuixIcon(COUIIcons.Light.Sort, null, modifier) },
                onClick = actions.onToggleRandomizePhotos,
            )
        )
        if (uiState.canSetAsHomeFolder && actions.onSetAsHomeFolder != null) {
            add(
                DropdownItem(
                    text = stringResource(R.string.set_as_home_folder),
                    icon = { modifier -> MiuixIcon(COUIIcons.Light.Ok, null, modifier) },
                    onClick = actions.onSetAsHomeFolder,
                )
            )
        }
        add(
            DropdownItem(
                text = stringResource(R.string.refresh),
                icon = { modifier -> MiuixIcon(COUIIcons.Light.Refresh, null, modifier) },
                onClick = actions.onRefresh,
            )
        )
        add(
            DropdownItem(
                text = stringResource(R.string.settings),
                icon = { modifier -> MiuixIcon(COUIIcons.Light.Settings, null, modifier) },
                onClick = actions.onOpenSettings,
            )
        )
        add(
            DropdownItem(
                text = stringResource(R.string.rotation_lock),
                summary = if (uiState.rotationLocked) "On" else "Off",
                icon = { modifier -> MiuixIcon(COUIIcons.Light.Lock, null, modifier) },
                onClick = actions.onToggleRotationLock,
            )
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
                    items = menuItems,
                ),
            ),
            onDismissRequest = { showMenu = false },
        )
    }
}
