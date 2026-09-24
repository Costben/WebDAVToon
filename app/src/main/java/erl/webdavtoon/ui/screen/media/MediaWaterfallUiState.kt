// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.media

import erl.webdavtoon.FolderSearchMatcher
import erl.webdavtoon.Photo
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.ui.screen.settings.WebDavSlotUi
import erl.webdavtoon.ui.screen.waterfall.MixedWaterfallItemUi

/** A media-only page reuses the mixed waterfall's media tile model. */
typealias MediaWaterfallItemUi = MixedWaterfallItemUi.MediaItem

data class MediaWaterfallUiState(
    val title: String = "",
    val folderPath: String = "",
    val isRemote: Boolean = false,
    val isRecursive: Boolean = false,
    val items: List<MediaWaterfallItemUi> = emptyList(),
    val searchKeyword: String = "",
    val isSearching: Boolean = false,
    val sortOrder: Int = SettingsManager.SORT_DATE_DESC,
    val rotationLocked: Boolean = false,
    val loading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val columns: Int = 2,
    val showFilenames: Boolean = true,
    val isSelectionMode: Boolean = false,
    val selectedKeys: Set<String> = emptySet(),
    val favoritePhotoIds: Set<String> = emptySet(),
    val currentSlot: Int = 0,
    val slots: List<WebDavSlotUi> = emptyList(),
    val isPrivacyMode: Boolean = false,
    val themeId: Int = erl.webdavtoon.ThemeHelper.THEME_FOLLOW_DEVICE,
    val useCouiDefaultColors: Boolean = false,
    val storagePermissionGranted: Boolean = true,
) {
    /** [items] filtered by the current search keyword. */
    val visibleItems: List<MediaWaterfallItemUi>
        get() {
            if (searchKeyword.isBlank()) return items
            return items.filter { FolderSearchMatcher.matches(it.title, searchKeyword) }
        }

    val selectedCount: Int get() = selectedKeys.size
    val isAllSelected: Boolean get() = visibleItems.isNotEmpty() && selectedKeys.size == visibleItems.size
    val selectedPhotos: List<Photo>
        get() = visibleItems.filter { it.key in selectedKeys }.map { it.photo }
}
