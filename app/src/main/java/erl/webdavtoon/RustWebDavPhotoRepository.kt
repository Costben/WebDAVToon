package erl.webdavtoon

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rust_core.SortOrder
import java.util.Locale

class RustWebDavPhotoRepository(
    private val settingsManager: SettingsManager
) : PhotoRepository {

    private val rustRepo = WebDAVToonApplication.rustRepository
    private var lastWebDavParams: String? = null

    override suspend fun queryMediaPage(
        folderPath: String,
        recursive: Boolean,
        query: MediaQuery,
        offset: Int,
        limit: Int,
        forceRefresh: Boolean
    ): MediaPageResult = withContext(Dispatchers.IO) {
        val photos = getPhotos(folderPath, recursive, forceRefresh)
        
        // Since Rust core might not support all sort orders (like DATE_ASC), 
        // we re-sort here to ensure the user's preference is respected.
        val sortedAll = when (settingsManager.getPhotoSortOrder()) {
            0 -> photos.sortedBy { it.title }
            1 -> photos.sortedByDescending { it.title }
            2 -> photos.sortedByDescending { it.dateModified }
            3 -> photos.sortedBy { it.dateModified }
            else -> photos.sortedByDescending { it.dateModified }
        }.asSequence()
            .filter { matchesMediaQuery(it, query) }
            .toList()

        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(1)
        val page = sortedAll.drop(safeOffset).take(safeLimit)
        val next = safeOffset + page.size
        MediaPageResult(items = page, hasMore = next < sortedAll.size, nextOffset = next)
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
                val parentPath = if (p.uri.contains("/")) {
                    p.uri.substringBeforeLast("/")
                } else {
                    ""
                }
                Photo(
                    id = p.id,
                    imageUri = Uri.parse(p.uri),
                    title = p.title,
                    width = 0,
                    height = 0,
                    isLocal = p.isLocal,
                    dateModified = p.dateModified.toLong() * 1000,
                    size = p.size.toLong(),
                    folderPath = parentPath
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
                        hasSubFolders = f.hasSubFolders
                    )
                }.toMutableList()

            // Handle mixed content: if subfolders exist, also check for direct photos
            if (folders.isNotEmpty() && rootPath != "/") {
                val directPhotos = repo.getPhotos(rootPath, SortOrder.DATE_DESC, false, false)
                if (directPhotos.isNotEmpty()) {
                    val photoCount = directPhotos.size
                    val previewUris = directPhotos.take(4).map { Uri.parse(it.uri) }
                    val dateModified = directPhotos.maxOfOrNull { it.dateModified.toLong() * 1000 } ?: 0L
                    folders.add(0, Folder(
                        path = "virtual://internal_photos?path=$rootPath",
                        name = "Internal Photos ($photoCount)",
                        isLocal = false,
                        photoCount = photoCount,
                        previewUris = previewUris,
                        hasSubFolders = false,
                        dateModified = dateModified
                    ))
                }
            }
            folders
        } catch (e: Exception) {
            android.util.Log.e("RustWebDavPhotoRepo", "Failed to get webdav folders", e)
            emptyList()
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
        try {
            val params = "${settingsManager.getFullWebDavUrl()}|${settingsManager.getWebDavUsername()}"
            if (params == lastWebDavParams) return

            repo.initWebdav(
                settingsManager.getFullWebDavUrl(),
                settingsManager.getWebDavUsername(),
                settingsManager.getWebDavPassword()
            )
            lastWebDavParams = params
        } catch (e: Exception) {
            android.util.Log.e("RustWebDavPhotoRepo", "Failed to init webdav", e)
            lastWebDavParams = null
        }
    }

    suspend fun testWebDavConnection(endpoint: String, username: String, password: String): String = withContext(Dispatchers.IO) {
        val repo = rustRepo ?: throw IllegalStateException("Repository not initialized")
        repo.testWebdav(endpoint, username, password)
    }

    override suspend fun deletePhoto(photo: Photo): Boolean = withContext(Dispatchers.IO) {
        val repo = rustRepo ?: return@withContext false
        
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
            val request = okhttp3.Request.Builder()
                .url(photo.imageUri.toString())
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
            val request = okhttp3.Request.Builder()
                .url(fullUrl)
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
