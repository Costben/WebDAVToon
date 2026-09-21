package erl.webdavtoon.ui.screen.waterfall

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import io.github.suqi8.coui.kmp.basic.Icon as MiuixIcon
import io.github.suqi8.coui.kmp.basic.IconButton as MiuixIconButton
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text as MiuixText
import io.github.suqi8.coui.kmp.basic.TopAppBar as MiuixTopAppBar
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.icon.extended.Sort
import erl.webdavtoon.ui.component.CouiCascadingMenu
import erl.webdavtoon.ui.component.TopBarActionIcon
import erl.webdavtoon.ui.component.TopBarIcons
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

    val topAppBarScrollBehavior = COUIScrollBehavior(rememberTopAppBarState())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(COUITheme.colorScheme.surface)
    ) {
        MixedWaterfallTopBarMiuix(
            uiState = uiState,
            actions = actions,
            scrollBehavior = topAppBarScrollBehavior,
        )

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
                        MiuixCircularProgressIndicator()
                    }
                }
                !uiState.loading && uiState.visibleItems.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        state = zoomState,
                        itemExtraHeights = extraHeights,
                        itemHorizontalPadding = MediaCardImageMargin,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
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
                        )
                    }
                }
            }

            if (!uiState.isSelectionMode && !uiState.isFavorites) {
                MiuixFloatingActionButton(
                    onClick = actions.onOpenRecursiveBrowser,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(20.dp),
                ) {
                    MiuixIcon(COUIIcons.Light.Add, contentDescription = null)
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
                            COUIIcons.Light.Close,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    } else {
                        MiuixIcon(
                            COUIIcons.Light.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                }
            },
            actions = {
                if (!uiState.isSelectionMode) {
                    MiuixIconButton(onClick = { searchExpanded = true }) {
                        TopBarActionIcon(
                            TopBarIcons.Filter,
                            contentDescription = stringResource(R.string.filter),
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
        TopBarActionIcon(TopBarIcons.More, contentDescription = stringResource(R.string.more))
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

