// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.autoworkflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import erl.webdavtoon.AutoWorkflowActionResult
import erl.webdavtoon.AutoWorkflowClient
import erl.webdavtoon.AutoWorkflowCompareStore
import erl.webdavtoon.AutoWorkflowPage
import erl.webdavtoon.AutoWorkflowCrumb
import erl.webdavtoon.AutoWorkflowFolder
import erl.webdavtoon.AutoWorkflowImage
import erl.webdavtoon.R
import erl.webdavtoon.SettingsManager
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How many directory listings stay in memory so stepping back up paints instantly. */
private const val LEVEL_CACHE_SIZE = 32

/** Maximum backend pages scanned per step when filtering out unlinked images. */
private const val MAX_FILTER_PAGE_HOPS = 8

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

/** A resubmit request the backend has previewed and the user has not confirmed yet. */
data class AutoWorkflowPendingAction(
    val action: String,
    val taskIds: List<Long>,
    val flow: String,
    val deleteOldOutput: Boolean,
    val message: String,
    val promptText: String? = null,
)

data class AutoWorkflowUiState(
    val relPath: String = "",
    val absPath: String = "",
    val rootPath: String = "",
    val parentRelPath: String = "",
    val breadcrumbs: List<AutoWorkflowCrumb> = emptyList(),
    val folders: List<AutoWorkflowFolder> = emptyList(),
    val images: List<AutoWorkflowImage> = emptyList(),
    val rawImageOffset: Int = 0,
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
    val selectedTaskIds: Set<Long> = emptySet(),
    val actionRunning: Boolean = false,
    val pendingConfirm: AutoWorkflowPendingAction? = null,
    val actionMessage: String? = null,
    val sortOrder: Int = SettingsManager.SORT_DATE_DESC,
) {
    val isRoot: Boolean get() = relPath.isEmpty()
    val isSelectionMode: Boolean get() = selectedTaskIds.isNotEmpty()
    val selectedCount: Int get() = selectedTaskIds.size
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

    /** One directory's last successful listing, so going back up paints immediately. */
    private data class CachedLevel(
        val absPath: String,
        val rootPath: String,
        val parentRelPath: String,
        val breadcrumbs: List<AutoWorkflowCrumb>,
        val folders: List<AutoWorkflowFolder>,
        val images: List<AutoWorkflowImage>,
        val rawImageOffset: Int,
        val totalImages: Int,
        val hasMore: Boolean,
        val linkedLoaded: Int,
        val taskCount: Int,
    )

    private val settingsManager = SettingsManager(app)
    private val _uiState = MutableStateFlow(
        AutoWorkflowUiState(sortOrder = settingsManager.getAutoWorkflowSortOrder()),
    )
    val uiState = _uiState.asStateFlow()

    private val levelCache = object : LinkedHashMap<String, CachedLevel>(LEVEL_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedLevel>): Boolean =
            size > LEVEL_CACHE_SIZE
    }

    private val scrollOffsets = LinkedHashMap<String, Int>(LEVEL_CACHE_SIZE, 0.75f, true).apply {
        putAll(settingsManager.getAutoWorkflowScrollOffsets())
    }

    private var hasLoadedOnce = false
    private var loadJob: Job? = null
    private var persistScrollJob: Job? = null
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

    fun scrollOffsetFor(relPath: String): Int = scrollOffsets[relPath] ?: 0

    fun saveScrollOffset(relPath: String, offset: Int) {
        val state = _uiState.value
        if (relPath != state.relPath) return
        // Only folder-listing levels need cross-navigation scroll memory so stepping back up
        // restores the folder list position; leaf image folders always open from the top.
        if (state.folders.isEmpty()) return
        val clean = offset.coerceAtLeast(0)
        val previous = scrollOffsets[relPath]
        if (clean == 0 && (previous == null || previous == 0)) return
        if (previous == clean) return
        if (clean == 0) {
            scrollOffsets.remove(relPath)
        } else {
            scrollOffsets[relPath] = clean
        }
        schedulePersistScrollOffsets()
    }

    fun openFolder(relPath: String) {
        if (scrollOffsets.remove(relPath) != null) {
            schedulePersistScrollOffsets()
        }
        load(relPath)
    }

    fun navigateUp() {
        val current = _uiState.value.relPath
        val parent = _uiState.value.parentRelPath
        if (current != parent && scrollOffsets.remove(current) != null) {
            schedulePersistScrollOffsets()
        }
        load(parent)
    }

    private fun schedulePersistScrollOffsets() {
        persistScrollJob?.cancel()
        val snapshot = LinkedHashMap(scrollOffsets)
        persistScrollJob = viewModelScope.launch {
            delay(350)
            settingsManager.setAutoWorkflowScrollOffsets(snapshot)
        }
    }

    override fun onCleared() {
        persistScrollJob?.cancel()
        settingsManager.setAutoWorkflowScrollOffsets(LinkedHashMap(scrollOffsets))
        super.onCleared()
    }

    fun ensureLoaded() {
        val currentUrl = settingsManager.getAutoWorkflowUrl()
        if (hasLoadedOnce && _uiState.value.serverUrl == currentUrl) return
        hasLoadedOnce = true
        load("", forceRefresh = true)
    }

    fun onHostResumed() {
        if (!hasLoadedOnce) return
        val currentUrl = settingsManager.getAutoWorkflowUrl()
        if (currentUrl != _uiState.value.serverUrl) {
            levelCache.clear()
            clearAllScrollOffsets()
            load("", forceRefresh = true)
            return
        }
        val externallyDeleted = AutoWorkflowCompareStore.consumeDeletedTasks()
        if (externallyDeleted.isNotEmpty()) {
            removeDeletedTasksLocally(externallyDeleted)
        }
    }

    fun load(relPath: String = _uiState.value.relPath, forceRefresh: Boolean = false) {
        hasLoadedOnce = true
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

        val sameFolder = relPath == _uiState.value.relPath
        val cached = if (!forceRefresh) levelCache[relPath] else null
        if (cached != null) {
            loadJob?.cancel()
            _uiState.update {
                it.copy(
                    loading = false,
                    loadingMore = false,
                    isConfigured = true,
                    serverUrl = baseUrl,
                    error = null,
                    relPath = relPath,
                    absPath = cached.absPath,
                    rootPath = cached.rootPath,
                    parentRelPath = cached.parentRelPath,
                    breadcrumbs = cached.breadcrumbs,
                    previousLevel = if (sameFolder) it.previousLevel else it.levelState(),
                    folders = cached.folders,
                    images = cached.images,
                    rawImageOffset = cached.rawImageOffset,
                    totalImages = cached.totalImages,
                    hasMore = cached.hasMore,
                    linkedLoaded = cached.linkedLoaded,
                    folderCount = cached.folders.size,
                    taskCount = cached.taskCount,
                    selectedTaskIds = if (sameFolder) it.selectedTaskIds else emptySet(),
                    pendingConfirm = null,
                )
            }
            return
        }

        loadJob?.cancel()
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
                rawImageOffset = if (sameFolder) it.rawImageOffset else 0,
                totalImages = if (sameFolder) it.totalImages else 0,
                hasMore = if (sameFolder) it.hasMore else false,
                linkedLoaded = if (sameFolder) it.linkedLoaded else 0,
                // Selection belongs to the level it was made in; walking into another directory
                // starts clean instead of carrying invisible task ids along.
                selectedTaskIds = if (sameFolder) it.selectedTaskIds else emptySet(),
                pendingConfirm = null,
            )
        }
        val activeSortOrder = _uiState.value.sortOrder
        val backendSort = backendSortFor(activeSortOrder)
        loadJob = viewModelScope.launch {
            fetchLinkedBatch(clientFor(baseUrl), relPath, startOffset = 0, sort = backendSort).fold(
                onSuccess = { batch ->
                    val page = batch.firstPage
                    val linkedFolders = sortAutoWorkflowFolders(
                        page.folders.filter { it.taskCount > 0 },
                        activeSortOrder,
                    )
                    val linkedImages = sortAutoWorkflowImages(batch.linkedImages, activeSortOrder)
                    val nextOffset = batch.nextRawOffset
                    levelCache[page.relPath] = CachedLevel(
                        absPath = page.absPath,
                        rootPath = page.rootPath,
                        parentRelPath = page.parentRelPath,
                        breadcrumbs = page.breadcrumbs,
                        folders = linkedFolders,
                        images = linkedImages,
                        rawImageOffset = nextOffset,
                        totalImages = batch.totalImages,
                        hasMore = batch.hasMore,
                        linkedLoaded = batch.linkedInBatch,
                        taskCount = page.taskCount,
                    )
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = null,
                            relPath = page.relPath,
                            absPath = page.absPath,
                            rootPath = page.rootPath,
                            parentRelPath = page.parentRelPath,
                            breadcrumbs = page.breadcrumbs,
                            folders = linkedFolders,
                            images = linkedImages,
                            rawImageOffset = nextOffset,
                            totalImages = batch.totalImages,
                            hasMore = batch.hasMore,
                            linkedLoaded = batch.linkedInBatch,
                            folderCount = linkedFolders.size,
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
        load(_uiState.value.relPath, forceRefresh = true)
    }

    fun setSortOrder(order: Int) {
        val current = _uiState.value.sortOrder
        val isRandom = order == SettingsManager.SORT_RANDOM_FOLDERS ||
            order == SettingsManager.SORT_RANDOM_PHOTOS
        if (current == order && !isRandom) return
        settingsManager.setAutoWorkflowSortOrder(order)
        levelCache.clear()
        clearAllScrollOffsets()
        _uiState.update { it.copy(sortOrder = order) }
        load(_uiState.value.relPath, forceRefresh = true)
    }

    private fun clearAllScrollOffsets() {
        persistScrollJob?.cancel()
        scrollOffsets.clear()
        settingsManager.clearAutoWorkflowScrollOffsets()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || !state.hasMore) return
        val baseUrl = state.serverUrl.ifBlank { settingsManager.getAutoWorkflowUrl() }
        if (baseUrl.isBlank()) return
        _uiState.update { it.copy(loadingMore = true) }
        val activeSortOrder = state.sortOrder
        val backendSort = backendSortFor(activeSortOrder)
        viewModelScope.launch {
            fetchLinkedBatch(
                clientFor(baseUrl),
                state.relPath,
                startOffset = state.rawImageOffset,
                sort = backendSort,
            ).fold(
                onSuccess = { batch ->
                    val sortedBatch = sortAutoWorkflowImages(batch.linkedImages, activeSortOrder)
                    _uiState.update {
                        it.copy(
                            loadingMore = false,
                            images = it.images + sortedBatch,
                            rawImageOffset = batch.nextRawOffset,
                            totalImages = batch.totalImages,
                            hasMore = batch.hasMore,
                            linkedLoaded = it.linkedLoaded + batch.linkedInBatch,
                        )
                    }
                    val loaded = _uiState.value
                    levelCache[loaded.relPath] = CachedLevel(
                        absPath = loaded.absPath,
                        rootPath = loaded.rootPath,
                        parentRelPath = loaded.parentRelPath,
                        breadcrumbs = loaded.breadcrumbs,
                        folders = loaded.folders,
                        images = loaded.images,
                        rawImageOffset = loaded.rawImageOffset,
                        totalImages = loaded.totalImages,
                        hasMore = loaded.hasMore,
                        linkedLoaded = loaded.linkedLoaded,
                        taskCount = loaded.taskCount,
                    )
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

    private data class LinkedImageBatch(
        val firstPage: AutoWorkflowPage,
        val linkedImages: List<AutoWorkflowImage>,
        val nextRawOffset: Int,
        val totalImages: Int,
        val hasMore: Boolean,
        val linkedInBatch: Int,
    )

    private suspend fun fetchLinkedBatch(
        client: AutoWorkflowClient,
        relPath: String,
        startOffset: Int,
        sort: String = AutoWorkflowClient.DEFAULT_SORT,
    ): Result<LinkedImageBatch> {
        val initial = client.browse(relPath, offset = startOffset, sort = sort)
            .getOrElse { return Result.failure(it) }
        val collected = initial.images.filter { it.isLinked }.toMutableList()
        var nextOffset = startOffset + initial.images.size
        var totalImages = initial.totalImages
        var hasMore = initial.hasMore
        var linkedInBatch = initial.linkedInPage
        var hops = 1
        while (collected.isEmpty() && hasMore && hops < MAX_FILTER_PAGE_HOPS) {
            val next = client.browse(relPath, offset = nextOffset, sort = sort).getOrNull() ?: break
            if (next.images.isEmpty()) {
                hasMore = false
                break
            }
            collected += next.images.filter { it.isLinked }
            nextOffset += next.images.size
            totalImages = next.totalImages
            hasMore = next.hasMore
            linkedInBatch += next.linkedInPage
            hops++
        }
        return Result.success(
            LinkedImageBatch(
                firstPage = initial,
                linkedImages = collected,
                nextRawOffset = nextOffset,
                totalImages = totalImages,
                hasMore = hasMore,
                linkedInBatch = linkedInBatch,
            ),
        )
    }

    fun toggleSelection(taskId: Long) {
        _uiState.update { state ->
            val selected = state.selectedTaskIds
            state.copy(
                selectedTaskIds = if (taskId in selected) selected - taskId else selected + taskId,
            )
        }
    }

    /** Selecting "all" only covers images that carry a task: the rest cannot be re-run or deleted. */
    fun selectAll() {
        _uiState.update { state ->
            val linked = state.images.mapNotNull { it.task?.id }
            state.copy(selectedTaskIds = linked.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedTaskIds = emptySet()) }
    }

    /**
     * First half of a mutation: the backend previews the resubmit and the confirm dialog shows its
     * wording. Nothing is written until [confirmPendingAction] runs the same request again.
     */
    fun previewResubmit(
        taskIds: List<Long>,
        flow: String,
        deleteOldOutput: Boolean,
        promptText: String? = null,
    ) {
        if (taskIds.isEmpty() || _uiState.value.actionRunning) return
        val baseUrl = _uiState.value.serverUrl.ifBlank { settingsManager.getAutoWorkflowUrl() }
        if (baseUrl.isBlank()) {
            _uiState.update { it.copy(actionMessage = errorString(R.string.autoworkflow_configure_first)) }
            return
        }
        val cleanPrompt = promptText?.trim()?.takeIf { it.isNotEmpty() }
        _uiState.update { it.copy(actionRunning = true, actionMessage = null) }
        viewModelScope.launch {
            clientFor(baseUrl).resubmit(
                taskIds = taskIds,
                flow = flow,
                deleteOldOutput = deleteOldOutput,
                confirm = false,
                promptText = cleanPrompt,
            ).fold(
                onSuccess = { result ->
                    when {
                        result.isBlocked -> _uiState.update {
                            it.copy(
                                actionRunning = false,
                                actionMessage = result.message.ifBlank {
                                    errorString(R.string.autoworkflow_action_failed_unknown)
                                },
                            )
                        }

                        result.isPreview -> _uiState.update {
                            it.copy(
                                actionRunning = false,
                                pendingConfirm = AutoWorkflowPendingAction(
                                    action = AutoWorkflowClient.ACTION_RESUBMIT,
                                    taskIds = taskIds,
                                    flow = flow,
                                    deleteOldOutput = deleteOldOutput,
                                    message = if (cleanPrompt != null) {
                                        reeditConfirmText(taskIds.size, deleteOldOutput)
                                    } else {
                                        resubmitConfirmText(taskIds.size, flow, deleteOldOutput)
                                    },
                                    promptText = cleanPrompt,
                                ),
                            )
                        }

                        else -> {
                            val removed = if (flow == AutoWorkflowClient.FLOW_EDIT && deleteOldOutput) {
                                result.affectedTaskIds.ifEmpty { taskIds }.toSet()
                            } else {
                                emptySet()
                            }
                            finishAction(result, R.string.autoworkflow_resubmit_done, removed)
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(actionRunning = false, actionMessage = failureText(error))
                    }
                },
            )
        }
    }

    fun confirmPendingAction() {
        val pending = _uiState.value.pendingConfirm ?: return
        val baseUrl = _uiState.value.serverUrl.ifBlank { settingsManager.getAutoWorkflowUrl() }
        if (baseUrl.isBlank() || _uiState.value.actionRunning) return
        _uiState.update { it.copy(actionRunning = true, pendingConfirm = null) }
        viewModelScope.launch {
            clientFor(baseUrl).resubmit(
                taskIds = pending.taskIds,
                flow = pending.flow,
                deleteOldOutput = pending.deleteOldOutput,
                confirm = true,
                promptText = pending.promptText,
            ).fold(
                onSuccess = { result ->
                    if (result.executed) {
                        val removed = if (
                            pending.flow == AutoWorkflowClient.FLOW_EDIT && pending.deleteOldOutput
                        ) {
                            result.affectedTaskIds.ifEmpty { pending.taskIds }.toSet()
                        } else {
                            emptySet()
                        }
                        finishAction(result, R.string.autoworkflow_resubmit_done, removed)
                    } else {
                        _uiState.update {
                            it.copy(
                                actionRunning = false,
                                actionMessage = result.message.ifBlank {
                                    errorString(R.string.autoworkflow_action_failed_unknown)
                                },
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(actionRunning = false, actionMessage = failureText(error))
                    }
                },
            )
        }
    }

    fun dismissPendingAction() {
        _uiState.update { it.copy(pendingConfirm = null) }
    }

    fun deleteTasks(taskIds: List<Long>) {
        if (taskIds.isEmpty() || _uiState.value.actionRunning) return
        val baseUrl = _uiState.value.serverUrl.ifBlank { settingsManager.getAutoWorkflowUrl() }
        if (baseUrl.isBlank()) {
            _uiState.update { it.copy(actionMessage = errorString(R.string.autoworkflow_configure_first)) }
            return
        }
        _uiState.update { it.copy(actionRunning = true, actionMessage = null) }
        viewModelScope.launch {
            clientFor(baseUrl).deleteTasks(taskIds).fold(
                onSuccess = { result ->
                    if (result.executed) {
                        val removed = result.affectedTaskIds.ifEmpty { taskIds }.toSet()
                        finishAction(result, R.string.autoworkflow_delete_done, removed)
                    } else {
                        _uiState.update {
                            it.copy(
                                actionRunning = false,
                                actionMessage = result.message.ifBlank {
                                    errorString(R.string.autoworkflow_action_failed_unknown)
                                },
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(actionRunning = false, actionMessage = failureText(error))
                    }
                },
            )
        }
    }

    fun consumeActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    private fun finishAction(
        result: AutoWorkflowActionResult,
        messageRes: Int,
        removedTaskIds: Set<Long> = emptySet(),
    ) {
        val affected = result.affectedTaskIds.size.takeIf { it > 0 } ?: result.matchedCount
        _uiState.update {
            it.copy(
                actionRunning = false,
                selectedTaskIds = emptySet(),
                actionMessage = getApplication<Application>().getString(messageRes, affected),
            )
        }
        if (removedTaskIds.isNotEmpty()) {
            removeDeletedTasksLocally(removedTaskIds)
        }
    }

    private fun removeDeletedTasksLocally(removedTaskIds: Set<Long>) {
        if (removedTaskIds.isEmpty()) return
        _uiState.update { state ->
            val filtered = removeTasksFromImages(state.images, removedTaskIds)
            val removedCount = state.images.size - filtered.size
            if (removedCount <= 0 && state.selectedTaskIds.none { it in removedTaskIds }) {
                state
            } else {
                state.copy(
                    images = filtered,
                    selectedTaskIds = state.selectedTaskIds - removedTaskIds,
                    totalImages = (state.totalImages - removedCount).coerceAtLeast(0),
                    linkedLoaded = (state.linkedLoaded - removedCount).coerceAtLeast(0),
                    taskCount = (state.taskCount - removedCount).coerceAtLeast(0),
                )
            }
        }
        levelCache.entries.forEach { entry ->
            val cached = entry.value
            val filtered = removeTasksFromImages(cached.images, removedTaskIds)
            val removedCount = cached.images.size - filtered.size
            if (removedCount > 0) {
                entry.setValue(
                    cached.copy(
                        images = filtered,
                        totalImages = (cached.totalImages - removedCount).coerceAtLeast(0),
                        linkedLoaded = (cached.linkedLoaded - removedCount).coerceAtLeast(0),
                        taskCount = (cached.taskCount - removedCount).coerceAtLeast(0),
                    ),
                )
            }
        }
    }

    private fun resubmitConfirmText(count: Int, flow: String, deleteOldOutput: Boolean): String {
        val app = getApplication<Application>()
        val flowLabel = app.getString(
            if (flow == AutoWorkflowClient.FLOW_UPSCALE) {
                R.string.autoworkflow_resubmit_upscale
            } else {
                R.string.autoworkflow_resubmit_edit
            },
        )
        val base = app.getString(R.string.autoworkflow_resubmit_confirm, count, flowLabel)
        return if (deleteOldOutput) {
            base + "\n" + app.getString(R.string.autoworkflow_resubmit_delete_old_confirm)
        } else {
            base
        }
    }

    private fun reeditConfirmText(count: Int, deleteOldOutput: Boolean): String {
        val app = getApplication<Application>()
        val base = if (count <= 1) {
            app.getString(R.string.autoworkflow_reedit_confirm)
        } else {
            app.getString(R.string.autoworkflow_reedit_batch_confirm, count)
        }
        return if (deleteOldOutput) {
            base + "\n" + app.getString(R.string.autoworkflow_resubmit_delete_old_confirm)
        } else {
            base
        }
    }

    private fun failureText(error: Throwable): String = getApplication<Application>().getString(
        R.string.autoworkflow_action_failed,
        error.message.orEmpty(),
    )

    private fun errorString(resId: Int): String = getApplication<Application>().getString(resId)
}

internal fun backendSortFor(sortOrder: Int): String = when (sortOrder) {
    SettingsManager.SORT_NAME_ASC -> AutoWorkflowClient.SORT_NAME_ASC
    SettingsManager.SORT_NAME_DESC -> AutoWorkflowClient.SORT_NAME_DESC
    SettingsManager.SORT_DATE_ASC -> AutoWorkflowClient.SORT_MTIME_ASC
    else -> AutoWorkflowClient.SORT_MTIME_DESC
}

internal fun sortAutoWorkflowFolders(
    folders: List<AutoWorkflowFolder>,
    sortOrder: Int,
): List<AutoWorkflowFolder> = when (sortOrder) {
    SettingsManager.SORT_NAME_ASC -> folders.sortedBy { it.name.lowercase(Locale.ROOT) }
    SettingsManager.SORT_NAME_DESC -> folders.sortedByDescending { it.name.lowercase(Locale.ROOT) }
    SettingsManager.SORT_DATE_ASC -> folders.sortedWith(
        compareBy<AutoWorkflowFolder> { it.firstTaskId ?: it.lastTaskId ?: Long.MAX_VALUE }
            .thenBy { it.name.lowercase(Locale.ROOT) },
    )
    SettingsManager.SORT_RANDOM_FOLDERS -> folders.shuffled()
    else -> folders.sortedWith(
        compareByDescending<AutoWorkflowFolder> { it.lastTaskId ?: Long.MIN_VALUE }
            .thenBy { it.name.lowercase(Locale.ROOT) },
    )
}

internal fun sortAutoWorkflowImages(
    images: List<AutoWorkflowImage>,
    sortOrder: Int,
): List<AutoWorkflowImage> = when (sortOrder) {
    SettingsManager.SORT_RANDOM_PHOTOS -> images.shuffled()
    else -> images
}

internal fun removeTasksFromImages(
    images: List<AutoWorkflowImage>,
    removedTaskIds: Set<Long>,
): List<AutoWorkflowImage> {
    if (removedTaskIds.isEmpty()) return images
    return images.filterNot { image ->
        val id = image.task?.id
        id != null && id in removedTaskIds
    }
}
