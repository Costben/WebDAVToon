package erl.webdavtoon.ui.screen.waterfall

import android.net.Uri
import erl.webdavtoon.Folder
import erl.webdavtoon.Photo
import erl.webdavtoon.ui.UiMode

sealed interface MixedWaterfallItemUi {
    val key: String
    val isSelected: Boolean

    data class FolderItem(
        val folder: Folder,
        override val key: String,
        val name: String,
        val path: String,
        val isLocal: Boolean,
        val photoCount: Int,
        val previewUris: List<String>,
        val aspectRatio: Float = 1.0f,
        override val isSelected: Boolean = false
    ) : MixedWaterfallItemUi

    data class MediaItem(
        val photo: Photo,
        override val key: String,
        val id: String,
        val uri: Uri,
        val title: String,
        val isLocal: Boolean,
        val isVideo: Boolean,
        val durationText: String,
        val aspectRatio: Float,
        val isFavorite: Boolean,
        override val isSelected: Boolean = false
    ) : MixedWaterfallItemUi
}

data class MixedWaterfallUiState(
    val folderPath: String = "",
    val isWebDav: Boolean = false,
    val isFavorites: Boolean = false,
    val title: String = "",
    val items: List<MixedWaterfallItemUi> = emptyList(),
    val searchKeyword: String = "",
    val isSearching: Boolean = false,
    val sortOrder: Int = erl.webdavtoon.SettingsManager.SORT_DATE_DESC,
    val rotationLocked: Boolean = false,
    val rawFolders: List<Folder> = emptyList(),
    val rawPhotos: List<Photo> = emptyList(),
    val loading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val uiMode: UiMode = UiMode.Miuix,
    val columns: Int = 2,
    val virtualColumns: Float = 2f,
    val isZooming: Boolean = false,
    val showFilenames: Boolean = true,
    val isSelectionMode: Boolean = false,
    val selectedKeys: Set<String> = emptySet(),
    val favoritePhotoIds: Set<String> = emptySet(),
    val favoriteFolderPaths: Set<String> = emptySet(),
    val storagePermissionGranted: Boolean = true
) {
    /** [items] filtered by the current search keyword (folders by name, media by title). */
    val visibleItems: List<MixedWaterfallItemUi>
        get() {
            if (searchKeyword.isBlank()) return items
            return items.filter { item ->
                when (item) {
                    is MixedWaterfallItemUi.FolderItem ->
                        erl.webdavtoon.FolderSearchMatcher.matches(item.name, searchKeyword)
                    is MixedWaterfallItemUi.MediaItem ->
                        erl.webdavtoon.FolderSearchMatcher.matches(item.title, searchKeyword)
                }
            }
        }

    val selectedCount: Int get() = selectedKeys.size
    val isAllSelected: Boolean get() = items.isNotEmpty() && selectedKeys.size == items.size
    val selectedPhotos: List<Photo>
        get() = items.mapNotNull { item ->
            if (item is MixedWaterfallItemUi.MediaItem && item.key in selectedKeys) item.photo else null
        }
    val selectedFolders: List<Folder>
        get() = items.mapNotNull { item ->
            if (item is MixedWaterfallItemUi.FolderItem && item.key in selectedKeys) item.folder else null
        }
}
