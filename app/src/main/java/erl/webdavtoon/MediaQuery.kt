// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

data class MediaQuery(
    val keyword: String = "",
    val extensions: Set<String> = emptySet(),
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val randomizePhotos: Boolean = false
)

data class MediaPageResult(
    val items: List<Photo>,
    val hasMore: Boolean,
    val nextOffset: Int
)
