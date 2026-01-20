package erl.webdavtoon

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * WebDAV 图片仓库实现
 */
class WebDavPhotoRepository(
    private val context: android.content.Context,
    private val webDavClient: WebDavClient, 
    private val settingsManager: SettingsManager
) : PhotoRepository {

    private val gson = com.google.gson.Gson()
    private val cacheDao = AppDatabase.getDatabase(context).folderCacheDao()

    override suspend fun getPhotos(folderPath: String, recursive: Boolean): List<Photo> = withContext(Dispatchers.IO) {
        val allPhotos = if (recursive) {
            crawlPhotosRecursive(folderPath)
        } else {
            fetchPhotosInFolder(folderPath)
        }

        if (recursive) {
            // 递归模式：按文件夹分组（即按 ID/路径 排序），同一文件夹内按标题排序
            allPhotos.sortedWith(compareBy({ it.id.substringBeforeLast('/') }, { it.title.lowercase() }))
        } else {
            // 普通模式：按设置排序
            val sortOrder = settingsManager.getSortOrder()
            when (sortOrder) {
                0 -> allPhotos.sortedBy { it.title.lowercase() }
                1 -> allPhotos.sortedByDescending { it.title.lowercase() }
                else -> allPhotos.sortedByDescending { it.dateModified }
            }
        }
    }

    private suspend fun fetchPhotosInFolder(folderPath: String): List<Photo> {
        val resources = webDavClient.listFiles(folderPath)
        val baseUrl = settingsManager.getFullWebDavUrl().trimEnd('/')
        val uri = android.net.Uri.parse(baseUrl)
        val host = "${uri.scheme}://${uri.authority}"
        
        return resources
            .filter { !it.isCollection && isImageFile(it.displayName) }
            .map { resource ->
                val fullUrl = when {
                    resource.href.startsWith("http") -> resource.href
                    resource.href.startsWith("/") -> "$host${resource.href}"
                    else -> "$baseUrl/${resource.href.trimStart('/')}"
                }
                Photo(
                    id = resource.href,
                    imageUri = android.net.Uri.parse(fullUrl),
                    title = resource.displayName,
                    isLocal = false,
                    size = resource.contentLength,
                    dateModified = parseDate(resource.lastModified)
                )
            }
    }

    private suspend fun crawlPhotosRecursive(folderPath: String): List<Photo> {
        val photos = mutableListOf<Photo>()
        val resources = try {
            webDavClient.listFiles(folderPath)
        } catch (e: Exception) {
            android.util.Log.e("WebDavPhotoRepository", "Failed to list files for $folderPath", e)
            return emptyList()
        }

        val baseUrl = settingsManager.getFullWebDavUrl().trimEnd('/')
        val uri = android.net.Uri.parse(baseUrl)
        val host = "${uri.scheme}://${uri.authority}"

        for (res in resources) {
            if (res.isCollection) {
                // 排除当前文件夹自身（如果 href 等于 folderPath）
                val normalizedRes = res.href.trimEnd('/')
                val normalizedFolder = folderPath.trimEnd('/')
                if (normalizedRes != normalizedFolder && normalizedRes.isNotEmpty()) {
                    photos.addAll(crawlPhotosRecursive(res.href))
                }
            } else if (isImageFile(res.displayName)) {
                val fullUrl = when {
                    res.href.startsWith("http") -> res.href
                    res.href.startsWith("/") -> "$host${res.href}"
                    else -> "$baseUrl/${res.href.trimStart('/')}"
                }
                photos.add(Photo(
                    id = res.href,
                    imageUri = android.net.Uri.parse(fullUrl),
                    title = res.displayName,
                    isLocal = false,
                    size = res.contentLength,
                    dateModified = parseDate(res.lastModified)
                ))
            }
        }
        return photos
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            // WebDAV dates are often in RFC 1123 format
            val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
            sdf.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    override suspend fun getFolders(rootPath: String, forceRefresh: Boolean): List<Folder> = withContext(Dispatchers.IO) {
        val resources = webDavClient.listFiles(rootPath)
        val baseUrl = settingsManager.getFullWebDavUrl().trimEnd('/')
        val baseUri = android.net.Uri.parse(baseUrl)
        val basePath = baseUri.path?.trimEnd('/') ?: ""
        val host = "${baseUri.scheme}://${baseUri.authority}"
        val sortOrder = settingsManager.getSortOrder()
        
        android.util.Log.d("WebDavPhotoRepository", "Fetched ${resources.size} resources from WebDAV. BasePath: $basePath")
        
        val folderResources = resources
            .filter { it.isCollection }
            .filter { 
                val href = it.href.trimEnd('/')
                val root = rootPath.trimEnd('/')
                val isSelf = href == root || href == basePath || href == "" || href == "/"
                !isSelf && it.displayName.isNotEmpty()
            }

        // 并发获取每个文件夹的预览图逻辑
        val folders = folderResources.map { resource ->
            async {
                // 1. 尝试从缓存获取
                if (!forceRefresh) {
                    val cached = cacheDao.getFolderCache(resource.href, sortOrder)
                    if (cached != null && System.currentTimeMillis() - cached.lastUpdated < 24 * 60 * 60 * 1000) {
                        val urisList = gson.fromJson(cached.previewUrisJson, Array<String>::class.java).map { android.net.Uri.parse(it) }
                        return@async Folder(
                            path = cached.path,
                            name = cached.name,
                            isLocal = false,
                            previewUris = urisList,
                            hasSubFolders = cached.hasSubFolders
                        )
                    }
                }

                // 2. 缓存不存在或强制刷新，从网络获取
                var previewUris = mutableListOf<android.net.Uri>()
                var hasSubFolders = false
                try {
                    // 获取当前目录的内容（包含图片和文件夹）
                    val contentResources = webDavClient.listFiles(resource.href)
                    
                    // 找出所有图片并按照 settings 排序
                    val directImages = contentResources
                        .filter { !it.isCollection && isImageFile(it.displayName) }
                        .let { images ->
                            when (sortOrder) {
                                0 -> images.sortedBy { it.displayName.lowercase() }
                                1 -> images.sortedByDescending { it.displayName.lowercase() }
                                else -> images.sortedByDescending { it.lastModified }
                            }
                        }

                    // 找出所有子文件夹
                    val subFolders = contentResources
                        .filter { it.isCollection && it.href.trimEnd('/') != resource.href.trimEnd('/') }

                    hasSubFolders = subFolders.isNotEmpty()

                    if (subFolders.isNotEmpty()) {
                        // 逻辑：扫描前 10 个文件夹，看看哪些有图
                        val subFolderData = subFolders.take(10).map { sub ->
                            async {
                                try {
                                    val images = webDavClient.listFiles(sub.href)
                                        .filter { !it.isCollection && isImageFile(it.displayName) }
                                        .let { images ->
                                            when (sortOrder) {
                                                0 -> images.sortedBy { it.displayName.lowercase() }
                                                1 -> images.sortedByDescending { it.displayName.lowercase() }
                                                else -> images.sortedByDescending { it.lastModified }
                                            }
                                        }
                                    Pair(sub, images)
                                } catch (e: Exception) {
                                    android.util.Log.e("WebDavPhotoRepository", "Failed to fetch images for subfolder ${sub.href}", e)
                                    Pair(sub, emptyList<WebDavResource>())
                                }
                            }
                        }.awaitAll().filter { it.second.isNotEmpty() }

                        val n = subFolderData.size
                        when {
                            n >= 4 -> {
                                // 4个及以上文件夹：前4个各取1张
                                for (i in 0 until 4) {
                                    previewUris.add(getImageUri(subFolderData[i].second[0], baseUrl, host))
                                }
                            }
                            n == 3 -> {
                                // 3个文件夹：第1个2张，其余1张
                                previewUris.add(getImageUri(subFolderData[0].second[0], baseUrl, host))
                                if (subFolderData[0].second.size > 1) {
                                    previewUris.add(getImageUri(subFolderData[0].second[1], baseUrl, host))
                                }
                                previewUris.add(getImageUri(subFolderData[1].second[0], baseUrl, host))
                                previewUris.add(getImageUri(subFolderData[2].second[0], baseUrl, host))
                            }
                            n == 2 -> {
                                // 2个文件夹：各2张
                                previewUris.add(getImageUri(subFolderData[0].second[0], baseUrl, host))
                                if (subFolderData[0].second.size > 1) {
                                    previewUris.add(getImageUri(subFolderData[0].second[1], baseUrl, host))
                                }
                                previewUris.add(getImageUri(subFolderData[1].second[0], baseUrl, host))
                                if (subFolderData[1].second.size > 1) {
                                    previewUris.add(getImageUri(subFolderData[1].second[1], baseUrl, host))
                                }
                            }
                            n == 1 -> {
                                // 只有1个文件夹有图片：取前4张
                                subFolderData[0].second.take(4).forEach { 
                                    previewUris.add(getImageUri(it, baseUrl, host))
                                }
                            }
                            else -> {
                                // 没有子文件夹有图片，看看当前文件夹是否有直属图片
                                directImages.take(4).forEach {
                                    previewUris.add(getImageUri(it, baseUrl, host))
                                }
                            }
                        }
                    } else {
                        // 没有子文件夹，直接取当前文件夹的直属图片
                        directImages.take(4).forEach {
                            previewUris.add(getImageUri(it, baseUrl, host))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WebDavPhotoRepository", "Failed to fetch previews for ${resource.href}", e)
                    // 如果获取失败，尝试从缓存读取（即使是 forceRefresh）
                    val cached = cacheDao.getFolderCache(resource.href, sortOrder)
                    if (cached != null) {
                        val urisList = gson.fromJson(cached.previewUrisJson, Array<String>::class.java).map { android.net.Uri.parse(it) }
                        previewUris = urisList.toMutableList()
                        hasSubFolders = cached.hasSubFolders
                    }
                }

                val previewUrisStrings = previewUris.take(4).map { it.toString() }
                
                // 更新缓存 (仅当成功获取到图片时)
                if (previewUrisStrings.isNotEmpty()) {
                    cacheDao.insertFolderCache(FolderCacheEntity(
                        path = resource.href,
                        name = resource.displayName,
                        previewUrisJson = gson.toJson(previewUrisStrings),
                        hasSubFolders = hasSubFolders,
                        lastUpdated = System.currentTimeMillis(),
                        sortOrder = sortOrder
                    ))
                }

                Folder(
                    path = resource.href,
                    name = resource.displayName,
                    isLocal = false,
                    previewUris = previewUris.take(4),
                    hasSubFolders = hasSubFolders
                )
            }
        }.awaitAll().filter { it.previewUris.isNotEmpty() } // 仅显示有预览图（即本人或子文件夹包含图片）的文件夹
        
        folders
    }

    private fun getImageUri(resource: WebDavResource, baseUrl: String, host: String): Uri {
        val fullUrl = when {
            resource.href.startsWith("http") -> resource.href
            resource.href.startsWith("/") -> "$host${resource.href}"
            else -> "$baseUrl/${resource.href.trimStart('/')}"
        }
        return Uri.parse(fullUrl)
    }

    private fun isImageFile(fileName: String): Boolean {
        val extensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")
        return extensions.any { fileName.lowercase().endsWith(it) }
    }
}
