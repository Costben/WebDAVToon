package erl.webdavtoon

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rust_core.MediaType as RustMediaType
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

        // The Rust core SortOrder enum might only support NAME_ASC, NAME_DESC, DATE_DESC.
        // We'll fetch with DATE_DESC as default and re-sort in queryMediaPage if needed.
        val fetchSortOrder = when (settingsManager.getPhotoSortOrder()) {
            0 -> SortOrder.NAME_ASC
            1 -> SortOrder.NAME_DESC
            else -> SortOrder.DATE_DESC
        }

        try {
            repo.getPhotos(folderPath, fetchSortOrder, forceRefresh, recursive).map { p ->
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
                Photo(
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
        } catch (e: Exception) {
            android.util.Log.e("RustWebDavPhotoRepo", "Failed to get webdav photos", e)
            emptyList()
        }
    }

    override suspend fun getFolders(rootPath: String, forceRefresh: Boolean): List<Folder> = withContext(Dispatchers.IO) {
        val repo = rustRepo ?: return@withContext emptyList()
        initializeWebDavIfNeeded(repo)
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
                        previewUris = f.previewUris.map { Uri.parse(it) },
                        hasSubFolders = f.hasSubFolders,
                        dateModified = f.dateModified.toLong() * 1000
                    )
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
            val endpoint = settingsManager.getFullWebDavUrl()
            val username = settingsManager.getWebDavUsername()
            val password = settingsManager.getWebDavPassword()

            val report = repo.testWebdav(endpoint, username, password)
            val rootEntries = report.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("- ") }
                .toList()

            when {
                rootEntries.isEmpty() -> {
                    android.util.Log.i(
                        "RustWebDavPhotoRepo",
                        "WebDAV root is reachable but returned no entries for endpoint=$endpoint"
                    )
                    "WebDAV 连接成功，但当前根目录下没有任何文件夹或文件。"
                }

                else -> {
                    android.util.Log.i(
                        "RustWebDavPhotoRepo",
                        "WebDAV root has ${rootEntries.size} entries, but none matched visible image-folder rules for endpoint=$endpoint"
                    )
                    "WebDAV 连接成功，但当前根目录下没有可显示的图片文件夹。仅显示包含受支持图片（jpg、jpeg、png、webp、gif）的非隐藏文件夹。"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RustWebDavPhotoRepo", "Failed to diagnose empty WebDAV folder result", e)
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
            val params = "${settingsManager.getFullWebDavUrl()}|${settingsManager.getWebDavUsername()}"
            val cachedParams = if (isPreviewRepo) lastPreviewWebDavParams else lastWebDavParams
            if (params == cachedParams) return

            repo.initWebdav(
                settingsManager.getFullWebDavUrl(),
                settingsManager.getWebDavUsername(),
                settingsManager.getWebDavPassword()
            )
            if (isPreviewRepo) {
                lastPreviewWebDavParams = params
            } else {
                lastWebDavParams = params
            }
        } catch (e: Exception) {
            Log.e("RustWebDavPhotoRepo", "Failed to init webdav", e)
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

    suspend fun testWebDavConnection(endpoint: String, username: String, password: String): String = withContext(Dispatchers.IO) {
        val repo = rustRepo ?: throw IllegalStateException("Repository not initialized")
        repo.testWebdav(endpoint, username, password)
    }

    suspend fun inspectFolder(folderPath: String): RemoteFolderPreview? = withContext(Dispatchers.IO) {
        val repo = getPreviewRustRepo() ?: rustRepo ?: return@withContext null
        initializeWebDavIfNeeded(repo, isPreviewRepo = repo !== rustRepo)

        return@withContext try {
            val inspection = repo.inspectFolder(folderPath)
            Log.i(
                "RustWebDavPhotoRepo",
                "inspectFolder path=$folderPath previews=${inspection.previewUris.size} hasSubFolders=${inspection.hasSubFolders}"
            )
            RemoteFolderPreview(
                hasSubFolders = inspection.hasSubFolders,
                previewUris = inspection.previewUris.map(Uri::parse)
            )
        } catch (e: Exception) {
            Log.e("RustWebDavPhotoRepo", "Failed to inspect folder preview: $folderPath", e)
            null
        }
    }

    override suspend fun deletePhoto(photo: Photo): Boolean = withContext(Dispatchers.IO) {
        // Try calling the rust repo first if we managed to update the FFI
        // Otherwise, fallback to direct OkHttp delete request
        try {
            // Since we can't easily update the UniFFI generated code here, 
            // we'll implement the delete via OkHttp directly to the WebDAV server.
            val username = settingsManager.getWebDavUsername()
            val password = settingsManager.getWebDavPassword()
            
            if (username.isEmpty() || password.isEmpty()) {
                android.util.Log.e("RustWebDavPhotoRepo", "No credentials for delete")
                return@withContext false
            }

            val credentials = okhttp3.Credentials.basic(username, password)
            val encodedUrl = FileUtils.encodeWebDavUrl(photo.imageUri.toString())
            val request = okhttp3.Request.Builder()
                .url(encodedUrl)
                .delete()
                .addHeader("Authorization", credentials)
                .build()

            val client = okhttp3.OkHttpClient()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    android.util.Log.i("RustWebDavPhotoRepo", "Successfully deleted: ${photo.imageUri}")
                    true
                } else {
                    android.util.Log.e("RustWebDavPhotoRepo", "Failed to delete: ${response.code} ${response.message}")
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RustWebDavPhotoRepo", "Exception during delete", e)
            false
        }
    }

    override suspend fun deleteFolder(folder: Folder): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = settingsManager.getWebDavUsername()
            val password = settingsManager.getWebDavPassword()
            val baseUrl = settingsManager.getFullWebDavUrl().trimEnd('/')
            val folderPath = folder.path.trim('/')
            val fullUrl = "$baseUrl/$folderPath/"
            
            if (username.isEmpty() || password.isEmpty()) {
                android.util.Log.e("RustWebDavPhotoRepo", "No credentials for folder delete")
                return@withContext false
            }

            val credentials = okhttp3.Credentials.basic(username, password)
            val encodedUrl = FileUtils.encodeWebDavUrl(fullUrl)
            val request = okhttp3.Request.Builder()
                .url(encodedUrl)
                .delete()
                .addHeader("Authorization", credentials)
                .build()

            val client = okhttp3.OkHttpClient()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    android.util.Log.i("RustWebDavPhotoRepo", "Successfully deleted folder: $fullUrl")
                    true
                } else {
                    android.util.Log.e("RustWebDavPhotoRepo", "Failed to delete folder: ${response.code} ${response.message}")
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RustWebDavPhotoRepo", "Exception during folder delete", e)
            false
        }
    }
}
