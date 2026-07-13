package erl.webdavtoon

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import java.security.MessageDigest

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

    private data class PersistedFolderEntry(
        val accountHash: String = "",
        val path: String = "/",
        val hasSubFolders: Boolean = false,
        val previewUriStringsBySortOrder: Map<String, List<String>> = emptyMap(),
        val updatedAtMs: Long = 0L
    )

    private const val PREFS_NAME = "remote_folder_preview_cache_v1"
    private const val STORAGE_KEY_PREFIX = "folder_"
    private const val MAX_MEMORY_ENTRIES = 4096
    private const val MAX_PERSISTED_FOLDERS = 1024

    private val gson = Gson()

    @Volatile
    private var preferences: SharedPreferences? = null

    private val entries = object : LinkedHashMap<Key, Entry>(MAX_MEMORY_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>?): Boolean {
            return size > MAX_MEMORY_ENTRIES
        }
    }

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun get(accountKey: String, sortOrder: Int, path: String): Entry? {
        val key = Key(accountKey, sortOrder, normalizePath(path))
        entries[key]?.let { return it }

        val persisted = readPersistedFolder(accountKey, key.path) ?: return null
        persisted.previewUriStringsBySortOrder.forEach { (persistedSortOrder, previewUriStrings) ->
            persistedSortOrder.toIntOrNull()?.let { order ->
                entries[Key(accountKey, order, key.path)] = Entry(
                    hasSubFolders = persisted.hasSubFolders,
                    previewUriStrings = previewUriStrings.distinct()
                )
            }
        }
        Log.i(
            "RemoteFolderPreviewCache",
            "persistentHit path=${key.path} sortOrder=$sortOrder previews=${entries[key]?.previewUriStrings?.size ?: 0}"
        )
        return entries[key]
    }

    @Synchronized
    fun put(
        accountKey: String,
        sortOrder: Int,
        path: String,
        hasSubFolders: Boolean,
        previewUriStrings: List<String>
    ) {
        val normalizedPath = normalizePath(path)
        val normalizedPreviews = previewUriStrings.distinct()
        entries[Key(accountKey, sortOrder, normalizedPath)] = Entry(
            hasSubFolders = hasSubFolders,
            previewUriStrings = normalizedPreviews
        )

        val persisted = readPersistedFolder(accountKey, normalizedPath)
        val mergedPreviews = persisted?.previewUriStringsBySortOrder.orEmpty().toMutableMap().apply {
            this[sortOrder.toString()] = normalizedPreviews
        }
        writePersistedFolder(
            accountKey = accountKey,
            normalizedPath = normalizedPath,
            hasSubFolders = hasSubFolders,
            previewUriStringsBySortOrder = mergedPreviews
        )
    }

    @Synchronized
    fun putAll(
        accountKey: String,
        path: String,
        hasSubFolders: Boolean,
        previewUriStringsBySortOrder: Map<Int, List<String>>
    ) {
        val normalizedPath = normalizePath(path)
        val normalizedPreviewsBySortOrder = previewUriStringsBySortOrder.mapValues { (_, previewUriStrings) ->
            previewUriStrings.distinct()
        }
        normalizedPreviewsBySortOrder.forEach { (sortOrder, previewUriStrings) ->
            entries[Key(accountKey, sortOrder, normalizedPath)] = Entry(
                hasSubFolders = hasSubFolders,
                previewUriStrings = previewUriStrings
            )
        }
        writePersistedFolder(
            accountKey = accountKey,
            normalizedPath = normalizedPath,
            hasSubFolders = hasSubFolders,
            previewUriStringsBySortOrder = normalizedPreviewsBySortOrder.mapKeys { (sortOrder, _) ->
                sortOrder.toString()
            }
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

        val accountHash = sha256(accountKey)
        val prefs = preferences ?: return
        val editor = prefs.edit()
        prefs.all.forEach { (storageKey, rawValue) ->
            val persisted = parsePersistedFolder(rawValue as? String) ?: return@forEach
            if (
                persisted.accountHash == accountHash &&
                (normalizedRoot == "/" || persisted.path == normalizedRoot || persisted.path.startsWith(normalizedRoot))
            ) {
                editor.remove(storageKey)
            }
        }
        editor.commit()
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

    private fun readPersistedFolder(accountKey: String, normalizedPath: String): PersistedFolderEntry? {
        val prefs = preferences ?: return null
        val persisted = parsePersistedFolder(prefs.getString(storageKey(accountKey, normalizedPath), null))
            ?: return null
        return persisted.takeIf {
            it.accountHash == sha256(accountKey) && it.path == normalizedPath
        }
    }

    private fun writePersistedFolder(
        accountKey: String,
        normalizedPath: String,
        hasSubFolders: Boolean,
        previewUriStringsBySortOrder: Map<String, List<String>>
    ) {
        val prefs = preferences ?: return
        val persisted = PersistedFolderEntry(
            accountHash = sha256(accountKey),
            path = normalizedPath,
            hasSubFolders = hasSubFolders,
            previewUriStringsBySortOrder = previewUriStringsBySortOrder,
            updatedAtMs = System.currentTimeMillis()
        )
        prefs.edit()
            .putString(storageKey(accountKey, normalizedPath), gson.toJson(persisted))
            .commit()
        trimPersistentCacheIfNeeded(prefs)
    }

    private fun trimPersistentCacheIfNeeded(prefs: SharedPreferences) {
        val all = prefs.all
        if (all.size <= MAX_PERSISTED_FOLDERS) return

        val overflow = all.mapNotNull { (storageKey, rawValue) ->
            parsePersistedFolder(rawValue as? String)?.let { storageKey to it.updatedAtMs }
        }.sortedBy { (_, updatedAtMs) -> updatedAtMs }
            .take(all.size - MAX_PERSISTED_FOLDERS)
        if (overflow.isEmpty()) return

        val editor = prefs.edit()
        overflow.forEach { (storageKey, _) -> editor.remove(storageKey) }
        editor.commit()
    }

    private fun parsePersistedFolder(rawValue: String?): PersistedFolderEntry? {
        if (rawValue.isNullOrBlank()) return null
        return runCatching {
            gson.fromJson(rawValue, PersistedFolderEntry::class.java)
        }.getOrNull()
    }

    private fun storageKey(accountKey: String, normalizedPath: String): String {
        return STORAGE_KEY_PREFIX + sha256("$accountKey\n$normalizedPath")
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
