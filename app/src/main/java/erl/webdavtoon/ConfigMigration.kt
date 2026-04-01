package erl.webdavtoon

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ConfigMigration {

    internal data class LegacySlot(
        val enabled: Boolean,
        val protocol: String,
        val url: String,
        val port: Int,
        val username: String,
        val password: String,
        val rememberPassword: Boolean,
        val alias: String
    )

    suspend fun migrateIfNeeded(context: Context) {
        val appSettings = AppSettingsStore(context)
        if (appSettings.isMigrated()) return

        val legacy = context.getSharedPreferences("webdavtoon_prefs", Context.MODE_PRIVATE)

        withContext(Dispatchers.IO) {
            // Simple settings
            legacy.getInt(SettingsManager.KEY_GRID_COLUMNS, 2).let { appSettings.putInt(AppSettingsStore.GRID_COLUMNS, it) }
            legacy.getInt(SettingsManager.KEY_PHOTO_GRID_COLUMNS, 2).let { appSettings.putInt(AppSettingsStore.PHOTO_GRID_COLUMNS, it) }
            legacy.getInt(SettingsManager.KEY_SORT_ORDER, 2).let { appSettings.putInt(AppSettingsStore.SORT_ORDER, it) }
            legacy.getInt(SettingsManager.KEY_PHOTO_SORT_ORDER, 2).let { appSettings.putInt(AppSettingsStore.PHOTO_SORT_ORDER, it) }
            legacy.getInt(SettingsManager.KEY_LOG_LEVEL, android.util.Log.INFO).let { appSettings.putInt(AppSettingsStore.LOG_LEVEL, it) }
            legacy.getInt(SettingsManager.KEY_THEME_ID, 0).let { appSettings.putInt(AppSettingsStore.THEME_ID, it) }
            legacy.getString(SettingsManager.KEY_LANGUAGE, "default")?.let { appSettings.putString(AppSettingsStore.LANGUAGE, it) }
            legacy.getString(SettingsManager.KEY_WATERFALL_QUALITY_MODE, SettingsManager.WATERFALL_MODE_PERCENT)?.let { appSettings.putString(AppSettingsStore.WATERFALL_QUALITY_MODE, it) }
            legacy.getInt(SettingsManager.KEY_WATERFALL_PERCENT, 70).let { appSettings.putInt(AppSettingsStore.WATERFALL_PERCENT, it) }
            legacy.getInt(SettingsManager.KEY_WATERFALL_MAX_WIDTH, 600).let { appSettings.putInt(AppSettingsStore.WATERFALL_MAX_WIDTH, it) }
            legacy.getInt(SettingsManager.KEY_READER_MAX_ZOOM_PERCENT, 300).let { appSettings.putInt(AppSettingsStore.READER_MAX_ZOOM_PERCENT, it) }
            legacy.getBoolean(SettingsManager.KEY_ROTATION_LOCKED, false).let { appSettings.putBoolean(AppSettingsStore.ROTATION_LOCKED, it) }
            legacy.getInt(SettingsManager.KEY_CURRENT_SLOT, 0).let { appSettings.putInt(AppSettingsStore.CURRENT_SLOT, it) }

            // WebDAV slots
            val slots = mutableMapOf<String, LegacySlot>()
            for (slot in 0..9) {
                val url = legacy.getString("slot${slot}_${SettingsManager.KEY_WEBDAV_URL}", "") ?: ""
                val alias = legacy.getString("slot${slot}_${SettingsManager.KEY_WEBDAV_ALIAS}", "") ?: ""
                if (url.isNotBlank() || alias.isNotBlank()) {
                    slots[slot.toString()] = LegacySlot(
                        enabled = legacy.getBoolean("slot${slot}_${SettingsManager.KEY_WEBDAV_ENABLED}", false),
                        protocol = legacy.getString("slot${slot}_${SettingsManager.KEY_WEBDAV_PROTOCOL}", "https") ?: "https",
                        url = url,
                        port = legacy.getInt("slot${slot}_${SettingsManager.KEY_WEBDAV_PORT}", 443),
                        username = legacy.getString("slot${slot}_${SettingsManager.KEY_WEBDAV_USERNAME}", "") ?: "",
                        password = legacy.getString("slot${slot}_${SettingsManager.KEY_WEBDAV_PASSWORD}", "") ?: "",
                        rememberPassword = legacy.getBoolean("slot${slot}_${SettingsManager.KEY_WEBDAV_REMEMBER_PASSWORD}", true),
                        alias = alias
                    )
                }
            }
            if (slots.isNotEmpty()) {
                appSettings.putString(AppSettingsStore.WEBDAV_SLOTS_JSON, MigrationJson.encodeSlots(slots))
            }

            // Favorites
            val favoritesString = legacy.getString(SettingsManager.KEY_FAVORITE_PHOTOS, "") ?: ""
            if (favoritesString.isNotBlank()) {
                val db = AppDatabase.getInstance(context)
                val legacyMap = MigrationJson.decodeFavoritePhotos(favoritesString)
                legacyMap.values.forEach { db.favoritePhotoDao().insert(it) }
            }

            // Mark migrated and keep legacy data for fallback, no destructive cleanup yet.
            appSettings.markMigrated()
            Log.i("ConfigMigration", "Legacy config migrated to DataStore/Room")
        }
    }

}
