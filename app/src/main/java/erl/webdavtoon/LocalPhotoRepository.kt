// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
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
    private val maxFolderPreviewCandidates = 12
    private val tinyPreviewImageBytes = 96L * 1024L

    companion object {
        fun getStorageRoots(context: Context? = null): List<String> {
            val roots = linkedSetOf<String>()
            runCatching {
                android.os.Environment.getExternalStorageDirectory()?.absolutePath?.trimEnd('/')?.takeIf { it.isNotEmpty() }?.let {
                    roots.add(it)
                }
            }
            if (context != null) {
                runCatching {
                    context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
                        val path = dir.absolutePath
                        val androidIdx = path.indexOf("/Android/")
                        if (androidIdx > 0) {
                            roots.add(path.substring(0, androidIdx).trimEnd('/'))
                        }
                    }
                }
            }
            if (roots.isEmpty()) {
                roots.add("/storage/emulated/0")
            }
            return roots.toList()
        }
    }

    private fun getStorageRoots(): List<String> = Companion.getStorageRoots(context)

    private fun localizeFolderName(name: String): String {
        return when (name) {
            "Pictures" -> context.getString(R.string.folder_pictures)
            "DCIM" -> context.getString(R.string.folder_dcim)
            "Download" -> context.getString(R.string.folder_download)
            "Movies" -> context.getString(R.string.folder_movies)
            else -> name
        }
    }

    private data class LocalPreviewCandidate(
        val uri: Uri,
        val title: String,
        val dateModified: Long,
        val mediaType: MediaType,
        val sizeBytes: Long
    )

    private fun buildSortOrder(): String {
        return when (SettingsManager(context).getPhotoSortOrder()) {
            0 -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC"
            1 -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} DESC"
            2 -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            3 -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} ASC"
            else -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        }
    }

    private fun buildFolderPreviewSortOrder(sortOrder: Int): String {
        return when (sortOrder) {
            SettingsManager.SORT_NAME_ASC -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC"
            SettingsManager.SORT_NAME_DESC -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} DESC"
            SettingsManager.SORT_DATE_DESC -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            SettingsManager.SORT_DATE_ASC -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} ASC"
            SettingsManager.SORT_RANDOM_FOLDERS -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
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
        } else if (!recursive) {
            val roots = getStorageRoots()
            val rootClauses = roots.map {
                "(${MediaStore.Files.FileColumns.DATA} LIKE ? AND ${MediaStore.Files.FileColumns.DATA} NOT LIKE ?)"
            }
            parts.add("(${rootClauses.joinToString(" OR ")})")
            roots.forEach { root ->
                args.add("$root/%")
                args.add("$root/%/%")
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
        if (path.contains("/.") || name.startsWith(".") || path.contains("/dev-dump/")) return null

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

    private fun registerPreviewCandidate(
        candidates: MutableList<LocalPreviewCandidate>,
        uri: Uri,
        title: String,
        dateModified: Long,
        mediaType: MediaType,
        sizeBytes: Long
    ) {
        if (candidates.size < maxFolderPreviewCandidates) {
            candidates.add(
                LocalPreviewCandidate(
                    uri = uri,
                    title = title,
                    dateModified = dateModified,
                    mediaType = mediaType,
                    sizeBytes = sizeBytes
                )
            )
        }
    }

    private fun selectPreviewUris(
        candidates: List<LocalPreviewCandidate>,
        sortOrder: Int
    ): List<Uri> {
        return FolderPreviewOrdering.selectPreviewValues(
            candidates.mapIndexed { index, candidate ->
                FolderPreviewOrdering.Candidate(
                    value = candidate.uri,
                    title = candidate.title,
                    dateModified = candidate.dateModified,
                    mediaType = candidate.mediaType,
                    isBlankLike = candidate.mediaType == MediaType.IMAGE && (
                        candidate.sizeBytes in 1 until tinyPreviewImageBytes ||
                            WebDavImageLoader.isLikelyBlankLocalImagePreview(context, candidate.uri)
                        ),
                    sourceOrder = index
                )
            },
            sortOrder = sortOrder,
            preferUsableMedia = true
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

    override suspend fun getFolders(rootPath: String, forceRefresh: Boolean): List<Folder> {
        val defaultSort = if (rootPath.isEmpty()) {
            SettingsManager(context).getSortOrder()
        } else {
            SettingsManager(context).getPhotoSortOrder()
        }
        return getFolders(rootPath, forceRefresh, defaultSort)
    }

    suspend fun getFolders(
        rootPath: String,
        forceRefresh: Boolean,
        sortOrder: Int
    ): List<Folder> = withContext(Dispatchers.IO) {
        val storageRoots = getStorageRoots()
        val primaryRoot = storageRoots.firstOrNull() ?: "/storage/emulated/0"
        val normalizedRootPath = rootPath.trimEnd('/')

        val foldersMap = linkedMapOf<String, Folder>()
        val previewCandidatesByFolder = linkedMapOf<String, MutableList<LocalPreviewCandidate>>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )

        val baseSelectionParts = mutableListOf<String>()
        val baseSelectionArgs = mutableListOf<String>()
        baseSelectionParts.add("(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)")
        baseSelectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
        baseSelectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())

        if (normalizedRootPath.isNotEmpty()) {
            baseSelectionParts.add("${MediaStore.Files.FileColumns.DATA} LIKE ?")
            baseSelectionArgs.add("$normalizedRootPath/%")
        }

        val selection = baseSelectionParts.joinToString(" AND ")
        val selectionArgs = baseSelectionArgs.toTypedArray()

        context.contentResolver.query(
            mediaCollection,
            projection,
            selection,
            selectionArgs,
            buildFolderPreviewSortOrder(sortOrder)
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

            val directRootPreviewCandidates = mutableListOf<LocalPreviewCandidate>()
            var directRootCount = 0
            var directRootDateModified = 0L

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: continue
                val name = cursor.getString(nameCol) ?: continue
                if (path.contains("/.") || name.startsWith(".") || path.contains("/dev-dump/")) continue
                if (!isSupportedMediaName(name)) continue

                val file = File(path)
                val parent = file.parentFile ?: continue
                val parentPath = parent.absolutePath.trimEnd('/')

                val id = cursor.getLong(idCol)
                val dateModified = cursor.getLong(dateCol)
                val sizeBytes = cursor.getLong(sizeCol)
                val mediaType = when (cursor.getInt(mediaTypeCol)) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaType.VIDEO
                    else -> MediaType.IMAGE
                }
                val uri = toContentUri(id, mediaType)

                val folderKey: String
                val folderName: String
                val hasSubFolders: Boolean

                if (normalizedRootPath.isNotEmpty()) {
                    if (parentPath == normalizedRootPath) {
                        directRootCount++
                        registerPreviewCandidate(directRootPreviewCandidates, uri, name, dateModified, mediaType, sizeBytes)
                        directRootDateModified = maxOf(directRootDateModified, dateModified)
                        continue
                    }
                    if (!parentPath.startsWith("$normalizedRootPath/")) continue
                    val relative = parentPath.removePrefix(normalizedRootPath).trimStart(File.separatorChar)
                    if (relative.isBlank()) continue
                    val first = relative.substringBefore(File.separator)
                    val directChildPath = File(normalizedRootPath, first).absolutePath
                    folderKey = directChildPath
                    folderName = localizeFolderName(first)
                    hasSubFolders = parentPath != directChildPath
                } else {
                    val matchedStorageRoot = storageRoots.firstOrNull { parentPath == it || parentPath.startsWith("$it/") } ?: primaryRoot
                    if (parentPath == matchedStorageRoot) {
                        directRootCount++
                        registerPreviewCandidate(directRootPreviewCandidates, uri, name, dateModified, mediaType, sizeBytes)
                        directRootDateModified = maxOf(directRootDateModified, dateModified)
                        continue
                    }
                    if (matchedStorageRoot == primaryRoot || storageRoots.size == 1) {
                        val relative = parentPath.removePrefix(matchedStorageRoot).trimStart(File.separatorChar)
                        if (relative.isBlank()) continue
                        val first = relative.substringBefore(File.separator)
                        val directChildPath = File(matchedStorageRoot, first).absolutePath
                        folderKey = directChildPath
                        folderName = localizeFolderName(first)
                        hasSubFolders = parentPath != directChildPath
                    } else {
                        folderKey = matchedStorageRoot
                        folderName = File(matchedStorageRoot).name
                        hasSubFolders = true
                    }
                }

                val existing = foldersMap[folderKey]
                val previewCandidates = previewCandidatesByFolder.getOrPut(folderKey) { mutableListOf() }
                registerPreviewCandidate(previewCandidates, uri, name, dateModified, mediaType, sizeBytes)
                if (existing == null) {
                    foldersMap[folderKey] = Folder(
                        path = folderKey,
                        name = folderName,
                        isLocal = true,
                        photoCount = 1,
                        previewUris = emptyList(),
                        hasSubFolders = hasSubFolders,
                        dateModified = dateModified
                    )
                } else {
                    foldersMap[folderKey] = existing.copy(
                        photoCount = existing.photoCount + 1,
                        hasSubFolders = existing.hasSubFolders || hasSubFolders,
                        dateModified = maxOf(existing.dateModified, dateModified)
                    )
                }
            }

            val effectiveDirectPath = if (normalizedRootPath.isNotEmpty()) normalizedRootPath else primaryRoot
            if (directRootCount > 0 && (normalizedRootPath.isEmpty() || foldersMap.isNotEmpty())) {
                val virtualPath = "virtual://internal_photos?path=$effectiveDirectPath"
                previewCandidatesByFolder[virtualPath] = directRootPreviewCandidates
                foldersMap[virtualPath] = Folder(
                    path = virtualPath,
                    name = context.getString(R.string.internal_photos, directRootCount),
                    isLocal = true,
                    photoCount = directRootCount,
                    previewUris = emptyList(),
                    hasSubFolders = false,
                    dateModified = directRootDateModified
                )
            }
        }

        foldersMap.values.map { folder ->
            folder.copy(
                previewUris = selectPreviewUris(previewCandidatesByFolder[folder.path].orEmpty(), sortOrder)
            )
        }
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
