package erl.webdavtoon

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

/**
 * 集中管理媒体库的分页加载逻辑，供 MainActivity �?PhotoViewActivity 共享
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
        val baseSorted = sortMediaItems(photos, sortOrder)
        if (baseSorted.isEmpty()) return baseSorted

        if (!isRecursive) {
            return if (randomizePhotos) baseSorted.shuffled(Random(photoShuffleSeed)) else baseSorted
        }

        val grouped = LinkedHashMap<String, List<Photo>>()
        for (photo in baseSorted) {
            grouped[photo.folderPath] = (grouped[photo.folderPath] ?: emptyList()) + photo
        }

        if (grouped.size <= 1) {
            return if (randomizePhotos) baseSorted.shuffled(Random(photoShuffleSeed)) else baseSorted
        }

        val sortedFolderPaths = sortFolderPaths(
            grouped = grouped,
            sortOrder = sortOrder,
            clusterShuffleSeed = clusterShuffleSeed,
            newestDate = { folderPhotos -> folderPhotos.maxOfOrNull { it.dateModified } ?: 0L },
            oldestDate = { folderPhotos -> folderPhotos.minOfOrNull { it.dateModified } ?: 0L }
        )

        val result = ArrayList<Photo>(baseSorted.size)
        result.addAll(flattenFolderGroups(grouped, sortedFolderPaths, randomizePhotos, photoShuffleSeed))
        return result
    }
    private fun sortMediaItems(photos: List<Photo>, sortOrder: Int): List<Photo> {
        return when (sortOrder) {
            SettingsManager.SORT_NAME_ASC -> photos.sortedBy { it.title.lowercase(Locale.ROOT) }
            SettingsManager.SORT_NAME_DESC -> photos.sortedByDescending { it.title.lowercase(Locale.ROOT) }
            SettingsManager.SORT_DATE_ASC -> photos.sortedBy { it.dateModified }
            SettingsManager.SORT_DATE_DESC,
            SettingsManager.SORT_RANDOM_FOLDERS -> photos.sortedByDescending { it.dateModified }
            else -> photos.sortedByDescending { it.dateModified }
        }
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
                val sortOrder = settingsManager.getPhotoSortOrder()
                val page = withContext(Dispatchers.IO) {
                    val allMedia = if (state.isFavorites) {
                        settingsManager.getFavoritePhotos()
                    } else if (state.isRemote) {
                        RustWebDavPhotoRepository(settingsManager)
                            .getPhotos(
                                state.folderPath,
                                state.isRecursive,
                                forceRefresh
                            )
                    } else {
                        LocalPhotoRepository(context)
                            .getPhotos(
                                state.folderPath,
                                state.isRecursive,
                                forceRefresh
                            )
                    }

                    val ordered = sortPhotos(
                        photos = allMedia.filter { matchesMediaQuery(it, state.currentQuery) },
                        sortOrder = sortOrder,
                        isRecursive = state.isRecursive,
                        clusterShuffleSeed = state.clusterShuffleSeed,
                        randomizePhotos = state.currentQuery.randomizePhotos,
                        photoShuffleSeed = state.photoShuffleSeed
                    )

                    val safeOffset = if (append) state.currentOffset.coerceAtLeast(0) else 0
                    val items = ordered.drop(safeOffset).take(PAGE_SIZE)
                    val next = safeOffset + items.size
                    MediaPageResult(items = items, hasMore = next < ordered.size, nextOffset = next)
                }

                val viewModel = mediaViewModel ?: return@launch
                viewModel.setPage(page.items, page.hasMore, page.nextOffset, append)
                val updatedState = viewModel.state.value
                MediaStateCache.setState(updatedState)
                PhotoCache.setPhotos(updatedState.photos)

            } catch (e: Exception) {
                android.util.Log.e("MediaManager", "Load failed: ${e.message}", e)
                mediaViewModel?.setError(e.message ?: e.toString())
            }
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
}
