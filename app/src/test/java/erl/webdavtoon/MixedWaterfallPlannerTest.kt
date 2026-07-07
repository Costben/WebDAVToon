package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedWaterfallPlannerTest {

    @Test
    fun buildItems_sortsFolderTilesBeforeMediaSection() {
        val items = MixedWaterfallPlanner.buildItems(
            folders = sampleFolders(),
            media = emptyList(),
            folderSortOrder = SettingsManager.SORT_NAME_ASC
        )

        assertEquals(listOf("alpha", "bravo", "charlie"), folderNames(items))
        assertTrue(items.all { it is MixedWaterfallItem.FolderTile })
    }

    @Test
    fun sortFolders_usesNameDescendingOrder() {
        val sorted = MixedWaterfallPlanner.sortFolders(
            folders = sampleFolders(),
            sortOrder = SettingsManager.SORT_NAME_DESC
        )

        assertEquals(listOf("charlie", "bravo", "alpha"), sorted.map { it.name })
    }

    @Test
    fun sortFolders_usesNewestFirstOrder() {
        val sorted = MixedWaterfallPlanner.sortFolders(
            folders = sampleFolders(),
            sortOrder = SettingsManager.SORT_DATE_DESC
        )

        assertEquals(listOf("bravo", "charlie", "alpha"), sorted.map { it.name })
    }

    @Test
    fun sortFolders_usesOldestFirstOrder() {
        val sorted = MixedWaterfallPlanner.sortFolders(
            folders = sampleFolders(),
            sortOrder = SettingsManager.SORT_DATE_ASC
        )

        assertEquals(listOf("alpha", "charlie", "bravo"), sorted.map { it.name })
    }

    private fun folderNames(items: List<MixedWaterfallItem>): List<String> {
        return items.mapNotNull { item ->
            (item as? MixedWaterfallItem.FolderTile)?.folder?.name
        }
    }

    private fun sampleFolders(): List<Folder> {
        return listOf(
            Folder(path = "charlie/", name = "charlie", isLocal = false, dateModified = 30L),
            Folder(path = "alpha/", name = "alpha", isLocal = false, dateModified = 10L),
            Folder(path = "bravo/", name = "bravo", isLocal = false, dateModified = 50L)
        )
    }
}
