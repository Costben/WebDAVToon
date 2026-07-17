package erl.webdavtoon

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rust_core.MediaType as RustMediaType
import uniffi.rust_core.Photo as RustPhoto
import uniffi.rust_core.SortOrder
import java.util.Locale

class RustWebDavPhotoRepository(
    private val settingsManager: SettingsManager
) : PhotoRepository {

    data class RemoteFolderPreview(
        val hasSubFolders: Boolean,
        val previewUris: List<Uri>
    )

    companion object {
        @Volatile
        private var lastWebDavParams: String? = null

        @Volatile
        private var previewRustRepo: uniffi.rust_core.RustRepository? = null

        @Volatile
        private var lastPreviewWebDavParams: String? = null

        private const val PREVIEW_LIMIT = 4

        private val previewCacheSortOrders = listOf(
            SettingsManager.SORT_NAME_ASC,
            SettingsManager.SORT_NAME_DESC,
            SettingsManager.SORT_DATE_DESC,
            SettingsManager.SORT_DATE_ASC,
            SettingsManager.SORT_RANDOM_FOLDERS
        )

        private val previewRepoLock = Any()
    }

    private val rustRepo = WebDAVToonApplication.rustRepository

    override suspend fun queryMediaPage(
        folderPath: String,
        recursive: Boolean,
        query: MediaQuery,
        offset: Int,
        limit: Int,
        forceRefresh: Boolean
    ): MediaPageResult = withContext(Dispatchers.IO) {
        val photos = getPhotos(folderPath, recursive, forceRefresh)

        val comparator: Comparator<Photo> = when (settingsManager.getPhotoSortOrder()) {
            0 -> compareBy { it.title.lowercase(Locale.ROOT) }
            1 -> compareByDescending { it.title.lowercase(Locale.ROOT) }
            2 -> compareByDescending { it.dateModified }
            3 -> compareBy { it.dateModified }
            else -> compareByDescending { it.dateModified }
        }

        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(1)

        val page = photos.asSequence()
            .sortedWith(comparator)
            .filter { matchesMediaQuery(it, query) }
            .drop(safeOffset)
            .take(safeLimit)
            .toList()

        val hasMore = photos.asSequence()
            .sortedWith(comparator)
            .filter { matchesMediaQuery(it, query) }
            .drop(safeOffset + safeLimit)
            .any()

        MediaPageResult(items = page, hasMore = hasMore, nextOffset = safeOffset + page.size)
    }

    override suspend fun getPhotos(folderPath: String, recursive: Boolean, forceRefresh: Boolean): List<Photo> = withContext(Dispatchers.IO) {
        val repo = rustRepo ?: return@withContext emptyList()
        initializeWebDavIfNeeded(repo)
        val sortOrder = settingsManager.getPhotoSortOrder()

        try {
            getSortedPhotosFromRepo(
                repo = repo,
                folderPath = folderPath,
                sortOrder = sortOrder,
                forceRefresh = forceRefresh,
                recursive = recursive
            )
        } catch (e: Exception) {
            android.util.Log.e("RustWebDavPhotoRepo", "Failed to get webdav photos", e)
            emptyList()
        }
    }

    override suspend fun getFolders(rootPath: String, forceRefresh: Boolean): List<Folder> = withContext(Dispatchers.IO) {
        val repo = rustRepo ?: return@withContext emptyList()
        initializeWebDavIfNeeded(repo)
        val accountKey = previewCacheAccountKey()
        val sortOrder = settingsManager.getSortOrder()
        val startedAt = SystemClock.elapsedRealtime()

        try {
            val folders = repo.getFolders(rootPath, forceRefresh)
                .filterNot { f ->
                    val name = f.name.trim()
                    val path = f.path.trim().trim('/')
                    name.startsWith(".") || path.split('/').any { it.length > 1 && it.startsWith(".") }
                }
                .map { f ->
                    Folder(
                        path = f.path,
                        name = f.name,
                        isLocal = f.isLocal,
                        photoCount = 0,
                        previewUris = emptyList(),
                        hasSubFolders = f.hasSubFolders,
                        dateModified = f.dateModified.toLong() * 1000
                    )
                }
                .map { folder ->
                    val cached = RemoteFolderPreviewMemoryCache.get(accountKey, sortOrder, folder.path)
                    when {
                        cached != null -> {
                            folder.copy(
                                previewUris = cached.previewUriStrings.map(Uri::parse),
                                hasSubFolders = folder.hasSubFolders || cached.hasSubFolders
                            )
                        }

                        else -> folder
                    }
                }.toMutableList()

            Log.i(
                "RustWebDavPhotoRepo",
                "getFolders rootPath=$rootPath forceRefresh=$forceRefresh count=${folders.size} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
            )
            folders
        } catch (e: Exception) {
            android.util.Log.e("RustWebDavPhotoRepo", "Failed to get webdav folders", e)
            emptyList()
        }
    }

    suspend fun diagnoseEmptyFolderResult(rootPath: String): String? = withContext(Dispatchers.IO) {
        if (rootPath != "/") return@withContext null

        val repo = rustRepo ?: return@withContext null

        return@withContext try {
            val config = settingsManager.buildRemoteConfig()
            val protocolName = settingsManager.getWebDavProtocol().uppercase()

            val report = repo.testRemote(config)
            val rootEntries = report.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("- ") }
                .toList()

            when {
                rootEntries.isEmpty() -> {
                    android.util.Log.i(
                        "RustWebDavPhotoRepo",
                        "$protocolName root is reachable but returned no entries for endpoint=${config.endpoint}"
                    )
                    "$protocolName 连接成功，但当前根目录下没有任何文件夹或文件。"
                }

                else -> {
                    android.util.Log.i(
                        "RustWebDavPhotoRepo",
                        "$protocolName root has ${rootEntries.size} entries, but none matched visible image-folder rules for endpoint=${config.endpoint}"
                    )
                    "$protocolName 连接成功，但当前根目录下没有可显示的图片文件夹。仅显示包含受支持图片（jpg、jpeg、png、webp、gif）的非隐藏文件夹。"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RustWebDavPhotoRepo", "Failed to diagnose empty remote folder result", e)
            null
        }
    }

    private fun matchesMediaQuery(photo: Photo, query: MediaQuery): Boolean {
        val keyword = query.keyword.trim()
        if (keyword.isNotEmpty() && !photo.title.contains(keyword, ignoreCase = true)) return false
        if (query.minSizeBytes != null && photo.size < query.minSizeBytes) return false
        if (query.maxSizeBytes != null && photo.size > query.maxSizeBytes) return false

        if (query.extensions.isNotEmpty()) {
            val uri = photo.imageUri.toString().lowercase(Locale.ROOT)
            val matched = query.extensions.any { ext ->
                val clean = ext.trim().trimStart('.').lowercase(Locale.ROOT)
                uri.endsWith(".$clean")
            }
            if (!matched) return false
        }
        return true
    }

    private fun initializeWebDavIfNeeded(repo: uniffi.rust_core.RustRepository) {
        initializeWebDavIfNeeded(repo, isPreviewRepo = false)
    }

    private fun initializeWebDavIfNeeded(
        repo: uniffi.rust_core.RustRepository,
        isPreviewRepo: Boolean
    ) {
        try {
            val params =
                "${settingsManager.getFullWebDavUrl()}|${settingsManager.getWebDavUsername()}|${settingsManager.getWebDavDomain()}"
            val cachedParams = if (isPreviewRepo) lastPreviewWebDavParams else lastWebDavParams
            if (params == cachedParams) return

            repo.initRemote(settingsManager.buildRemoteConfig())
            if (isPreviewRepo) {
                lastPreviewWebDavParams = params
            } else {
                lastWebDavParams = params
            }
        } catch (e: Exception) {
            Log.e("RustWebDavPhotoRepo", "Failed to init remote", e)
            if (isPreviewRepo) {
                lastPreviewWebDavParams = null
            } else {
                lastWebDavParams = null
            }
        }
    }

    private fun getPreviewRustRepo(): uniffi.rust_core.RustRepository? {
        previewRustRepo?.let { return it }

        val appContext = runCatching { WebDAVToonApplication.appContext }.getOrNull() ?: return null
        synchronized(previewRepoLock) {
            previewRustRepo?.let { return it }
            val dbPath = appContext.getDatabasePath("rust_core_preview.db").absolutePath
            return try {
                uniffi.rust_core.RustRepository(dbPath).also { previewRustRepo = it }
            } catch (e: Exception) {
                Log.e("RustWebDavPhotoRepo", "Failed to create preview rust repo", e)
                null
            }
        }
    }

    suspend fun testRemoteConnection(config: uniffi.rust_core.RemoteConfig): String = withContext(Dispatchers.IO) {
        val repo = rustRepo ?: throw IllegalStateException("Repository not initialized")
        repo.testRemote(config)
    }

    suspend fun inspectFolder(
        folderPath: String,
        sortOrder: Int = settingsManager.getSortOrder(),
        forceRefresh: Boolean = false
    ): RemoteFolderPreview? = withContext(Dispatchers.IO) {
        val repo = getPreviewRustRepo() ?: rustRepo ?: return@withContext null
        initializeWebDavIfNeeded(repo, isPreviewRepo = repo !== rustRepo)
        val accountKey = previewCacheAccountKey()
        if (!forceRefresh) {
            RemoteFolderPreviewMemoryCache.get(accountKey, sortOrder, folderPath)?.let { cached ->
                Log.i(
                    "RustWebDavPhotoRepo",
                    "inspectFolder cacheHit path=$folderPath sortOrder=$sortOrder previews=${cached.previewUriStrings.size} hasSubFolders=${cached.hasSubFolders}"
                )
                return@withContext RemoteFolderPreview(
                    hasSubFolders = cached.hasSubFolders,
                    previewUris = cached.previewUriStrings.map(Uri::parse)
                )
            }
        }

        return@withContext try {
            val inspection = repo.inspectFolder(folderPath)

            // SMB: directory listings are several protocol round-trips each, so
            // the recursive enumeration that sort-aware previews need is far too
            // expensive (measured 90s for a 445-dir tree). Previews are the
            // first 4 media files the Rust scan finds, identical for every sort
            // order.
            if (settingsManager.getWebDavProtocol() == "smb") {
                val previewUriStrings = inspection.previewUris.take(PREVIEW_LIMIT)
                val sortOrders = (previewCacheSortOrders + sortOrder).distinct()
                Log.i(
                    "RustWebDavPhotoRepo",
                    "inspectFolder smbLazy path=$folderPath previews=${previewUriStrings.size} hasSubFolders=${inspection.hasSubFolders}"
                )
                RemoteFolderPreviewMemoryCache.putAll(
                    accountKey = accountKey,
                    path = folderPath,
                    hasSubFolders = inspection.hasSubFolders,
                    previewUriStringsBySortOrder = sortOrders.associateWith { previewUriStrings }
                )
                return@withContext RemoteFolderPreview(
                    hasSubFolders = inspection.hasSubFolders,
                    previewUris = previewUriStrings.map(Uri::parse)
                )
            }

            val previewPhotos = getPhotosFromRepo(
                repo = repo,
                folderPath = folderPath,
                sortOrder = SettingsManager.SORT_DATE_DESC,
                forceRefresh = forceRefresh,
                recursive = true
            )
            val previewsBySortOrder = buildPreviewUrisBySortOrder(previewPhotos, sortOrder)
            val previewUris = previewsBySortOrder[sortOrder].orEmpty()
            if (previewUris.isEmpty() && inspection.previewUris.isNotEmpty()) {
                Log.i(
                    "RustWebDavPhotoRepo",
                    "inspectFolder ignoredLegacyPreviews path=$folderPath sortOrder=$sortOrder legacyPreviews=${inspection.previewUris.size}"
                )
            }
            Log.i(
                "RustWebDavPhotoRepo",
                "inspectFolder path=$folderPath sortOrder=$sortOrder forceRefresh=$forceRefresh previews=${previewUris.size} hasSubFolders=${inspection.hasSubFolders} primedSortOrders=${previewsBySortOrder.keys.sorted()} media=${previewPhotos.size}"
            )
            RemoteFolderPreviewMemoryCache.putAll(
                accountKey = accountKey,
                path = folderPath,
                hasSubFolders = inspection.hasSubFolders,
                previewUriStringsBySortOrder = previewsBySortOrder.mapValues { (_, uris) ->
                    uris.map { it.toString() }
                }
            )
            RemoteFolderPreview(
                hasSubFolders = inspection.hasSubFolders,
                previewUris = previewUris
            )
        } catch (e: Exception) {
            Log.e("RustWebDavPhotoRepo", "Failed to inspect folder preview: $folderPath", e)
            null
        }
    }

    override suspend fun deletePhoto(photo: Photo): Boolean = withContext(Dispatchers.IO) {
        try {
            val repo = rustRepo
            if (repo == null) {
                android.util.Log.e("RustWebDavPhotoRepo", "Rust repo unavailable for delete")
                return@withContext false
            }
            initializeWebDavIfNeeded(repo, isPreviewRepo = false)

            // photo.id already carries the service-relative path minted by the
            // Rust side; fall back to stripping the endpoint prefix for photos
            // hydrated from older persisted data.
            val relPath = photo.id.ifBlank {
                val base = settingsManager.getFullWebDavUrl().trimEnd('/')
                photo.imageUri.toString().removePrefix(base).trimStart('/')
            }
            if (relPath.isBlank() || RemoteMediaUrlResolver.isRemoteMediaUri(relPath)) {
                android.util.Log.e("RustWebDavPhotoRepo", "Cannot derive relative path for delete: ${photo.imageUri}")
                return@withContext false
            }

            repo.deletePhoto(relPath)
            android.util.Log.i("RustWebDavPhotoRepo", "Successfully deleted: $relPath")
            true
        } catch (e: Exception) {
            android.util.Log.e("RustWebDavPhotoRepo", "Exception during delete", e)
            false
        }
    }

    override suspend fun deleteFolder(folder: Folder): Boolean = withContext(Dispatchers.IO) {
        try {
            val repo = rustRepo
            if (repo == null) {
                android.util.Log.e("RustWebDavPhotoRepo", "Rust repo unavailable for folder delete")
                return@withContext false
            }
            initializeWebDavIfNeeded(repo, isPreviewRepo = false)

            val folderPath = folder.path.trim('/')
            if (folderPath.isBlank()) {
                android.util.Log.e("RustWebDavPhotoRepo", "Refusing to delete empty folder path")
                return@withContext false
            }

            repo.deleteFolder("$folderPath/")
            android.util.Log.i("RustWebDavPhotoRepo", "Successfully deleted folder: $folderPath")
            true
        } catch (e: Exception) {
            android.util.Log.e("RustWebDavPhotoRepo", "Exception during folder delete", e)
            false
        }
    }

    private fun fetchSortOrderFor(sortOrder: Int): SortOrder {
        return when (sortOrder) {
            SettingsManager.SORT_NAME_ASC -> SortOrder.NAME_ASC
            SettingsManager.SORT_NAME_DESC -> SortOrder.NAME_DESC
            else -> SortOrder.DATE_DESC
        }
    }

    private fun previewCacheAccountKey(): String {
        return "${settingsManager.getFullWebDavUrl().trimEnd('/')}|${settingsManager.getWebDavUsername()}"
    }

    private fun getSortedPhotosFromRepo(
        repo: uniffi.rust_core.RustRepository,
        folderPath: String,
        sortOrder: Int,
        forceRefresh: Boolean,
        recursive: Boolean
    ): List<Photo> {
        return FolderPreviewOrdering.sortPhotos(
            getPhotosFromRepo(
                repo = repo,
                folderPath = folderPath,
                sortOrder = sortOrder,
                forceRefresh = forceRefresh,
                recursive = recursive
            ),
            sortOrder
        )
    }

    private fun getPhotosFromRepo(
        repo: uniffi.rust_core.RustRepository,
        folderPath: String,
        sortOrder: Int,
        forceRefresh: Boolean,
        recursive: Boolean
    ): List<Photo> {
        return repo.getPhotos(
            folderPath,
            fetchSortOrderFor(sortOrder),
            forceRefresh,
            recursive
        ).map(::toAppPhoto)
    }

    private fun buildPreviewUrisBySortOrder(
        photos: List<Photo>,
        requestedSortOrder: Int
    ): Map<Int, List<Uri>> {
        val sortOrders = if (requestedSortOrder in previewCacheSortOrders) {
            previewCacheSortOrders
        } else {
            previewCacheSortOrders + requestedSortOrder
        }
        return sortOrders.associateWith { order ->
            FolderPreviewOrdering.sortPhotos(photos, order)
                .take(PREVIEW_LIMIT)
                .map { it.imageUri }
        }
    }

    private fun toAppPhoto(p: RustPhoto): Photo {
        val mediaUri = Uri.parse(p.uri)
        val parentPath = if (p.uri.contains("/")) {
            p.uri.substringBeforeLast("/")
        } else {
            ""
        }
        val mediaType = when (p.mediaType) {
            RustMediaType.VIDEO -> MediaType.VIDEO
            RustMediaType.IMAGE -> MediaType.IMAGE
        }
        return Photo(
            id = p.id,
            imageUri = mediaUri,
            title = p.title,
            width = 0,
            height = 0,
            isLocal = p.isLocal,
            dateModified = p.dateModified.toLong() * 1000,
            size = p.size.toLong(),
            folderPath = parentPath,
            mediaType = mediaType,
            durationMs = p.durationMs?.toLong()
        )
    }
}
