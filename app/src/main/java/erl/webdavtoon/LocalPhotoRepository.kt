package erl.webdavtoon

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地图片仓库实现
 */
class LocalPhotoRepository(private val context: Context) : PhotoRepository {

    override suspend fun getPhotos(folderPath: String, recursive: Boolean): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATA
        )

        // 如果提供了 folderPath，只在该目录下查找
        val selection = if (folderPath.isNotEmpty()) {
            if (recursive) {
                "${MediaStore.Images.Media.DATA} LIKE ?"
            } else {
                "${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?"
            }
        } else {
            null
        }
        
        val selectionArgs = if (folderPath.isNotEmpty()) {
            val normalizedPath = folderPath.trimEnd('/')
            if (recursive) {
                arrayOf("$normalizedPath/%")
            } else {
                // 非递归：匹配当前文件夹，但不匹配子文件夹
                arrayOf("$normalizedPath/%", "$normalizedPath/%/%")
            }
        } else {
            null
        }

        // 如果是递归模式，先按文件夹路径排序，再按文件名排序
        // 如果不是递归模式，按设置的排序
        val sortOrder = if (recursive) {
            "${MediaStore.Images.Media.DATA} ASC" // DATA 包含完整路径，按此排序即按文件夹分组
        } else {
            when (SettingsManager(context).getSortOrder()) {
                0 -> "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
                1 -> "${MediaStore.Images.Media.DISPLAY_NAME} DESC"
                else -> "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            }
        }

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val date = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                photos.add(Photo(id.toString(), contentUri, name, width, height, true, date, size))
            }
        }
        photos
    }

    override suspend fun getFolders(rootPath: String, forceRefresh: Boolean): List<Folder> = withContext(Dispatchers.IO) {
        val foldersMap = mutableMapOf<String, Folder>()
        
        // 我们需要这些字段来决定预览图和计数
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        // 为首页加载时，不使用 selection，获取所有图片及其所在目录
        val selection = if (rootPath.isNotEmpty()) {
            "${MediaStore.Images.Media.DATA} LIKE ?"
        } else {
            null
        }
        val selectionArgs = if (rootPath.isNotEmpty()) {
            arrayOf("$rootPath/%")
        } else {
            null
        }

        val sortOrder = when (SettingsManager(context).getSortOrder()) {
            0 -> "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            1 -> "${MediaStore.Images.Media.DISPLAY_NAME} DESC"
            else -> "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        }

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn) ?: continue
                    val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"
                    val file = File(path)
                    val parentFile = file.parentFile ?: continue
                    val parentPath = parentFile.absolutePath
                    
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    
                    if (rootPath.isEmpty()) {
                        // 首页：显示包含图片的直接文件夹
                        val folder = foldersMap[parentPath]
                        if (folder == null) {
                            foldersMap[parentPath] = Folder(parentPath, bucketName, true, 1, listOf(contentUri))
                        } else {
                            val newPreviewUris = if (folder.previewUris.size < 4) {
                                folder.previewUris + contentUri
                            } else {
                                folder.previewUris
                            }
                            foldersMap[parentPath] = folder.copy(
                                photoCount = folder.photoCount + 1,
                                previewUris = newPreviewUris
                            )
                        }
                    } else {
                        // 子目录逻辑
                        if (parentPath.startsWith(rootPath) && parentPath != rootPath) {
                            val relativePath = parentPath.substring(rootPath.length).trimStart('/')
                            val pathComponents = relativePath.split('/')
                            val firstComponent = pathComponents.firstOrNull() ?: continue
                            val directChildPath = File(rootPath, firstComponent).absolutePath
                            
                            val folder = foldersMap[directChildPath]
                            // 如果 relativePath 包含多个组件，说明这个一级子目录还有二级子目录
                            val currentlyHasSubFolders = pathComponents.size > 1

                            if (folder == null) {
                                foldersMap[directChildPath] = Folder(
                                    directChildPath, 
                                    firstComponent, 
                                    true, 
                                    1, 
                                    listOf(contentUri),
                                    currentlyHasSubFolders
                                )
                            } else {
                                val newPreviewUris = if (folder.previewUris.size < 4) {
                                    folder.previewUris + contentUri
                                } else {
                                    folder.previewUris
                                }
                                foldersMap[directChildPath] = folder.copy(
                                    photoCount = folder.photoCount + 1,
                                    previewUris = newPreviewUris,
                                    hasSubFolders = folder.hasSubFolders || currentlyHasSubFolders
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalPhotoRepository", "Error querying MediaStore", e)
        }

        // 如果 rootPath 为空且不是子目录浏览，我们需要特殊的预览逻辑？
        // 其实用户要求的是 WebDAV 的特殊预览逻辑。本地照片暂时遵循默认的前4张。
        
        foldersMap.values.sortedBy { it.name }
    }
}
