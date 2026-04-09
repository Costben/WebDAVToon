package erl.webdavtoon

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsManager(context: Context) {
    private val appContext = context.applicationContext
    private val appSettings = AppSettingsStore(appContext)
    private val credentialPolicy = WebDavCredentialPolicy(
        persistentStore = AndroidKeystoreWebDavPasswordStore(appContext),
        sessionStore = WebDavSessionPasswordStore
    )
    private val favoritePhotoStore = FavoritePhotoStore.getInstance(appContext)

    companion object {
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_LOG_LEVEL = "log_level"
        const val KEY_CURRENT_SLOT = "current_slot"

        const val KEY_WEBDAV_ENABLED = "webdav_enabled"
        const val KEY_WEBDAV_PROTOCOL = "webdav_protocol"
        const val KEY_WEBDAV_URL = "webdav_url"
        const val KEY_WEBDAV_PORT = "webdav_port"
        const val KEY_WEBDAV_USERNAME = "webdav_username"
        const val KEY_WEBDAV_PASSWORD = "webdav_password"
        const val KEY_WEBDAV_REMEMBER_PASSWORD = "webdav_remember_password"
        const val KEY_WEBDAV_ALIAS = "webdav_alias"

        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_PHOTO_GRID_COLUMNS = "photo_grid_columns"
        const val KEY_PHOTO_SORT_ORDER = "photo_sort_order"
        const val KEY_FAVORITE_PHOTOS = "favorite_photos"
        const val KEY_THEME_ID = "theme_id"
        const val KEY_LANGUAGE = "language"

        const val KEY_WATERFALL_QUALITY_MODE = "waterfall_quality_mode"
        const val KEY_WATERFALL_PERCENT = "waterfall_percent"
        const val KEY_WATERFALL_MAX_WIDTH = "waterfall_max_width"
        const val KEY_READER_MAX_ZOOM_PERCENT = "reader_max_zoom_percent"
        const val KEY_ROTATION_LOCKED = "rotation_locked"

        const val WATERFALL_MODE_PERCENT = "percent"
        const val WATERFALL_MODE_MAX_WIDTH = "max_width"

        const val SORT_NAME_ASC = 0
        const val SORT_NAME_DESC = 1
        const val SORT_DATE_DESC = 2
        const val SORT_DATE_ASC = 3

        private val gson = Gson()
        private val slotCacheLock = Any()

        @Volatile
        private var cachedSlotsJson: String? = null

        @Volatile
        private var cachedSlotsMap: MutableMap<Int, WebDavSlotConfig> = mutableMapOf()
    }

    fun getGridColumns(): Int = appSettings.getOrDefaultInt(AppSettingsStore.GRID_COLUMNS, 2)
    fun setGridColumns(count: Int) = appSettings.putInt(AppSettingsStore.GRID_COLUMNS, count)

    fun getPhotoGridColumns(): Int = appSettings.getOrDefaultInt(AppSettingsStore.PHOTO_GRID_COLUMNS, 2)
    fun setPhotoGridColumns(count: Int) = appSettings.putInt(AppSettingsStore.PHOTO_GRID_COLUMNS, count)

    fun getSortOrder(): Int = appSettings.getOrDefaultInt(AppSettingsStore.SORT_ORDER, 2)
    fun setSortOrder(order: Int) = appSettings.putInt(AppSettingsStore.SORT_ORDER, order)

    fun getPhotoSortOrder(): Int = appSettings.getOrDefaultInt(AppSettingsStore.PHOTO_SORT_ORDER, 2)
    fun setPhotoSortOrder(order: Int) = appSettings.putInt(AppSettingsStore.PHOTO_SORT_ORDER, order)

    fun getLogLevel(): Int = appSettings.getOrDefaultInt(AppSettingsStore.LOG_LEVEL, Log.INFO)
    fun setLogLevel(level: Int) = appSettings.putInt(AppSettingsStore.LOG_LEVEL, level)

    fun getThemeId(): Int = appSettings.getOrDefaultInt(AppSettingsStore.THEME_ID, 0)
    fun setThemeId(id: Int) = appSettings.putInt(AppSettingsStore.THEME_ID, id)

    fun getLanguage(): String = appSettings.getOrDefaultString(AppSettingsStore.LANGUAGE, "default")
    fun setLanguage(lang: String) = appSettings.putString(AppSettingsStore.LANGUAGE, lang)

    fun getWaterfallQualityMode(): String =
        appSettings.getOrDefaultString(AppSettingsStore.WATERFALL_QUALITY_MODE, WATERFALL_MODE_PERCENT)

    fun setWaterfallQualityMode(mode: String) =
        appSettings.putString(AppSettingsStore.WATERFALL_QUALITY_MODE, mode)

    fun getWaterfallPercent(): Int = appSettings.getOrDefaultInt(AppSettingsStore.WATERFALL_PERCENT, 70)
    fun setWaterfallPercent(percent: Int) = appSettings.putInt(AppSettingsStore.WATERFALL_PERCENT, percent)

    fun getWaterfallMaxWidth(): Int = appSettings.getOrDefaultInt(AppSettingsStore.WATERFALL_MAX_WIDTH, 600)
    fun setWaterfallMaxWidth(maxWidth: Int) = appSettings.putInt(AppSettingsStore.WATERFALL_MAX_WIDTH, maxWidth)

    fun getReaderMaxZoomPercent(): Int =
        appSettings.getOrDefaultInt(AppSettingsStore.READER_MAX_ZOOM_PERCENT, 300)

    fun setReaderMaxZoomPercent(percent: Int) =
        appSettings.putInt(AppSettingsStore.READER_MAX_ZOOM_PERCENT, percent)

    fun isRotationLocked(): Boolean =
        appSettings.getOrDefaultBoolean(AppSettingsStore.ROTATION_LOCKED, false)

    fun setRotationLocked(locked: Boolean) =
        appSettings.putBoolean(AppSettingsStore.ROTATION_LOCKED, locked)

    fun getCurrentSlot(): Int = appSettings.getOrDefaultInt(AppSettingsStore.CURRENT_SLOT, 0)
    fun setCurrentSlot(slot: Int) = appSettings.putInt(AppSettingsStore.CURRENT_SLOT, slot)

    // Deprecated legacy switch: app runs in webdav-only mode.
    fun getServerType(): String = "webdav"
    fun setServerType(type: String) = Unit

    fun deleteSlot(slot: Int) {
        val slots = getWebDavSlots()
        if (slots.remove(slot) != null) {
            saveWebDavSlots(slots)
            credentialPolicy.deletePassword(slot)
        }

        if (getCurrentSlot() == slot) {
            val next = slots.keys.firstOrNull() ?: 0
            setCurrentSlot(next)
        }
    }

    fun getAllSlots(): List<Int> {
        val slots = getWebDavSlots().keys.sorted()
        return if (slots.isEmpty()) listOf(0) else slots
    }

    fun getWebDavUrl(slot: Int = getCurrentSlot()): String = getWebDavSlot(slot)?.url ?: ""
    fun setWebDavUrl(url: String, slot: Int = getCurrentSlot()) = upsertWebDavSlot(slot) { copy(url = url) }

    fun getWebDavProtocol(slot: Int = getCurrentSlot()): String = getWebDavSlot(slot)?.protocol ?: "https"
    fun setWebDavProtocol(protocol: String, slot: Int = getCurrentSlot()) =
        upsertWebDavSlot(slot) { copy(protocol = protocol) }

    fun getWebDavPort(slot: Int = getCurrentSlot()): Int =
        getWebDavSlot(slot)?.port ?: if (getWebDavProtocol(slot) == "https") 443 else 80

    fun setWebDavPort(port: Int, slot: Int = getCurrentSlot()) = upsertWebDavSlot(slot) { copy(port = port) }

    fun getWebDavUsername(slot: Int = getCurrentSlot()): String = getWebDavSlot(slot)?.username ?: ""
    fun setWebDavUsername(username: String, slot: Int = getCurrentSlot()) =
        upsertWebDavSlot(slot) { copy(username = username) }

    fun getWebDavPassword(slot: Int = getCurrentSlot()): String {
        val slotConfig = getWebDavSlot(slot)
        return credentialPolicy.resolvePassword(slot, slotConfig?.rememberPassword ?: true)
    }

    fun setWebDavPassword(password: String, slot: Int = getCurrentSlot()) {
        credentialPolicy.savePassword(slot, isWebDavRememberPassword(slot), password)
    }

    fun isWebDavRememberPassword(slot: Int = getCurrentSlot()): Boolean =
        getWebDavSlot(slot)?.rememberPassword ?: true

    fun setWebDavRememberPassword(remember: Boolean, slot: Int = getCurrentSlot()) {
        val currentPassword = getWebDavPassword(slot)
        upsertWebDavSlot(slot) { copy(rememberPassword = remember) }
        credentialPolicy.updateRememberPreference(slot, currentPassword, remember)
    }

    fun getWebDavAlias(slot: Int = getCurrentSlot()): String = getWebDavSlot(slot)?.alias ?: ""
    fun setWebDavAlias(alias: String, slot: Int = getCurrentSlot()) = upsertWebDavSlot(slot) { copy(alias = alias) }

    fun isWebDavEnabled(slot: Int = getCurrentSlot()): Boolean = getWebDavSlot(slot)?.enabled ?: false
    fun setWebDavEnabled(enabled: Boolean, slot: Int = getCurrentSlot()) =
        upsertWebDavSlot(slot) { copy(enabled = enabled) }

    fun getFullWebDavUrl(slot: Int = getCurrentSlot()): String {
        val fullUrl = WebDavEndpointNormalizer.normalize(
            protocol = getWebDavProtocol(slot),
            rawUrl = getWebDavUrl(slot),
            port = getWebDavPort(slot)
        )
        LogManager.log("Built URL: $fullUrl", Log.DEBUG, "SettingsManager")
        return fullUrl
    }

    fun isPhotoFavorite(photoId: String): Boolean = favoritePhotoStore.isFavorite(photoId)

    fun addFavoritePhoto(photo: Photo) {
        favoritePhotoStore.add(photo)
        removeFavoritePhotoFromLegacyPrefs(photo.id)
    }

    fun removeFavoritePhoto(photoId: String) {
        favoritePhotoStore.remove(photoId)
        removeFavoritePhotoFromLegacyPrefs(photoId)
    }

    fun getFavoritePhotos(): List<Photo> = favoritePhotoStore.getAll()

    private fun getWebDavSlots(): MutableMap<Int, WebDavSlotConfig> {
        val json = appSettings.getOrDefaultString(AppSettingsStore.WEBDAV_SLOTS_JSON, "")
        if (json.isBlank()) {
            cachedSlotsJson = ""
            cachedSlotsMap = mutableMapOf()
            return mutableMapOf()
        }

        if (cachedSlotsJson == json) {
            return cachedSlotsMap.toMutableMap()
        }

        synchronized(slotCacheLock) {
            if (cachedSlotsJson == json) {
                return cachedSlotsMap.toMutableMap()
            }

            return try {
                val type = object : TypeToken<Map<String, WebDavSlotConfig>>() {}.type
                val result = gson.fromJson<Map<String, WebDavSlotConfig>>(json, type).orEmpty()
                val parsed = result.mapNotNull { (key, value) ->
                    key.toIntOrNull()?.let { it to value }
                }.toMap().toMutableMap()

                cachedSlotsJson = json
                cachedSlotsMap = parsed
                parsed.toMutableMap()
            } catch (e: Exception) {
                Log.e("SettingsManager", "Error parsing WebDAV slots: $json", e)
                cachedSlotsJson = json
                cachedSlotsMap = mutableMapOf()
                mutableMapOf()
            }
        }
    }

    private fun saveWebDavSlots(slots: Map<Int, WebDavSlotConfig>) {
        val asStringMap = slots.mapKeys { it.key.toString() }
        val json = gson.toJson(asStringMap)
        synchronized(slotCacheLock) {
            cachedSlotsJson = json
            cachedSlotsMap = slots.toMutableMap()
        }
        appSettings.putString(AppSettingsStore.WEBDAV_SLOTS_JSON, json)
    }

    private fun getWebDavSlot(slot: Int): WebDavSlotConfig? {
        return getWebDavSlots()[slot]
    }

    private fun upsertWebDavSlot(slot: Int, transform: WebDavSlotConfig.() -> WebDavSlotConfig) {
        val slots = getWebDavSlots()
        val current = slots[slot] ?: WebDavSlotConfig()
        slots[slot] = current.transform()
        saveWebDavSlots(slots)
    }

    private fun removeFavoritePhotoFromLegacyPrefs(photoId: String) = Unit
}
