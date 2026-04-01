package erl.webdavtoon

import android.content.ContentUris
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalPhotoRepository(private val context: Context) : PhotoRepository {

    private fun buildSortOrder(): String {
        return when (SettingsManager(context).getPhotoSortOrder()) {
            0 -> "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            1 -> "${MediaStore.Images.Media.DISPLAY_NAME} DESC"
            2 -> "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            3 -> "${MediaStore.Images.Media.DATE_MODIFIED} ASC"
            else -> "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        }
    }

    private fun buildSelection(
        folderPath: String,
        recursive: Boolean,
        query: MediaQuery
    ): Pair<String?, Array<String>?> {
        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (folderPath.isNotEmpty()) {
            val normalized = folderPath.trimEnd('/')
            parts.add("${MediaStore.Images.Media.DATA} LIKE ?")
            args.add("$normalized/%")
            if (!recursive) {
                parts.add("${MediaStore.Images.Media.DATA} NOT LIKE ?")
                args.add("$normalized/%/%")
            }
        }

        if (query.minSizeBytes != null) {
            parts.add("${MediaStore.Images.Media.SIZE} >= ?")
            args.add(query.minSizeBytes.toString())
        }
        if (query.maxSizeBytes != null) {
            parts.add("${MediaStore.Images.Media.SIZE} <= ?")
            args.add(query.maxSizeBytes.toString())
        }

        return if (parts.isEmpty()) null to null else parts.joinToString(" AND ") to args.toTypedArray()
    }

    private fun matchesMediaQuery(photo: Photo, query: MediaQuery): Boolean {
        val keyword = query.keyword.trim()
        if (keyword.isNotEmpty() && !photo.title.contains(keyword, ignoreCase = true)) return false
        if (query.extensions.isNotEmpty()) {
            val uri = photo.imageUri.toString().lowercase()
            val matched = query.extensions.any { ext ->
                val clean = ext.trim().trimStart('.').lowercase()
                uri.endsWith(".$clean")
            }
            if (!matched) return false
        }
        return true
    }

    private fun parsePhotoFromCursor(cursor: android.database.Cursor): Photo? {
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

        val path = cursor.getString(dataCol) ?: return null
        val name = cursor.getString(nameCol) ?: return null
        if (path.contains("/.") || name.startsWith(".")) return null

        val id = cursor.getLong(idCol)
        val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        val parentPath = File(path).parent ?: ""

        return Photo(
            id = id.toString(),
            imageUri = contentUri,
            title = name,
            width = cursor.getInt(widthCol),
            height = cursor.getInt(heightCol),
            isLocal = true,
            dateModified = cursor.getLong(dateCol),
            size = cursor.getLong(sizeCol),
            folderPath = parentPath
        )
    }

    override suspend fun queryMediaPage(
        folderPath: String,
        recursive: Boolean,
        query: MediaQuery,
        offset: Int,
        limit: Int,
        forceRefresh: Boolean
    ): MediaPageResult = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATA
        )

        val sortOrder = buildSortOrder()
        val (selection, selectionArgs) = buildSelection(folderPath, recursive, query)

        // 查询总数
        val totalCount = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            sortOrder
        )?.use { it.count } ?: 0

        // SQL 分页查询，使用 queryArgs 避免把 LIMIT/OFFSET 拼进 sortOrder
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(1)

        val items = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val queryArgs = Bundle().apply {
                if (selection != null) {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                }
                if (selectionArgs != null) {
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                }
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sortOrder.substringBefore(' ')))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, if (sortOrder.endsWith("ASC")) {
                    ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                } else {
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                })
                putInt(ContentResolver.QUERY_ARG_LIMIT, safeLimit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, safeOffset)
            }

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                queryArgs,
                null
            )
        } else {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
        }?.use { cursor ->
            val photos = mutableListOf<Photo>()
            while (cursor.moveToNext()) {
                parsePhotoFromCursor(cursor)?.let { photos.add(it) }
            }
            photos
        } ?: emptyList()

        // 应用 keyword / extension 过滤
        val filtered = items.asSequence()
            .filter { matchesMediaQuery(it, query) }
            .toList()

        MediaPageResult(
            items = filtered,
            hasMore = safeOffset + filtered.size < totalCount,
            nextOffset = safeOffset + filtered.size
        )
    }

    override suspend fun getPhotos(folderPath: String, recursive: Boolean, forceRefresh: Boolean): List<Photo> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATA
        )

        val sortOrder = buildSortOrder()
        val (selection, selectionArgs) = buildSelection(folderPath, recursive, MediaQuery())

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val photos = mutableListOf<Photo>()
            while (cursor.moveToNext()) {
                parsePhotoFromCursor(cursor)?.let { photos.add(it) }
            }
            photos
        } ?: emptyList()
    }

    override suspend fun getFolders(rootPath: String, forceRefresh: Boolean): List<Folder> = withContext(Dispatchers.IO) {
        val foldersMap = linkedMapOf<String, Folder>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        val selection = if (rootPath.isNotEmpty()) "${MediaStore.Images.Media.DATA} LIKE ?" else null
        val selectionArgs = if (rootPath.isNotEmpty()) arrayOf("${rootPath.trimEnd('/')}/%") else null

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

            val directRootPhotos = mutableListOf<Uri>()
            var directRootPhotoCount = 0
            var directRootDateModified = 0L

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: continue
                if (path.contains("/.") || path.substringAfterLast('/').startsWith(".")) continue

                val file = File(path)
                val parent = file.parentFile ?: continue
                val parentPath = parent.absolutePath

                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val dateModified = cursor.getLong(dateCol)

                if (rootPath.isNotEmpty() && parentPath == rootPath) {
                    directRootPhotoCount++
                    if (directRootPhotos.size < 4) directRootPhotos.add(uri)
                    directRootDateModified = maxOf(directRootDateModified, dateModified)
                    continue
                }

                val folderKey: String
                val folderName: String
                val hasSubFolders: Boolean

                if (rootPath.isEmpty()) {
                    val relative = parentPath.trimStart(File.separatorChar)
                    val first = relative.substringBefore(File.separator)
                    
                    // On Android, the path might start with /storage/emulated/0
                    // We need to be careful not to just take the first segment of the absolute path
                    // Let's try to find a meaningful root like "Pictures", "DCIM", "Download"
                    val commonRoots = listOf("Pictures", "DCIM", "Download", "Movies")
                    val pathSegments = parentPath.split(File.separator).filter { it.isNotEmpty() }
                    
                    var foundRootIndex = -1
                    for (rootName in commonRoots) {
                        foundRootIndex = pathSegments.indexOf(rootName)
                        if (foundRootIndex != -1) break
                    }

                    if (foundRootIndex != -1 && foundRootIndex < pathSegments.size - 1) {
                        // If we found a common root and it's not the last segment, 
                        // the next segment is our top-level folder
                        val topLevelName = pathSegments[foundRootIndex + 1]
                        val topLevelPath = pathSegments.take(foundRootIndex + 2).joinToString(File.separator, prefix = File.separator)
                        
                        folderKey = topLevelPath
                        folderName = when (topLevelName) {
                            "Pictures" -> context.getString(R.string.folder_pictures)
                            "DCIM" -> context.getString(R.string.folder_dcim)
                            "Download" -> context.getString(R.string.folder_download)
                            "Movies" -> context.getString(R.string.folder_movies)
                            else -> topLevelName
                        }
                        hasSubFolders = parentPath != topLevelPath
                    } else {
                        // Fallback to previous logic if no common root found
                        folderKey = parentPath
                        val rawName = parent.name
                        folderName = when (rawName) {
                            "Pictures" -> context.getString(R.string.folder_pictures)
                            "DCIM" -> context.getString(R.string.folder_dcim)
                            "Download" -> context.getString(R.string.folder_download)
                            "Movies" -> context.getString(R.string.folder_movies)
                            else -> rawName
                        }
                        hasSubFolders = false
                    }
                } else {
                    if (!parentPath.startsWith(rootPath)) continue
                    val relative = parentPath.removePrefix(rootPath).trimStart(File.separatorChar)
                    if (relative.isBlank()) continue
                    val first = relative.substringBefore(File.separator)
                    val directChildPath = File(rootPath, first).absolutePath
                    folderKey = directChildPath
                    folderName = when (first) {
                        "Pictures" -> context.getString(R.string.folder_pictures)
                        "DCIM" -> context.getString(R.string.folder_dcim)
                        "Download" -> context.getString(R.string.folder_download)
                        "Movies" -> context.getString(R.string.folder_movies)
                        else -> first
                    }
                    hasSubFolders = parentPath != directChildPath
                }

                val existing = foldersMap[folderKey]
                if (existing == null) {
                    foldersMap[folderKey] = Folder(
                        path = folderKey,
                        name = folderName,
                        isLocal = true,
                        photoCount = 1,
                        previewUris = listOf(uri),
                        hasSubFolders = hasSubFolders,
                        dateModified = dateModified
                    )
                } else {
                    val previews = if (existing.previewUris.size < 4) existing.previewUris + uri else existing.previewUris
                    foldersMap[folderKey] = existing.copy(
                        photoCount = existing.photoCount + 1,
                        previewUris = previews,
                        hasSubFolders = existing.hasSubFolders || hasSubFolders,
                        dateModified = maxOf(existing.dateModified, dateModified)
                    )
                }
            }

            if (rootPath.isNotEmpty() && directRootPhotoCount > 0 && foldersMap.isNotEmpty()) {
                foldersMap["virtual://internal_photos?path=$rootPath"] = Folder(
                    path = "virtual://internal_photos?path=$rootPath",
                    name = context.getString(R.string.internal_photos, directRootPhotoCount),
                    isLocal = true,
                    photoCount = directRootPhotoCount,
                    previewUris = directRootPhotos,
                    hasSubFolders = false,
                    dateModified = directRootDateModified
                )
            }
        }

        foldersMap.values.toList() // Return unsorted, Activity handles sorting
    }

    override suspend fun deletePhoto(photo: Photo): Boolean = withContext(Dispatchers.IO) {
        try {
            val deletedRows = context.contentResolver.delete(photo.imageUri, null, null)
            deletedRows > 0
        } catch (e: Exception) {
            android.util.Log.e("LocalPhotoRepo", "Failed to delete photo: ${photo.imageUri}", e)
            false
        }
    }

    override suspend fun deleteFolder(folder: Folder): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedPath = folder.path.trimEnd('/')
            val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("$normalizedPath/%")
            val deletedRows = context.contentResolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                selection,
                selectionArgs
            )
            
            // Also try to delete the physical folder if it's empty or exists
            val file = File(normalizedPath)
            if (file.exists() && file.isDirectory) {
                file.deleteRecursively()
            }
            
            deletedRows >= 0 // Even if 0 rows deleted from MediaStore, filesystem delete might have happened
        } catch (e: Exception) {
            android.util.Log.e("LocalPhotoRepo", "Failed to delete folder: ${folder.path}", e)
            false
        }
    }
}
