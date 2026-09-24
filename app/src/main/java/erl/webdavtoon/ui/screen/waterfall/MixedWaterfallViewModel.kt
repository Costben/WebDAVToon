// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.waterfall

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import erl.webdavtoon.AppSettingsStore
import erl.webdavtoon.FavoriteSelectionPlanner
import erl.webdavtoon.Folder
import erl.webdavtoon.FolderPreviewOrdering
import erl.webdavtoon.LocalPhotoRepository
import erl.webdavtoon.MediaType
import erl.webdavtoon.MixedWaterfallIdentity
import erl.webdavtoon.MixedWaterfallItem
import erl.webdavtoon.MixedWaterfallPlanner
import erl.webdavtoon.Photo
import erl.webdavtoon.PhotoAspectRatioResolver
import erl.webdavtoon.PhotoRepository
import erl.webdavtoon.R
import erl.webdavtoon.RemoteFolderPreviewBackfill
import erl.webdavtoon.RemoteFolderPreviewMemoryCache
import erl.webdavtoon.RemoteFolderSynthesizer
import erl.webdavtoon.RustWebDavPhotoRepository
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.WebDavImageLoader
import erl.webdavtoon.detectMediaTypeByName
import erl.webdavtoon.detectMediaTypeByUri
import erl.webdavtoon.formatVideoDuration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

class MixedWaterfallViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val settingsManager = SettingsManager(context)
    private val appSettings = AppSettingsStore(context)
    private val cachedDimensions = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val webDavSlotMutex = Mutex()
    private var folderShuffleSeed = Random.nextLong()
    private var photoShuffleSeed = Random.nextLong()
    private var loadJob: Job? = null
    private val previewBackfill = RemoteFolderPreviewBackfill(
        scope = viewModelScope,
        // Eligibility (remote, not hidden) is handled by the coordinator's isInspectable
        // gate; this only decides whether the active sort order still needs an inspect.
        needsPreview = { folder ->
            val accountKey = settingsManager.previewCacheAccountKey()
            val sortOrder = settingsManager.getPhotoSortOrder()
            val cached = RemoteFolderPreviewMemoryCache.get(accountKey, sortOrder, folder.path)
            cached == null || cached.previewUriStrings.isEmpty()
        }
    ) { folder, force ->
        loadRemoteFolderPreview(folder, force)
    }

    private val _uiState = MutableStateFlow(
            MixedWaterfallUiState(
                columns = appSettings.getOrDefaultInt(AppSettingsStore.GRID_COLUMNS, 2).coerceIn(1, 4),
            virtualColumns = appSettings.getOrDefaultInt(AppSettingsStore.GRID_COLUMNS, 2).coerceIn(1, 4).toFloat(),
            sortOrder = settingsManager.getPhotoSortOrder(),
            rotationLocked = settingsManager.isRotationLocked(),
            showFilenames = appSettings.getOrDefaultBoolean(AppSettingsStore.WATERFALL_SHOW_FILENAMES, true)
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            appSettings.observeInt(AppSettingsStore.GRID_COLUMNS, 2).collect { cols ->
                val clamped = cols.coerceIn(1, 4)
                _uiState.update { state ->
                    if (state.isZooming) {
                        state.copy(columns = clamped)
                    } else {
                        state.copy(columns = clamped, virtualColumns = clamped.toFloat())
                    }
                }
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
    }

    fun init(folderPath: String, isWebDav: Boolean, isFavorites: Boolean) {
        val current = _uiState.value
        if (current.folderPath == folderPath &&
            current.isWebDav == isWebDav &&
            current.isFavorites == isFavorites &&
            current.items.isNotEmpty()
        ) {
            return
        }

        val title = computeTitle(folderPath, isWebDav, isFavorites)
        _uiState.update {
            it.copy(
                folderPath = folderPath,
                isWebDav = isWebDav,
                isFavorites = isFavorites,
                title = title
            )
        }
        loadContent()
    }

    fun loadContent(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = !forceRefresh && it.items.isEmpty(),
                    isRefreshing = forceRefresh || it.items.isNotEmpty(),
                    error = null
                )
            }
            previewBackfill.retire(forgetResolved = forceRefresh)
            try {
                if (forceRefresh) {
                    resetFolderShuffleIfRandomSort()
                }
                val currentState = _uiState.value
                val isFavs = currentState.isFavorites
                val path = currentState.folderPath
                val isWd = currentState.isWebDav

                val (folders, photos, plannerItems) = withContext(Dispatchers.IO) {
                    if (isFavs) {
                        val favPhotos = settingsManager.getFavoritePhotos()
                        val favFolders = settingsManager.getFavoriteFolders()
                        val sortedMedia = sortMediaForDisplay(
                            favPhotos,
                            settingsManager.getPhotoSortOrder()
                        )
                        val items = MixedWaterfallPlanner.buildItems(
                            folders = favFolders,
                            media = sortedMedia,
                            folderSortOrder = settingsManager.getPhotoSortOrder(),
                            folderShuffleSeed = folderShuffleSeed
                        )
                        Triple(favFolders, sortedMedia, items)
                    } else {
                        val repository: PhotoRepository = if (isWd) {
                            RustWebDavPhotoRepository(settingsManager)
                        } else {
                            LocalPhotoRepository(context)
                        }

                        val loadedFolders = if (isWd) {
                            RustWebDavPhotoRepository(settingsManager).getFolders(
                                rootPath = path,
                                forceRefresh = forceRefresh,
                                sortOrder = settingsManager.getPhotoSortOrder()
                            )
                                .asSequence()
                                .filterNot { it.path.startsWith("virtual://internal_photos") }
                                .filterNot { folder ->
                                    !folder.isLocal && (
                                        folder.name.startsWith(".") ||
                                            folder.path.trim('/').split('/').any { it.startsWith(".") }
                                    )
                                }
                                .toList()
                        } else {
                            repository.getFolders(path, forceRefresh)
                                .asSequence()
                                .filterNot { it.path.startsWith("virtual://internal_photos") }
                                .filterNot { folder ->
                                    !folder.isLocal && (
                                        folder.name.startsWith(".") ||
                                            folder.path.trim('/').split('/').any { it.startsWith(".") }
                                    )
                                }
                                .toList()
                        }

                        val directMedia = repository.getPhotos(
                            folderPath = path,
                            recursive = false,
                            forceRefresh = forceRefresh
                        )
                        val resolvedFolders = if (isWd && loadedFolders.isEmpty()) {
                            val recursiveMedia = repository.getPhotos(
                                folderPath = path,
                                recursive = true,
                                forceRefresh = forceRefresh
                            )
                            RemoteFolderSynthesizer.synthesizeFromRecursivePhotos(
                                currentFolderPath = path,
                                photos = recursiveMedia,
                                endpoint = settingsManager.getFullWebDavUrl(),
                                sortOrder = settingsManager.getPhotoSortOrder()
                            )
                        } else {
                            loadedFolders
                        }
                        val sortedMedia = sortMediaForDisplay(
                            directMedia,
                            settingsManager.getPhotoSortOrder()
                        )

                        val sourceSlot = if (isWd) settingsManager.getCurrentSlot() else -1
                        val currentPreviewsByPath = _uiState.value.rawFolders.associate { it.path to it.previewUris }
                        val sourcedFolders = resolvedFolders.map { folder ->
                            val resolvedSlot = if (folder.isLocal) -1 else sourceSlot
                            val slotFolder = if (folder.sourceSlot == resolvedSlot) folder else folder.copy(sourceSlot = resolvedSlot)
                            if (slotFolder.previewUris.isNotEmpty()) {
                                slotFolder
                            } else {
                                val prev = currentPreviewsByPath[slotFolder.path]
                                if (!prev.isNullOrEmpty()) slotFolder.copy(previewUris = prev) else slotFolder
                            }
                        }

                        val items = MixedWaterfallPlanner.buildItems(
                            folders = sourcedFolders,
                            media = sortedMedia,
                            folderSortOrder = settingsManager.getPhotoSortOrder(),
                            folderShuffleSeed = folderShuffleSeed
                        )
                        Triple(sourcedFolders, sortedMedia, items)
                    }
                }

                val favPhotoIds = withContext(Dispatchers.IO) {
                    settingsManager.getFavoritePhotos().map { it.id }.toSet()
                }
                val favFolderPaths = withContext(Dispatchers.IO) {
                    settingsManager.getFavoriteFolders().map { it.path }.toSet()
                }

                val currentSelectedKeys = _uiState.value.selectedKeys
                val uiItems = buildItemUis(plannerItems, favPhotoIds, currentSelectedKeys)
                val currentItemKeys = uiItems.map { it.key }.toSet()
                val retainedSelectedKeys = currentSelectedKeys.intersect(currentItemKeys)
                val isSelecting = retainedSelectedKeys.isNotEmpty() && _uiState.value.isSelectionMode

                _uiState.update { state ->
                    state.copy(
                        items = if (retainedSelectedKeys.size == currentSelectedKeys.size) {
                            uiItems
                        } else {
                            uiItems.map { item ->
                                when (item) {
                                    is MixedWaterfallItemUi.FolderItem -> item.copy(isSelected = item.key in retainedSelectedKeys)
                                    is MixedWaterfallItemUi.MediaItem -> item.copy(isSelected = item.key in retainedSelectedKeys)
                                }
                            }
                        },
                        rawFolders = folders,
                        rawPhotos = photos,
                        favoritePhotoIds = favPhotoIds,
                        favoriteFolderPaths = favFolderPaths,
                        selectedKeys = retainedSelectedKeys,
                        isSelectionMode = isSelecting,
                         loading = false,
                        isRefreshing = false,
                        error = null
                    )
                }
                requestMissingFolderPreviews(forceRefresh = forceRefresh)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        isRefreshing = false,
                        error = e.message ?: e.toString()
                    )
                }
            }
        }
    }

    private fun buildItemUis(
        plannerItems: List<MixedWaterfallItem>,
        favPhotoIds: Set<String>,
        selectedKeys: Set<String>
    ): List<MixedWaterfallItemUi> {
        return plannerItems.map { item ->
            when (item) {
                is MixedWaterfallItem.FolderTile -> {
                    val folder = item.folder
                    val key = MixedWaterfallIdentity.folderKey(folder)
                    val existingPreviewUris = (_uiState.value.items.firstOrNull { it is MixedWaterfallItemUi.FolderItem && it.key == key } as? MixedWaterfallItemUi.FolderItem)?.previewUris.orEmpty()
                    val folderUris = folder.previewUris.map { it.toString() }
                    val resolvedPreviewUris = if (folderUris.isNotEmpty()) folderUris else existingPreviewUris
                    MixedWaterfallItemUi.FolderItem(
                        folder = folder,
                        key = key,
                        name = folder.name,
                        path = folder.path,
                        isLocal = folder.isLocal,
                        photoCount = folder.photoCount,
                        previewUris = resolvedPreviewUris,
                        aspectRatio = 1.0f,
                        isSelected = key in selectedKeys
                    )
                }
                is MixedWaterfallItem.MediaTile -> {
                    val photo = item.photo
                    val key = MixedWaterfallIdentity.mediaKey(photo)
                    val isVid = isVideo(photo)
                    val duration = formatVideoDuration(photo.durationMs)
                    val ratio = PhotoAspectRatioResolver.resolve(photo, cachedDimensions[photo.id])
                    val isFav = photo.id in favPhotoIds || settingsManager.isPhotoFavorite(photo.id)
                    MixedWaterfallItemUi.MediaItem(
                        photo = photo,
                        key = key,
                        id = photo.id,
                        uri = photo.imageUri,
                        title = photo.title,
                        isLocal = photo.isLocal,
                        isVideo = isVid,
                        durationText = duration,
                        aspectRatio = ratio,
                        isFavorite = isFav,
                        isSelected = key in selectedKeys
                    )
                }
            }
        }
    }

    fun updateResolvedDimensions(photoId: String, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        cachedDimensions[photoId] = width to height
        val newRatio = PhotoAspectRatioResolver.resolve(width, height)
        _uiState.update { state ->
            var changed = false
            val updatedItems = state.items.map { item ->
                if (item is MixedWaterfallItemUi.MediaItem && item.id == photoId) {
                    if (item.aspectRatio != newRatio) {
                        changed = true
                        item.copy(aspectRatio = newRatio)
                    } else {
                        item
                    }
                } else {
                    item
                }
            }
            if (changed) state.copy(items = updatedItems) else state
        }
    }

    fun enterSelectionMode(initialKey: String? = null) {
        _uiState.update { state ->
            val newSelectedKeys = if (initialKey != null) {
                state.selectedKeys + initialKey
            } else {
                state.selectedKeys
            }
            state.copy(
                isSelectionMode = true,
                selectedKeys = newSelectedKeys,
                items = state.items.map { item ->
                    when (item) {
                        is MixedWaterfallItemUi.FolderItem -> item.copy(isSelected = item.key in newSelectedKeys)
                        is MixedWaterfallItemUi.MediaItem -> item.copy(isSelected = item.key in newSelectedKeys)
                    }
                }
            )
        }
    }

    fun toggleSelection(key: String) {
        _uiState.update { state ->
            val newSelectedKeys = if (key in state.selectedKeys) {
                state.selectedKeys - key
            } else {
                state.selectedKeys + key
            }
            val isSelecting = newSelectedKeys.isNotEmpty()
            state.copy(
                isSelectionMode = isSelecting,
                selectedKeys = newSelectedKeys,
                items = state.items.map { item ->
                    when (item) {
                        is MixedWaterfallItemUi.FolderItem -> item.copy(isSelected = item.key in newSelectedKeys)
                        is MixedWaterfallItemUi.MediaItem -> item.copy(isSelected = item.key in newSelectedKeys)
                    }
                }
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val allKeys = state.items.map { it.key }.toSet()
            state.copy(
                isSelectionMode = allKeys.isNotEmpty(),
                selectedKeys = allKeys,
                items = state.items.map { item ->
                    when (item) {
                        is MixedWaterfallItemUi.FolderItem -> item.copy(isSelected = true)
                        is MixedWaterfallItemUi.MediaItem -> item.copy(isSelected = true)
                    }
                }
            )
        }
    }

    fun clearSelection() {
        _uiState.update { state ->
            state.copy(
                isSelectionMode = false,
                selectedKeys = emptySet(),
                items = state.items.map { item ->
                    when (item) {
                        is MixedWaterfallItemUi.FolderItem -> item.copy(isSelected = false)
                        is MixedWaterfallItemUi.MediaItem -> item.copy(isSelected = false)
                    }
                }
            )
        }
    }

    fun exitSelectionMode() {
        clearSelection()
    }

    fun updateVirtualColumns(virtualColumns: Float, isZooming: Boolean) {
        _uiState.update { it.copy(virtualColumns = virtualColumns, isZooming = isZooming) }
    }

    fun setColumns(columns: Int) {
        val clamped = columns.coerceIn(1, 4)
        appSettings.putInt(AppSettingsStore.GRID_COLUMNS, clamped)
        _uiState.update { it.copy(columns = clamped, virtualColumns = clamped.toFloat()) }
    }

    fun setSearchKeyword(keyword: String) {
        _uiState.update { it.copy(searchKeyword = keyword, isSearching = keyword.isNotBlank()) }
    }

    fun setSortOrder(order: Int) {
        settingsManager.setPhotoSortOrder(order)
        if (order == SettingsManager.SORT_RANDOM_FOLDERS) {
            folderShuffleSeed = Random.nextLong()
        }
        if (SettingsManager.isRandomPhotoSort(order)) {
            photoShuffleSeed = Random.nextLong()
        }
        val accountKey = settingsManager.previewCacheAccountKey()
        _uiState.update { state ->
            val updatedItems = state.items.map { item ->
                if (item is MixedWaterfallItemUi.FolderItem && !item.isLocal) {
                    val cached = RemoteFolderPreviewMemoryCache.get(accountKey, order, item.path)
                    if (cached != null && cached.previewUriStrings.isNotEmpty()) {
                        val stringUris = cached.previewUriStrings
                        val uris = stringUris.map(Uri::parse)
                        item.copy(
                            folder = item.folder.copy(previewUris = uris, hasSubFolders = item.folder.hasSubFolders || cached.hasSubFolders),
                            previewUris = stringUris
                        )
                    } else {
                        // Retain existing preview thumbnails while calculating or loading new sort order
                        item
                    }
                } else {
                    item
                }
            }
            state.copy(sortOrder = order, items = updatedItems)
        }
        previewBackfill.retire(forgetResolved = true)
        loadContent()
        requestMissingFolderPreviews()
    }

    fun toggleRotationLock() {
        val locked = !_uiState.value.rotationLocked
        settingsManager.setRotationLocked(locked)
        _uiState.update { it.copy(rotationLocked = locked) }
    }

    fun toggleFavorite(photo: Photo) {
        val isFav = settingsManager.isPhotoFavorite(photo.id)
        if (isFav) {
            settingsManager.removeFavoritePhoto(photo.id)
        } else {
            settingsManager.addFavoritePhoto(photo)
        }
        if (_uiState.value.isFavorites) {
            loadContent()
        } else {
            val updatedFavIds = if (isFav) {
                _uiState.value.favoritePhotoIds - photo.id
            } else {
                _uiState.value.favoritePhotoIds + photo.id
            }
            _uiState.update { state ->
                state.copy(
                    favoritePhotoIds = updatedFavIds,
                    items = state.items.map { item ->
                        if (item is MixedWaterfallItemUi.MediaItem && item.id == photo.id) {
                            item.copy(isFavorite = !isFav)
                        } else {
                            item
                        }
                    }
                )
            }
        }
    }

    fun batchToggleFavorite(photos: List<Photo>, folders: List<Folder>) {
        val selectedItems = folders.map { MixedWaterfallItem.FolderTile(it) } +
            photos.map { MixedWaterfallItem.MediaTile(it) }
        if (selectedItems.isEmpty()) return

        val favoriteKeys = selectedItems
            .filter { item ->
                when (item) {
                    is MixedWaterfallItem.FolderTile -> settingsManager.isFolderFavorite(item.folder)
                    is MixedWaterfallItem.MediaTile -> settingsManager.isPhotoFavorite(item.photo.id)
                }
            }
            .mapTo(mutableSetOf(), MixedWaterfallIdentity::key)

        val plan = FavoriteSelectionPlanner.buildPlan(
            selectedItems = selectedItems,
            favoriteIds = favoriteKeys,
            isFavoritesView = _uiState.value.isFavorites,
            idSelector = MixedWaterfallIdentity::key
        )

        plan.toAdd.forEach { item ->
            when (item) {
                is MixedWaterfallItem.FolderTile -> settingsManager.addFavoriteFolder(item.folder)
                is MixedWaterfallItem.MediaTile -> settingsManager.addFavoritePhoto(item.photo)
            }
        }
        plan.toRemove.forEach { item ->
            when (item) {
                is MixedWaterfallItem.FolderTile -> settingsManager.removeFavoriteFolder(item.folder)
                is MixedWaterfallItem.MediaTile -> settingsManager.removeFavoritePhoto(item.photo.id)
            }
        }

        clearSelection()
        if (_uiState.value.isFavorites) {
            loadContent()
        } else {
            val favPhotoIds = settingsManager.getFavoritePhotos().map { it.id }.toSet()
            val favFolderPaths = settingsManager.getFavoriteFolders().map { it.path }.toSet()
            _uiState.update { state ->
                state.copy(
                    favoritePhotoIds = favPhotoIds,
                    favoriteFolderPaths = favFolderPaths,
                    items = state.items.map { item ->
                        if (item is MixedWaterfallItemUi.MediaItem) {
                            item.copy(isFavorite = item.id in favPhotoIds)
                        } else {
                            item
                        }
                    }
                )
            }
        }
    }

    suspend fun executeDelete(
        selectedPhotos: List<Photo>,
        selectedFolders: List<Folder>,
        alreadyDeletedLocalPhotos: List<Photo> = emptyList()
    ): Int = withContext(Dispatchers.IO) {
        val deletedPhotos = alreadyDeletedLocalPhotos.toMutableList()
        val deletedFolders = mutableListOf<Folder>()

        selectedPhotos.forEach { photo ->
            if (photo in alreadyDeletedLocalPhotos) return@forEach
            val repository: PhotoRepository = if (photo.isLocal) {
                LocalPhotoRepository(context)
            } else {
                RustWebDavPhotoRepository(settingsManager)
            }
            if (repository.deletePhoto(photo)) {
                deletedPhotos.add(photo)
            }
        }

        selectedFolders.forEach { folder ->
            val deleted = if (folder.isLocal) {
                LocalPhotoRepository(context).deleteFolder(folder)
            } else {
                withFolderSourceSlot(folder, restoreAfter = true) {
                    RustWebDavPhotoRepository(settingsManager).deleteFolder(folder)
                }
            }
            if (deleted) {
                deletedFolders.add(folder)
            }
        }

        deletedPhotos.forEach { settingsManager.removeFavoritePhoto(it.id) }
        deletedFolders.forEach(settingsManager::removeFavoriteFolder)

        val deletedCount = deletedPhotos.size + deletedFolders.size
        if (deletedCount > 0) {
            if (deletedPhotos.any { !it.isLocal } || deletedFolders.any { !it.isLocal }) {
                WebDavImageLoader.clearCache(context)
            } else {
                withContext(Dispatchers.Main) {
                    Glide.get(context).clearMemory()
                }
            }
        }

        clearSelection()
        loadContent(forceRefresh = !_uiState.value.isFavorites)

        deletedCount
    }

    fun onFolderPreviewVisible(folder: Folder, visible: Boolean) {
        previewBackfill.setVisible(folder, visible)
    }

    fun requestMissingFolderPreviews(forceRefresh: Boolean = false) {
        previewBackfill.requestVisiblePreviews(forceRefresh = forceRefresh)
    }

    fun updateFolderPreview(folder: Folder, previewUris: List<Uri>, hasSubFolders: Boolean) {
        val folderKey = MixedWaterfallIdentity.folderKey(folder)
        val stringUris = previewUris.map { it.toString() }
        var matched = 0
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item is MixedWaterfallItemUi.FolderItem && item.key == folderKey) {
                        matched++
                        val updatedFolder = item.folder.copy(
                            previewUris = previewUris,
                            hasSubFolders = item.folder.hasSubFolders || hasSubFolders
                        )
                        item.copy(
                            folder = updatedFolder,
                            previewUris = stringUris
                        )
                    } else {
                        item
                    }
                },
                rawFolders = state.rawFolders.map { current ->
                    if (current.path != folder.path) current else current.copy(
                        previewUris = previewUris,
                        hasSubFolders = current.hasSubFolders || hasSubFolders
                    )
                }
            )
        }
        android.util.Log.i(
            "MixedWaterfallVM",
            "updateFolderPreview path=${folder.path} previews=${previewUris.size} matched=$matched itemCount=${_uiState.value.items.size} first=${previewUris.firstOrNull()}"
        )
    }

    private suspend fun loadRemoteFolderPreview(folder: Folder, forceRefresh: Boolean) {
        if (folder.isLocal) return
        val sortOrder = settingsManager.getPhotoSortOrder()
        val accountKey = settingsManager.previewCacheAccountKey()
        if (!forceRefresh) {
            val cached = RemoteFolderPreviewMemoryCache.get(accountKey, sortOrder, folder.path)
            if (cached != null && cached.previewUriStrings.isNotEmpty()) {
                val cachedUris = cached.previewUriStrings.map(Uri::parse)
                updateFolderPreview(folder, cachedUris, cached.hasSubFolders)
                previewBackfill.markResolved(folder)
                return
            }
        }
        val preview = withFolderSourceSlot(folder, restoreAfter = true) {
            RustWebDavPhotoRepository(settingsManager).inspectFolder(folder.path, sortOrder, forceRefresh)
        } ?: return
        if (settingsManager.getPhotoSortOrder() != sortOrder) return
        val hasPreview = preview.previewUris.isNotEmpty()
        val resolvedUris = if (hasPreview) preview.previewUris else folder.previewUris
        updateFolderPreview(folder, resolvedUris, preview.hasSubFolders)
        if (hasPreview) {
            previewBackfill.markResolved(folder)
        } else {
            previewBackfill.markFailed(folder)
        }
    }

    fun updateStoragePermission(granted: Boolean) {
        _uiState.update { it.copy(storagePermissionGranted = granted) }
        if (granted) {
            loadContent()
        }
    }

    private suspend fun <T> withFolderSourceSlot(
        folder: Folder,
        restoreAfter: Boolean,
        block: suspend () -> T
    ): T {
        if (folder.isLocal || folder.sourceSlot < 0) {
            return block()
        }

        return webDavSlotMutex.withLock {
            val originalSlot = settingsManager.getCurrentSlot()
            if (originalSlot != folder.sourceSlot) {
                settingsManager.setCurrentSlot(folder.sourceSlot)
            }
            try {
                block()
            } finally {
                if (restoreAfter && originalSlot != folder.sourceSlot) {
                    settingsManager.setCurrentSlot(originalSlot)
                }
            }
        }
    }

    private fun resetFolderShuffleIfRandomSort() {
        val order = settingsManager.getPhotoSortOrder()
        if (order == SettingsManager.SORT_RANDOM_FOLDERS) {
            folderShuffleSeed = Random.nextLong()
        }
        if (SettingsManager.isRandomPhotoSort(order)) {
            photoShuffleSeed = Random.nextLong()
        }
    }

    /** Date-ordered media, shuffled when the "random photos" sort is active. */
    private fun sortMediaForDisplay(photos: List<Photo>, sortOrder: Int): List<Photo> {
        val sorted = FolderPreviewOrdering.sortPhotos(photos, sortOrder)
        return if (SettingsManager.isRandomPhotoSort(sortOrder)) {
            sorted.shuffled(Random(photoShuffleSeed))
        } else {
            sorted
        }
    }

    private fun computeTitle(folderPath: String, isWebDav: Boolean, isFavorites: Boolean): String {
        return when {
            isFavorites -> context.getString(R.string.favorites)
            folderPath.isEmpty() && !isWebDav -> context.getString(R.string.local_photos)
            folderPath.isEmpty() && isWebDav -> context.getString(R.string.remote)
            else -> {
                val lastSegment = folderPath.trimEnd('/').split('/').lastOrNull { it.isNotEmpty() } ?: folderPath
                Uri.decode(lastSegment)
            }
        }
    }

    private fun isVideo(photo: Photo): Boolean {
        return photo.mediaType == MediaType.VIDEO ||
            isVideo(photo.imageUri.toString()) ||
            detectMediaTypeByName(photo.title) == MediaType.VIDEO
    }

    private fun isVideo(uriOrName: String): Boolean {
        return detectMediaTypeByName(uriOrName) == MediaType.VIDEO ||
            (runCatching { detectMediaTypeByUri(Uri.parse(uriOrName)) }.getOrNull() == MediaType.VIDEO)
    }

    private fun SettingsManager.isFavoritePhoto(photoId: String): Boolean = isPhotoFavorite(photoId)
}
