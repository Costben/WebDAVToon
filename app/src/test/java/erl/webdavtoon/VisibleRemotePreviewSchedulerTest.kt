// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleRemotePreviewSchedulerTest {

    @Test
    fun hiddenPendingFolder_isNotInspected() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val inspectedPaths = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val scheduler = VisibleRemotePreviewScheduler(scope) { folder, _ ->
            inspectedPaths += folder.path
            if (folder.path == "first") {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        val first = Folder(path = "first", name = "first", isLocal = false)
        val second = Folder(path = "second", name = "second", isLocal = false)

        scheduler.setVisible(first, true)
        scheduler.setVisible(second, true)
        scheduler.enqueue(first, forceRefresh = false)
        firstStarted.await()
        scheduler.enqueue(second, forceRefresh = false)
        scheduler.setVisible(second, false)
        releaseFirst.complete(Unit)

        assertEquals(listOf("first"), inspectedPaths)
    }
}
