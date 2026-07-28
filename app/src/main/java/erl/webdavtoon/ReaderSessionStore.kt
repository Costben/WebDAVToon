package erl.webdavtoon

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Identifies the entry point that owns a reader image snapshot. */
enum class ReaderSessionSource {
    MAIN_ACTIVITY,
    MIXED_FOLDER
}

internal data class ReaderSessionSnapshot<T>(
    val id: String,
    val source: ReaderSessionSource,
    val items: List<T>
)

internal data class ResolvedReaderSession<T>(
    val snapshot: ReaderSessionSnapshot<T>,
    val index: Int,
    val restoredFromSameSession: Boolean
)

/**
 * Keeps reader content isolated from the global media-list caches.  A snapshot is deliberately
 * addressed by a fresh ID so an Intent can state exactly which list and target image it owns.
 */
internal class ReaderSessionStore<T>(
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private val sessions = ConcurrentHashMap<String, ReaderSessionSnapshot<T>>()

    fun create(source: ReaderSessionSource, items: List<T>): ReaderSessionSnapshot<T> {
        val snapshot = ReaderSessionSnapshot(
            id = idFactory(),
            source = source,
            items = items.toList()
        )
        sessions[snapshot.id] = snapshot
        return snapshot
    }

    fun resolve(
        requestedSessionId: String?,
        restoredSessionId: String?,
        savedIndex: Int?,
        intentIndex: Int
    ): ResolvedReaderSession<T>? {
        val snapshot = requestedSessionId?.let(sessions::get) ?: return null
        val restoreSameSession = restoredSessionId == snapshot.id && savedIndex != null
        val requestedIndex = if (restoreSameSession) savedIndex!! else intentIndex
        return ResolvedReaderSession(
            snapshot = snapshot,
            index = requestedIndex.coerceToSnapshot(snapshot.items),
            restoredFromSameSession = restoreSameSession
        )
    }

    fun replaceItems(sessionId: String, items: List<T>) {
        sessions.computeIfPresent(sessionId) { _, snapshot ->
            snapshot.copy(items = items.toList())
        }
    }

    private fun Int.coerceToSnapshot(items: List<T>): Int {
        return if (items.isEmpty()) 0 else coerceIn(0, items.lastIndex)
    }
}

internal object ReaderSessions {
    const val EXTRA_SESSION_ID = "EXTRA_READER_SESSION_ID"
    const val STATE_SESSION_ID = "STATE_READER_SESSION_ID"

    private val store = ReaderSessionStore<Photo>()

    fun create(source: ReaderSessionSource, photos: List<Photo>): ReaderSessionSnapshot<Photo> =
        store.create(source, photos)

    fun resolve(
        requestedSessionId: String?,
        restoredSessionId: String?,
        savedIndex: Int?,
        intentIndex: Int
    ): ResolvedReaderSession<Photo>? =
        store.resolve(requestedSessionId, restoredSessionId, savedIndex, intentIndex)

    fun replacePhotos(sessionId: String, photos: List<Photo>) {
        store.replaceItems(sessionId, photos)
    }
}
