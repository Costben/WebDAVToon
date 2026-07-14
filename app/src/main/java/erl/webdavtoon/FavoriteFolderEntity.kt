package erl.webdavtoon

import androidx.room.Entity

@Entity(
    tableName = "favorite_folders",
    primaryKeys = ["path", "isLocal", "sourceSlot", "isPrivate"]
)
data class FavoriteFolderEntity(
    val path: String,
    val name: String,
    val isLocal: Boolean,
    val previewUrisJson: String,
    val hasSubFolders: Boolean,
    val dateModified: Long,
    val sourceSlot: Int,
    val addedAt: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false
)
