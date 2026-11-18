// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.autoworkflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import erl.webdavtoon.AutoWorkflowClient
import erl.webdavtoon.R
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.TerminalHubLogResult
import erl.webdavtoon.TerminalHubStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TerminalHubUiState(
    val isConfigured: Boolean = true,
    val serverUrl: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val status: TerminalHubStatus? = null,
    val busyServiceName: String? = null,
    val groupActionRunning: Boolean = false,
    val actionMessage: String? = null,
    val logTargetName: String? = null,
    val logLines: Int = 200,
    val logLoading: Boolean = false,
    val logError: String? = null,
    val logResult: TerminalHubLogResult? = null,
)

/**
 * State holder for the Terminal Hub control panel accessed from the AutoWorkflow output screen.
 */
class TerminalHubViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsManager = SettingsManager(app)
    private val _uiState = MutableStateFlow(TerminalHubUiState())
    val uiState = _uiState.asStateFlow()

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

    fun loadStatus(showSpinner: Boolean = true) {
        val baseUrl = settingsManager.getTerminalHubUrl()
        if (baseUrl.isBlank()) {
            _uiState.update {
                it.copy(
                    isConfigured = false,
                    serverUrl = "",
                    loading = false,
                    error = null,
                    status = null,
                )
            }
            return
        }
        val urlChanged = baseUrl != _uiState.value.serverUrl
        _uiState.update {
            it.copy(
                isConfigured = true,
                serverUrl = baseUrl,
                loading = if (showSpinner || urlChanged) true else it.loading,
                error = null,
                status = if (urlChanged) null else it.status,
            )
        }
        viewModelScope.launch {
            clientFor(baseUrl).fetchTerminalHubStatus().fold(
                onSuccess = { status ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = null,
                            status = status,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: getApplication<Application>()
                                .getString(R.string.autoworkflow_action_failed_unknown),
                        )
                    }
                },
            )
        }
    }

    fun refreshIfServerChanged() {
        val baseUrl = settingsManager.getTerminalHubUrl()
        if (baseUrl != _uiState.value.serverUrl) {
            loadStatus(showSpinner = true)
        }
    }

    fun performServiceAction(name: String, action: String) {
        val state = _uiState.value
        if (state.busyServiceName != null || state.groupActionRunning) return
        val baseUrl = settingsManager.getTerminalHubUrl()
        if (baseUrl.isBlank()) return

        _uiState.update { it.copy(busyServiceName = name, actionMessage = null) }
        viewModelScope.launch {
            clientFor(baseUrl).controlTerminalHubService(name, action).fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            busyServiceName = null,
                            actionMessage = result.message.ifBlank { "$name: $action" },
                        )
                    }
                    // Give the restarted/started process a brief beat before refreshing status.
                    delay(if (name.equals("AWF Backend", ignoreCase = true) && action == AutoWorkflowClient.HUB_ACTION_RESTART) 2500L else 400L)
                    loadStatus(showSpinner = false)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            busyServiceName = null,
                            actionMessage = getApplication<Application>().getString(
                                R.string.autoworkflow_action_failed,
                                error.message.orEmpty(),
                            ),
                        )
                    }
                },
            )
        }
    }

    fun performGroupAction(action: String) {
        val state = _uiState.value
        if (state.busyServiceName != null || state.groupActionRunning) return
        val baseUrl = settingsManager.getTerminalHubUrl()
        if (baseUrl.isBlank()) return

        _uiState.update { it.copy(groupActionRunning = true, actionMessage = null) }
        viewModelScope.launch {
            clientFor(baseUrl).controlAutoWorkflowGroup(action).fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            groupActionRunning = false,
                            actionMessage = result.message.ifBlank { action },
                        )
                    }
                    delay(400L)
                    loadStatus(showSpinner = false)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            groupActionRunning = false,
                            actionMessage = getApplication<Application>().getString(
                                R.string.autoworkflow_action_failed,
                                error.message.orEmpty(),
                            ),
                        )
                    }
                },
            )
        }
    }

    fun openLog(name: String) {
        val currentLines = _uiState.value.logLines
        _uiState.update {
            it.copy(
                logTargetName = name,
                logLoading = true,
                logError = null,
                logResult = null,
            )
        }
        fetchLogInternal(name, currentLines)
    }

    fun setLogLines(lines: Int) {
        val target = _uiState.value.logTargetName ?: return
        _uiState.update { it.copy(logLines = lines, logLoading = true, logError = null) }
        fetchLogInternal(target, lines)
    }

    fun refreshLog() {
        val target = _uiState.value.logTargetName ?: return
        val lines = _uiState.value.logLines
        _uiState.update { it.copy(logLoading = true, logError = null) }
        fetchLogInternal(target, lines)
    }

    fun closeLog() {
        _uiState.update {
            it.copy(
                logTargetName = null,
                logLoading = false,
                logError = null,
                logResult = null,
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    private fun fetchLogInternal(name: String, lines: Int) {
        val baseUrl = settingsManager.getTerminalHubUrl()
        if (baseUrl.isBlank()) {
            _uiState.update { it.copy(logLoading = false, logError = "Terminal Hub server is not configured") }
            return
        }
        viewModelScope.launch {
            clientFor(baseUrl).fetchTerminalHubLog(name, lines).fold(
                onSuccess = { result ->
                    if (_uiState.value.logTargetName == name) {
                        _uiState.update {
                            it.copy(
                                logLoading = false,
                                logError = null,
                                logResult = result,
                            )
                        }
                    }
                },
                onFailure = { error ->
                    if (_uiState.value.logTargetName == name) {
                        _uiState.update {
                            it.copy(
                                logLoading = false,
                                logError = error.message ?: getApplication<Application>()
                                    .getString(R.string.autoworkflow_action_failed_unknown),
                            )
                        }
                    }
                },
            )
        }
    }
}
