package erl.webdavtoon

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "webdavtoon_settings"
)

class AppSettingsStore(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val PHOTO_GRID_COLUMNS = intPreferencesKey("photo_grid_columns")
        val SORT_ORDER = intPreferencesKey("sort_order")
        val PHOTO_SORT_ORDER = intPreferencesKey("photo_sort_order")
        val LOG_LEVEL = intPreferencesKey("log_level")
        val CURRENT_SLOT = intPreferencesKey("current_slot")
        val WEBDAV_SLOTS_JSON = stringPreferencesKey("webdav_slots_json")
        val THEME_ID = intPreferencesKey("theme_id")
        val LANGUAGE = stringPreferencesKey("language")
        val WATERFALL_QUALITY_MODE = stringPreferencesKey("waterfall_quality_mode")
        val WATERFALL_PERCENT = intPreferencesKey("waterfall_percent")
        val WATERFALL_MAX_WIDTH = intPreferencesKey("waterfall_max_width")
        val READER_MAX_ZOOM_PERCENT = intPreferencesKey("reader_max_zoom_percent")
        val VIDEO_EXTERNAL_PLAYER_MODE = stringPreferencesKey("video_external_player_mode")
        val ROTATION_LOCKED = booleanPreferencesKey("rotation_locked")
        val MIGRATED_TO_DATASTORE = booleanPreferencesKey("migrated_to_datastore")
        val WEBDAV_SECRET_MIGRATED = booleanPreferencesKey("webdav_secret_migrated")

        private val initLock = Any()
        private val cache = ConcurrentHashMap<String, Any>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var initialized = false

        fun prime(context: Context) {
            ensureInitialized(context.applicationContext)
        }

        private fun ensureInitialized(context: Context) {
            if (initialized) return
            synchronized(initLock) {
                if (initialized) return

                val snapshot = runBlocking {
                    context.dataStore.data.first()
                }
                updateCache(snapshot)

                scope.launch {
                    context.dataStore.data.collect { preferences ->
                        updateCache(preferences)
                    }
                }

                initialized = true
            }
        }

        private fun updateCache(preferences: Preferences) {
            cache.clear()
            preferences.asMap().forEach { (key, value) ->
                cache[key.name] = value
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> readCached(key: Preferences.Key<T>, defaultValue: T): T {
            return cache[key.name] as? T ?: defaultValue
        }

        private fun <T> writeCached(key: Preferences.Key<T>, value: T) {
            cache[key.name] = value as Any
        }

        private fun <T> removeCached(key: Preferences.Key<T>) {
            cache.remove(key.name)
        }
    }

    init {
        ensureInitialized(appContext)
    }

    fun getOrDefaultInt(key: Preferences.Key<Int>, defaultValue: Int): Int {
        return readCached(key, defaultValue)
    }

    fun putInt(key: Preferences.Key<Int>, value: Int) {
        writeCached(key, value)
        scope.launch {
            appContext.dataStore.edit { it[key] = value }
        }
    }

    suspend fun putIntSync(key: Preferences.Key<Int>, value: Int) {
        writeCached(key, value)
        appContext.dataStore.edit { it[key] = value }
    }

    fun getOrDefaultString(key: Preferences.Key<String>, defaultValue: String): String {
        return readCached(key, defaultValue)
    }

    fun putString(key: Preferences.Key<String>, value: String) {
        writeCached(key, value)
        scope.launch {
            appContext.dataStore.edit { it[key] = value }
        }
    }

    suspend fun putStringSync(key: Preferences.Key<String>, value: String) {
        writeCached(key, value)
        appContext.dataStore.edit { it[key] = value }
    }

    fun getOrDefaultBoolean(key: Preferences.Key<Boolean>, defaultValue: Boolean): Boolean {
        return readCached(key, defaultValue)
    }

    fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        writeCached(key, value)
        scope.launch {
            appContext.dataStore.edit { it[key] = value }
        }
    }

    suspend fun putBooleanSync(key: Preferences.Key<Boolean>, value: Boolean) {
        writeCached(key, value)
        appContext.dataStore.edit { it[key] = value }
    }

    suspend fun editSync(transform: MutablePreferences.() -> Unit) {
        appContext.dataStore.edit { preferences ->
            preferences.transform()
            updateCache(preferences)
        }
    }

    fun <T> remove(key: Preferences.Key<T>) {
        removeCached(key)
        scope.launch {
            appContext.dataStore.edit { it.remove(key) }
        }
    }

    suspend fun <T> removeSync(key: Preferences.Key<T>) {
        removeCached(key)
        appContext.dataStore.edit { it.remove(key) }
    }

    fun isMigrated(): Boolean {
        return getOrDefaultBoolean(MIGRATED_TO_DATASTORE, false)
    }

    suspend fun markMigrated() {
        putBooleanSync(MIGRATED_TO_DATASTORE, true)
    }

    fun isWebDavSecretMigrated(): Boolean {
        return getOrDefaultBoolean(WEBDAV_SECRET_MIGRATED, false)
    }

    suspend fun markWebDavSecretMigrated() {
        putBooleanSync(WEBDAV_SECRET_MIGRATED, true)
    }

    fun observeInt(key: Preferences.Key<Int>, defaultValue: Int): Flow<Int> {
        return appContext.dataStore.data.map { it[key] ?: defaultValue }
    }

    fun observeString(key: Preferences.Key<String>, defaultValue: String): Flow<String> {
        return appContext.dataStore.data.map { it[key] ?: defaultValue }
    }

    fun observeBoolean(key: Preferences.Key<Boolean>, defaultValue: Boolean): Flow<Boolean> {
        return appContext.dataStore.data.map { it[key] ?: defaultValue }
    }
}
