package erl.webdavtoon

import android.net.Uri

/**
 * 媒体数据模型（图片/视频）
 */
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

/**
 * 文件夹数据模型
 */
data class Folder(
    val path: String,
    val name: String,
    val isLocal: Boolean = true,
    val photoCount: Int = 0,
    val previewUris: List<Uri> = emptyList(),
    val hasSubFolders: Boolean = false,
    val dateModified: Long = 0
)
