// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import java.util.Locale
import kotlin.random.Random

/**
 * Shared media ordering rules. Paging now lives in the Compose view models, so this
 * object keeps only the pure sorting helpers used by both the media page and tests.
 */
object MediaManager {

    /**
     * Orders photos for the media waterfall.
     *
     * Random sorts come in two flavours: [SettingsManager.SORT_RANDOM_PHOTOS_GROUPED] keeps the
     * folder order and only shuffles inside each folder, while [SettingsManager.SORT_RANDOM_PHOTOS]
     * (and the randomize toggle) shuffle every photo across all folders.
     */
    fun sortPhotos(
        photos: List<Photo>,
        sortOrder: Int,
        isRecursive: Boolean,
        recursiveImageArrangement: Int = SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GROUPED,
        clusterShuffleSeed: Long = 0L,
        randomizePhotos: Boolean = false,
        photoShuffleSeed: Long = 0L
    ): List<Photo> = orderMedia(
        items = photos,
        sortOrder = sortOrder,
        isRecursive = isRecursive,
        recursiveImageArrangement = recursiveImageArrangement,
        clusterShuffleSeed = clusterShuffleSeed,
        randomizePhotos = randomizePhotos,
        itemShuffleSeed = photoShuffleSeed,
        title = { it.title },
        folderPath = { it.folderPath },
        dateModified = { it.dateModified }
    )

    /** Pure, [Photo]-free variant of [sortPhotos] so the ordering rules stay unit testable. */
    internal fun <T> orderMedia(
        items: List<T>,
        sortOrder: Int,
        isRecursive: Boolean,
        recursiveImageArrangement: Int = SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GROUPED,
        clusterShuffleSeed: Long = 0L,
        randomizePhotos: Boolean = false,
        itemShuffleSeed: Long = 0L,
        title: (T) -> String,
        folderPath: (T) -> String,
        dateModified: (T) -> Long
    ): List<T> {
        val baseSorted = sortMediaItems(items, sortOrder, title, dateModified)
        if (baseSorted.isEmpty()) return baseSorted

        val effectiveItemSeed = if (itemShuffleSeed != 0L) itemShuffleSeed else Random.nextLong()
        val shuffleWithinFolders = SettingsManager.isRandomPhotoSort(sortOrder)

        if (randomizePhotos || SettingsManager.isFullyShuffledPhotoSort(sortOrder)) {
            return baseSorted.shuffled(Random(effectiveItemSeed))
        }

        if (!isRecursive) {
            return if (shuffleWithinFolders) baseSorted.shuffled(Random(effectiveItemSeed)) else baseSorted
        }

        if (recursiveImageArrangement != SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GROUPED) {
            return sortRecursivelyByGlobalDate(
                photos = items,
                arrangement = recursiveImageArrangement,
                dateModified = dateModified
            )
        }

        val grouped = LinkedHashMap<String, List<T>>()
        for (item in baseSorted) {
            val path = folderPath(item)
            grouped[path] = (grouped[path] ?: emptyList()) + item
        }

        if (grouped.size <= 1) {
            return if (shuffleWithinFolders) baseSorted.shuffled(Random(effectiveItemSeed)) else baseSorted
        }

        val sortedFolderPaths = sortFolderPaths(
            grouped = grouped,
            sortOrder = sortOrder,
            clusterShuffleSeed = clusterShuffleSeed,
            newestDate = { folderItems -> folderItems.maxOfOrNull(dateModified) ?: 0L },
            oldestDate = { folderItems -> folderItems.minOfOrNull(dateModified) ?: 0L }
        )

        val result = ArrayList<T>(baseSorted.size)
        result.addAll(flattenFolderGroups(grouped, sortedFolderPaths, shuffleWithinFolders, effectiveItemSeed))
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

    private fun <T> sortMediaItems(
        items: List<T>,
        sortOrder: Int,
        title: (T) -> String,
        dateModified: (T) -> Long
    ): List<T> {
        return when (sortOrder) {
            SettingsManager.SORT_NAME_ASC -> items.sortedBy { title(it).lowercase(Locale.ROOT) }
            SettingsManager.SORT_NAME_DESC -> items.sortedByDescending { title(it).lowercase(Locale.ROOT) }
            SettingsManager.SORT_DATE_ASC -> items.sortedBy(dateModified)
            SettingsManager.SORT_DATE_DESC,
            SettingsManager.SORT_RANDOM_FOLDERS -> items.sortedByDescending(dateModified)
            else -> items.sortedByDescending(dateModified)
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
