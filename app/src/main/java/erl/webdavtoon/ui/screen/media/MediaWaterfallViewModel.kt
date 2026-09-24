// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.media

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import erl.webdavtoon.AppSettingsStore
import erl.webdavtoon.FavoriteSelectionPlanner
import erl.webdavtoon.LocalPhotoRepository
import erl.webdavtoon.MediaManager
import erl.webdavtoon.MediaType
import erl.webdavtoon.MixedWaterfallIdentity
import erl.webdavtoon.Photo
import erl.webdavtoon.PhotoAspectRatioResolver
import erl.webdavtoon.PhotoCache
import erl.webdavtoon.PhotoRepository
import erl.webdavtoon.PrivacyModeState
import erl.webdavtoon.R
import erl.webdavtoon.RustWebDavPhotoRepository
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.WebDavImageLoader
import erl.webdavtoon.detectMediaTypeByName
import erl.webdavtoon.detectMediaTypeByUri
import erl.webdavtoon.formatVideoDuration
import erl.webdavtoon.ui.screen.settings.WebDavSlotUi
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State holder for the media-only waterfall page (the former RecyclerView-based
 * MainActivity). It keeps [MediaManager]'s in-memory paging contract (PAGE_SIZE
 * per page) while rendering through the shared Compose waterfall cards.
 */
class MediaWaterfallViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val settingsManager = SettingsManager(context)
    private val appSettings = AppSettingsStore(context)
    private val cachedDimensions = ConcurrentHashMap<String, Pair<Int, Int>>()

    private var orderedPhotos: List<Photo> = emptyList()
    private var clusterShuffleSeed = Random.nextLong()
    private var photoShuffleSeed = Random.nextLong()
    private var previousSortOrder: Int = SettingsManager.SORT_DATE_DESC
    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow(
            MediaWaterfallUiState(
                columns = settingsManager.getGridColumns().coerceIn(1, 4),
            sortOrder = settingsManager.getPhotoSortOrder(),
            rotationLocked = settingsManager.isRotationLocked(),
            showFilenames = settingsManager.shouldShowWaterfallFilenames(),
            isPrivacyMode = PrivacyModeState.isPrivacyMode,
            themeId = settingsManager.getThemeId(),
            useCouiDefaultColors = settingsManager.useCouiDefaultColors(),
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        observeSettings()
        refreshSlots()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            appSettings.observeInt(AppSettingsStore.GRID_COLUMNS, 2).collect { cols ->
                val clamped = cols.coerceIn(1, 4)
                _uiState.update { it.copy(columns = clamped) }
            }
        }
        viewModelScope.launch {
            appSettings.observeBoolean(AppSettingsStore.WATERFALL_SHOW_FILENAMES, true).collect { show ->
                _uiState.update { it.copy(showFilenames = show) }
            }
        }
        viewModelScope.launch {
            appSettings.observeInt(AppSettingsStore.PHOTO_SORT_ORDER, SettingsManager.SORT_DATE_DESC).collect { order ->
                _uiState.update { it.copy(sortOrder = order) }
            }
        }
        viewModelScope.launch {
            appSettings.observeBoolean(AppSettingsStore.ROTATION_LOCKED, false).collect { locked ->
                _uiState.update { it.copy(rotationLocked = locked) }
            }
        }
        viewModelScope.launch {
            appSettings.observeInt(AppSettingsStore.THEME_ID, erl.webdavtoon.ThemeHelper.THEME_FOLLOW_DEVICE)
                .collect { themeId -> _uiState.update { it.copy(themeId = themeId) } }
        }
        viewModelScope.launch {
            appSettings.observeBoolean(AppSettingsStore.USE_COUI_DEFAULT_COLORS, false)
                .collect { enabled -> _uiState.update { it.copy(useCouiDefaultColors = enabled) } }
        }
    }

    fun init(folderPath: String, isRemote: Boolean, isRecursive: Boolean) {
        val current = _uiState.value
        if (current.folderPath == folderPath &&
            current.isRemote == isRemote &&
            current.isRecursive == isRecursive &&
            current.items.isNotEmpty()
        ) {
            return
        }
        _uiState.update {
            it.copy(
                folderPath = folderPath,
                isRemote = isRemote,
                isRecursive = isRecursive,
                title = computeTitle(folderPath, isRemote, isRecursive),
            )
        }
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = !forceRefresh && it.items.isEmpty(),
                    isRefreshing = forceRefresh || it.items.isNotEmpty(),
                    error = null,
                )
            }
            try {
                if (forceRefresh) {
                    clusterShuffleSeed = Random.nextLong()
                    photoShuffleSeed = Random.nextLong()
                }
                val state = _uiState.value
                val all = withContext(Dispatchers.IO) {
                    val repository: PhotoRepository = if (state.isRemote) {
                        RustWebDavPhotoRepository(settingsManager)
                    } else {
                        LocalPhotoRepository(context)
                    }
                    repository.getPhotos(state.folderPath, state.isRecursive, forceRefresh)
                }
                val ordered = MediaManager.sortPhotos(
                    photos = all,
                    sortOrder = settingsManager.getPhotoSortOrder(),
                    isRecursive = state.isRecursive,
                    recursiveImageArrangement = settingsManager.getRecursiveImageArrangement(),
                    clusterShuffleSeed = clusterShuffleSeed,
                    photoShuffleSeed = photoShuffleSeed,
                )
                orderedPhotos = ordered
                val favIds = withContext(Dispatchers.IO) {
                    settingsManager.getFavoritePhotos().map { it.id }.toSet()
                }
                val page = ordered.take(PAGE_SIZE)
                PhotoCache.setPhotos(ordered)
                _uiState.update {
                    it.copy(
                        items = buildItems(page, favIds, it.selectedKeys),
                        favoritePhotoIds = favIds,
                        hasMore = ordered.size > page.size,
                        loading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, isRefreshing = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.loading || state.isRefreshing || !state.hasMore) return
        val loaded = state.items.size
        val next = orderedPhotos.drop(loaded).take(PAGE_SIZE)
        if (next.isEmpty()) {
            _uiState.update { it.copy(hasMore = false) }
            return
        }
        _uiState.update {
            it.copy(
                items = it.items + buildItems(next, it.favoritePhotoIds, it.selectedKeys),
                hasMore = loaded + next.size < orderedPhotos.size,
            )
        }
    }

    private fun buildItems(
        photos: List<Photo>,
        favIds: Set<String>,
        selectedKeys: Set<String>,
    ): List<MediaWaterfallItemUi> = photos.map { photo ->
        val key = MixedWaterfallIdentity.mediaKey(photo)
        MediaWaterfallItemUi(
            photo = photo,
            key = key,
            id = photo.id,
            uri = photo.imageUri,
            title = photo.title,
            isLocal = photo.isLocal,
            isVideo = isVideo(photo),
            durationText = formatVideoDuration(photo.durationMs),
            aspectRatio = PhotoAspectRatioResolver.resolve(photo, cachedDimensions[photo.id]),
            isFavorite = photo.id in favIds,
            isSelected = key in selectedKeys,
        )
    }

    fun updateResolvedDimensions(photoId: String, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        cachedDimensions[photoId] = width to height
        val ratio = PhotoAspectRatioResolver.resolve(width, height)
        _uiState.update { state ->
            var changed = false
            val updated = state.items.map { item ->
                if (item.id == photoId && item.aspectRatio != ratio) {
                    changed = true
                    item.copy(aspectRatio = ratio)
                } else {
                    item
                }
            }
            if (changed) state.copy(items = updated) else state
        }
    }

    fun setColumns(columns: Int) {
        val clamped = columns.coerceIn(1, 4)
        appSettings.putInt(AppSettingsStore.GRID_COLUMNS, clamped)
        _uiState.update { it.copy(columns = clamped) }
    }

    fun setSortOrder(order: Int) {
        settingsManager.setPhotoSortOrder(order)
        if (order != SettingsManager.SORT_RANDOM_PHOTOS) previousSortOrder = order
        if (order == SettingsManager.SORT_RANDOM_FOLDERS) clusterShuffleSeed = Random.nextLong()
        if (SettingsManager.isRandomPhotoSort(order)) photoShuffleSeed = Random.nextLong()
        _uiState.update { it.copy(sortOrder = order) }
        load()
    }

    /** The "randomize photos" switch is just a shortcut for the [SettingsManager.SORT_RANDOM_PHOTOS] sort. */
    fun toggleRandomizePhotos() {
        val current = _uiState.value.sortOrder
        val next = if (current == SettingsManager.SORT_RANDOM_PHOTOS) {
            previousSortOrder
        } else {
            previousSortOrder = current
            SettingsManager.SORT_RANDOM_PHOTOS
        }
        setSortOrder(next)
    }

    fun setSearchKeyword(keyword: String) {
        _uiState.update { it.copy(searchKeyword = keyword, isSearching = keyword.isNotBlank()) }
    }

    fun toggleRotationLock() {
        val locked = !_uiState.value.rotationLocked
        settingsManager.setRotationLocked(locked)
        _uiState.update { it.copy(rotationLocked = locked) }
    }

    fun enterSelectionMode(initialKey: String? = null) {
        _uiState.update { state ->
            val keys = if (initialKey != null) state.selectedKeys + initialKey else state.selectedKeys
            state.copy(isSelectionMode = true, selectedKeys = keys, items = state.items.withSelection(keys))
        }
    }

    fun toggleSelection(key: String) {
        _uiState.update { state ->
            val keys = if (key in state.selectedKeys) state.selectedKeys - key else state.selectedKeys + key
            state.copy(isSelectionMode = keys.isNotEmpty(), selectedKeys = keys, items = state.items.withSelection(keys))
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val keys = state.visibleItems.map { it.key }.toSet()
            state.copy(isSelectionMode = keys.isNotEmpty(), selectedKeys = keys, items = state.items.withSelection(keys))
        }
    }

    fun clearSelection() {
        _uiState.update { state ->
            state.copy(isSelectionMode = false, selectedKeys = emptySet(), items = state.items.withSelection(emptySet()))
        }
    }

    fun exitSelectionMode() = clearSelection()

    fun batchToggleFavorite(photos: List<Photo>) {
        if (photos.isEmpty()) return
        val favoriteIds = photos.filter { settingsManager.isPhotoFavorite(it.id) }.mapTo(mutableSetOf()) { it.id }
        val plan = FavoriteSelectionPlanner.buildPlan(
            selectedItems = photos,
            favoriteIds = favoriteIds,
            isFavoritesView = false,
            idSelector = { it.id },
        )
        plan.toAdd.forEach(settingsManager::addFavoritePhoto)
        plan.toRemove.forEach { settingsManager.removeFavoritePhoto(it.id) }
        val favIds = settingsManager.getFavoritePhotos().map { it.id }.toSet()
        clearSelection()
        _uiState.update { state ->
            state.copy(
                favoritePhotoIds = favIds,
                items = state.items.map { it.copy(isFavorite = it.id in favIds) },
            )
        }
    }

    suspend fun executeDelete(
        selectedPhotos: List<Photo>,
        alreadyDeletedLocalPhotos: List<Photo> = emptyList(),
    ): Int = withContext(Dispatchers.IO) {
        val deletedPhotos = alreadyDeletedLocalPhotos.toMutableList()
        selectedPhotos.forEach { photo ->
            if (photo in alreadyDeletedLocalPhotos) return@forEach
            val repository: PhotoRepository = if (photo.isLocal) {
                LocalPhotoRepository(context)
            } else {
                RustWebDavPhotoRepository(settingsManager)
            }
            if (repository.deletePhoto(photo)) deletedPhotos.add(photo)
        }
        deletedPhotos.forEach { settingsManager.removeFavoritePhoto(it.id) }

        if (deletedPhotos.isNotEmpty()) {
            if (deletedPhotos.any { !it.isLocal }) {
                WebDavImageLoader.clearCache(context)
            } else {
                withContext(Dispatchers.Main) { Glide.get(context).clearMemory() }
            }
        }
        clearSelection()
        load(forceRefresh = true)
        deletedPhotos.size
    }

    fun refreshSlots() {
        val current = settingsManager.getCurrentSlot()
        val slots = settingsManager.getAllSlotsUnfiltered().map { slot ->
            WebDavSlotUi(
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
        _uiState.update {
            it.copy(currentSlot = current, slots = slots, isPrivacyMode = PrivacyModeState.isPrivacyMode)
        }
    }

    fun selectSlot(slot: Int) {
        settingsManager.setCurrentSlot(slot)
        refreshSlots()
        load(forceRefresh = true)
    }

    fun updateStoragePermission(granted: Boolean) {
        _uiState.update { it.copy(storagePermissionGranted = granted) }
        if (granted) load()
    }

    private fun computeTitle(folderPath: String, isRemote: Boolean, isRecursive: Boolean): String {
        val base = when {
            folderPath.isEmpty() && !isRemote -> context.getString(R.string.local_photos)
            folderPath.isEmpty() && isRemote -> context.getString(R.string.remote)
            else -> {
                val last = folderPath.trimEnd('/').split('/').lastOrNull { it.isNotEmpty() } ?: folderPath
                Uri.decode(last)
            }
        }
        return if (isRecursive) "$base ${context.getString(R.string.all_suffix)}" else base
    }

    private fun isVideo(photo: Photo): Boolean {
        return photo.mediaType == MediaType.VIDEO ||
            detectMediaTypeByName(photo.title) == MediaType.VIDEO ||
            runCatching { detectMediaTypeByUri(photo.imageUri) }.getOrNull() == MediaType.VIDEO
    }

    private fun List<MediaWaterfallItemUi>.withSelection(keys: Set<String>): List<MediaWaterfallItemUi> =
        map { it.copy(isSelected = it.key in keys) }

    private companion object {
        const val PAGE_SIZE = 120
    }
}
