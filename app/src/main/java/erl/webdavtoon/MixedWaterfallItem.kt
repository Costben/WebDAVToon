// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

sealed class MixedWaterfallItem {
    data class FolderTile(val folder: Folder) : MixedWaterfallItem()
    data class MediaTile(val photo: Photo) : MixedWaterfallItem()
}
