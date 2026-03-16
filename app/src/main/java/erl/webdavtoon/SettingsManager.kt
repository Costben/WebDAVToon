package erl.webdavtoon

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("webdavtoon_prefs", Context.MODE_PRIVATE)
    
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

    private fun getSlotKey(key: String): String = "slot${getCurrentSlot()}_$key"
    private fun getSpecificSlotKey(slot: Int, key: String): String = "slot${slot}_$key"

    fun getGridColumns(): Int = prefs.getInt(KEY_GRID_COLUMNS, 2)
    fun setGridColumns(count: Int) = prefs.edit().putInt(KEY_GRID_COLUMNS, count).apply()

    fun getPhotoGridColumns(): Int = prefs.getInt(KEY_PHOTO_GRID_COLUMNS, 2)
    fun setPhotoGridColumns(count: Int) = prefs.edit().putInt(KEY_PHOTO_GRID_COLUMNS, count).apply()

    fun getSortOrder(): Int = prefs.getInt(KEY_SORT_ORDER, 2)
    fun setSortOrder(order: Int) = prefs.edit().putInt(KEY_SORT_ORDER, order).apply()

    fun getPhotoSortOrder(): Int = prefs.getInt(KEY_PHOTO_SORT_ORDER, 2)
    fun setPhotoSortOrder(order: Int) = prefs.edit().putInt(KEY_PHOTO_SORT_ORDER, order).apply()

    fun getLogLevel(): Int = prefs.getInt(KEY_LOG_LEVEL, Log.INFO)
    fun setLogLevel(level: Int) = prefs.edit().putInt(KEY_LOG_LEVEL, level).apply()

    fun getThemeId(): Int = prefs.getInt(KEY_THEME_ID, 0)
    fun setThemeId(id: Int) = prefs.edit().putInt(KEY_THEME_ID, id).apply()

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "default") ?: "default"
    fun setLanguage(lang: String) = prefs.edit().putString(KEY_LANGUAGE, lang).apply()

    fun getWaterfallQualityMode(): String = prefs.getString(KEY_WATERFALL_QUALITY_MODE, WATERFALL_MODE_PERCENT) ?: WATERFALL_MODE_PERCENT
    fun setWaterfallQualityMode(mode: String) = prefs.edit().putString(KEY_WATERFALL_QUALITY_MODE, mode).apply()

    fun getWaterfallPercent(): Int = prefs.getInt(KEY_WATERFALL_PERCENT, 70)
    fun setWaterfallPercent(percent: Int) = prefs.edit().putInt(KEY_WATERFALL_PERCENT, percent).apply()

    fun getWaterfallMaxWidth(): Int = prefs.getInt(KEY_WATERFALL_MAX_WIDTH, 600)
    fun setWaterfallMaxWidth(maxWidth: Int) = prefs.edit().putInt(KEY_WATERFALL_MAX_WIDTH, maxWidth).apply()

    fun getReaderMaxZoomPercent(): Int = prefs.getInt(KEY_READER_MAX_ZOOM_PERCENT, 300)
    fun setReaderMaxZoomPercent(percent: Int) = prefs.edit().putInt(KEY_READER_MAX_ZOOM_PERCENT, percent).apply()

    fun isRotationLocked(): Boolean = prefs.getBoolean(KEY_ROTATION_LOCKED, false)
    fun setRotationLocked(locked: Boolean) = prefs.edit().putBoolean(KEY_ROTATION_LOCKED, locked).apply()

    fun getCurrentSlot(): Int = prefs.getInt(KEY_CURRENT_SLOT, 0)
    fun setCurrentSlot(slot: Int) = prefs.edit().putInt(KEY_CURRENT_SLOT, slot).apply()

    // Deprecated legacy switch: app runs in webdav-only mode.
    fun getServerType(): String = "webdav"
    fun setServerType(type: String) {
        // no-op, kept for backward compatibility
    }

    fun deleteSlot(slot: Int) {
        val editor = prefs.edit()
        editor.remove(getSpecificSlotKey(slot, KEY_WEBDAV_ENABLED))
        editor.remove(getSpecificSlotKey(slot, KEY_WEBDAV_PROTOCOL))
        editor.remove(getSpecificSlotKey(slot, KEY_WEBDAV_URL))
        editor.remove(getSpecificSlotKey(slot, KEY_WEBDAV_PORT))
        editor.remove(getSpecificSlotKey(slot, KEY_WEBDAV_USERNAME))
        editor.remove(getSpecificSlotKey(slot, KEY_WEBDAV_PASSWORD))
        editor.remove(getSpecificSlotKey(slot, KEY_WEBDAV_REMEMBER_PASSWORD))
        editor.remove(getSpecificSlotKey(slot, KEY_WEBDAV_ALIAS))
        editor.apply()
        
        // If current slot is deleted, switch to another valid slot or 0
        if (getCurrentSlot() == slot) {
            val all = getAllSlots()
            val next = all.firstOrNull { it != slot } ?: 0
            setCurrentSlot(next)
        }
    }

    fun getAllSlots(): List<Int> {
        val slots = mutableListOf<Int>()
        // We scan slots 0 to 9 for simplicity, or we could store a list of active slots.
        // For now, let's assume if an alias or URL exists, the slot is valid.
        for (i in 0..9) {
            if (getWebDavUrl(i).isNotEmpty() || getWebDavAlias(i).isNotEmpty()) {
                slots.add(i)
            }
        }
        if (slots.isEmpty()) slots.add(0) // Ensure at least default slot
        return slots
    }

    fun getWebDavUrl(slot: Int = getCurrentSlot()): String = prefs.getString(getSpecificSlotKey(slot, KEY_WEBDAV_URL), "") ?: ""
    fun setWebDavUrl(url: String, slot: Int = getCurrentSlot()) = prefs.edit().putString(getSpecificSlotKey(slot, KEY_WEBDAV_URL), url).apply()

    fun getWebDavProtocol(slot: Int = getCurrentSlot()): String = prefs.getString(getSpecificSlotKey(slot, KEY_WEBDAV_PROTOCOL), "https") ?: "https"
    fun setWebDavProtocol(protocol: String, slot: Int = getCurrentSlot()) = prefs.edit().putString(getSpecificSlotKey(slot, KEY_WEBDAV_PROTOCOL), protocol).apply()

    fun getWebDavPort(slot: Int = getCurrentSlot()): Int = prefs.getInt(getSpecificSlotKey(slot, KEY_WEBDAV_PORT), if (getWebDavProtocol(slot) == "https") 443 else 80)
    fun setWebDavPort(port: Int, slot: Int = getCurrentSlot()) = prefs.edit().putInt(getSpecificSlotKey(slot, KEY_WEBDAV_PORT), port).apply()

    fun getWebDavUsername(slot: Int = getCurrentSlot()): String = prefs.getString(getSpecificSlotKey(slot, KEY_WEBDAV_USERNAME), "") ?: ""
    fun setWebDavUsername(username: String, slot: Int = getCurrentSlot()) = prefs.edit().putString(getSpecificSlotKey(slot, KEY_WEBDAV_USERNAME), username).apply()

    fun getWebDavPassword(slot: Int = getCurrentSlot()): String = prefs.getString(getSpecificSlotKey(slot, KEY_WEBDAV_PASSWORD), "") ?: ""
    fun setWebDavPassword(password: String, slot: Int = getCurrentSlot()) = prefs.edit().putString(getSpecificSlotKey(slot, KEY_WEBDAV_PASSWORD), password).apply()

    fun isWebDavRememberPassword(slot: Int = getCurrentSlot()): Boolean = prefs.getBoolean(getSpecificSlotKey(slot, KEY_WEBDAV_REMEMBER_PASSWORD), true)
    fun setWebDavRememberPassword(remember: Boolean, slot: Int = getCurrentSlot()) = prefs.edit().putBoolean(getSpecificSlotKey(slot, KEY_WEBDAV_REMEMBER_PASSWORD), remember).apply()

    fun getWebDavAlias(slot: Int = getCurrentSlot()): String =
        prefs.getString(getSpecificSlotKey(slot, KEY_WEBDAV_ALIAS), "") ?: ""

    fun setWebDavAlias(alias: String, slot: Int = getCurrentSlot()) =
        prefs.edit().putString(getSpecificSlotKey(slot, KEY_WEBDAV_ALIAS), alias).apply()

    fun isWebDavEnabled(slot: Int = getCurrentSlot()): Boolean = prefs.getBoolean(getSpecificSlotKey(slot, KEY_WEBDAV_ENABLED), false)
    fun setWebDavEnabled(enabled: Boolean, slot: Int = getCurrentSlot()) = prefs.edit().putBoolean(getSpecificSlotKey(slot, KEY_WEBDAV_ENABLED), enabled).apply()

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

        Log.d("SettingsManager", "Built URL: $fullUrl")
        return fullUrl
    }

    fun isPhotoFavorite(photoId: String): Boolean = getFavoritePhotosMap().containsKey(photoId)

    fun addFavoritePhoto(photo: Photo) {
        val favorites = getFavoritePhotosMap().toMutableMap()
        favorites[photo.id] = gson.toJson(photo)
        saveFavoritePhotosMap(favorites)
    }

    fun removeFavoritePhoto(photoId: String) {
        val favorites = getFavoritePhotosMap().toMutableMap()
        favorites.remove(photoId)
        saveFavoritePhotosMap(favorites)
    }

    fun getFavoritePhotos(): List<Photo> {
        val map = getFavoritePhotosMap()
        val photos = mutableListOf<Photo>()
        for (json in map.values) {
            try {
                val photo = gson.fromJson(json, Photo::class.java)
                if (photo != null) {
                    photos.add(photo)
                }
            } catch (e: Exception) {
                Log.e("SettingsManager", "Error parsing favorite photo: $json", e)
            }
        }
        return photos
    }

    private fun getFavoritePhotosMap(): Map<String, String> {
        val favoritesString = prefs.getString(KEY_FAVORITE_PHOTOS, "") ?: ""
        if (favoritesString.isEmpty()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val result: Map<String, String> = gson.fromJson(favoritesString, type)
            result
        } catch (e: Exception) {
            Log.e("SettingsManager", "Error parsing favorite photos map: $favoritesString", e)
            emptyMap()
        }
    }

    private fun saveFavoritePhotosMap(favorites: Map<String, String>) {
        prefs.edit().putString(KEY_FAVORITE_PHOTOS, gson.toJson(favorites)).apply()
    }
}
