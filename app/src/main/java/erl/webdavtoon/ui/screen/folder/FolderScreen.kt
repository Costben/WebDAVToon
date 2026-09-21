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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import erl.webdavtoon.ui.component.AppAdaptiveNavigationScaffold
import erl.webdavtoon.ui.component.ServerSheetActions
import erl.webdavtoon.ui.screen.waterfall.FolderCardExtraHeight
import erl.webdavtoon.ui.screen.waterfall.FollowZoomWaterfallLayout
import erl.webdavtoon.ui.screen.waterfall.rememberFollowZoomGridState
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import io.github.suqi8.coui.kmp.basic.Icon as MiuixIcon
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text as MiuixText
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.theme.COUITheme

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

@Composable
fun FolderScreen(
    uiState: FolderUiState,
    actions: FolderScreenActions,
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
    val topBarActions = FolderTopBarActions(
        onOpenDrawer = { showServerSheet = true },
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

    val topAppBarScrollBehavior = COUIScrollBehavior(rememberTopAppBarState())

    AppAdaptiveNavigationScaffold(
        showServerSheet = showServerSheet,
        onDismissServerSheet = { showServerSheet = false },
        slots = uiState.slots,
        isPrivacyMode = uiState.isPrivacyMode,
        actions = serverSheetActions,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize().background(COUITheme.colorScheme.surface)) {
            Column(modifier = Modifier.fillMaxSize()) {
                FolderTopBarMiuix(
                    uiState = uiState,
                    actions = topBarActions,
                    scrollBehavior = topAppBarScrollBehavior,
                )
                uiState.remoteError?.let { message ->
                    RemoteErrorBanner(message = message)
                }
                PullToRefresh(
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
            MiuixCircularProgressIndicator()
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
            spacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            state = zoomState,
            itemExtraHeights = extraHeights,
            itemHorizontalPadding = 0.dp,
            modifier = modifier
                .navigationBarsPadding()
                .then(
                    if (scrollBehavior != null) {
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        Modifier
                    }
                ),
        ) { index, _, _ ->
            // Filtering can shrink the list while a subcomposition for a stale index is still
            // alive, so a missing index must render nothing instead of crashing the composer.
            val folder = folders.getOrNull(index) ?: return@FollowZoomWaterfallLayout
            val onClick = {
                if (uiState.isSelectionMode) actions.onToggleSelection(folder.path)
                else actions.onFolderClick(folder)
            }
            val onLongClick = { actions.onToggleSelection(folder.path) }
            FolderCardMiuix(
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
            .background(COUITheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiuixText(
            text = summary,
            modifier = Modifier.weight(1f),
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onErrorContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FolderMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        MiuixText(text = text, color = COUITheme.colorScheme.onSurfaceVariantSummary)
    }
}
