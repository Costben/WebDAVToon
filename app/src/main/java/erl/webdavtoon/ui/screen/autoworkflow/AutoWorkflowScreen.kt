// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.autoworkflow

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import erl.webdavtoon.AutoWorkflowClient
import erl.webdavtoon.AutoWorkflowCompareInfo
import erl.webdavtoon.AutoWorkflowCompareStore
import erl.webdavtoon.AutoWorkflowFolder
import erl.webdavtoon.AutoWorkflowImage
import erl.webdavtoon.AutoWorkflowTask
import erl.webdavtoon.Folder
import erl.webdavtoon.Photo
import erl.webdavtoon.PhotoViewActivity
import erl.webdavtoon.PromptPreset
import erl.webdavtoon.R
import erl.webdavtoon.ReaderSessionSource
import erl.webdavtoon.ReaderSessions
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.WebDavImageLoader
import erl.webdavtoon.autoWorkflowFileName
import erl.webdavtoon.ui.component.AnimatedSearchField
import erl.webdavtoon.ui.component.CouiCascadingMenuPopup
import erl.webdavtoon.ui.component.CouiFlatMenu
import erl.webdavtoon.ui.component.GalleryChrome
import erl.webdavtoon.ui.component.GalleryTopAppBar
import erl.webdavtoon.ui.component.GlassTopBarScaffold
import erl.webdavtoon.ui.component.HighlightedTopBarAction
import erl.webdavtoon.ui.component.TopBarIcons
import erl.webdavtoon.ui.component.rememberStrongHaptic
import erl.webdavtoon.ui.component.rememberTapHaptic
import erl.webdavtoon.ui.screen.waterfall.FollowZoomWaterfallLayout
import erl.webdavtoon.ui.screen.waterfall.FolderCardExtraHeight
import erl.webdavtoon.ui.screen.waterfall.MediaCardExtraHeight
import erl.webdavtoon.ui.screen.waterfall.MediaCardImageMargin
import erl.webdavtoon.ui.screen.waterfall.MediaCardMiuix
import erl.webdavtoon.ui.screen.waterfall.MediaCardOverlay
import erl.webdavtoon.ui.screen.waterfall.MixedWaterfallItemUi
import erl.webdavtoon.ui.screen.waterfall.rememberFollowZoomGridState
import io.github.suqi8.coui.kmp.anim.COUIInEase
import io.github.suqi8.coui.kmp.anim.COUIOutEase
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingScrollToolbar
import io.github.suqi8.coui.kmp.basic.BottomFloatingScrollToolbarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingScrollToolbarItem
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.NavigationBarMotion
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.couiGlassHeader
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.SelectAll
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.icon.extended.Sort
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.RadioButtonPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

/** Callbacks the AutoWorkflow output browser needs from its host. */
data class AutoWorkflowActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onUp: () -> Unit,
    val onOpenFolder: (relPath: String) -> Unit,
    val onLoadMore: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onSetSortOrder: (Int) -> Unit,
    val getScrollOffset: (relPath: String) -> Int = { 0 },
    val onScrollOffsetChanged: (relPath: String, offset: Int) -> Unit = { _, _ -> },
    val onToggleSelection: (taskId: Long) -> Unit,
    val onSelectAll: () -> Unit,
    val onClearSelection: () -> Unit,
    val onPreviewReedit: (taskIds: List<Long>, promptText: String, deleteOldOutput: Boolean) -> Unit,
    val onPreviewResubmit: (taskIds: List<Long>, flow: String, deleteOldOutput: Boolean) -> Unit,
    val onConfirmPendingAction: () -> Unit,
    val onDismissPendingAction: () -> Unit,
    val onDeleteTasks: (taskIds: List<Long>, includeSource: Boolean) -> Unit,
    val onMessageShown: () -> Unit,
)

/**
 * Browses the ComfyUI output folder through the AutoWorkflow backend.
 *
 * The page is the folder page's grid, one level of detail deeper: folder and image tiles are the
 * app's own waterfall cards, so the only AutoWorkflow-specific chrome is the task badge and the
 * original-image inset on an image tile. A tap hands the directory to the album reader, which
 * keeps its own paging and chrome; long-pressing a linked image selects it, and the selection bar
 * then offers resubmit / delete through the reviewer actions endpoint.
 */
@Composable
fun AutoWorkflowScreen(
    state: AutoWorkflowUiState,
    actions: AutoWorkflowActions,
    modifier: Modifier = Modifier,
) {
    var reeditRequest by remember { mutableStateOf<List<Long>?>(null) }
    var resubmitRequest by remember { mutableStateOf<List<Long>?>(null) }
    var deleteRequest by remember { mutableStateOf<List<Long>?>(null) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val settings = remember { SettingsManager(context) }
    var columns by remember { mutableIntStateOf(settings.getGridColumns()) }
    val onColumnsChanged: (Int) -> Unit = { newColumns ->
        settings.setGridColumns(newColumns)
        columns = newColumns
    }

    val rootTitle = stringResource(R.string.autoworkflow_output_title)
    // Derived from the target path, not from `breadcrumbs`: those still describe the previous
    // level until the request returns, which made the title flash the parent's name mid-load.
    val title = state.relPath.substringAfterLast('/').takeIf { it.isNotBlank() } ?: rootTitle

    LaunchedEffect(state.relPath) {
        searchQuery = ""
        searchExpanded = false
    }

    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        actions.onMessageShown()
    }

    val goUpOrBack = {
        if (state.relPath.isNotEmpty()) actions.onUp() else actions.onBack()
    }
    BackHandler {
        when {
            state.isSelectionMode -> actions.onClearSelection()
            searchExpanded -> {
                searchQuery = ""
                searchExpanded = false
            }
            reeditRequest != null -> reeditRequest = null
            resubmitRequest != null -> resubmitRequest = null
            deleteRequest != null -> deleteRequest = null
            else -> goUpOrBack()
        }
    }

    val linkedIds = remember(state.images) { state.images.mapNotNull { it.task?.id }.toSet() }
    val isAllSelected = linkedIds.isNotEmpty() && state.selectedTaskIds.containsAll(linkedIds)

    GlassTopBarScaffold(
        modifier = modifier,
        topBar = {
            Column {
                GalleryTopAppBar(
                    title = if (state.isSelectionMode) {
                        stringResource(R.string.autoworkflow_selected_count, state.selectedCount)
                    } else {
                        title
                    },
                    navigationIcon = {
                        if (state.isSelectionMode) {
                            HighlightedTopBarAction(
                                icon = COUIIcons.Light.Close,
                                contentDescription = stringResource(R.string.cancel),
                                onClick = actions.onClearSelection,
                            )
                        } else {
                            HighlightedTopBarAction(
                                icon = COUIIcons.Light.Back,
                                contentDescription = stringResource(R.string.back),
                                onClick = goUpOrBack,
                            )
                        }
                    },
                    actions = {
                        if (!state.isSelectionMode) {
                            HighlightedTopBarAction(
                                icon = TopBarIcons.Filter,
                                contentDescription = stringResource(R.string.filter),
                                onClick = { searchExpanded = true },
                            )
                            AutoWorkflowMoreMenuButton(
                                sortOrder = state.sortOrder,
                                onSetSortOrder = actions.onSetSortOrder,
                                columns = columns,
                                onColumnsChanged = onColumnsChanged,
                                onRefresh = actions.onRefresh,
                                onOpenSettings = actions.onOpenSettings,
                            )
                        }
                    },
                )
                Column(modifier = Modifier.couiGlassHeader(LocalBackdrop.current, COUITheme.colorScheme.surface)) {
                    AnimatedSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(R.string.autoworkflow_search_hint),
                        visible = !state.isSelectionMode && searchExpanded,
                        onClose = {
                            searchQuery = ""
                            searchExpanded = false
                        },
                    )
                }
            }
        },
        bottomBar = {
            AutoWorkflowSelectionBar(
                visible = state.isSelectionMode,
                selectedCount = state.selectedCount,
                isAllSelected = isAllSelected,
                onToggleSelectAll = {
                    if (isAllSelected) actions.onClearSelection() else actions.onSelectAll()
                },
                onReedit = { reeditRequest = state.selectedTaskIds.toList() },
                onResubmit = { resubmitRequest = state.selectedTaskIds.toList() },
                onDelete = { deleteRequest = state.selectedTaskIds.toList() },
                onDismiss = actions.onClearSelection,
            )
        },
    ) { paddingValues ->
        AutoWorkflowLevels(
            state = state,
            columns = columns,
            searchQuery = searchQuery,
            onColumnsChanged = onColumnsChanged,
            topInset = paddingValues.calculateTopPadding(),
            actions = actions,
            onOpenImage = { target ->
                val query = searchQuery.trim()
                val visibleImages = state.images.filter {
                    it.isLinked && (query.isEmpty() || it.name.contains(query, ignoreCase = true))
                }
                openOutputReader(context, visibleImages, target.relPath, title)
            },
        )

        reeditRequest?.let { taskIds ->
            ReeditPromptDialog(
                onDismiss = { reeditRequest = null },
                onConfirm = { promptText, deleteOldOutput ->
                    actions.onPreviewReedit(taskIds, promptText, deleteOldOutput)
                    reeditRequest = null
                },
            )
        }

        resubmitRequest?.let { taskIds ->
            ResubmitDialog(
                onDismiss = { resubmitRequest = null },
                onConfirm = { flow, deleteOldOutput ->
                    actions.onPreviewResubmit(taskIds, flow, deleteOldOutput)
                    resubmitRequest = null
                },
            )
        }

        state.pendingConfirm?.let { pending ->
            ConfirmActionDialog(
                pending = pending,
                running = state.actionRunning,
                onConfirm = actions.onConfirmPendingAction,
                onDismiss = actions.onDismissPendingAction,
            )
        }

        deleteRequest?.let { taskIds ->
            AutoWorkflowDeleteDialog(
                taskCount = taskIds.size,
                running = state.actionRunning,
                onConfirm = { includeSource ->
                    actions.onDeleteTasks(taskIds, includeSource)
                    deleteRequest = null
                },
                onDismiss = { deleteRequest = null },
            )
        }
    }
}

/**
 * Opens the app's own reader on this directory's output images.
 *
 * The viewer, its page transitions and its gestures are the album reader's — this only hands over a
 * [ReaderSessions] snapshot (one [Photo] per output file, addressed by its relative path) and
 * records each output's original-image counterpart so the reader can draw its compare inset.
 */
private fun openOutputReader(
    context: Context,
    images: List<AutoWorkflowImage>,
    startRelPath: String,
    title: String,
) {
    if (images.isEmpty()) return
    val photos = images.map { image ->
        Photo(
            id = image.relPath,
            imageUri = Uri.parse(image.imageUrl ?: image.thumbUrl ?: image.absPath),
            title = image.name,
            size = image.size,
            isLocal = false,
        )
    }
    val startIndex = photos.indexOfFirst { it.id == startRelPath }.coerceAtLeast(0)
    AutoWorkflowCompareStore.putAll(
        images.mapNotNull { image ->
            val task = image.task ?: return@mapNotNull null
            image.relPath to AutoWorkflowCompareInfo(
                sourceUrl = task.sourceImageUrl ?: task.sourceThumbUrl,
                generatedUrl = image.imageUrl ?: image.thumbUrl,
                taskId = task.id,
                sourceName = task.sourcePath?.let(::autoWorkflowFileName),
                workflowFile = task.workflowFile,
                seed = task.seed,
            )
        }.toMap(),
    )
    val session = ReaderSessions.create(ReaderSessionSource.AUTOWORKFLOW, photos, title)
    val intent = Intent(context, PhotoViewActivity::class.java).apply {
        putExtra(ReaderSessions.EXTRA_SESSION_ID, session.id)
        putExtra("EXTRA_CURRENT_INDEX", startIndex)
        putExtra("EXTRA_IS_FAVORITES", false)
        // Output images are separate files, so the card pager is the only mode that makes
        // left/right stepping work the way the album does.
        putExtra("EXTRA_IS_CARD_MODE", true)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
private fun AutoWorkflowMoreMenuButton(
    sortOrder: Int,
    onSetSortOrder: (Int) -> Unit,
    columns: Int,
    onColumnsChanged: (Int) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val sortItems = autoWorkflowSortItems().map { (order, label) ->
        DropdownItem(
            text = label,
            selected = sortOrder == order,
            onClick = { onSetSortOrder(order) },
        )
    }
    val columnItems = (1..4).map { count ->
        DropdownItem(
            text = stringResource(R.string.columns_suffix, count),
            selected = columns == count,
            onClick = { onColumnsChanged(count) },
        )
    }
    Box {
        HighlightedTopBarAction(
            icon = COUIIcons.Light.More,
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
                            onClick = onRefresh,
                        ),
                        DropdownItem(
                            text = stringResource(R.string.settings),
                            icon = { modifier -> Icon(COUIIcons.Light.Settings, null, modifier) },
                            onClick = onOpenSettings,
                        ),
                    ),
                ),
            ),
            onDismissRequest = { showMenu = false },
        )
    }
}

@Composable
private fun autoWorkflowSortItems(): List<Pair<Int, String>> = listOf(
    SettingsManager.SORT_NAME_ASC to stringResource(R.string.sort_name_asc),
    SettingsManager.SORT_NAME_DESC to stringResource(R.string.sort_name_desc),
    SettingsManager.SORT_DATE_DESC to stringResource(R.string.sort_date_desc),
    SettingsManager.SORT_DATE_ASC to stringResource(R.string.sort_date_asc),
    SettingsManager.SORT_RANDOM_FOLDERS to stringResource(R.string.sort_random_folders),
    SettingsManager.SORT_RANDOM_PHOTOS to stringResource(R.string.sort_random_photos),
)

/**
 * Slides one directory level out and the next one in.
 *
 * The level being left is drawn from [AutoWorkflowUiState.previousLevel] — the state snapshot
 * taken when navigation started — so the outgoing page keeps its own grid and does not flash the
 * new directory's empty loading state.
 */
@Composable
private fun AutoWorkflowLevels(
    state: AutoWorkflowUiState,
    columns: Int,
    searchQuery: String,
    onColumnsChanged: (Int) -> Unit,
    topInset: Dp,
    actions: AutoWorkflowActions,
    onOpenImage: (AutoWorkflowImage) -> Unit,
) {
    AnimatedContent(
        targetState = state.relPath,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val forward = levelDepth(targetState) > levelDepth(initialState)
            val slide = tween<IntOffset>(durationMillis = 280, easing = FastOutSlowInEasing)
            val fade = tween<Float>(durationMillis = 220)
            if (forward) {
                (slideInHorizontally(slide) { it / 4 } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(slide) { -it / 4 } + fadeOut(fade))
            } else {
                (slideInHorizontally(slide) { -it / 4 } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(slide) { it / 4 } + fadeOut(fade))
            }
        },
        label = "autoworkflow-level",
    ) { relPath ->
        val isActive = relPath == state.relPath
        val level = when {
            isActive -> state.levelState()
            else -> state.previousLevel?.takeIf { it.relPath == relPath }
                ?: AutoWorkflowLevelState(relPath = relPath, loading = true)
        }
        AutoWorkflowLevel(
            level = level,
            isActive = isActive,
            columns = columns,
            searchQuery = if (isActive) searchQuery else "",
            initialScrollOffset = actions.getScrollOffset(relPath),
            onScrollOffsetChanged = { offset -> actions.onScrollOffsetChanged(relPath, offset) },
            onColumnsChanged = onColumnsChanged,
            topInset = topInset,
            actions = actions,
            selectedTaskIds = if (isActive) state.selectedTaskIds else emptySet(),
            onOpenImage = onOpenImage,
        )
    }
}

private fun levelDepth(relPath: String): Int =
    if (relPath.isBlank()) 0 else relPath.count { it == '/' } + 1

@Composable
private fun AutoWorkflowLevel(
    level: AutoWorkflowLevelState,
    isActive: Boolean,
    columns: Int,
    searchQuery: String,
    initialScrollOffset: Int,
    onScrollOffsetChanged: (Int) -> Unit,
    onColumnsChanged: (Int) -> Unit,
    topInset: Dp,
    actions: AutoWorkflowActions,
    selectedTaskIds: Set<Long>,
    onOpenImage: (AutoWorkflowImage) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val cells = remember(level.folders, level.images, selectedTaskIds, searchQuery) {
            level.toCells(selectedTaskIds, searchQuery)
        }
        when {
            !level.isConfigured -> MessagePanel(
                message = stringResource(R.string.autoworkflow_configure_first),
                actionLabel = stringResource(R.string.autoworkflow_open_settings),
                onAction = actions.onOpenSettings,
            )

            level.loading && cells.isEmpty() -> CenteredProgress()

            level.error != null && cells.isEmpty() -> MessagePanel(
                message = level.error.orEmpty(),
                actionLabel = stringResource(R.string.autoworkflow_retry),
                onAction = actions.onRefresh,
            )

            cells.isEmpty() -> MessagePanel(
                message = stringResource(R.string.autoworkflow_empty),
                actionLabel = stringResource(R.string.autoworkflow_retry),
                onAction = actions.onRefresh,
            )

            else -> key(level.relPath) {
                AutoWorkflowGrid(
                    cells = cells,
                    columns = columns,
                    onColumnsChanged = onColumnsChanged,
                    initialScrollOffset = initialScrollOffset,
                    onScrollOffsetChanged = onScrollOffsetChanged,
                    isSearchFiltered = searchQuery.isNotBlank(),
                    isRefreshing = level.loading,
                    isActive = isActive,
                    isSelectionMode = selectedTaskIds.isNotEmpty(),
                    topInset = topInset,
                    onRefresh = actions.onRefresh,
                    onLoadMore = actions.onLoadMore,
                    onOpenFolder = actions.onOpenFolder,
                    onOpenImage = onOpenImage,
                    onToggleSelection = actions.onToggleSelection,
                )
            }
        }
    }
}

/**
 * The output grid.
 *
 * Rebuilt per directory ([key] on the relative path), restoring the remembered scroll offset for
 * that directory so navigating back up or returning from the reader resumes where the user left off.
 */
@Composable
private fun AutoWorkflowGrid(
    cells: List<AutoWorkflowCell>,
    columns: Int,
    onColumnsChanged: (Int) -> Unit,
    initialScrollOffset: Int,
    onScrollOffsetChanged: (Int) -> Unit,
    isSearchFiltered: Boolean,
    isRefreshing: Boolean,
    isActive: Boolean,
    isSelectionMode: Boolean,
    topInset: Dp,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenImage: (AutoWorkflowImage) -> Unit,
    onToggleSelection: (Long) -> Unit,
) {
    val zoomState = rememberFollowZoomGridState(
        columns = columns,
        minColumns = 1,
        maxColumns = 4,
        onColumnsChanged = onColumnsChanged,
        initialScrollOffset = initialScrollOffset,
    )
    val latestIsSearchFiltered by rememberUpdatedState(isSearchFiltered)
    val latestOnScrollOffsetChanged by rememberUpdatedState(onScrollOffsetChanged)
    val aspectRatios = remember(cells) { cells.map { it.item.aspectRatio } }
    val extraHeights = remember(cells) {
        cells.map { cell ->
            if (cell is AutoWorkflowCell.FolderCell) FolderCardExtraHeight else MediaCardExtraHeight
        }
    }
    LaunchedEffect(zoomState, cells.size, isActive) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow { zoomState.scrollOffset }.collect {
            val total = cells.size
            if (total > 0 && zoomState.lastVisibleIndex >= total - 16) onLoadMore()
        }
    }
    LaunchedEffect(zoomState, isActive) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow { zoomState.scrollOffset }.collect { offset ->
            if (!latestIsSearchFiltered) {
                latestOnScrollOffsetChanged(offset)
            }
        }
    }

    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset),
    ) {
        FollowZoomWaterfallLayout(
            itemCount = cells.size,
            aspectRatios = aspectRatios,
            columns = columns,
            minColumns = 1,
            maxColumns = 4,
            onColumnsChanged = onColumnsChanged,
            spacing = 12.dp,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = topInset + 12.dp,
                end = 16.dp,
                bottom = 96.dp,
            ),
            state = zoomState,
            itemExtraHeights = extraHeights,
            itemHorizontalPadding = MediaCardImageMargin,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) { index, widthPx, heightPx ->
            val cell = cells.getOrNull(index) ?: return@FollowZoomWaterfallLayout
            when (cell) {
                is AutoWorkflowCell.FolderCell -> MediaCardMiuix(
                    item = cell.item,
                    showFilename = true,
                    isSelectionMode = false,
                    onClick = { onOpenFolder(cell.source.relPath) },
                    onLongClick = {},
                    targetWidthPx = widthPx,
                    targetHeightPx = heightPx,
                    fillHeight = true,
                    isZooming = zoomState.isZoomActive,
                    folderSubtitle = folderSummary(cell.source),
                )

                is AutoWorkflowCell.ImageCell -> MediaCardMiuix(
                    item = cell.item,
                    showFilename = true,
                    isSelectionMode = isSelectionMode,
                    onClick = {
                        val taskId = cell.source.task?.id
                        when {
                            isSelectionMode && taskId != null -> onToggleSelection(taskId)
                            isSelectionMode -> Unit
                            else -> onOpenImage(cell.source)
                        }
                    },
                    onLongClick = { cell.source.task?.id?.let(onToggleSelection) },
                    targetWidthPx = widthPx,
                    targetHeightPx = heightPx,
                    fillHeight = true,
                    isZooming = zoomState.isZoomActive,
                    overlay = imageOverlay(cell.source),
                )
            }
        }
    }
}

/** One cell of the output grid: an output folder or one generated image. */
private sealed interface AutoWorkflowCell {
    val item: MixedWaterfallItemUi

    data class FolderCell(
        val source: AutoWorkflowFolder,
        override val item: MixedWaterfallItemUi.FolderItem,
    ) : AutoWorkflowCell

    data class ImageCell(
        val source: AutoWorkflowImage,
        override val item: MixedWaterfallItemUi.MediaItem,
    ) : AutoWorkflowCell
}

private const val IMAGE_TILE_ASPECT = 0.75f

private fun AutoWorkflowLevelState.toCells(
    selectedTaskIds: Set<Long> = emptySet(),
    searchQuery: String = "",
): List<AutoWorkflowCell> {
    val query = searchQuery.trim()
    val visibleFolders = folders.filter {
        it.taskCount > 0 && (query.isEmpty() || it.name.contains(query, ignoreCase = true))
    }
    val visibleImages = images.filter {
        it.isLinked && (query.isEmpty() || it.name.contains(query, ignoreCase = true))
    }
    return visibleFolders.map { folder ->
        val path = folder.relPath
        AutoWorkflowCell.FolderCell(
            source = folder,
            item = MixedWaterfallItemUi.FolderItem(
                folder = Folder(
                    path = path,
                    name = folder.name,
                    isLocal = true,
                    photoCount = folder.directImageCount,
                    hasSubFolders = folder.subfolderCount > 0,
                ),
                key = "folder:$path",
                name = folder.name,
                path = path,
                isLocal = true,
                photoCount = folder.directImageCount,
                previewUris = folder.previewUrls,
                aspectRatio = 1f,
            ),
        )
    } + visibleImages.mapNotNull { image ->
        val url = image.thumbUrl ?: image.imageUrl ?: return@mapNotNull null
        val uri = Uri.parse(url)
        AutoWorkflowCell.ImageCell(
            source = image,
            item = MixedWaterfallItemUi.MediaItem(
                photo = Photo(
                    id = image.relPath,
                    imageUri = uri,
                    title = image.name,
                    isLocal = true,
                    size = image.size,
                ),
                key = "image:${image.relPath}",
                id = image.relPath,
                uri = uri,
                title = image.name,
                isLocal = true,
                isVideo = false,
                durationText = "",
                aspectRatio = IMAGE_TILE_ASPECT,
                isFavorite = false,
                isSelected = image.task?.id?.let { it in selectedTaskIds } ?: false,
            ),
        )
    }
}

@Composable
private fun folderSummary(folder: AutoWorkflowFolder): String = when {
    folder.taskCount <= 0 && folder.directImageCount > 0 ->
        stringResource(R.string.autoworkflow_folder_images_no_tasks, folder.directImageCount)

    folder.taskCount <= 0 && folder.subfolderCount > 0 ->
        stringResource(R.string.autoworkflow_folder_subfolders_no_tasks, folder.subfolderCount)

    folder.directImageCount > 0 ->
        stringResource(
            R.string.autoworkflow_folder_images_tasks,
            folder.directImageCount,
            folder.taskCount,
        )

    folder.subfolderCount > 0 ->
        stringResource(
            R.string.autoworkflow_folder_subfolders_tasks,
            folder.subfolderCount,
            folder.taskCount,
        )

    else -> stringResource(R.string.autoworkflow_folder_images, folder.directImageCount)
}

@Composable
private fun imageOverlay(image: AutoWorkflowImage): MediaCardOverlay {
    val task = image.task
    return MediaCardOverlay(
        badgeText = if (task != null) {
            stringResource(R.string.autoworkflow_task_badge, task.id)
        } else {
            stringResource(R.string.autoworkflow_unlinked)
        },
        badgeHighlighted = task != null,
        sourceThumbUrl = task?.sourceThumbUrl ?: task?.sourceImageUrl,
    )
}

/**
 * The COUI scrolling toolbar shown while images are selected, built from the same
 * [BottomFloatingScrollToolbar] capsule the folder pages use for their bulk actions.
 */
@Composable
private fun AutoWorkflowSelectionBar(
    visible: Boolean,
    selectedCount: Int,
    isAllSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onReedit: () -> Unit,
    onResubmit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasSelection = selectedCount > 0
    val tapHaptic = rememberTapHaptic()
    val strongHaptic = rememberStrongHaptic()
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val colors = if (isDark) {
        BottomFloatingScrollToolbarDefaults.colors()
    } else {
        BottomFloatingScrollToolbarDefaults.colors(
            containerColor = Color(0xF5F5F5F7),
            strokeColor = Color(0x1A000000),
            contentColor = Color(0xB3000000),
            disabledContentColor = Color(0x33000000),
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            tween(NavigationBarMotion.ShowTranslateDurationMillis, easing = COUIInEase),
        ) { it },
        exit = slideOutVertically(
            tween(NavigationBarMotion.HideTranslateDurationMillis, easing = COUIOutEase),
        ) { it },
    ) {
        GalleryChrome {
            BottomFloatingScrollToolbar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = BottomFloatingNavigationBarDefaults.navigationBarPadding() + 16.dp,
                    ),
                colors = colors,
            ) {
                BottomFloatingScrollToolbarItem(
                    onClick = {
                        tapHaptic()
                        onToggleSelectAll()
                    },
                    icon = COUIIcons.SelectAll,
                    label = stringResource(
                        if (isAllSelected) R.string.unselect_all else R.string.select_all,
                    ),
                    selected = isAllSelected,
                )
                BottomFloatingScrollToolbarItem(
                    onClick = {
                        tapHaptic()
                        onReedit()
                    },
                    icon = COUIIcons.Light.Edit,
                    label = stringResource(R.string.autoworkflow_resubmit_edit),
                    enabled = hasSelection,
                )
                BottomFloatingScrollToolbarItem(
                    onClick = {
                        tapHaptic()
                        onResubmit()
                    },
                    icon = COUIIcons.Light.Refresh,
                    label = stringResource(R.string.autoworkflow_resubmit),
                    enabled = hasSelection,
                )
                BottomFloatingScrollToolbarItem(
                    onClick = {
                        strongHaptic()
                        onDelete()
                    },
                    icon = COUIIcons.Delete,
                    label = stringResource(R.string.autoworkflow_delete_task),
                    enabled = hasSelection,
                )
                BottomFloatingScrollToolbarItem(
                    onClick = {
                        tapHaptic()
                        onDismiss()
                    },
                    icon = COUIIcons.Close,
                    label = stringResource(R.string.cancel),
                )
            }
        }
    }
}

private val DialogRowInsideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp)

@Composable
internal fun ReeditPromptDialog(
    onDismiss: () -> Unit,
    onConfirm: (promptText: String, deleteOldOutput: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager(context) }
    val serverUrl = remember { settings.getAutoWorkflowUrl() }
    var presets by remember { mutableStateOf<List<PromptPreset>>(emptyList()) }
    var presetsLoading by remember { mutableStateOf(serverUrl.isNotBlank()) }
    var selectedPresetName by remember { mutableStateOf(settings.getAutoWorkflowReeditPresetName()) }
    var presetExpanded by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf(settings.getAutoWorkflowReeditPrompt()) }
    var promptError by remember { mutableStateOf(false) }
    var deleteOldOutput by remember { mutableStateOf(settings.getAutoWorkflowReeditDeleteOld()) }

    LaunchedEffect(serverUrl) {
        if (serverUrl.isBlank()) {
            presetsLoading = false
            return@LaunchedEffect
        }
        presetsLoading = true
        AutoWorkflowClient(serverUrl).fetchPromptPresets().onSuccess { loaded ->
            presets = loaded
            if (selectedPresetName != null && loaded.none { it.name == selectedPresetName }) {
                selectedPresetName = null
            }
        }
        presetsLoading = false
    }

    val presetDisplayValue = when {
        presetsLoading -> stringResource(R.string.autoworkflow_preset_loading)
        selectedPresetName != null -> selectedPresetName.orEmpty()
        presets.isEmpty() -> stringResource(R.string.autoworkflow_preset_empty)
        else -> stringResource(R.string.autoworkflow_preset_placeholder)
    }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.autoworkflow_reedit_title),
        onDismissRequest = onDismiss,
        messageBottomPadding = DialogDefaults.MessageBottomPaddingWithCustomContent,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    TextField(
                        value = presetDisplayValue,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.autoworkflow_preset_label),
                        singleLine = true,
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                enabled = !presetsLoading && presets.isNotEmpty(),
                                role = Role.Button,
                            ) { presetExpanded = true },
                    ) {
                        Text(
                            text = "\u25BE",
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp),
                        )
                    }
                    CouiFlatMenu(
                        expanded = presetExpanded,
                        entries = listOf(
                            DropdownEntry(
                                items = buildList {
                                    add(
                                        DropdownItem(
                                            text = stringResource(R.string.autoworkflow_preset_none),
                                            selected = selectedPresetName == null,
                                            onClick = {
                                                selectedPresetName = null
                                                presetExpanded = false
                                            },
                                        ),
                                    )
                                    presets.forEach { preset ->
                                        add(
                                            DropdownItem(
                                                text = preset.name,
                                                selected = selectedPresetName == preset.name,
                                                onClick = {
                                                    selectedPresetName = preset.name
                                                    // Copy into the submission buffer only; saved backend presets stay read-only.
                                                    prompt = preset.content
                                                    promptError = false
                                                    presetExpanded = false
                                                },
                                            ),
                                        )
                                    }
                                },
                            ),
                        ),
                        onDismissRequest = { presetExpanded = false },
                    )
                }
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = prompt,
                    onValueChange = { value ->
                        prompt = value
                        if (value.isNotBlank()) promptError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .heightIn(min = 96.dp),
                    label = stringResource(R.string.autoworkflow_reedit_prompt_hint),
                    singleLine = false,
                )
                if (promptError) {
                    Text(
                        text = stringResource(R.string.autoworkflow_reedit_prompt_required),
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                SwitchPreference(
                    checked = deleteOldOutput,
                    onCheckedChange = { deleteOldOutput = it },
                    title = stringResource(R.string.autoworkflow_resubmit_delete_old),
                    insideMargin = DialogRowInsideMargin,
                )
            }

            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                ),
                positive = DialogButtonBarAction(
                    text = stringResource(R.string.autoworkflow_reedit_title),
                    recommended = true,
                    onClick = {
                        if (prompt.isBlank()) {
                            promptError = true
                        } else {
                            val trimmedPrompt = prompt.trim()
                            settings.setAutoWorkflowReeditPresetName(selectedPresetName)
                            settings.setAutoWorkflowReeditPrompt(trimmedPrompt)
                            settings.setAutoWorkflowReeditDeleteOld(deleteOldOutput)
                            onConfirm(trimmedPrompt, deleteOldOutput)
                        }
                    },
                ),
                hasContentAbove = true,
            )
        }
    }
}

@Composable
internal fun ResubmitDialog(
    onDismiss: () -> Unit,
    onConfirm: (flow: String, deleteOldOutput: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager(context) }
    var flow by remember { mutableStateOf(settings.getAutoWorkflowResubmitFlow()) }
    var deleteOldOutput by remember { mutableStateOf(settings.getAutoWorkflowResubmitDeleteOld()) }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.autoworkflow_resubmit_title),
        onDismissRequest = onDismiss,
        messageBottomPadding = DialogDefaults.MessageBottomPaddingWithCustomContent,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            RadioButtonPreference(
                title = stringResource(R.string.autoworkflow_resubmit_edit),
                selected = flow == AutoWorkflowClient.FLOW_EDIT,
                onClick = {
                    flow = AutoWorkflowClient.FLOW_EDIT
                    settings.setAutoWorkflowResubmitFlow(AutoWorkflowClient.FLOW_EDIT)
                },
                insideMargin = DialogRowInsideMargin,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            RadioButtonPreference(
                title = stringResource(R.string.autoworkflow_resubmit_upscale),
                selected = flow == AutoWorkflowClient.FLOW_UPSCALE,
                onClick = {
                    flow = AutoWorkflowClient.FLOW_UPSCALE
                    settings.setAutoWorkflowResubmitFlow(AutoWorkflowClient.FLOW_UPSCALE)
                },
                insideMargin = DialogRowInsideMargin,
            )
            if (flow == AutoWorkflowClient.FLOW_EDIT) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                SwitchPreference(
                    checked = deleteOldOutput,
                    onCheckedChange = {
                        deleteOldOutput = it
                        settings.setAutoWorkflowResubmitDeleteOld(it)
                    },
                    title = stringResource(R.string.autoworkflow_resubmit_delete_old),
                    insideMargin = DialogRowInsideMargin,
                )
            }

            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                ),
                positive = DialogButtonBarAction(
                    text = stringResource(R.string.autoworkflow_resubmit),
                    recommended = true,
                    onClick = {
                        settings.setAutoWorkflowResubmitFlow(flow)
                        val effectiveDeleteOld = if (flow == AutoWorkflowClient.FLOW_EDIT) {
                            settings.setAutoWorkflowResubmitDeleteOld(deleteOldOutput)
                            deleteOldOutput
                        } else {
                            false
                        }
                        onConfirm(flow, effectiveDeleteOld)
                    },
                ),
                hasContentAbove = true,
            )
        }
    }
}

@Composable
internal fun ConfirmActionDialog(
    pending: AutoWorkflowPendingAction,
    running: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isReedit = !pending.promptText.isNullOrBlank()
    val titleRes = if (isReedit) R.string.autoworkflow_reedit_title else R.string.autoworkflow_resubmit_title
    val positiveRes = if (isReedit) R.string.autoworkflow_reedit_title else R.string.autoworkflow_resubmit

    OverlayDialog(
        show = true,
        title = stringResource(titleRes),
        summary = pending.message,
        onDismissRequest = if (running) null else onDismiss,
    ) {
        DialogButtonBar(
            negative = DialogButtonBarAction(
                text = stringResource(R.string.cancel),
                onClick = { if (!running) onDismiss() },
            ),
            positive = DialogButtonBarAction(
                text = if (running) {
                    stringResource(R.string.autoworkflow_running)
                } else {
                    stringResource(positiveRes)
                },
                recommended = true,
                onClick = { if (!running) onConfirm() },
            ),
            hasContentAbove = true,
        )
    }
}

@Composable
internal fun AutoWorkflowDeleteDialog(
    taskCount: Int,
    running: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (includeSource: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager(context) }
    val strongHaptic = rememberStrongHaptic()
    var includeSource by remember { mutableStateOf(settings.getAutoWorkflowDeleteIncludeSource()) }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.autoworkflow_delete_task),
        summary = stringResource(R.string.autoworkflow_delete_confirm, taskCount),
        onDismissRequest = if (running) null else onDismiss,
        messageBottomPadding = DialogDefaults.MessageBottomPaddingWithCustomContent,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                checked = includeSource,
                onCheckedChange = { if (!running) includeSource = it },
                title = stringResource(R.string.autoworkflow_delete_include_source),
                summary = stringResource(R.string.autoworkflow_delete_include_source_summary),
                insideMargin = DialogRowInsideMargin,
            )

            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = stringResource(R.string.cancel),
                    onClick = { if (!running) onDismiss() },
                ),
                positive = DialogButtonBarAction(
                    text = if (running) {
                        stringResource(R.string.autoworkflow_running)
                    } else {
                        stringResource(R.string.delete)
                    },
                    onClick = {
                        if (!running) {
                            strongHaptic()
                            settings.setAutoWorkflowDeleteIncludeSource(includeSource)
                            onConfirm(includeSource)
                        }
                    },
                ),
                hasContentAbove = true,
            )
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessagePanel(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message,
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
private fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (highlighted) Color(0xCC0A59F7) else Color(0x99000000),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = COUITheme.textStyles.footnote2,
            color = Color.White,
            maxLines = 1,
        )
    }
}
