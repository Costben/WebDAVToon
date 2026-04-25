package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaManagerSortTest {

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
