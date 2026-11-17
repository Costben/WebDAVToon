// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.autoworkflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import erl.webdavtoon.AutoWorkflowClient
import erl.webdavtoon.AutoWorkflowCrumb
import erl.webdavtoon.AutoWorkflowFolder
import erl.webdavtoon.AutoWorkflowImage
import erl.webdavtoon.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable snapshot of one directory level.
 *
 * The screen keeps the level it is leaving around so the outgoing page can animate away with its
 * own content instead of flashing the new directory's (still empty) grid.
 */
data class AutoWorkflowLevelState(
    val relPath: String,
    val folders: List<AutoWorkflowFolder> = emptyList(),
    val images: List<AutoWorkflowImage> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val isConfigured: Boolean = true,
) {
    val isEmpty: Boolean
        get() = !loading && error == null && folders.isEmpty() && images.isEmpty()
}

data class AutoWorkflowUiState(
    val relPath: String = "",
    val absPath: String = "",
    val rootPath: String = "",
    val parentRelPath: String = "",
    val breadcrumbs: List<AutoWorkflowCrumb> = emptyList(),
    val folders: List<AutoWorkflowFolder> = emptyList(),
    val images: List<AutoWorkflowImage> = emptyList(),
    val totalImages: Int = 0,
    val hasMore: Boolean = false,
    val linkedLoaded: Int = 0,
    val folderCount: Int = 0,
    val taskCount: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val isConfigured: Boolean = true,
    val serverUrl: String = "",
    val previousLevel: AutoWorkflowLevelState? = null,
) {
    val isRoot: Boolean get() = relPath.isEmpty()
    val empty: Boolean
        get() = !loading && error == null && folders.isEmpty() && images.isEmpty()

    fun levelState(): AutoWorkflowLevelState = AutoWorkflowLevelState(
        relPath = relPath,
        folders = folders,
        images = images,
        loading = loading,
        error = error,
        isConfigured = isConfigured,
    )
}

/**
 * Page state for the AutoWorkflow output explorer.
 *
 * Every request is a GET against the backend's read-only `/api/output/browse`; nothing here
 * touches the AutoWorkflow database directly.
 */
class AutoWorkflowViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsManager = SettingsManager(app)
    private val _uiState = MutableStateFlow(AutoWorkflowUiState())
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var client: AutoWorkflowClient? = null
    private var clientBaseUrl: String = ""

    private fun clientFor(baseUrl: String): AutoWorkflowClient {
        val cached = client
        if (cached != null && baseUrl == clientBaseUrl) return cached
        return AutoWorkflowClient(baseUrl).also {
            client = it
            clientBaseUrl = baseUrl
        }
    }

    fun load(relPath: String = _uiState.value.relPath) {
        val baseUrl = settingsManager.getAutoWorkflowUrl()
        if (baseUrl.isBlank()) {
            _uiState.update {
                it.copy(
                    loading = false,
                    loadingMore = false,
                    isConfigured = false,
                    error = null,
                    serverUrl = "",
                    relPath = relPath,
                    previousLevel = if (relPath == it.relPath) it.previousLevel else it.levelState(),
                    folders = emptyList(),
                    images = emptyList(),
                )
            }
            return
        }

        loadJob?.cancel()
        val sameFolder = relPath == _uiState.value.relPath
        _uiState.update {
            it.copy(
                loading = true,
                loadingMore = false,
                isConfigured = true,
                serverUrl = baseUrl,
                error = null,
                relPath = relPath,
                previousLevel = if (sameFolder) it.previousLevel else it.levelState(),
                folders = if (sameFolder) it.folders else emptyList(),
                images = if (sameFolder) it.images else emptyList(),
                totalImages = if (sameFolder) it.totalImages else 0,
                hasMore = false,
                linkedLoaded = if (sameFolder) it.linkedLoaded else 0,
            )
        }
        loadJob = viewModelScope.launch {
            clientFor(baseUrl).browse(relPath, offset = 0).fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = null,
                            relPath = page.relPath,
                            absPath = page.absPath,
                            rootPath = page.rootPath,
                            parentRelPath = page.parentRelPath,
                            breadcrumbs = page.breadcrumbs,
                            folders = page.folders,
                            images = page.images,
                            totalImages = page.totalImages,
                            hasMore = page.hasMore,
                            linkedLoaded = page.linkedInPage,
                            folderCount = page.folderCount,
                            taskCount = page.taskCount,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "AutoWorkflow request failed",
                        )
                    }
                },
            )
        }
    }

    fun refresh() {
        load(_uiState.value.relPath)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || !state.hasMore) return
        val baseUrl = state.serverUrl.ifBlank { settingsManager.getAutoWorkflowUrl() }
        if (baseUrl.isBlank()) return
        _uiState.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            clientFor(baseUrl).browse(state.relPath, offset = state.images.size).fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(
                            loadingMore = false,
                            images = it.images + page.images,
                            totalImages = page.totalImages,
                            hasMore = page.hasMore,
                            linkedLoaded = it.linkedLoaded + page.linkedInPage,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            loadingMore = false,
                            error = error.message ?: "AutoWorkflow request failed",
                        )
                    }
                },
            )
        }
    }
}
