package erl.webdavtoon

sealed class MixedWaterfallItem {
    data class FolderTile(val folder: Folder) : MixedWaterfallItem()
    data class MediaTile(val photo: Photo) : MixedWaterfallItem()
}
