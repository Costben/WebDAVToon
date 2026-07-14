package erl.webdavtoon

object MixedWaterfallIdentity {
    fun key(item: MixedWaterfallItem): String = when (item) {
        is MixedWaterfallItem.FolderTile -> folderKey(item.folder)
        is MixedWaterfallItem.MediaTile -> mediaKey(item.photo)
    }

    fun folderKey(folder: Folder): String {
        val slot = if (folder.isLocal) -1 else folder.sourceSlot
        return "folder:${folder.isLocal}:$slot:${folder.path}"
    }

    fun mediaKey(photo: Photo): String = mediaKey(photo.id)

    fun mediaKey(photoId: String): String = "media:$photoId"
}
