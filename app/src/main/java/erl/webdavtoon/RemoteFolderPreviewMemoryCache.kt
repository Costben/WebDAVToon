package erl.webdavtoon

object RemoteFolderPreviewMemoryCache {

    data class Entry(
        val hasSubFolders: Boolean,
        val previewUriStrings: List<String>
    )

    private data class Key(
        val accountKey: String,
        val sortOrder: Int,
        val path: String
    )

    private const val MAX_ENTRIES = 1024

    private val entries = object : LinkedHashMap<Key, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun get(accountKey: String, sortOrder: Int, path: String): Entry? {
        return entries[Key(accountKey, sortOrder, normalizePath(path))]
    }

    @Synchronized
    fun put(
        accountKey: String,
        sortOrder: Int,
        path: String,
        hasSubFolders: Boolean,
        previewUriStrings: List<String>
    ) {
        if (previewUriStrings.isEmpty()) return
        entries[Key(accountKey, sortOrder, normalizePath(path))] = Entry(
            hasSubFolders = hasSubFolders,
            previewUriStrings = previewUriStrings.distinct()
        )
    }

    @Synchronized
    fun invalidateFolderTree(accountKey: String, rootPath: String) {
        val normalizedRoot = normalizePath(rootPath)
        val keysToRemove = entries.keys.filter { key ->
            key.accountKey == accountKey &&
                (normalizedRoot == "/" || key.path == normalizedRoot || key.path.startsWith(normalizedRoot))
        }
        keysToRemove.forEach(entries::remove)
    }

    @Synchronized
    fun clearForTests() {
        entries.clear()
    }

    internal fun normalizePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed == "/") return "/"
        return "${trimmed.trim('/')}/"
    }
}
