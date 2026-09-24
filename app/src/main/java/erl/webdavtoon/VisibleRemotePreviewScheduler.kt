// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs remote folder inspection one visible card at a time.
 *
 * The Rust preview repository serializes these requests internally. Keeping the
 * queue on the Kotlin side avoids filling IO workers with scans for cards the
 * user has already scrolled past.
 */
internal class VisibleRemotePreviewScheduler(
    private val scope: CoroutineScope,
    private val load: suspend (Folder, Boolean) -> Unit
) {

    private data class Request(
        val folder: Folder,
        val forceRefresh: Boolean
    )

    private val lock = Any()
    private val visiblePaths = linkedSetOf<String>()
    private val pendingByPath = linkedMapOf<String, Request>()
    private var worker: Job? = null

    fun setVisible(folder: Folder, visible: Boolean) {
        synchronized(lock) {
            if (visible) {
                visiblePaths.add(folder.path)
            } else {
                visiblePaths.remove(folder.path)
                pendingByPath.remove(folder.path)
            }
        }
        ensureWorker()
    }

    fun enqueue(folder: Folder, forceRefresh: Boolean) {
        synchronized(lock) {
            if (folder.path !in visiblePaths) return

            val existing = pendingByPath.remove(folder.path)
            pendingByPath[folder.path] = Request(
                folder = folder,
                forceRefresh = forceRefresh || existing?.forceRefresh == true
            )
        }
        ensureWorker()
    }

    fun enqueueVisible(folders: List<Folder>, forceRefresh: Boolean = false) {
        synchronized(lock) {
            folders.filter { it.path in visiblePaths }.forEach { folder ->
                val existing = pendingByPath.remove(folder.path)
                pendingByPath[folder.path] = Request(
                    folder = folder,
                    forceRefresh = forceRefresh || existing?.forceRefresh == true
                )
            }
        }
        ensureWorker()
    }

    private fun ensureWorker() {
        synchronized(lock) {
            if (worker?.isActive == true) return
            if (pendingByPath.keys.none { it in visiblePaths }) return
            worker = scope.launch {
                try {
                    while (true) {
                        val request = synchronized(lock) {
                            val next = pendingByPath.entries.firstOrNull { (path, _) ->
                                path in visiblePaths
                            } ?: return@launch
                            pendingByPath.remove(next.key)
                            next.value
                        }
                        load(request.folder, request.forceRefresh)
                    }
                } finally {
                    synchronized(lock) {
                        worker = null
                    }
                    ensureWorker()
                }
            }
        }
    }
}
