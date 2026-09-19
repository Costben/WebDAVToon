package erl.webdavtoon

/**
 * Thread-safe snapshot cache for preference values.
 *
 * Readers are lock-free and always observe a *complete* snapshot: writers publish a new immutable
 * map through a single volatile reference swap instead of mutating the previous map in place.
 *
 * This matters for the settings cache, which the DataStore collector refreshes on every write.
 * With an in-place `clear()` + repopulate, a reader racing a refresh could observe an empty or
 * partially filled map and silently fall back to defaults — which surfaced as a wrong theme and a
 * phantom "slot 0" when switching servers quickly.
 */
internal class PreferenceSnapshotCache {

    @Volatile
    private var snapshot: Map<String, Any> = emptyMap()

    private val lock = Any()

    fun <T : Any> read(name: String, defaultValue: T): T {
        @Suppress("UNCHECKED_CAST")
        return snapshot[name] as? T ?: defaultValue
    }

    fun <T : Any> write(name: String, value: T) {
        synchronized(lock) {
            snapshot = HashMap(snapshot).apply { this[name] = value }
        }
    }

    fun remove(name: String) {
        synchronized(lock) {
            snapshot = HashMap(snapshot).apply { remove(name) }
        }
    }

    fun replace(values: Map<String, Any>) {
        synchronized(lock) {
            snapshot = values.toMap()
        }
    }
}
