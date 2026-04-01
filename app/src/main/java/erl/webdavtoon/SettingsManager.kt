package erl.webdavtoon

import android.content.Context
import android.util.Log
import android.net.Uri
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonDeserializationContext
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Type

class SettingsManager(private val context: Context) {
    private val appSettings = AppSettingsStore(context)
    private val gson = GsonBuilder()
        .registerTypeAdapter(Uri::class.java, UriAdapter())
        .create()

    private class UriAdapter : JsonSerializer<Uri>, JsonDeserializer<Uri> {
        override fun serialize(src: Uri, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.toString())
        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Uri {
            return Uri.parse(json.asString)
        }
    }

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
    }

    fun getGridColumns(): Int = runBlocking { appSettings.getOrDefaultInt(AppSettingsStore.GRID_COLUMNS, 2) }
    fun setGridColumns(count: Int) = runBlocking { appSettings.putInt(AppSettingsStore.GRID_COLUMNS, count) }

    fun getPhotoGridColumns(): Int = runBlocking { appSettings.getOrDefaultInt(AppSettingsStore.PHOTO_GRID_COLUMNS, 2) }
    fun setPhotoGridColumns(count: Int) = runBlocking { appSettings.putInt(AppSettingsStore.PHOTO_GRID_COLUMNS, count) }

    fun getSortOrder(): Int = runBlocking { appSettings.getOrDefaultInt(AppSettingsStore.SORT_ORDER, 2) }
    fun setSortOrder(order: Int) = runBlocking { appSettings.putInt(AppSettingsStore.SORT_ORDER, order) }

    fun getPhotoSortOrder(): Int = runBlocking { appSettings.getOrDefaultInt(AppSettingsStore.PHOTO_SORT_ORDER, 2) }
    fun setPhotoSortOrder(order: Int) = runBlocking { appSettings.putInt(AppSettingsStore.PHOTO_SORT_ORDER, order) }

    fun getLogLevel(): Int = runBlocking { appSettings.getOrDefaultInt(AppSettingsStore.LOG_LEVEL, Log.INFO) }
    fun setLogLevel(level: Int) = runBlocking { appSettings.putInt(AppSettingsStore.LOG_LEVEL, level) }

    fun getThemeId(): Int = runBlocking { appSettings.getOrDefaultInt(AppSettingsStore.THEME_ID, 0) }
    fun setThemeId(id: Int) = runBlocking { appSettings.putInt(AppSettingsStore.THEME_ID, id) }

    fun getLanguage(): String = runBlocking { appSettings.getOrDefaultString(AppSettingsStore.LANGUAGE, "default") }
    fun setLanguage(lang: String) = runBlocking { appSettings.putString(AppSettingsStore.LANGUAGE, lang) }

    fun getWaterfallQualityMode(): String = runBlocking { appSettings.getOrDefaultString(AppSettingsStore.WATERFALL_QUALITY_MODE, WATERFALL_MODE_PERCENT) }
    fun setWaterfallQualityMode(mode: String) = runBlocking { appSettings.putString(AppSettingsStore.WATERFALL_QUALITY_MODE, mode) }

    fun getWaterfallPercent(): Int = runBlocking { appSettings.getOrDefaultInt(AppSettingsStore.WATERFALL_PERCENT, 70) }
    fun setWaterfallPercent(percent: Int) = runBlocking { appSettings.putInt(AppSettingsStore.WATERFALL_PERCENT, percent) }

    fun getWaterfallMaxWidth(): Int = runBlocking { appSettings.getOrDefaultInt(AppSettingsStore.WATERFALL_MAX_WIDTH, 600) }
    fun setWaterfallMaxWidth(maxWidth: Int) = runBlocking { appSettings.putInt(AppSettingsStore.WATERFALL_MAX_WIDTH, maxWidth) }

    fun getReaderMaxZoomPercent(): Int = runBlocking { appSettings.getOrDefaultInt(AppSettingsStore.READER_MAX_ZOOM_PERCENT, 300) }
    fun setReaderMaxZoomPercent(percent: Int) = runBlocking { appSettings.putInt(AppSettingsStore.READER_MAX_ZOOM_PERCENT, percent) }

    fun isRotationLocked(): Boolean = runBlocking { appSettings.getOrDefaultBoolean(AppSettingsStore.ROTATION_LOCKED, false) }
    fun setRotationLocked(locked: Boolean) = runBlocking { appSettings.putBoolean(AppSettingsStore.ROTATION_LOCKED, locked) }

    fun getCurrentSlot(): Int = runBlocking { appSettings.getOrDefaultInt(AppSettingsStore.CURRENT_SLOT, 0) }
    fun setCurrentSlot(slot: Int) = runBlocking { appSettings.putInt(AppSettingsStore.CURRENT_SLOT, slot) }

    // Deprecated legacy switch: app runs in webdav-only mode.
    fun getServerType(): String = "webdav"
    fun setServerType(type: String) { }

    fun deleteSlot(slot: Int) = runBlocking {
        val slots = getWebDavSlots()
        if (slots.remove(slot) != null) {
            saveWebDavSlots(slots)
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
    fun setWebDavProtocol(protocol: String, slot: Int = getCurrentSlot()) = upsertWebDavSlot(slot) { copy(protocol = protocol) }

    fun getWebDavPort(slot: Int = getCurrentSlot()): Int = getWebDavSlot(slot)?.port ?: if (getWebDavProtocol(slot) == "https") 443 else 80
    fun setWebDavPort(port: Int, slot: Int = getCurrentSlot()) = upsertWebDavSlot(slot) { copy(port = port) }

    fun getWebDavUsername(slot: Int = getCurrentSlot()): String = getWebDavSlot(slot)?.username ?: ""
    fun setWebDavUsername(username: String, slot: Int = getCurrentSlot()) = upsertWebDavSlot(slot) { copy(username = username) }

    fun getWebDavPassword(slot: Int = getCurrentSlot()): String = getWebDavSlot(slot)?.password ?: ""
    fun setWebDavPassword(password: String, slot: Int = getCurrentSlot()) = upsertWebDavSlot(slot) { copy(password = password) }

    fun isWebDavRememberPassword(slot: Int = getCurrentSlot()): Boolean = getWebDavSlot(slot)?.rememberPassword ?: true
    fun setWebDavRememberPassword(remember: Boolean, slot: Int = getCurrentSlot()) = upsertWebDavSlot(slot) { copy(rememberPassword = remember) }

    fun getWebDavAlias(slot: Int = getCurrentSlot()): String = getWebDavSlot(slot)?.alias ?: ""

    fun setWebDavAlias(alias: String, slot: Int = getCurrentSlot()) = upsertWebDavSlot(slot) { copy(alias = alias) }

    fun isWebDavEnabled(slot: Int = getCurrentSlot()): Boolean = getWebDavSlot(slot)?.enabled ?: false
    fun setWebDavEnabled(enabled: Boolean, slot: Int = getCurrentSlot()) = upsertWebDavSlot(slot) { copy(enabled = enabled) }

    fun getFullWebDavUrl(slot: Int = getCurrentSlot()): String {
        val protocol = getWebDavProtocol(slot)
        var rawUrl = getWebDavUrl(slot).replace("http://", "").replace("https://", "")
        if (rawUrl.endsWith('/')) rawUrl = rawUrl.dropLast(1)

        val firstSlash = rawUrl.indexOf('/')
        val hostPart = if (firstSlash != -1) rawUrl.substring(0, firstSlash) else rawUrl
        val pathPart = if (firstSlash != -1) rawUrl.substring(firstSlash) else ""

        var host = hostPart
        var finalPort = getWebDavPort(slot)

        if (host.contains(':')) {
            val parts = host.split(':')
            if (parts.size == 2) {
                host = parts[0]
                parts[1].toIntOrNull()?.let { finalPort = it }
            }
        }

        val fullUrl = if (finalPort == 80 || finalPort == 443) {
            "$protocol://$host$pathPart"
        } else {
            "$protocol://$host:$finalPort$pathPart"
        }

        LogManager.log("Built URL: $fullUrl", Log.DEBUG, "SettingsManager")
        return fullUrl
    }

    fun isPhotoFavorite(photoId: String): Boolean = runBlocking {
        AppDatabase.getInstance(context).favoritePhotoDao().isFavorite(photoId)
    }

    fun addFavoritePhoto(photo: Photo) {
        runBlocking {
            val dao = AppDatabase.getInstance(context).favoritePhotoDao()
            dao.insert(photo.toFavoriteEntity())
            removeFavoritePhotoFromLegacyPrefs(photo.id)
        }
    }

    fun removeFavoritePhoto(photoId: String) {
        runBlocking {
            val dao = AppDatabase.getInstance(context).favoritePhotoDao()
            dao.deleteById(photoId)
            removeFavoritePhotoFromLegacyPrefs(photoId)
        }
    }

    fun getFavoritePhotos(): List<Photo> {
        return runBlocking {
            val dao = AppDatabase.getInstance(context).favoritePhotoDao()
            val favorites = dao.getAll()
            if (favorites.isEmpty()) {
                migrateFavoritesFromLegacyPrefs(dao)
                dao.getAll().map { it.toPhoto() }
            } else {
                favorites.map { it.toPhoto() }
            }
        }
    }

    private data class LegacyWebDavSlotConfig(
        val enabled: Boolean = false,
        val protocol: String = "https",
        val url: String = "",
        val port: Int = 443,
        val username: String = "",
        val password: String = "",
        val rememberPassword: Boolean = true,
        val alias: String = ""
    )

    private fun getWebDavSlots(): MutableMap<Int, LegacyWebDavSlotConfig> = runBlocking {
        val json = appSettings.getOrDefaultString(AppSettingsStore.WEBDAV_SLOTS_JSON, "")
        if (json.isBlank()) return@runBlocking mutableMapOf()
        return@runBlocking try {
            val type = object : TypeToken<Map<String, LegacyWebDavSlotConfig>>() {}.type
            @Suppress("UNCHECKED_CAST")
            val result = gson.fromJson<Map<String, LegacyWebDavSlotConfig>>(json, type)
            result.mapKeys { it.key.toInt() }.toMutableMap()
        } catch (e: Exception) {
            Log.e("SettingsManager", "Error parsing WebDAV slots: $json", e)
            mutableMapOf()
        }
    }

    private fun saveWebDavSlots(slots: Map<Int, LegacyWebDavSlotConfig>) = runBlocking {
        val asStringMap = slots.mapKeys { it.key.toString() }
        appSettings.putString(AppSettingsStore.WEBDAV_SLOTS_JSON, gson.toJson(asStringMap))
    }

    private fun getWebDavSlot(slot: Int): LegacyWebDavSlotConfig? {
        return getWebDavSlots()[slot]
    }

    private fun upsertWebDavSlot(slot: Int, transform: LegacyWebDavSlotConfig.() -> LegacyWebDavSlotConfig) = runBlocking {
        val slots = getWebDavSlots()
        val current = slots[slot] ?: LegacyWebDavSlotConfig()
        slots[slot] = current.transform()
        saveWebDavSlots(slots)
    }

    private suspend fun migrateFavoritesFromLegacyPrefs(dao: FavoritePhotoDao) { }

    private fun removeFavoritePhotoFromLegacyPrefs(photoId: String) { }

    private fun Photo.toFavoriteEntity(): FavoritePhotoEntity = FavoritePhotoEntity(
        id = id,
        imageUri = imageUri.toString(),
        title = title,
        width = width,
        height = height,
        isLocal = isLocal,
        dateModified = dateModified,
        size = size,
        folderPath = folderPath
    )

    private fun FavoritePhotoEntity.toPhoto(): Photo = Photo(
        id = id,
        imageUri = Uri.parse(imageUri),
        title = title,
        width = width,
        height = height,
        isLocal = isLocal,
        dateModified = dateModified,
        size = size,
        folderPath = folderPath
    )
}
