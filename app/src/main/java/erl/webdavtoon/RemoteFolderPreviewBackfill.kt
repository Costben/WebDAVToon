package erl.webdavtoon

import kotlinx.coroutines.CoroutineScope

/**
 * Drives background preview inspection for folder tiles rendered in a waterfall.
 *
 * A listing call only carries previews for folders the remote scan already touched,
 * so many tiles first render as plain folder icons. This coordinator inspects the
 * folders a waterfall actually shows, one at a time, and remembers which paths have
 * already been resolved so scrolling never re-queues finished work.
 */
internal class RemoteFolderPreviewBackfill(
    scope: CoroutineScope,
    private val needsPreview: (Folder) -> Boolean = defaultNeedsPreview,
    private val inspect: suspend (Folder, Boolean) -> Unit
) {

    private val scheduler = VisibleRemotePreviewScheduler(scope) { folder, forceRefresh ->
        try {
            inspect(folder, forceRefresh)
        } finally {
            synchronized(lock) { inFlightPaths.remove(folder.path) }
        }
    }

    private val lock = Any()
    private val visibleFolders = linkedMapOf<String, Folder>()
    private val resolvedPaths = linkedSetOf<String>()
    private val inFlightPaths = linkedSetOf<String>()

    fun setVisible(folder: Folder, visible: Boolean) {
        synchronized(lock) {
            if (visible) {
                visibleFolders[folder.path] = folder
                if (folder.path in resolvedPaths) return
            } else {
                visibleFolders.remove(folder.path)
                inFlightPaths.remove(folder.path)
            }
        }
        scheduler.setVisible(folder, visible)
    }

    fun requestVisiblePreviews(forceRefresh: Boolean = false) {
        val pending = synchronized(lock) {
            if (forceRefresh) {
                resolvedPaths.clear()
                inFlightPaths.clear()
            }
            visibleFolders.values
                .filter { needsPreview(it) }
                .filterNot { it.path in resolvedPaths || it.path in inFlightPaths }
                .onEach { inFlightPaths += it.path }
                .toList()
        }
        pending.forEach { folder -> scheduler.enqueue(folder, forceRefresh) }
    }

    fun markResolved(folder: Folder) {
        synchronized(lock) {
            resolvedPaths += folder.path
            inFlightPaths.remove(folder.path)
        }
    }

    /** Records an inspection that returned nothing so the path is retried next round. */
    fun markFailed(folder: Folder) {
        synchronized(lock) { inFlightPaths.remove(folder.path) }
    }

    /**
     * Starts a fresh inspection round. Folders that never resolved are retried, and
     * already-resolved folders are inspected again only when [forgetResolved] is set.
     */
    fun retire(forgetResolved: Boolean = false) {
        synchronized(lock) {
            inFlightPaths.clear()
            if (forgetResolved) resolvedPaths.clear()
        }
    }

    companion object {
        val defaultNeedsPreview: (Folder) -> Boolean = { folder ->
            !folder.isLocal &&
                !folder.path.startsWith("virtual://") &&
                folder.previewUris.isEmpty() &&
                folder.path.trim('/').split('/').none { it.length > 1 && it.startsWith(".") }
        }
    }
}
