package erl.webdavtoon

import android.net.Uri

/** Media model for images and videos. */
enum class MediaType {
    IMAGE,
    VIDEO
}

data class Photo(
    val id: String,
    val imageUri: Uri,
    val title: String,
    val width: Int = 0,
    val height: Int = 0,
    val isLocal: Boolean = true,
    val dateModified: Long = 0,
    val size: Long = 0,
    val folderPath: String = "",
    val mediaType: MediaType = MediaType.IMAGE,
    val durationMs: Long? = null
)

/** Folder data model. */
data class Folder(
    val path: String,
    val name: String,
    val isLocal: Boolean = true,
    val photoCount: Int = 0,
    val previewUris: List<Uri> = emptyList(),
    val hasSubFolders: Boolean = false,
    val dateModified: Long = 0,
    val sourceSlot: Int = -1
)
