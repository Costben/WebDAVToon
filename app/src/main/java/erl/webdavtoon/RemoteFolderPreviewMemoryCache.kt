// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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

    private data class DirectMediaKey(
        val accountKey: String,
        val path: String
    )

    private data class PersistedFolderEntry(
        @SerializedName("accountHash") val accountHash: String? = null,
        @SerializedName("path") val path: String? = null,
        @SerializedName("hasSubFolders") val hasSubFolders: Boolean = false,
        @SerializedName("previewUriStringsBySortOrder") val previewUriStringsBySortOrder: Map<String, List<String>>? = null,
        @SerializedName("emptyDirectMediaCheckedAtMs") val emptyDirectMediaCheckedAtMs: Long = 0L,
        @SerializedName("updatedAtMs") val updatedAtMs: Long = 0L
    )

    private const val LEGACY_PREFS_NAME = "remote_folder_preview_cache_v1"
    private const val PREFS_NAME = "remote_folder_preview_cache_v2"
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

    private val emptyDirectMediaEntries = object : LinkedHashMap<DirectMediaKey, Long>(MAX_MEMORY_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DirectMediaKey, Long>?): Boolean {
            return size > MAX_MEMORY_ENTRIES
        }
    }

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        runCatching {
            val legacy = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            if (legacy.all.isNotEmpty()) {
                legacy.edit().clear().apply()
            }
        }
    }

    @Synchronized
    fun get(accountKey: String, sortOrder: Int, path: String): Entry? {
        val key = Key(accountKey, sortOrder, normalizePath(path))
        entries[key]?.let { return it }

        val persisted = runCatching { readPersistedFolder(accountKey, key.path) }.getOrNull() ?: return null
        persisted.previewUriStringsBySortOrder?.forEach { (persistedSortOrder, previewUriStrings) ->
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
            previewUriStringsBySortOrder = mergedPreviews,
            emptyDirectMediaCheckedAtMs = persisted?.emptyDirectMediaCheckedAtMs ?: 0L
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
            },
            emptyDirectMediaCheckedAtMs = readPersistedFolder(accountKey, normalizedPath)?.emptyDirectMediaCheckedAtMs ?: 0L
        )
    }

    @Synchronized
    fun hasKnownEmptyDirectMedia(accountKey: String, path: String): Boolean {
        val normalizedPath = normalizePath(path)
        val key = DirectMediaKey(accountKey, normalizedPath)
        if (emptyDirectMediaEntries.containsKey(key)) return true

        val persisted = readPersistedFolder(accountKey, normalizedPath) ?: return false
        if (persisted.emptyDirectMediaCheckedAtMs <= 0L) return false

        emptyDirectMediaEntries[key] = persisted.emptyDirectMediaCheckedAtMs
        Log.i("RemoteFolderPreviewCache", "emptyDirectMediaHit path=$normalizedPath")
        return true
    }

    @Synchronized
    fun recordDirectMediaResult(accountKey: String, path: String, isEmpty: Boolean) {
        val normalizedPath = normalizePath(path)
        val key = DirectMediaKey(accountKey, normalizedPath)
        if (isEmpty) {
            emptyDirectMediaEntries[key] = System.currentTimeMillis()
        } else {
            emptyDirectMediaEntries.remove(key)
        }

        val persisted = readPersistedFolder(accountKey, normalizedPath)
        writePersistedFolder(
            accountKey = accountKey,
            normalizedPath = normalizedPath,
            hasSubFolders = persisted?.hasSubFolders ?: false,
            previewUriStringsBySortOrder = persisted?.previewUriStringsBySortOrder.orEmpty(),
            emptyDirectMediaCheckedAtMs = if (isEmpty) System.currentTimeMillis() else 0L
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
        emptyDirectMediaEntries.keys
            .filter { key ->
                key.accountKey == accountKey &&
                    (normalizedRoot == "/" || key.path == normalizedRoot || key.path.startsWith(normalizedRoot))
            }
            .forEach(emptyDirectMediaEntries::remove)

        val accountHash = sha256(accountKey)
        val prefs = preferences ?: return
        val editor = prefs.edit()
        prefs.all.forEach { (storageKey, rawValue) ->
            val persisted = parsePersistedFolder(rawValue as? String)
            if (persisted == null) {
                editor.remove(storageKey)
                return@forEach
            }
            val path = persisted.path
            if (
                persisted.accountHash == accountHash &&
                (normalizedRoot == "/" || path == normalizedRoot || (path != null && path.startsWith(normalizedRoot)))
            ) {
                editor.remove(storageKey)
            }
        }
        editor.commit()
    }

    @Synchronized
    fun clearForTests() {
        entries.clear()
        emptyDirectMediaEntries.clear()
    }

    internal fun normalizePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed == "/") return "/"
        return "${trimmed.trim('/')}/"
    }

    private fun readPersistedFolder(accountKey: String, normalizedPath: String): PersistedFolderEntry? {
        val prefs = preferences ?: return null
        val storageKey = storageKey(accountKey, normalizedPath)
        val raw = runCatching { prefs.getString(storageKey, null) }.getOrNull() ?: return null
        val persisted = parsePersistedFolder(raw)
        if (persisted == null) {
            runCatching { prefs.edit().remove(storageKey).apply() }
            return null
        }
        val expectedHash = sha256(accountKey)
        return persisted.takeIf {
            it.accountHash == expectedHash && it.path == normalizedPath
        }
    }

    private fun writePersistedFolder(
        accountKey: String,
        normalizedPath: String,
        hasSubFolders: Boolean,
        previewUriStringsBySortOrder: Map<String, List<String>>,
        emptyDirectMediaCheckedAtMs: Long = 0L
    ) {
        val prefs = preferences ?: return
        val persisted = PersistedFolderEntry(
            accountHash = sha256(accountKey),
            path = normalizedPath,
            hasSubFolders = hasSubFolders,
            previewUriStringsBySortOrder = previewUriStringsBySortOrder,
            emptyDirectMediaCheckedAtMs = emptyDirectMediaCheckedAtMs,
            updatedAtMs = System.currentTimeMillis()
        )
        runCatching {
            prefs.edit()
                .putString(storageKey(accountKey, normalizedPath), gson.toJson(persisted))
                .commit()
            trimPersistentCacheIfNeeded(prefs)
        }
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
            val entry = gson.fromJson(rawValue, PersistedFolderEntry::class.java) ?: return null
            if (entry.accountHash.isNullOrBlank() || entry.path.isNullOrBlank()) {
                null
            } else {
                entry
            }
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
