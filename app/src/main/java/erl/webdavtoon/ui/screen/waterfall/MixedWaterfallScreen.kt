// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.waterfall

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
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Icon as MiuixIcon
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text as MiuixText
import io.github.suqi8.coui.kmp.basic.couiGlassHeader
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.icon.extended.Sort
import erl.webdavtoon.ui.component.CouiCascadingMenuPopup
import erl.webdavtoon.ui.component.CouiFloatingBall
import erl.webdavtoon.ui.component.GalleryChrome
import erl.webdavtoon.ui.component.GalleryTopAppBar
import erl.webdavtoon.ui.component.GlassTopBarScaffold
import erl.webdavtoon.ui.component.rememberGalleryScrollBehavior
import erl.webdavtoon.ui.component.rememberGalleryTopBarScrollConnection
import erl.webdavtoon.ui.component.HighlightedTopBarAction
import erl.webdavtoon.ui.component.TopBarIcons
import erl.webdavtoon.ui.component.rememberTapHaptic
import io.github.suqi8.coui.kmp.theme.COUITheme

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
    val onSetAsHomeFolder: (() -> Unit)? = null,
    val onOpenDrawer: (() -> Unit)? = null,
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

    val visibleItems = uiState.visibleItems
    val zoomState = rememberFollowZoomGridState(
        columns = uiState.columns,
        minColumns = 1,
        maxColumns = 4,
        onColumnsChanged = actions.onColumnsChange,
    )
    val aspectRatios = remember(visibleItems) { visibleItems.map { it.aspectRatio } }
    val extraHeights = remember(visibleItems, uiState.showFilenames) {
        visibleItems.map { item ->
            val isFolder = item is MixedWaterfallItemUi.FolderItem
            when {
                isFolder -> if (uiState.showFilenames) FolderCardExtraHeight else FolderCardExtraHeightBare
                else -> if (uiState.showFilenames) MediaCardExtraHeight else MediaCardImageMargin * 2
            }
        }
    }

    // Pins the title to the toolbar row while scrolled down and expands only at the list's top.
    val topAppBarScrollBehavior = rememberGalleryScrollBehavior()
    val topAppBarScrollConnection = rememberGalleryTopBarScrollConnection(
        scrollBehavior = topAppBarScrollBehavior,
        atTop = { zoomState.scrollOffset == 0 },
    )

    GlassTopBarScaffold(
        modifier = modifier,
        topBar = {
            MixedWaterfallTopBarMiuix(
                uiState = uiState,
                actions = actions,
                scrollBehavior = topAppBarScrollBehavior,
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
                    uiState.loading && uiState.items.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiuixCircularProgressIndicator()
                        }
                    }
                    !uiState.loading && uiState.visibleItems.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = padding.calculateTopPadding())
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiuixText(
                                text = stringResource(R.string.no_photos_found),
                                style = COUITheme.textStyles.body1,
                                color = COUITheme.colorScheme.disabledOnSurface,
                            )
                        }
                    }
                    else -> {
                        // The custom layout keeps folders and media in strict index order while
                        // continuously reflowing the visible window as the pinch column count changes.
                        FollowZoomWaterfallLayout(
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
                            MixedWaterfallCard(
                                item,
                                uiState,
                                actions,
                                fillHeight = true,
                                targetWidthPx = widthPx,
                                targetHeightPx = heightPx,
                                isZooming = zoomState.isZoomActive,
                            )
                        }
                    }
                }
            }

            if (!uiState.isSelectionMode && !uiState.isFavorites) {
                val ballModifier = Modifier
                    .align(Alignment.BottomEnd)
                    // Same 16dp / 24dp placement as the gallery's accessory ball, measured on the
                    // PKT110 as 56px and 84px at the gallery's 3.5 px/dp.
                    .padding(
                        end = 16.dp,
                        bottom = BottomFloatingNavigationBarDefaults.navigationBarPadding() + 16.dp,
                    )
                GalleryChrome {
                    CouiFloatingBall(
                        onClick = actions.onOpenRecursiveBrowser,
                        modifier = ballModifier,
                    ) {
                        MiuixIcon(COUIIcons.Light.Add, contentDescription = null)
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
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
private fun MixedWaterfallCard(
    item: MixedWaterfallItemUi,
    uiState: MixedWaterfallUiState,
    actions: MixedWaterfallActions,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    targetWidthPx: Int = 0,
    targetHeightPx: Int = 0,
    isZooming: Boolean = false,
) {
    val onFolderVisibilityChanged: (Folder, Boolean) -> Unit = { folder, visible ->
        actions.onFolderVisibilityChanged(folder, visible)
        if (visible) actions.onFolderPreviewsRequested()
    }
    MediaCardMiuix(
        item = item,
        showFilename = uiState.showFilenames,
        isSelectionMode = uiState.isSelectionMode,
        onClick = { actions.onItemClick(item) },
        onLongClick = { actions.onItemLongClick(item) },
        onDimensionsResolved = actions.onDimensionsResolved,
        onFolderVisibilityChanged = onFolderVisibilityChanged,
        targetWidthPx = targetWidthPx,
        targetHeightPx = targetHeightPx,
        modifier = modifier,
        fillHeight = fillHeight,
        isZooming = isZooming,
    )
}

@Composable
private fun MixedWaterfallTopBarMiuix(
    uiState: MixedWaterfallUiState,
    actions: MixedWaterfallActions,
    scrollBehavior: ScrollBehavior? = null,
    modifier: Modifier = Modifier,
) {
    var searchExpanded by remember { mutableStateOf(uiState.isSearching) }
    val tapHaptic = rememberTapHaptic()

    val titleText = if (uiState.isSelectionMode) {
        stringResource(R.string.selected_count, uiState.selectedCount)
    } else {
        uiState.title.ifEmpty { stringResource(R.string.app_name) }
    }

    val onNavClick = {
        if (uiState.isSelectionMode) {
            tapHaptic()
            actions.onExitSelectionMode()
        } else if (uiState.isRootPage && actions.onOpenDrawer != null) {
            tapHaptic()
            actions.onOpenDrawer.invoke()
        } else {
            actions.onBackClick()
        }
    }

    Column(modifier = modifier) {
        GalleryTopAppBar(
            title = titleText,
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                HighlightedTopBarAction(
                    icon = when {
                        uiState.isSelectionMode -> COUIIcons.Light.Close
                        uiState.isRootPage && actions.onOpenDrawer != null -> TopBarIcons.Panel
                        else -> COUIIcons.Light.Back
                    },
                    contentDescription = when {
                        uiState.isSelectionMode -> stringResource(R.string.cancel)
                        uiState.isRootPage && actions.onOpenDrawer != null ->
                            stringResource(R.string.navigation_drawer_open)
                        else -> stringResource(R.string.back)
                    },
                    onClick = onNavClick,
                )
            },
            actions = {
                if (!uiState.isSelectionMode) {
                    HighlightedTopBarAction(
                        icon = TopBarIcons.Filter,
                        contentDescription = stringResource(R.string.filter),
                        onClick = { searchExpanded = true },
                    )
                    MixedWaterfallMiuixMenuButton(uiState = uiState, actions = actions)
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

/**
 * The inner-page "more" menu: sort order and grid columns expand a second
 * level, while refresh / settings / rotation lock are direct actions.
 */
@Composable
private fun MixedWaterfallMiuixMenuButton(
    uiState: MixedWaterfallUiState,
    actions: MixedWaterfallActions,
) {
    var showMenu by remember { mutableStateOf(false) }
    val sortItems = mixedSortItems().map { (order, label) ->
        DropdownItem(
            text = label,
            selected = uiState.sortOrder == order,
            onClick = { actions.onSetSortOrder(order) },
        )
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

@Composable
private fun mixedSortItems(): List<Pair<Int, String>> = listOf(
    SettingsManager.SORT_NAME_ASC to stringResource(R.string.sort_name_asc),
    SettingsManager.SORT_NAME_DESC to stringResource(R.string.sort_name_desc),
    SettingsManager.SORT_DATE_DESC to stringResource(R.string.sort_date_desc),
    SettingsManager.SORT_DATE_ASC to stringResource(R.string.sort_date_asc),
    SettingsManager.SORT_RANDOM_FOLDERS to stringResource(R.string.sort_random_folders),
    SettingsManager.SORT_RANDOM_PHOTOS_GROUPED to stringResource(R.string.sort_random_photos_grouped),
    SettingsManager.SORT_RANDOM_PHOTOS to stringResource(R.string.sort_random_photos),
)

