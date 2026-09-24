// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaManagerSortTest {

    @Test
    fun recursiveGlobalDateArrangement_flattensFolderPathsAndSortsNewestFirst() {
        val items = listOf(
            PhotoStub("/a", "a1", 100),
            PhotoStub("/b", "b1", 300),
            PhotoStub("/a", "a2", 250),
            PhotoStub("/c", "c1", 200)
        )

        val sorted = MediaManager.sortRecursivelyByGlobalDate(
            photos = items,
            arrangement = SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_DESC,
            dateModified = { it.dateModified }
        )

        assertEquals(listOf("b1", "a2", "c1", "a1"), sorted.map { it.id })
        assertEquals(listOf(300L, 250L, 200L, 100L), sorted.map { it.dateModified })
    }

    @Test
    fun recursiveGroupedArrangement_preservesExistingFolderClusters() {
        val grouped = sampleGroupedPhotos()
        val folderOrder = MediaManager.sortFolderPaths(
            grouped = grouped,
            sortOrder = SettingsManager.SORT_DATE_DESC,
            clusterShuffleSeed = 0L,
            newestDate = { photos -> photos.maxOfOrNull { it.dateModified } ?: 0L },
            oldestDate = { photos -> photos.minOfOrNull { it.dateModified } ?: 0L }
        )
        val groupedResult = folderOrder.flatMap(grouped::getValue)
        val unchanged = MediaManager.sortRecursivelyByGlobalDate(
            photos = groupedResult,
            arrangement = SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GROUPED,
            dateModified = { it.dateModified }
        )

        assertEquals(folderOrder, unchanged.map { it.folderPath }.distinct())
        assertEquals(groupedResult, unchanged)
    }

    @Test
    fun randomFolderSort_keepsFolderClustersTogether() {
        val sortedFolderPaths = MediaManager.sortFolderPaths(
            grouped = sampleGroupedPhotos(),
            sortOrder = SettingsManager.SORT_RANDOM_FOLDERS,
            clusterShuffleSeed = 4L,
            newestDate = { photos -> photos.maxOfOrNull { it.dateModified } ?: 0L },
            oldestDate = { photos -> photos.minOfOrNull { it.dateModified } ?: 0L }
        )
        val sorted = sortedFolderPaths.flatMap { folder -> sampleGroupedPhotos().getValue(folder) }

        val folderOrder = sorted.map { it.folderPath }.distinct()
        assertEquals(folderOrder.flatMap { folder -> sorted.filter { it.folderPath == folder } }, sorted)
    }

    @Test
    fun randomFolderSort_isStableForSameSeed() {
        val grouped = sampleGroupedPhotos()

        val first = MediaManager.sortFolderPaths(
            grouped = grouped,
            sortOrder = SettingsManager.SORT_RANDOM_FOLDERS,
            clusterShuffleSeed = 4L,
            newestDate = { photos -> photos.maxOfOrNull { it.dateModified } ?: 0L },
            oldestDate = { photos -> photos.minOfOrNull { it.dateModified } ?: 0L }
        )
        val second = MediaManager.sortFolderPaths(
            grouped = grouped,
            sortOrder = SettingsManager.SORT_RANDOM_FOLDERS,
            clusterShuffleSeed = 4L,
            newestDate = { photos -> photos.maxOfOrNull { it.dateModified } ?: 0L },
            oldestDate = { photos -> photos.minOfOrNull { it.dateModified } ?: 0L }
        )

        assertEquals(first, second)
    }

    @Test
    fun randomFolderSort_canChangeWhenSeedChanges() {
        val grouped = sampleGroupedPhotos()
        val baseline = MediaManager.sortFolderPaths(
            grouped = grouped,
            sortOrder = SettingsManager.SORT_RANDOM_FOLDERS,
            clusterShuffleSeed = 1L,
            newestDate = { photos -> photos.maxOfOrNull { it.dateModified } ?: 0L },
            oldestDate = { photos -> photos.minOfOrNull { it.dateModified } ?: 0L }
        )

        val hasDifferentOrder = (2L..20L).any { seed ->
            MediaManager.sortFolderPaths(
                grouped = grouped,
                sortOrder = SettingsManager.SORT_RANDOM_FOLDERS,
                clusterShuffleSeed = seed,
                newestDate = { photos -> photos.maxOfOrNull { it.dateModified } ?: 0L },
                oldestDate = { photos -> photos.minOfOrNull { it.dateModified } ?: 0L }
            ) != baseline
        }

        assertTrue(hasDifferentOrder)
    }

    @Test
    fun randomizePhotos_keepsFolderOrderUnchanged() {
        val grouped = sampleGroupedPhotos()
        val sortedFolderPaths = listOf("/a", "/b", "/c", "/d")

        val sorted = MediaManager.flattenFolderGroups(
            grouped = grouped,
            sortedFolderPaths = sortedFolderPaths,
            randomizeItems = true,
            itemShuffleSeed = 3L
        )

        assertEquals(sortedFolderPaths, sorted.map { it.folderPath }.distinct())
    }

    @Test
    fun randomizePhotos_isStableForSameSeed() {
        val grouped = sampleGroupedPhotos()
        val sortedFolderPaths = listOf("/a", "/b", "/c", "/d")

        val first = MediaManager.flattenFolderGroups(
            grouped = grouped,
            sortedFolderPaths = sortedFolderPaths,
            randomizeItems = true,
            itemShuffleSeed = 3L
        )
        val second = MediaManager.flattenFolderGroups(
            grouped = grouped,
            sortedFolderPaths = sortedFolderPaths,
            randomizeItems = true,
            itemShuffleSeed = 3L
        )

        assertEquals(first, second)
    }

    @Test
    fun randomizePhotos_canChangeWhenSeedChanges() {
        val grouped = sampleGroupedPhotos()
        val sortedFolderPaths = listOf("/a", "/b", "/c", "/d")
        val baseline = MediaManager.flattenFolderGroups(
            grouped = grouped,
            sortedFolderPaths = sortedFolderPaths,
            randomizeItems = true,
            itemShuffleSeed = 1L
        )

        val hasDifferentOrder = (2L..20L).any { seed ->
            MediaManager.flattenFolderGroups(
                grouped = grouped,
                sortedFolderPaths = sortedFolderPaths,
                randomizeItems = true,
                itemShuffleSeed = seed
            ) != baseline
        }

        assertTrue(hasDifferentOrder)
    }

    @Test
    fun groupedRandomPhotoSort_keepsFolderRunsContiguous() {
        val stubs = sampleGroupedPhotos().values.flatten()

        val ordered = orderStubs(stubs, sortOrder = SettingsManager.SORT_RANDOM_PHOTOS_GROUPED, itemShuffleSeed = 3L)

        assertEquals(stubs.size, ordered.size)
        assertEquals(stubs.map { it.id }.sorted(), ordered.map { it.id }.sorted())
        assertEquals(4, folderRuns(ordered))
    }

    @Test
    fun fullRandomPhotoSort_interleavesFolders() {
        val stubs = sampleGroupedPhotos().values.flatten()

        val ordered = orderStubs(stubs, sortOrder = SettingsManager.SORT_RANDOM_PHOTOS, itemShuffleSeed = 3L)

        assertEquals(stubs.map { it.id }.sorted(), ordered.map { it.id }.sorted())
        assertTrue(
            "expected photos from different folders to interleave, got ${ordered.map { it.folderPath }}",
            folderRuns(ordered) > 4
        )
    }

    @Test
    fun fullRandomPhotoSort_isStableForSameSeed() {
        val stubs = sampleGroupedPhotos().values.flatten()

        val first = orderStubs(stubs, sortOrder = SettingsManager.SORT_RANDOM_PHOTOS, itemShuffleSeed = 5L)
        val second = orderStubs(stubs, sortOrder = SettingsManager.SORT_RANDOM_PHOTOS, itemShuffleSeed = 5L)

        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun randomizeToggle_interleavesFoldersForDateSort() {
        val stubs = sampleGroupedPhotos().values.flatten()

        val ordered = orderStubs(
            stubs,
            sortOrder = SettingsManager.SORT_DATE_DESC,
            randomizePhotos = true,
            itemShuffleSeed = 11L
        )

        assertTrue(folderRuns(ordered) > 4)
    }

    @Test
    fun globalDateArrangement_stillWinsForGroupedRandomPhotoSort() {
        val stubs = sampleGroupedPhotos().values.flatten()

        val ordered = orderStubs(
            stubs,
            sortOrder = SettingsManager.SORT_RANDOM_PHOTOS_GROUPED,
            recursiveImageArrangement = SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_DESC,
            itemShuffleSeed = 3L
        )

        assertEquals(stubs.sortedByDescending { it.dateModified }.map { it.id }, ordered.map { it.id })
    }

    private fun orderStubs(
        stubs: List<PhotoStub>,
        sortOrder: Int,
        recursiveImageArrangement: Int = SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GROUPED,
        randomizePhotos: Boolean = false,
        itemShuffleSeed: Long = 0L
    ): List<PhotoStub> = MediaManager.orderMedia(
        items = stubs,
        sortOrder = sortOrder,
        isRecursive = true,
        recursiveImageArrangement = recursiveImageArrangement,
        clusterShuffleSeed = 1L,
        randomizePhotos = randomizePhotos,
        itemShuffleSeed = itemShuffleSeed,
        title = { it.id },
        folderPath = { it.folderPath },
        dateModified = { it.dateModified }
    )

    private fun folderRuns(items: List<PhotoStub>): Int {
        var runs = 0
        var previous: String? = null
        for (item in items) {
            if (item.folderPath != previous) runs++
            previous = item.folderPath
        }
        return runs
    }

    private fun sampleGroupedPhotos(): Map<String, List<PhotoStub>> = linkedMapOf(
        "/a" to listOf(PhotoStub("/a", "a1", 100), PhotoStub("/a", "a2", 90), PhotoStub("/a", "a3", 80)),
        "/b" to listOf(PhotoStub("/b", "b1", 300), PhotoStub("/b", "b2", 280), PhotoStub("/b", "b3", 260)),
        "/c" to listOf(PhotoStub("/c", "c1", 200), PhotoStub("/c", "c2", 180), PhotoStub("/c", "c3", 160)),
        "/d" to listOf(PhotoStub("/d", "d1", 400), PhotoStub("/d", "d2", 380), PhotoStub("/d", "d3", 360))
    )

    private data class PhotoStub(
        val folderPath: String,
        val id: String,
        val dateModified: Long
    )
}
