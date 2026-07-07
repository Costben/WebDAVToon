package erl.webdavtoon

object MixedWaterfallPlanner {

    fun buildItems(
        folders: List<Folder>,
        media: List<Photo>,
        folderSortOrder: Int,
        folderShuffleSeed: Long = 0L
    ): List<MixedWaterfallItem> {
        return sortFolders(folders, folderSortOrder, folderShuffleSeed).map { MixedWaterfallItem.FolderTile(it) } +
            media.map { MixedWaterfallItem.MediaTile(it) }
    }

    fun sortFolders(
        folders: List<Folder>,
        sortOrder: Int,
        folderShuffleSeed: Long = 0L
    ): List<Folder> {
        return when (sortOrder) {
            SettingsManager.SORT_NAME_ASC -> folders.sortedBy { it.name.lowercase() }
            SettingsManager.SORT_NAME_DESC -> folders.sortedByDescending { it.name.lowercase() }
            SettingsManager.SORT_DATE_DESC -> folders.sortedByDescending { it.dateModified }
            SettingsManager.SORT_DATE_ASC -> folders.sortedBy { it.dateModified }
            SettingsManager.SORT_RANDOM_FOLDERS -> folders.shuffled(kotlin.random.Random(folderShuffleSeed))
            else -> folders.sortedByDescending { it.dateModified }
        }
    }
}
