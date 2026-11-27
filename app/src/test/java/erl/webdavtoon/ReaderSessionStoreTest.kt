// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSessionStoreTest {

    @Test
    fun freshTap_usesItsOwnSnapshotAndIntentTargetInsteadOfOlderBrowseAllSession() {
        val ids = ArrayDeque(listOf("browse-all", "mixed-tap"))
        val store = ReaderSessionStore<String> { ids.removeFirst() }
        val browseAll = store.create(ReaderSessionSource.MAIN_ACTIVITY, listOf("old-0", "old-1", "old-2"))
        val mixedTap = store.create(ReaderSessionSource.MIXED_FOLDER, listOf("direct-0", "direct-1"))

        val resolved = store.resolve(
            requestedSessionId = mixedTap.id,
            restoredSessionId = browseAll.id,
            savedIndex = 2,
            intentIndex = 1
        )!!

        assertEquals(ReaderSessionSource.MIXED_FOLDER, resolved.snapshot.source)
        assertEquals(listOf("direct-0", "direct-1"), resolved.snapshot.items)
        assertEquals(1, resolved.index)
        assertFalse(resolved.restoredFromSameSession)
    }

    @Test
    fun recreation_restoresIndexOnlyWhenSessionIdMatches() {
        val store = ReaderSessionStore<String> { "reader-session" }
        val session = store.create(ReaderSessionSource.MIXED_FOLDER, listOf("0", "1", "2", "3"))

        val restored = store.resolve(session.id, session.id, savedIndex = 3, intentIndex = 1)!!

        assertTrue(restored.restoredFromSameSession)
        assertEquals(3, restored.index)
    }

    @Test
    fun appendItems_deduplicatesUpdatesHasMoreAndPreservesItemsAcrossResolve() {
        val store = ReaderSessionStore<String> { "paged-session" }
        val session = store.create(
            source = ReaderSessionSource.AUTOWORKFLOW,
            items = listOf("a", "b", "c"),
            title = "Album",
            hasMore = true,
            loadMore = { ReaderSessionPage(listOf("d"), hasMore = false) },
        )

        assertTrue(session.hasMore)

        val afterFirstAppend = store.appendItems(
            sessionId = session.id,
            newItems = listOf("b", "d", "e"),
            hasMore = true,
        )!!
        assertEquals(listOf("a", "b", "c", "d", "e"), afterFirstAppend.items)
        assertTrue(afterFirstAppend.hasMore)

        val afterFinalAppend = store.appendItems(
            sessionId = session.id,
            newItems = listOf("e", "f"),
            hasMore = false,
        )!!
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), afterFinalAppend.items)
        assertFalse(afterFinalAppend.hasMore)

        val resolved = store.resolve(session.id, session.id, savedIndex = 5, intentIndex = 0)!!
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), resolved.snapshot.items)
        assertEquals(5, resolved.index)
    }
}
