package erl.webdavtoon.ui.screen.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import erl.webdavtoon.ui.UiMode
import erl.webdavtoon.ui.component.AppAdaptiveNavigationScaffold
import erl.webdavtoon.ui.component.NavigationDrawerActions
import erl.webdavtoon.ui.screen.waterfall.FolderCardExtraHeight
import erl.webdavtoon.ui.screen.waterfall.FollowZoomWaterfallLayout
import erl.webdavtoon.ui.screen.waterfall.rememberFollowZoomGridState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import kotlinx.coroutines.launch

/**
 * Activity-independent Compose aggregation for the folder landing page.
 *
 * Navigation, biometric prompts, Activity results, and process-wide window work deliberately
 * stay in the host Activity; this screen owns only adaptive layout and user interaction state.
 */
data class FolderScreenActions(
    val onFolderClick: (FolderItemUi) -> Unit,
    val onToggleSelection: (String) -> Unit,
    val onPreviewVisibilityChanged: (String, Boolean) -> Unit,
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
    val onSelectSlot: (Int) -> Unit,
    val onEditSlot: (Int) -> Unit,
    val onDuplicateSlot: (Int) -> Unit,
    val onAddSlot: () -> Unit,
    val onEnterPrivacy: () -> Unit,
    val onExitPrivacy: () -> Unit,
    val onOpenFavorites: () -> Unit,
    val onOpenRecursiveBrowser: () -> Unit,
    val onBack: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    uiState: FolderUiState,
    actions: FolderScreenActions,
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
    val topBarActions = FolderTopBarActions(
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onSearchQueryChange = actions.onSearchQueryChange,
        onClearSearch = actions.onClearSearch,
        onToggleSearch = actions.onToggleSearch,
        onSetSortOrder = actions.onSetSortOrder,
        onSetGridColumns = actions.onSetGridColumns,
        onToggleRotationLock = actions.onToggleRotationLock,
        onRefresh = actions.onRefresh,
        onOpenSettings = actions.onOpenSettings,
        onClearSelection = actions.onClearSelection,
        onSelectAll = actions.onSelectAll,
        onDeleteSelected = actions.onDeleteSelected,
    )

    BackHandler {
        if (uiState.isSelectionMode) actions.onClearSelection() else actions.onBack()
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    AppAdaptiveNavigationScaffold(
        drawerState = drawerState,
        slots = uiState.slots,
        isPrivacyMode = uiState.isPrivacyMode,
        drawerEdgeWidthPercent = uiState.drawerEdgeWidthPercent,
        actions = drawerActions,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (uiState.uiMode) {
                    UiMode.Miuix -> FolderTopBarMiuix(
                        uiState = uiState,
                        actions = topBarActions,
                        scrollBehavior = topAppBarScrollBehavior,
                    )
                    UiMode.Material -> FolderTopBarMaterial(uiState = uiState, actions = topBarActions)
                }
                uiState.remoteError?.let { message ->
                    RemoteErrorBanner(message = message)
                }
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = actions.onRefresh,
                    modifier = Modifier.weight(1f),
                ) {
                    FolderGrid(
                        uiState = uiState,
                        actions = actions,
                        scrollBehavior = topAppBarScrollBehavior,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            FloatingActionButton(
                onClick = actions.onOpenRecursiveBrowser,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp),
            ) {
                Text("+")
            }
        }
    }
}

@Composable
private fun FolderGrid(
    uiState: FolderUiState,
    actions: FolderScreenActions,
    scrollBehavior: ScrollBehavior? = null,
    modifier: Modifier = Modifier,
) {
    val folders = uiState.folders
    val zoomState = rememberFollowZoomGridState(
        columns = uiState.gridColumns,
        minColumns = 1,
        maxColumns = 4,
        onColumnsChanged = actions.onSetGridColumns,
    )
    val aspectRatios = remember(folders) { List(folders.size) { 1f } }
    val extraHeights = remember(folders) { List(folders.size) { FolderCardExtraHeight } }
    when {
        uiState.loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        uiState.error != null -> FolderMessage(
            text = uiState.error,
            modifier = modifier,
        )
        uiState.folders.isEmpty() -> FolderMessage(
            text = "No folders found",
            modifier = modifier,
        )
        else -> FollowZoomWaterfallLayout(
            itemCount = folders.size,
            aspectRatios = aspectRatios,
            columns = uiState.gridColumns,
            minColumns = 1,
            maxColumns = 4,
            onColumnsChanged = actions.onSetGridColumns,
            spacing = 10.dp,
            contentPadding = PaddingValues(12.dp),
            state = zoomState,
            itemExtraHeights = extraHeights,
            itemHorizontalPadding = 8.dp,
            modifier = modifier
                .navigationBarsPadding()
                .then(
                    if (uiState.uiMode == UiMode.Miuix && scrollBehavior != null) {
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        Modifier
                    }
                ),
        ) { index, _, _ ->
            val folder = folders[index]
            val onClick = {
                if (uiState.isSelectionMode) actions.onToggleSelection(folder.path)
                else actions.onFolderClick(folder)
            }
            val onLongClick = { actions.onToggleSelection(folder.path) }
            when (uiState.uiMode) {
                UiMode.Miuix -> FolderCardMiuix(
                    folder = folder,
                    isSelectionMode = uiState.isSelectionMode,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onVisibilityChanged = actions.onPreviewVisibilityChanged,
                    fillHeight = true,
                )
                UiMode.Material -> FolderCardMaterial(
                    folder = folder,
                    isSelectionMode = uiState.isSelectionMode,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onVisibilityChanged = actions.onPreviewVisibilityChanged,
                    fillHeight = true,
                )
            }
        }
    }
}

/**
 * Non-blocking strip for remote (WebDAV/SMB/FTP) failures. Local folders can
 * still be browsed, but the rejection must not stay invisible the way an
 * empty remote result used to.
 */
@Composable
private fun RemoteErrorBanner(message: String, modifier: Modifier = Modifier) {
    // The full Rust error is multi-line; the banner shows just the summary line
    // (the dialog keeps the verbatim detail).
    val summary = message.lineSequence().firstOrNull { it.isNotBlank() } ?: message
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summary,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FolderMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
