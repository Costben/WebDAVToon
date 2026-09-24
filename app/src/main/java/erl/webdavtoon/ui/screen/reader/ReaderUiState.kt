// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.reader

import erl.webdavtoon.Photo

enum class ReadingMode {
    CARD,
    WEBTOON
}

data class ReaderUiState(
    val photos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val readingMode: ReadingMode = ReadingMode.WEBTOON,
    val isImmersive: Boolean = false,
    val isSlideshowPlaying: Boolean = false,
    val slideshowIntervalMs: Long = 3000L,
    val isOrientationLocked: Boolean = false,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val sessionId: String? = null
) {
    val currentPhoto: Photo? get() = photos.getOrNull(currentIndex)
    val totalCount: Int get() = photos.size
    val pageDisplay: String get() = if (photos.isEmpty()) "0/0" else "${currentIndex + 1}/${photos.size}"
}
