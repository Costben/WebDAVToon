package erl.webdavtoon

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalPhotoRepository(private val context: Context) : PhotoRepository {

    private val mediaCollection: Uri = MediaStore.Files.getContentUri("external")

    private fun buildSortOrder(): String {
        return when (SettingsManager(context).getPhotoSortOrder()) {
            0 -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC"
            1 -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} DESC"
            2 -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            3 -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} ASC"
            else -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        }
    }

    private fun buildSelection(
        folderPath: String,
        recursive: Boolean,
        query: MediaQuery
    ): Pair<String?, Array<String>?> {
        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()

        parts.add("(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)")
        args.add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
        args.add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())

        if (folderPath.isNotEmpty()) {
            val normalized = folderPath.trimEnd('/')
            parts.add("${MediaStore.Files.FileColumns.DATA} LIKE ?")
            args.add("$normalized/%")
            if (!recursive) {
                parts.add("${MediaStore.Files.FileColumns.DATA} NOT LIKE ?")
                args.add("$normalized/%/%")
            }
        }

        if (query.minSizeBytes != null) {
            parts.add("${MediaStore.Files.FileColumns.SIZE} >= ?")
            args.add(query.minSizeBytes.toString())
        }
        if (query.maxSizeBytes != null) {
            parts.add("${MediaStore.Files.FileColumns.SIZE} <= ?")
            args.add(query.maxSizeBytes.toString())
        }

        return if (parts.isEmpty()) {
            null to null
        } else {
            parts.joinToString(" AND ") to args.toTypedArray()
        }
    }

    private fun matchesMediaQuery(media: Photo, query: MediaQuery): Boolean {
        val keyword = query.keyword.trim()
        if (keyword.isNotEmpty() && !media.title.contains(keyword, ignoreCase = true)) return false

        if (query.extensions.isNotEmpty()) {
            val uri = media.imageUri.toString().lowercase()
            val matched = query.extensions.any { ext ->
                val clean = ext.trim().trimStart('.').lowercase()
                uri.endsWith(".$clean")
            }
            if (!matched) return false
        }

        return true
    }

    private fun toContentUri(id: Long, mediaType: MediaType): Uri {
        return when (mediaType) {
            MediaType.IMAGE -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            MediaType.VIDEO -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
        }
    }

    private fun parseMediaFromCursor(cursor: android.database.Cursor): Photo? {
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
        val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
        val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)

        val path = cursor.getString(dataCol) ?: return null
        val name = cursor.getString(nameCol) ?: return null
        if (path.contains("/.") || name.startsWith(".")) return null

        val rawType = cursor.getInt(mediaTypeCol)
        val mediaType = when (rawType) {
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaType.IMAGE
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaType.VIDEO
            else -> detectMediaTypeByName(name) ?: return null
        }

        if (!isSupportedMediaName(name)) return null

        val id = cursor.getLong(idCol)
        val contentUri = toContentUri(id, mediaType)
        val parentPath = File(path).parent ?: ""
        val durationMs = if (mediaType == MediaType.VIDEO) {
            cursor.getLong(durationCol).takeIf { it > 0L }
        } else {
            null
        }

        return Photo(
            id = id.toString(),
            imageUri = contentUri,
            title = name,
            width = cursor.getInt(widthCol),
            height = cursor.getInt(heightCol),
            isLocal = true,
            dateModified = cursor.getLong(dateCol),
            size = cursor.getLong(sizeCol),
            folderPath = parentPath,
            mediaType = mediaType,
            durationMs = durationMs
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
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Video.VideoColumns.DURATION
        )

        val sortOrder = buildSortOrder()
        val (selection, selectionArgs) = buildSelection(folderPath, recursive, query)

        val allItems = context.contentResolver.query(
            mediaCollection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val medias = mutableListOf<Photo>()
            while (cursor.moveToNext()) {
                parseMediaFromCursor(cursor)?.let { medias.add(it) }
            }
            medias
        } ?: emptyList()

        val filtered = allItems.asSequence().filter { matchesMediaQuery(it, query) }.toList()

        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(1)
        val pageItems = filtered.drop(safeOffset).take(safeLimit)
        val next = safeOffset + pageItems.size

        MediaPageResult(
            items = pageItems,
            hasMore = next < filtered.size,
            nextOffset = next
        )
    }

    override suspend fun getPhotos(folderPath: String, recursive: Boolean, forceRefresh: Boolean): List<Photo> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Video.VideoColumns.DURATION
        )

        val sortOrder = buildSortOrder()
        val (selection, selectionArgs) = buildSelection(folderPath, recursive, MediaQuery())

        context.contentResolver.query(
            mediaCollection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val media = mutableListOf<Photo>()
            while (cursor.moveToNext()) {
                parseMediaFromCursor(cursor)?.let { media.add(it) }
            }
            media
        } ?: emptyList()
    }

    override suspend fun getFolders(rootPath: String, forceRefresh: Boolean): List<Folder> = withContext(Dispatchers.IO) {
        val foldersMap = linkedMapOf<String, Folder>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )

        val baseSelectionParts = mutableListOf<String>()
        val baseSelectionArgs = mutableListOf<String>()
        baseSelectionParts.add("(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)")
        baseSelectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
        baseSelectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())

        if (rootPath.isNotEmpty()) {
            baseSelectionParts.add("${MediaStore.Files.FileColumns.DATA} LIKE ?")
            baseSelectionArgs.add("${rootPath.trimEnd('/')}/%")
        }

        val selection = baseSelectionParts.joinToString(" AND ")
        val selectionArgs = baseSelectionArgs.toTypedArray()

        context.contentResolver.query(
            mediaCollection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

            val directRootPreviewUris = mutableListOf<Uri>()
            var directRootCount = 0
            var directRootDateModified = 0L

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: continue
                val name = cursor.getString(nameCol) ?: continue
                if (path.contains("/.") || name.startsWith(".")) continue
                if (!isSupportedMediaName(name)) continue

                val file = File(path)
                val parent = file.parentFile ?: continue
                val parentPath = parent.absolutePath

                val id = cursor.getLong(idCol)
                val dateModified = cursor.getLong(dateCol)
                val mediaType = when (cursor.getInt(mediaTypeCol)) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaType.VIDEO
                    else -> MediaType.IMAGE
                }
                val uri = toContentUri(id, mediaType)

                if (rootPath.isNotEmpty() && parentPath == rootPath) {
                    directRootCount++
                    if (directRootPreviewUris.size < 4) directRootPreviewUris.add(uri)
                    directRootDateModified = maxOf(directRootDateModified, dateModified)
                    continue
                }

                val folderKey: String
                val folderName: String
                val hasSubFolders: Boolean

                if (rootPath.isEmpty()) {
                    val commonRoots = listOf("Pictures", "DCIM", "Download", "Movies")
                    val pathSegments = parentPath.split(File.separator).filter { it.isNotEmpty() }

                    var foundRootIndex = -1
                    for (rootName in commonRoots) {
                        foundRootIndex = pathSegments.indexOf(rootName)
                        if (foundRootIndex != -1) break
                    }

                    if (foundRootIndex != -1 && foundRootIndex < pathSegments.size - 1) {
                        val topLevelName = pathSegments[foundRootIndex + 1]
                        val topLevelPath = pathSegments.take(foundRootIndex + 2)
                            .joinToString(File.separator, prefix = File.separator)

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

            if (rootPath.isNotEmpty() && directRootCount > 0 && foldersMap.isNotEmpty()) {
                foldersMap["virtual://internal_photos?path=$rootPath"] = Folder(
                    path = "virtual://internal_photos?path=$rootPath",
                    name = context.getString(R.string.internal_photos, directRootCount),
                    isLocal = true,
                    photoCount = directRootCount,
                    previewUris = directRootPreviewUris,
                    hasSubFolders = false,
                    dateModified = directRootDateModified
                )
            }
        }

        foldersMap.values.toList()
    }

    override suspend fun deletePhoto(photo: Photo): Boolean = withContext(Dispatchers.IO) {
        try {
            val deletedRows = context.contentResolver.delete(photo.imageUri, null, null)
            deletedRows > 0
        } catch (e: Exception) {
            android.util.Log.e("LocalPhotoRepo", "Failed to delete media: ${photo.imageUri}", e)
            false
        }
    }

    override suspend fun deleteFolder(folder: Folder): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedPath = folder.path.trimEnd('/')
            val selection = "${MediaStore.Files.FileColumns.DATA} LIKE ?"
            val selectionArgs = arrayOf("$normalizedPath/%")
            val deletedRows = context.contentResolver.delete(
                mediaCollection,
                selection,
                selectionArgs
            )

            val file = File(normalizedPath)
            if (file.exists() && file.isDirectory) {
                file.deleteRecursively()
            }

            deletedRows >= 0
        } catch (e: Exception) {
            android.util.Log.e("LocalPhotoRepo", "Failed to delete folder: ${folder.path}", e)
            false
        }
    }
}
