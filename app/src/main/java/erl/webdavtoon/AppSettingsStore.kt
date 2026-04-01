package erl.webdavtoon

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull

private val Context.dataStore: DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(
    name = "webdavtoon_settings"
)

class AppSettingsStore(private val context: Context) {

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
        val ROTATION_LOCKED = booleanPreferencesKey("rotation_locked")
        val MIGRATED_TO_DATASTORE = booleanPreferencesKey("migrated_to_datastore")
    }

    suspend fun getOrDefaultInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, defaultValue: Int): Int {
        return context.dataStore.data.map { it[key] ?: defaultValue }.first()
    }

    suspend fun putInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun getOrDefaultString(key: androidx.datastore.preferences.core.Preferences.Key<String>, defaultValue: String): String {
        return context.dataStore.data.map { it[key] ?: defaultValue }.first()
    }

    suspend fun putString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun getOrDefaultBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, defaultValue: Boolean): Boolean {
        return context.dataStore.data.map { it[key] ?: defaultValue }.first()
    }

    suspend fun putBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun <T> remove(key: androidx.datastore.preferences.core.Preferences.Key<T>) {
        context.dataStore.edit { it.remove(key) }
    }

    suspend fun isMigrated(): Boolean {
        return context.dataStore.data.map { it[MIGRATED_TO_DATASTORE] ?: false }.first()
    }

    suspend fun markMigrated() {
        context.dataStore.edit { it[MIGRATED_TO_DATASTORE] = true }
    }

    fun observeInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, defaultValue: Int): Flow<Int> {
        return context.dataStore.data.map { it[key] ?: defaultValue }
    }

    fun observeString(key: androidx.datastore.preferences.core.Preferences.Key<String>, defaultValue: String): Flow<String> {
        return context.dataStore.data.map { it[key] ?: defaultValue }
    }

    fun observeBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, defaultValue: Boolean): Flow<Boolean> {
        return context.dataStore.data.map { it[key] ?: defaultValue }
    }
}
