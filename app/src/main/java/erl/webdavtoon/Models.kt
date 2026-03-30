package erl.webdavtoon

import android.net.Uri

/**
 * 图片数据模型
 */
data class Photo(
    val id: String,
    val imageUri: android.net.Uri,
    val title: String,
    val width: Int = 0,
    val height: Int = 0,
    val isLocal: Boolean = true,
    val dateModified: Long = 0,
    val size: Long = 0,
    val folderPath: String = "" // Added folderPath for grouping
)

/**
 * 文件夹数据模型
 */
data class Folder(
    val path: String,
    val name: String,
    val isLocal: Boolean = true,
    val photoCount: Int = 0,
    val previewUris: List<android.net.Uri> = emptyList(),
    val hasSubFolders: Boolean = false,
    val dateModified: Long = 0
)
