package erl.webdavtoon

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteFolderPreviewMemoryCacheTest {

    @After
    fun tearDown() {
        RemoteFolderPreviewMemoryCache.clearForTests()
    }

    @Test
    fun get_reusesPreviewAcrossEquivalentFolderPaths() {
        RemoteFolderPreviewMemoryCache.put(
            accountKey = "server|user",
            sortOrder = SettingsManager.SORT_DATE_DESC,
            path = "FL勾",
            hasSubFolders = true,
            previewUriStrings = listOf("https://example.test/a.jpg", "https://example.test/a.jpg")
        )

        val cached = RemoteFolderPreviewMemoryCache.get(
            accountKey = "server|user",
            sortOrder = SettingsManager.SORT_DATE_DESC,
            path = "/FL勾/"
        )

        assertEquals(true, cached?.hasSubFolders)
        assertEquals(listOf("https://example.test/a.jpg"), cached?.previewUriStrings)
    }

    @Test
    fun get_separatesSortOrders() {
        RemoteFolderPreviewMemoryCache.put(
            accountKey = "server|user",
            sortOrder = SettingsManager.SORT_DATE_DESC,
            path = "conf out/",
            hasSubFolders = false,
            previewUriStrings = listOf("https://example.test/newest.jpg")
        )

        val cached = RemoteFolderPreviewMemoryCache.get(
            accountKey = "server|user",
            sortOrder = SettingsManager.SORT_NAME_ASC,
            path = "conf out/"
        )

        assertNull(cached)
    }

    @Test
    fun invalidateFolderTree_removesOnlyMatchingSubtree() {
        RemoteFolderPreviewMemoryCache.put(
            accountKey = "server|user",
            sortOrder = SettingsManager.SORT_DATE_DESC,
            path = "FL勾/child/",
            hasSubFolders = false,
            previewUriStrings = listOf("https://example.test/fl.jpg")
        )
        RemoteFolderPreviewMemoryCache.put(
            accountKey = "server|user",
            sortOrder = SettingsManager.SORT_DATE_DESC,
            path = "conf out/child/",
            hasSubFolders = false,
            previewUriStrings = listOf("https://example.test/conf.jpg")
        )

        RemoteFolderPreviewMemoryCache.invalidateFolderTree("server|user", "FL勾/")

        assertNull(
            RemoteFolderPreviewMemoryCache.get(
                accountKey = "server|user",
                sortOrder = SettingsManager.SORT_DATE_DESC,
                path = "FL勾/child/"
            )
        )
        assertEquals(
            listOf("https://example.test/conf.jpg"),
            RemoteFolderPreviewMemoryCache.get(
                accountKey = "server|user",
                sortOrder = SettingsManager.SORT_DATE_DESC,
                path = "conf out/child/"
            )?.previewUriStrings
        )
    }
}
