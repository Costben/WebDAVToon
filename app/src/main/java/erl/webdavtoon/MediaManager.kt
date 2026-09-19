package erl.webdavtoon

import java.util.Locale
import kotlin.random.Random

/**
 * Shared media ordering rules. Paging now lives in the Compose view models, so this
 * object keeps only the pure sorting helpers used by both the media page and tests.
 */
object MediaManager {

    fun sortPhotos(
        photos: List<Photo>,
        sortOrder: Int,
        isRecursive: Boolean,
        recursiveImageArrangement: Int = SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GROUPED,
        clusterShuffleSeed: Long = 0L,
        randomizePhotos: Boolean = false,
        photoShuffleSeed: Long = 0L
    ): List<Photo> {
        val baseSorted = sortMediaItems(photos, sortOrder)
        if (baseSorted.isEmpty()) return baseSorted

        val shouldRandomizePhotos = randomizePhotos || sortOrder == SettingsManager.SORT_RANDOM_PHOTOS
        val effectivePhotoSeed = if (photoShuffleSeed != 0L) photoShuffleSeed else Random.nextLong()

        if (!isRecursive) {
            return if (shouldRandomizePhotos) baseSorted.shuffled(Random(effectivePhotoSeed)) else baseSorted
        }

        if (recursiveImageArrangement != SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GROUPED) {
            return sortRecursivelyByGlobalDate(
                photos = photos,
                arrangement = recursiveImageArrangement,
                dateModified = { it.dateModified }
            )
        }

        val grouped = LinkedHashMap<String, List<Photo>>()
        for (photo in baseSorted) {
            grouped[photo.folderPath] = (grouped[photo.folderPath] ?: emptyList()) + photo
        }

        if (grouped.size <= 1) {
            return if (shouldRandomizePhotos) baseSorted.shuffled(Random(effectivePhotoSeed)) else baseSorted
        }

        val sortedFolderPaths = sortFolderPaths(
            grouped = grouped,
            sortOrder = sortOrder,
            clusterShuffleSeed = clusterShuffleSeed,
            newestDate = { folderPhotos -> folderPhotos.maxOfOrNull { it.dateModified } ?: 0L },
            oldestDate = { folderPhotos -> folderPhotos.minOfOrNull { it.dateModified } ?: 0L }
        )

        val result = ArrayList<Photo>(baseSorted.size)
        result.addAll(flattenFolderGroups(grouped, sortedFolderPaths, shouldRandomizePhotos, effectivePhotoSeed))
        return result
    }

    internal fun <T> sortRecursivelyByGlobalDate(
        photos: List<T>,
        arrangement: Int,
        dateModified: (T) -> Long
    ): List<T> {
        return when (arrangement) {
            SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_ASC ->
                photos.sortedWith(compareBy<T> { dateModified(it) })
            SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_DESC ->
                photos.sortedWith(compareByDescending<T> { dateModified(it) })
            else -> photos
        }
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
}
