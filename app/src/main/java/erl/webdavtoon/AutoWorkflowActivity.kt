// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import erl.webdavtoon.ui.screen.autoworkflow.AutoWorkflowActions
import erl.webdavtoon.ui.screen.autoworkflow.AutoWorkflowScreen
import erl.webdavtoon.ui.screen.autoworkflow.AutoWorkflowViewModel
import erl.webdavtoon.ui.theme.WebDAVToonTheme

/**
 * Hosts the AutoWorkflow output browser.
 *
 * The output tree, the task number behind each generated image and the original input image all
 * come from the AutoWorkflow backend's `/api/output/browse`. Resubmit and delete are reviewer
 * batch actions: the screen previews them before anything is written.
 */
class AutoWorkflowActivity : ComponentActivity() {

    private val viewModel: AutoWorkflowViewModel by viewModels()
    private var hasResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val navOwner = rememberNavigationEventDispatcherOwner(parent = null)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navOwner) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val settings = SettingsManager(this)
                WebDAVToonTheme(
                    themeId = settings.getThemeId(),
                    useCouiDefaultColors = settings.useCouiDefaultColors(),
                ) {
                    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
                    AutoWorkflowScreen(
                        state = uiState,
                        actions = AutoWorkflowActions(
                            onBack = { finish() },
                            onRefresh = { viewModel.refresh() },
                            onUp = { viewModel.navigateUp() },
                            onOpenFolder = { relPath -> viewModel.openFolder(relPath) },
                            onLoadMore = { viewModel.loadMore() },
                            onSetSortOrder = viewModel::setSortOrder,
                            getScrollOffset = viewModel::scrollOffsetFor,
                            onScrollOffsetChanged = viewModel::saveScrollOffset,
                            onToggleSelection = viewModel::toggleSelection,
                            onSelectAll = viewModel::selectAll,
                            onClearSelection = viewModel::clearSelection,
                            onPreviewReedit = { taskIds, promptText, deleteOldOutput ->
                                viewModel.previewResubmit(
                                    taskIds = taskIds,
                                    flow = AutoWorkflowClient.FLOW_EDIT,
                                    deleteOldOutput = deleteOldOutput,
                                    promptText = promptText,
                                )
                            },
                            onPreviewResubmit = { taskIds, flow, deleteOldOutput ->
                                viewModel.previewResubmit(
                                    taskIds = taskIds,
                                    flow = flow,
                                    deleteOldOutput = deleteOldOutput,
                                )
                            },
                            onConfirmPendingAction = viewModel::confirmPendingAction,
                            onDismissPendingAction = viewModel::dismissPendingAction,
                            onDeleteTasks = viewModel::deleteTasks,
                            onMessageShown = viewModel::consumeActionMessage,
                            onOpenSettings = {
                                startActivity(
                                    Intent(
                                        this@AutoWorkflowActivity,
                                        SettingsActivity::class.java,
                                    ),
                                )
                            },
                        ),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Only reload when returning from Settings with a changed server address, or apply
        // any deleted tasks from the reader locally without resetting list position.
        if (hasResumed) viewModel.onHostResumed() else hasResumed = true
    }
}
