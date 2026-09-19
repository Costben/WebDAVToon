package erl.webdavtoon

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFolderPreviewBackfillTest {

    private fun folder(path: String, isLocal: Boolean = false): Folder = Folder(
        path = path,
        name = path.trim('/').substringAfterLast('/'),
        isLocal = isLocal,
        sourceSlot = 0
    )

    private fun countingBackfill(
        inspected: MutableList<String>,
        missing: (Folder) -> Boolean = { it.path.contains("missing") && !it.isLocal }
    ): RemoteFolderPreviewBackfill {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return RemoteFolderPreviewBackfill(scope, missing) { folder, _ -> inspected += folder.path }
    }

    @Test
    fun onlyFoldersThePredicateSelects_areInspected() = runBlocking {
        val inspected = mutableListOf<String>()
        val backfill = countingBackfill(inspected)

        backfill.setVisible(folder("/missing-one"), true)
        backfill.setVisible(folder("/filled"), true)
        backfill.setVisible(folder("/missing-local", isLocal = true), true)
        backfill.requestVisiblePreviews()

        assertEquals(listOf("/missing-one"), inspected)
    }

    @Test
    fun resolvedFolder_isNotInspectedTwice() = runBlocking {
        val inspected = mutableListOf<String>()
        val backfill = countingBackfill(inspected)
        val target = folder("/missing-sub")

        backfill.setVisible(target, true)
        backfill.requestVisiblePreviews()
        backfill.markResolved(target)
        backfill.requestVisiblePreviews()

        assertEquals(listOf("/missing-sub"), inspected)
    }

    @Test
    fun hiddenBeforeInspection_isNotInspected() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val inspected = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val backfill = RemoteFolderPreviewBackfill(scope, { true }) { folder, _ ->
            inspected += folder.path
            if (folder.path == "/first") {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        val first = folder("/first")
        val second = folder("/second")

        backfill.setVisible(first, true)
        backfill.setVisible(second, true)
        backfill.requestVisiblePreviews()
        firstStarted.await()
        backfill.setVisible(second, false)
        releaseFirst.complete(Unit)

        assertEquals(listOf("/first"), inspected)
    }

    @Test
    fun failedInspection_isRetriedOnNextRound() = runBlocking {
        val inspected = mutableListOf<String>()
        val backfill = countingBackfill(inspected)
        val target = folder("/missing-sub")

        backfill.setVisible(target, true)
        backfill.requestVisiblePreviews()
        backfill.markFailed(target)
        backfill.retire()
        backfill.requestVisiblePreviews()

        assertEquals(listOf("/missing-sub", "/missing-sub"), inspected)
    }

    @Test
    fun forceRefresh_reinspectsResolvedFolders() = runBlocking {
        val inspected = mutableListOf<String>()
        val backfill = countingBackfill(inspected)
        val target = folder("/missing-sub")

        backfill.setVisible(target, true)
        backfill.requestVisiblePreviews()
        backfill.markResolved(target)
        backfill.retire(forgetResolved = true)
        backfill.requestVisiblePreviews(forceRefresh = true)

        assertEquals(listOf("/missing-sub", "/missing-sub"), inspected)
    }

    @Test
    fun defaultPredicate_acceptsOnlyRemoteFolderWithoutPreviewsOrHiddenSegments() {
        val needs = RemoteFolderPreviewBackfill.defaultNeedsPreview
        assertTrue(needs(folder("/sub")))
        assertFalse(needs(folder("/local", isLocal = true)))
        assertFalse(needs(folder("virtual://local_root")))
        assertFalse(needs(folder("/parent/.hidden")))
    }
}
