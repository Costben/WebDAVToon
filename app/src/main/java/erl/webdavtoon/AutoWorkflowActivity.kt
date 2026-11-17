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
 * come from the AutoWorkflow backend's read-only `/api/output/browse`; nothing is persisted here.
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
                    LaunchedEffect(Unit) { viewModel.load("") }
                    AutoWorkflowScreen(
                        state = uiState,
                        actions = AutoWorkflowActions(
                            onBack = { finish() },
                            onRefresh = { viewModel.refresh() },
                            onUp = { viewModel.load(uiState.parentRelPath) },
                            onOpenFolder = { relPath -> viewModel.load(relPath) },
                            onLoadMore = { viewModel.loadMore() },
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
        // Coming back from Settings may have changed the server address.
        if (hasResumed) viewModel.refresh() else hasResumed = true
    }
}
