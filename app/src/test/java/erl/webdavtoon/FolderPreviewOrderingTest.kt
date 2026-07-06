package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPreviewOrderingTest {

    @Test
    fun selectPreviewValues_usesNameAscendingOrder() {
        val selected = FolderPreviewOrdering.selectPreviewValues(
            candidates = sampleCandidates(),
            sortOrder = SettingsManager.SORT_NAME_ASC
        )

        assertEquals(listOf("alpha", "bravo", "charlie", "delta"), selected)
    }

    @Test
    fun selectPreviewValues_usesNameDescendingOrder() {
        val selected = FolderPreviewOrdering.selectPreviewValues(
            candidates = sampleCandidates(),
            sortOrder = SettingsManager.SORT_NAME_DESC
        )

        assertEquals(listOf("foxtrot", "echo", "delta", "charlie"), selected)
    }

    @Test
    fun selectPreviewValues_usesNewestFirstOrder() {
        val selected = FolderPreviewOrdering.selectPreviewValues(
            candidates = sampleCandidates(),
            sortOrder = SettingsManager.SORT_DATE_DESC
        )

        assertEquals(listOf("foxtrot", "echo", "delta", "charlie"), selected)
    }

    @Test
    fun selectPreviewValues_usesOldestFirstOrder() {
        val selected = FolderPreviewOrdering.selectPreviewValues(
            candidates = sampleCandidates(),
            sortOrder = SettingsManager.SORT_DATE_ASC
        )

        assertEquals(listOf("alpha", "bravo", "charlie", "delta"), selected)
    }

    @Test
    fun selectPreviewValues_usesNewestFirstForRandomFolderSort() {
        val selected = FolderPreviewOrdering.selectPreviewValues(
            candidates = sampleCandidates(),
            sortOrder = SettingsManager.SORT_RANDOM_FOLDERS
        )

        assertEquals(listOf("foxtrot", "echo", "delta", "charlie"), selected)
    }

    private fun sampleCandidates(): List<FolderPreviewOrdering.Candidate<String>> {
        return listOf(
            candidate("charlie", 30L),
            candidate("alpha", 10L),
            candidate("foxtrot", 60L),
            candidate("bravo", 20L),
            candidate("echo", 50L),
            candidate("delta", 40L)
        )
    }

    private fun candidate(
        title: String,
        dateModified: Long
    ): FolderPreviewOrdering.Candidate<String> {
        return FolderPreviewOrdering.Candidate(
            value = title,
            title = title,
            dateModified = dateModified
        )
    }
}
