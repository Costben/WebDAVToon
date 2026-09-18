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
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import erl.webdavtoon.R
import erl.webdavtoon.ui.UiMode
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class MixedWaterfallActions(
    val onBackClick: () -> Unit,
    val onItemClick: (MixedWaterfallItemUi) -> Unit,
    val onItemLongClick: (MixedWaterfallItemUi) -> Unit,
    val onRefresh: () -> Unit,
    val onColumnsChange: (Int) -> Unit,
    val onToggleSelectAll: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onDeleteClick: () -> Unit,
    val onShareClick: () -> Unit,
    val onExitSelectionMode: () -> Unit,
    val onDimensionsResolved: (photoId: String, width: Int, height: Int) -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (uiState.uiMode == UiMode.Miuix) MiuixTheme.colorScheme.background
                else MaterialTheme.colorScheme.background
            )
    ) {
        if (uiState.uiMode == UiMode.Miuix) {
            MixedWaterfallTopBarMiuix(uiState = uiState, actions = actions)
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
                !uiState.loading && uiState.items.isEmpty() -> {
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
                        columns = StaggeredGridCells.Fixed(uiState.columns),
                        modifier = Modifier
                            .fillMaxSize()
                            .followZoom(zoomState),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalItemSpacing = 8.dp,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.items, key = { it.key }) { item ->
                            if (uiState.uiMode == UiMode.Miuix) {
                                MediaCardMiuix(
                                    item = item,
                                    showFilename = uiState.showFilenames,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onClick = { actions.onItemClick(item) },
                                    onLongClick = { actions.onItemLongClick(item) },
                                    onDimensionsResolved = actions.onDimensionsResolved,
                                )
                            } else {
                                MediaCardMaterial(
                                    item = item,
                                    showFilename = uiState.showFilenames,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onClick = { actions.onItemClick(item) },
                                    onLongClick = { actions.onItemLongClick(item) },
                                    onDimensionsResolved = actions.onDimensionsResolved,
                                )
                            }
                        }
                    }
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
    modifier: Modifier = Modifier,
) {
    var columnsMenuExpanded by remember { mutableStateOf(false) }

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

    MiuixTopAppBar(
        modifier = modifier.statusBarsPadding(),
        title = titleText,
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
                MiuixIconButton(onClick = actions.onRefresh) {
                    MiuixIcon(
                        MiuixIcons.Light.Refresh,
                        contentDescription = stringResource(R.string.refresh_status_refreshing),
                    )
                }

                Box {
                    MiuixIconButton(onClick = { columnsMenuExpanded = true }) {
                        MiuixIcon(
                            MiuixIcons.Light.GridView,
                            contentDescription = stringResource(R.string.grid_columns),
                        )
                    }

                    DropdownMenu(
                        expanded = columnsMenuExpanded,
                        onDismissRequest = { columnsMenuExpanded = false },
                    ) {
                        (1..5).forEach { col ->
                            DropdownMenuItem(
                                text = {
                                    M3Text(
                                        text = stringResource(R.string.columns_suffix, col),
                                        color = if (uiState.columns == col) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                },
                                onClick = {
                                    actions.onColumnsChange(col)
                                    columnsMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MixedWaterfallTopBarMaterial(
    uiState: MixedWaterfallUiState,
    actions: MixedWaterfallActions,
    modifier: Modifier = Modifier,
) {
    var columnsMenuExpanded by remember { mutableStateOf(false) }

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

    M3TopAppBar(
        modifier = modifier.statusBarsPadding(),
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
                M3IconButton(onClick = actions.onRefresh) {
                    M3Icon(
                        MiuixIcons.Light.Refresh,
                        contentDescription = stringResource(R.string.refresh_status_refreshing),
                    )
                }

                Box {
                    M3IconButton(onClick = { columnsMenuExpanded = true }) {
                        M3Icon(
                            MiuixIcons.Light.GridView,
                            contentDescription = stringResource(R.string.grid_columns),
                        )
                    }

                    DropdownMenu(
                        expanded = columnsMenuExpanded,
                        onDismissRequest = { columnsMenuExpanded = false },
                    ) {
                        (1..5).forEach { col ->
                            DropdownMenuItem(
                                text = {
                                    M3Text(
                                        text = stringResource(R.string.columns_suffix, col),
                                        color = if (uiState.columns == col) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                },
                                onClick = {
                                    actions.onColumnsChange(col)
                                    columnsMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        colors = colors,
    )
}
