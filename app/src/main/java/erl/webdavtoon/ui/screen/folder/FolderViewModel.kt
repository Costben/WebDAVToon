// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.folder

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import erl.webdavtoon.AppSettingsStore
import erl.webdavtoon.Folder
import erl.webdavtoon.FolderSearchMatcher
import erl.webdavtoon.LocalPhotoRepository
import erl.webdavtoon.PrivacyModeState
import erl.webdavtoon.R
import erl.webdavtoon.RemoteFolderPreviewMemoryCache
import erl.webdavtoon.RustWebDavPhotoRepository
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.VisibleRemotePreviewScheduler
import erl.webdavtoon.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class FolderViewModel @JvmOverloads constructor(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val settingsManager = SettingsManager(context)
    private val appSettings = AppSettingsStore(context)
    private val _uiState = MutableStateFlow(snapshotSettings(FolderUiState()))
    val uiState = _uiState.asStateFlow()
    private var shuffleSeed = Random.nextLong()
    private val previewScheduler = VisibleRemotePreviewScheduler(viewModelScope) { folder, force ->
        loadRemotePreview(folder, force)
    }

    init {
        observeSettings()
        refreshSlots()
        loadFolders()
    }

    private fun observeSettings() {
        observe(appSettings.observeInt(AppSettingsStore.GRID_COLUMNS, 2)) { copy(gridColumns = it) }
        viewModelScope.launch {
            appSettings.observeInt(AppSettingsStore.SORT_ORDER, SettingsManager.SORT_DATE_DESC).collect { order ->
                if (order == _uiState.value.sortOrder) return@collect
                if (order == SettingsManager.SORT_RANDOM_FOLDERS) resetShuffleSeed()
                applySortOrderPreviews(order)
                refreshVisibleFolderPreviews()
            }
        }
        observe(appSettings.observeBoolean(AppSettingsStore.ROTATION_LOCKED, false)) { copy(rotationLocked = it) }
        observe(appSettings.observeInt(AppSettingsStore.THEME_ID, erl.webdavtoon.ThemeHelper.THEME_FOLLOW_DEVICE)) {
            copy(themeId = it)
        }
        observe(appSettings.observeBoolean(AppSettingsStore.USE_COUI_DEFAULT_COLORS, false)) {
            copy(useCouiDefaultColors = it)
        }
    }

    private fun <T> observe(flow: kotlinx.coroutines.flow.Flow<T>, transform: FolderUiState.(T) -> FolderUiState) {
        viewModelScope.launch { flow.collect { value -> _uiState.update { it.transform(value) } } }
    }

    fun loadFolders(forceRefresh: Boolean = false) {
        _uiState.update {
            it.copy(
                loading = !forceRefresh && it.rawFolders.isEmpty(),
                isRefreshing = forceRefresh || it.rawFolders.isNotEmpty(),
                refreshStatus = RefreshStatus.Refreshing,
                error = null,
                remoteError = null,
                isWebDavEnabled = settingsManager.isWebDavEnabled(),
            )
        }
        viewModelScope.launch {
            try {
                val (folders, remoteError) = withContext(Dispatchers.IO) {
                    val result = mutableListOf<Folder>()
                    var remoteFailure: String? = null
                    if (settingsManager.isWebDavEnabled()) {
                        val remoteRepo = RustWebDavPhotoRepository(settingsManager)
                        val remote = remoteRepo.getFolders("/", forceRefresh).filterNot { folder ->
                            folder.name.startsWith(".") ||
                                folder.path.trim('/').split('/').any { it.startsWith(".") }
                        }
                        result += remote
                        if (remote.isEmpty()) {
                            remoteFailure = remoteRepo.diagnoseEmptyFolderResult("/")
                        }
                    }
                    try {
                        val local = LocalPhotoRepository(context).getFolders("", forceRefresh)
                        if (local.isNotEmpty()) {
                            result += Folder(
                                path = "virtual://local_root",
                                name = context.getString(R.string.local_photos),
                                isLocal = true,
                                photoCount = local.sumOf { it.photoCount },
                                previewUris = local.flatMap { it.previewUris }.take(4),
                                hasSubFolders = true,
                            )
                        }
                    } catch (_: Exception) {
                        // A missing local media permission should not hide remote folders.
                    }
                    // Nothing at all to show: surface the remote reason as a fatal error.
                    if (result.isEmpty() && remoteFailure != null) {
                        throw IllegalStateException(remoteFailure)
                    }
                    result to remoteFailure
                }
                _uiState.update {
                    it.copy(
                        rawFolders = folders,
                        loading = false,
                        isRefreshing = false,
                        refreshStatus = RefreshStatus.Completed,
                        error = null,
                        remoteError = remoteError,
                    )
                }
                publishFolders()
                if (forceRefresh) refreshVisibleFolderPreviews(forceRefresh = true)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        isRefreshing = false,
                        refreshStatus = RefreshStatus.Completed,
                        error = error.message ?: "Folder load failed",
                    )
                }
            }
        }
    }

    fun setSearchKeyword(keyword: String) {
        _uiState.update { it.copy(searchKeyword = keyword, isSearching = keyword.isNotBlank()) }
        publishFolders()
    }

    fun setSortOrder(order: Int) {
        settingsManager.setSortOrder(order)
        if (order == SettingsManager.SORT_RANDOM_FOLDERS) resetShuffleSeed()
        applySortOrderPreviews(order)
        refreshVisibleFolderPreviews()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val local = LocalPhotoRepository(context).getFolders("", false, order)
                if (local.isNotEmpty()) {
                    val localPreviews = local.flatMap { it.previewUris }.take(4)
                    _uiState.update { state ->
                        if (state.sortOrder != order) state
                        else state.copy(
                            rawFolders = state.rawFolders.map { folder ->
                                if (folder.path == "virtual://local_root") {
                                    folder.copy(previewUris = localPreviews)
                                } else folder
                            }
                        )
                    }
                    publishFolders()
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Swaps in the cached previews for [order] and keeps whatever thumbnails a tile is
     * already showing when that order has no cache yet, so a sort change never blanks it.
     */
    private fun applySortOrderPreviews(order: Int) {
        val accountKey = settingsManager.previewCacheAccountKey()
        _uiState.update { state ->
            val updatedRawFolders = state.rawFolders.map { folder ->
                if (folder.isLocal) folder
                else {
                    val cached = RemoteFolderPreviewMemoryCache.get(accountKey, order, folder.path)
                    if (cached != null && cached.previewUriStrings.isNotEmpty()) {
                        folder.copy(
                            previewUris = cached.previewUriStrings.map(Uri::parse),
                            hasSubFolders = folder.hasSubFolders || cached.hasSubFolders
                        )
                    } else {
                        // Retain existing preview thumbnails while calculating or loading new sort order
                        folder
                    }
                }
            }
            state.copy(sortOrder = order, rawFolders = updatedRawFolders)
        }
        publishFolders()
    }

    private fun refreshVisibleFolderPreviews(forceRefresh: Boolean = false) {
        val accountKey = settingsManager.previewCacheAccountKey()
        val sortOrder = settingsManager.getSortOrder()
        val foldersToRefresh = _uiState.value.rawFolders.filter { folder ->
            if (folder.isLocal) false
            else if (forceRefresh) true
            else {
                val cached = RemoteFolderPreviewMemoryCache.get(accountKey, sortOrder, folder.path)
                cached == null || cached.previewUriStrings.isEmpty()
            }
        }
        if (foldersToRefresh.isNotEmpty()) {
            previewScheduler.enqueueVisible(foldersToRefresh, forceRefresh = forceRefresh)
        }
    }

    fun resetShuffleSeed() {
        shuffleSeed = Random.nextLong()
        publishFolders()
    }

    fun onFolderPreviewVisible(path: String, visible: Boolean) {
        _uiState.value.rawFolders.firstOrNull { it.path == path }?.let { folder ->
            previewScheduler.setVisible(folder, visible)
            if (visible) {
                requestRemotePreview(folder)
            }
        }
    }

    fun requestRemotePreview(folder: Folder, forceRefresh: Boolean = false) {
        if (folder.isLocal) return
        val accountKey = settingsManager.previewCacheAccountKey()
        val sortOrder = settingsManager.getSortOrder()
        val cached = RemoteFolderPreviewMemoryCache.get(accountKey, sortOrder, folder.path)
        if (!forceRefresh && cached != null && cached.previewUriStrings.isNotEmpty()) {
            val cachedUris = cached.previewUriStrings.map(Uri::parse)
            if (folder.previewUris != cachedUris) {
                _uiState.update { state ->
                    state.copy(rawFolders = state.rawFolders.map { current ->
                        if (current.path != folder.path) current else current.copy(
                            previewUris = cachedUris,
                            hasSubFolders = current.hasSubFolders || cached.hasSubFolders
                        )
                    })
                }
                publishFolders()
            }
            return
        }
        previewScheduler.enqueue(folder, forceRefresh)
    }

    private suspend fun loadRemotePreview(folder: Folder, forceRefresh: Boolean) {
        if (folder.isLocal) return
        val sortOrder = settingsManager.getSortOrder()
        val accountKey = settingsManager.previewCacheAccountKey()
        if (!forceRefresh) {
            val cached = RemoteFolderPreviewMemoryCache.get(accountKey, sortOrder, folder.path)
            if (cached != null && cached.previewUriStrings.isNotEmpty()) {
                val cachedUris = cached.previewUriStrings.map(Uri::parse)
                _uiState.update { state ->
                    state.copy(rawFolders = state.rawFolders.map { current ->
                        if (current.path != folder.path) current else current.copy(
                            previewUris = cachedUris,
                            hasSubFolders = current.hasSubFolders || cached.hasSubFolders
                        )
                    })
                }
                publishFolders()
                return
            }
        }
        val preview = RustWebDavPhotoRepository(settingsManager).inspectFolder(folder.path, sortOrder, forceRefresh)
            ?: return
        if (settingsManager.getSortOrder() != sortOrder) return
        val resolvedUris = if (preview.previewUris.isNotEmpty()) preview.previewUris else folder.previewUris
        _uiState.update { state ->
            state.copy(rawFolders = state.rawFolders.map { current ->
                if (current.path != folder.path) current else current.copy(
                    previewUris = resolvedUris,
                    hasSubFolders = current.hasSubFolders || preview.hasSubFolders,
                )
            })
        }
        publishFolders()
    }

    fun toggleSelection(path: String) {
        _uiState.update {
            val selected = it.selectedPaths.toMutableSet().apply {
                if (!add(path)) remove(path)
            }
            it.copy(selectedPaths = selected, selectedCount = selected.size, isSelectionMode = selected.isNotEmpty())
        }
        publishFolders()
    }

    fun selectAll() {
        val paths = _uiState.value.rawFolders.map { it.path }.toSet()
        _uiState.update { it.copy(selectedPaths = paths, selectedCount = paths.size, isSelectionMode = paths.isNotEmpty()) }
        publishFolders()
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet(), selectedCount = 0, isSelectionMode = false) }
        publishFolders()
    }

    fun deleteSelected(onComplete: (deletedCount: Int) -> Unit) {
        val selected = _uiState.value.rawFolders.filter { it.path in _uiState.value.selectedPaths }
        viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            selected.forEach { folder ->
                val repository: PhotoRepository = if (folder.isLocal) LocalPhotoRepository(context)
                else RustWebDavPhotoRepository(settingsManager)
                if (repository.deleteFolder(folder)) count++
            }
            withContext(Dispatchers.Main) {
                Glide.get(context).clearMemory()
                clearSelection()
                onComplete(count)
                loadFolders(forceRefresh = true)
            }
        }
    }

    fun setGridColumns(columns: Int) {
        val value = columns.coerceIn(1, 4)
        settingsManager.setGridColumns(value)
        _uiState.update { it.copy(gridColumns = value) }
    }

    fun toggleRotationLock() {
        val locked = !_uiState.value.rotationLocked
        settingsManager.setRotationLocked(locked)
        _uiState.update { it.copy(rotationLocked = locked) }
    }

    fun refreshSlots() {
        val current = settingsManager.getCurrentSlot()
        val slots = settingsManager.getAllSlotsUnfiltered().map { slot ->
            erl.webdavtoon.ui.screen.settings.WebDavSlotUi(
                slot = slot,
                alias = settingsManager.getWebDavAlias(slot),
                protocol = settingsManager.getWebDavProtocol(slot),
                url = settingsManager.getWebDavUrl(slot),
                port = settingsManager.getWebDavPort(slot),
                username = settingsManager.getWebDavUsername(slot),
                domain = settingsManager.getWebDavDomain(slot),
                rememberPassword = settingsManager.isWebDavRememberPassword(slot),
                isPrivate = settingsManager.isWebDavPrivate(slot),
                enabled = settingsManager.isWebDavEnabled(slot),
                hasPassword = settingsManager.getWebDavPassword(slot).isNotBlank(),
                isCurrent = slot == current,
            )
        }
        _uiState.update { it.copy(currentSlot = current, slots = slots, isWebDavEnabled = settingsManager.isWebDavEnabled()) }
    }

    fun selectSlot(slot: Int) {
        settingsManager.setCurrentSlot(slot)
        refreshSlots()
        loadFolders(forceRefresh = true)
    }

    private fun publishFolders() {
        val state = _uiState.value
        val filtered = state.rawFolders.filter { FolderSearchMatcher.matches(it.name, state.searchKeyword) }
        val sorted = when (state.sortOrder) {
            SettingsManager.SORT_NAME_ASC -> filtered.sortedBy { it.name }
            SettingsManager.SORT_NAME_DESC -> filtered.sortedByDescending { it.name }
            SettingsManager.SORT_DATE_ASC -> filtered.sortedBy { it.dateModified }
            SettingsManager.SORT_RANDOM_FOLDERS -> filtered.shuffled(Random(shuffleSeed))
            else -> filtered.sortedByDescending { it.dateModified }
        }
        val selected = state.selectedPaths
        val previousPreviewsByPath = state.folders.associate { it.path to it.previewUris }
        _uiState.update { it.copy(folders = sorted.map { folder ->
            val stringPreviews = folder.previewUris.map { uri -> uri.toString() }
            val resolvedPreviews = if (stringPreviews.isNotEmpty()) stringPreviews else previousPreviewsByPath[folder.path].orEmpty()
            FolderItemUi(
                path = folder.path,
                name = folder.name,
                isLocal = folder.isLocal,
                photoCount = folder.photoCount,
                previewUris = resolvedPreviews,
                hasSubFolders = folder.hasSubFolders,
                isSelected = folder.path in selected,
            )
        }) }
    }

    private fun snapshotSettings(state: FolderUiState): FolderUiState = state.copy(
        sortOrder = settingsManager.getSortOrder(),
        gridColumns = settingsManager.getGridColumns(),
        rotationLocked = settingsManager.isRotationLocked(),
        isPrivacyMode = PrivacyModeState.isPrivacyMode,
        isWebDavEnabled = settingsManager.isWebDavEnabled(),
        themeId = settingsManager.getThemeId(),
        useCouiDefaultColors = settingsManager.useCouiDefaultColors(),
    )
}
