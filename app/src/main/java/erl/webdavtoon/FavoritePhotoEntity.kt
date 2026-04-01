package erl.webdavtoon

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_photos")
data class FavoritePhotoEntity(
    @PrimaryKey val id: String,
    val imageUri: String,
    val title: String,
    val width: Int,
    val height: Int,
    val isLocal: Boolean,
    val dateModified: Long,
    val size: Long,
    val folderPath: String,
    val addedAt: Long = System.currentTimeMillis()
)
