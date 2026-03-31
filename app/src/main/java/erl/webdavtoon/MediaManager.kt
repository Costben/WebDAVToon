package erl.webdavtoon

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 集中管理媒体库的分页加载逻辑，供 MainActivity 和 PhotoViewActivity 共享
 */
object MediaManager {
    private const val PAGE_SIZE = 120

    fun sortPhotos(photos: List<Photo>, sortOrder: Int, isRecursive: Boolean): List<Photo> {
        if (!isRecursive) return photos

        val grouped = photos.groupBy { it.folderPath }

        val sortedFolderPaths = when (sortOrder) {
            SettingsManager.SORT_NAME_ASC -> grouped.keys.sortedBy { it }
            SettingsManager.SORT_NAME_DESC -> grouped.keys.sortedByDescending { it }
            SettingsManager.SORT_DATE_DESC -> grouped.keys.sortedByDescending { path ->
                grouped[path]?.maxOfOrNull { it.dateModified } ?: 0L
            }
            SettingsManager.SORT_DATE_ASC -> grouped.keys.sortedBy { path ->
                grouped[path]?.minOfOrNull { it.dateModified } ?: 0L
            }
            else -> grouped.keys.sortedByDescending { path ->
                grouped[path]?.maxOfOrNull { it.dateModified } ?: 0L
            }
        }

        val result = mutableListOf<Photo>()
        for (path in sortedFolderPaths) {
            val folderPhotos = grouped[path] ?: continue
            val sortedPhotos = when (sortOrder) {
                SettingsManager.SORT_NAME_ASC -> folderPhotos.sortedBy { it.title }
                SettingsManager.SORT_NAME_DESC -> folderPhotos.sortedByDescending { it.title }
                SettingsManager.SORT_DATE_DESC -> folderPhotos.sortedByDescending { it.dateModified }
                SettingsManager.SORT_DATE_ASC -> folderPhotos.sortedBy { it.dateModified }
                else -> folderPhotos.sortedByDescending { it.dateModified }
            }
            result.addAll(sortedPhotos)
        }
        return result
    }

    fun loadNextPage(context: Context, scope: CoroutineScope) {
        val state = MediaState.state.value
        if (state.isLoading || !state.hasMore) return

        val sessionKey = state.sessionKey
        MediaState.startAppend()

        loadPageInternal(context, scope, sessionKey, state, append = true, forceRefresh = false)
    }

    fun refresh(context: Context, scope: CoroutineScope, sessionKey: String, 
                folderPath: String, isRemote: Boolean, isRecursive: Boolean, 
                isFavorites: Boolean, query: MediaQuery) {
        
        MediaState.start(sessionKey, folderPath, isRemote, isRecursive, isFavorites, query)
        val state = MediaState.state.value
        
        loadPageInternal(context, scope, sessionKey, state, append = false, forceRefresh = true)
    }

    private fun loadPageInternal(
        context: Context, 
        scope: CoroutineScope, 
        sessionKey: String,
        state: MediaUiState,
        append: Boolean,
        forceRefresh: Boolean
    ) {
        scope.launch {
            try {
                val settingsManager = SettingsManager(context)
                val page = withContext(Dispatchers.IO) {
                    if (state.isFavorites) {
                        val allFavorites = settingsManager.getFavoritePhotos()
                        val keyword = state.currentQuery.keyword.trim()
                        val filtered = if (keyword.isNotEmpty()) {
                            allFavorites.filter { it.title.contains(keyword, ignoreCase = true) }
                        } else {
                            allFavorites
                        }
                        
                        val safeOffset = if (append) state.currentOffset.coerceAtLeast(0) else 0
                        val items = filtered.drop(safeOffset).take(PAGE_SIZE)
                        val next = safeOffset + items.size
                        MediaPageResult(items = items, hasMore = next < filtered.size, nextOffset = next)
                    } else if (state.isRemote) {
                        RustWebDavPhotoRepository(settingsManager)
                            .queryMediaPage(
                                state.folderPath, 
                                state.isRecursive, 
                                state.currentQuery, 
                                if (append) state.currentOffset else 0, 
                                PAGE_SIZE, 
                                forceRefresh
                            )
                    } else {
                        LocalPhotoRepository(context)
                            .queryMediaPage(
                                state.folderPath, 
                                state.isRecursive, 
                                state.currentQuery, 
                                if (append) state.currentOffset else 0, 
                                PAGE_SIZE, 
                                forceRefresh
                            )
                    }
                }

                MediaState.setPage(sessionKey, page.items, page.hasMore, page.nextOffset, append)
                
                // 同时更新 PhotoCache，确保 PhotoViewActivity 也能拿到最新的全量列表
                if (append) {
                    val currentPhotos = PhotoCache.getPhotos().toMutableList()
                    currentPhotos.addAll(page.items)
                    PhotoCache.setPhotos(currentPhotos)
                } else {
                    PhotoCache.setPhotos(page.items)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("MediaManager", "Load failed: ${e.message}", e)
                MediaState.setError(sessionKey, e.message ?: e.toString())
            }
        }
    }
}
