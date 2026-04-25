package erl.webdavtoon

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * 集中管理媒体库的分页加载逻辑，供 MainActivity 和 PhotoViewActivity 共享
 */
object MediaManager {
    private const val PAGE_SIZE = 120
    var mediaViewModel: MediaViewModel? = null

    fun sortPhotos(
        photos: List<Photo>,
        sortOrder: Int,
        isRecursive: Boolean,
        clusterShuffleSeed: Long = 0L,
        randomizePhotos: Boolean = false,
        photoShuffleSeed: Long = 0L
    ): List<Photo> {
        if (photos.isEmpty()) return photos

        // 非递归模式的数据已经由 Repository 排好序，直接返回，避免重复排序
        if (!isRecursive) {
            return if (randomizePhotos) photos.shuffled(Random(photoShuffleSeed)) else photos
        }

        val grouped = LinkedHashMap<String, List<Photo>>()
        for (photo in photos) {
            grouped[photo.folderPath] = (grouped[photo.folderPath] ?: emptyList()) + photo
        }

        // 只有一个文件夹时无需重排文件夹，但仍可随机当前文件夹内图片
        if (grouped.size <= 1) {
            return if (randomizePhotos) photos.shuffled(Random(photoShuffleSeed)) else photos
        }

        val sortedFolderPaths = sortFolderPaths(
            grouped = grouped,
            sortOrder = sortOrder,
            clusterShuffleSeed = clusterShuffleSeed,
            newestDate = { folderPhotos -> folderPhotos.maxOfOrNull { it.dateModified } ?: 0L },
            oldestDate = { folderPhotos -> folderPhotos.minOfOrNull { it.dateModified } ?: 0L }
        )

        val result = ArrayList<Photo>(photos.size)
        result.addAll(flattenFolderGroups(grouped, sortedFolderPaths, randomizePhotos, photoShuffleSeed))
        return result
    }

    internal fun <T> sortFolderPaths(
        grouped: Map<String, List<T>>,
        sortOrder: Int,
        clusterShuffleSeed: Long,
        newestDate: (List<T>) -> Long,
        oldestDate: (List<T>) -> Long
    ): List<String> {
        return when (sortOrder) {
            SettingsManager.SORT_NAME_ASC -> grouped.keys.sortedBy { it }
            SettingsManager.SORT_NAME_DESC -> grouped.keys.sortedByDescending { it }
            SettingsManager.SORT_DATE_DESC -> grouped.keys.sortedByDescending { path ->
                grouped[path]?.let(newestDate) ?: 0L
            }
            SettingsManager.SORT_DATE_ASC -> grouped.keys.sortedBy { path ->
                grouped[path]?.let(oldestDate) ?: 0L
            }
            SettingsManager.SORT_RANDOM_FOLDERS -> grouped.keys.shuffled(Random(clusterShuffleSeed))
            else -> grouped.keys.sortedByDescending { path ->
                grouped[path]?.let(newestDate) ?: 0L
            }
        }
    }

    internal fun <T> flattenFolderGroups(
        grouped: Map<String, List<T>>,
        sortedFolderPaths: List<String>,
        randomizeItems: Boolean,
        itemShuffleSeed: Long
    ): List<T> {
        val result = ArrayList<T>(grouped.values.sumOf { it.size })
        for (path in sortedFolderPaths) {
            val items = grouped[path] ?: continue
            if (randomizeItems) {
                result.addAll(items.shuffled(Random(itemShuffleSeed xor path.hashCode().toLong())))
            } else {
                result.addAll(items)
            }
        }
        return result
    }

    fun loadNextPage(context: Context, scope: CoroutineScope) {
        val viewModel = mediaViewModel ?: return
        val state = viewModel.state.value
        if (state.isLoading || !state.hasMore) return

        viewModel.startAppend()

        loadPageInternal(context, scope, state, append = true, forceRefresh = false)
    }

    fun refresh(context: Context, scope: CoroutineScope, sessionKey: String,
                folderPath: String, isRemote: Boolean, isRecursive: Boolean,
                isFavorites: Boolean, query: MediaQuery, reshuffleClusters: Boolean = false) {

        val viewModel = mediaViewModel ?: return
        val currentState = viewModel.state.value
        val clusterShuffleSeed = if (reshuffleClusters || currentState.clusterShuffleSeed == 0L) {
            Random.nextLong()
        } else {
            currentState.clusterShuffleSeed
        }
        val photoShuffleSeed = if (query.randomizePhotos && (reshuffleClusters || currentState.photoShuffleSeed == 0L)) {
            Random.nextLong()
        } else {
            currentState.photoShuffleSeed
        }
        viewModel.start(
            sessionKey = sessionKey,
            folderPath = folderPath,
            isRemote = isRemote,
            isRecursive = isRecursive,
            isFavorites = isFavorites,
            query = query,
            clusterShuffleSeed = clusterShuffleSeed,
            photoShuffleSeed = photoShuffleSeed
        )
        val state = viewModel.state.value

        loadPageInternal(context, scope, state, append = false, forceRefresh = true)
    }

    private fun loadPageInternal(
        context: Context,
        scope: CoroutineScope,
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

                mediaViewModel?.setPage(page.items, page.hasMore, page.nextOffset, append)

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
                mediaViewModel?.setError(e.message ?: e.toString())
            }
        }
    }
}
