package erl.webdavtoon

import android.content.Context
import android.util.Log

/**
 * 设置管理类
 */
class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("webdavtoon_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_LOG_LEVEL = "log_level"
        const val KEY_CURRENT_SLOT = "current_slot"
        
        // Slot-specific keys
        const val KEY_WEBDAV_ENABLED = "webdav_enabled"
        const val KEY_WEBDAV_PROTOCOL = "webdav_protocol"
        const val KEY_WEBDAV_URL = "webdav_url"
        const val KEY_WEBDAV_PORT = "webdav_port"
        const val KEY_WEBDAV_USERNAME = "webdav_username"
        const val KEY_WEBDAV_PASSWORD = "webdav_password"
        const val KEY_WEBDAV_REMEMBER_PASSWORD = "webdav_remember_password"
        const val KEY_WEBDAV_ALIAS = "webdav_alias"
        const val KEY_SORT_ORDER = "sort_order" // 0: A-Z, 1: Z-A, 2: Newest First
        
        // Favorite photos
        const val KEY_FAVORITE_PHOTOS = "favorite_photos"
    }

    // Global settings
    fun getGridColumns(): Int = prefs.getInt(KEY_GRID_COLUMNS, 2)
    fun setGridColumns(count: Int) = prefs.edit().putInt(KEY_GRID_COLUMNS, count).apply()

    fun getSortOrder(): Int = prefs.getInt(KEY_SORT_ORDER, 2)
    fun setSortOrder(order: Int) = prefs.edit().putInt(KEY_SORT_ORDER, order).apply()

    fun getLogLevel(): Int = prefs.getInt(KEY_LOG_LEVEL, Log.INFO)
    fun setLogLevel(level: Int) = prefs.edit().putInt(KEY_LOG_LEVEL, level).apply()

    fun getCurrentSlot(): Int = prefs.getInt(KEY_CURRENT_SLOT, 0)
    fun setCurrentSlot(slot: Int) = prefs.edit().putInt(KEY_CURRENT_SLOT, slot).apply()

    // Slot-specific settings
    private fun getSlotKey(key: String): String = "slot${getCurrentSlot()}_$key"
    private fun getSpecificSlotKey(slot: Int, key: String): String = "slot${slot}_$key"

    fun getWebDavAlias(slot: Int = getCurrentSlot()): String = prefs.getString(getSpecificSlotKey(slot, KEY_WEBDAV_ALIAS), "") ?: ""
    fun setWebDavAlias(alias: String, slot: Int = getCurrentSlot()) = prefs.edit().putString(getSpecificSlotKey(slot, KEY_WEBDAV_ALIAS), alias).apply()

    fun isWebDavEnabled(): Boolean = prefs.getBoolean(getSlotKey(KEY_WEBDAV_ENABLED), false)
    fun setWebDavEnabled(enabled: Boolean) = prefs.edit().putBoolean(getSlotKey(KEY_WEBDAV_ENABLED), enabled).apply()

    fun getWebDavProtocol(): String = prefs.getString(getSlotKey(KEY_WEBDAV_PROTOCOL), "https") ?: "https"
    fun setWebDavProtocol(protocol: String) = prefs.edit().putString(getSlotKey(KEY_WEBDAV_PROTOCOL), protocol).apply()

    fun getWebDavUrl(): String = prefs.getString(getSlotKey(KEY_WEBDAV_URL), "") ?: ""
    fun setWebDavUrl(url: String) = prefs.edit().putString(getSlotKey(KEY_WEBDAV_URL), url).apply()

    fun getWebDavPort(): Int = prefs.getInt(getSlotKey(KEY_WEBDAV_PORT), if (getWebDavProtocol() == "https") 443 else 80)
    fun setWebDavPort(port: Int) = prefs.edit().putInt(getSlotKey(KEY_WEBDAV_PORT), port).apply()

    fun getWebDavUsername(): String = prefs.getString(getSlotKey(KEY_WEBDAV_USERNAME), "") ?: ""
    fun setWebDavUsername(username: String) = prefs.edit().putString(getSlotKey(KEY_WEBDAV_USERNAME), username).apply()

    fun getWebDavPassword(): String = prefs.getString(getSlotKey(KEY_WEBDAV_PASSWORD), "") ?: ""
    fun setWebDavPassword(password: String) = prefs.edit().putString(getSlotKey(KEY_WEBDAV_PASSWORD), password).apply()

    fun isWebDavRememberPassword(): Boolean = prefs.getBoolean(getSlotKey(KEY_WEBDAV_REMEMBER_PASSWORD), true)
    fun setWebDavRememberPassword(remember: Boolean) = prefs.edit().putBoolean(getSlotKey(KEY_WEBDAV_REMEMBER_PASSWORD), remember).apply()

    /**
     * 构建完整的 WebDAV 基础 URL
     */
    fun getFullWebDavUrl(): String {
        val protocol = getWebDavProtocol()
        val rawUrl = getWebDavUrl().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val port = getWebDavPort()
        
        // 尝试解析 url 中的主机和路径
        val firstSlash = rawUrl.indexOf('/')
        var host = if (firstSlash != -1) rawUrl.substring(0, firstSlash) else rawUrl
        val path = if (firstSlash != -1) rawUrl.substring(firstSlash) else ""
        
        // 如果主机名中包含端口号，优先使用它
        val portColon = host.lastIndexOf(':')
        var finalPort = port
        if (portColon != -1) {
            val portStr = host.substring(portColon + 1)
            val p = portStr.toIntOrNull()
            if (p != null) {
                finalPort = p
                host = host.substring(0, portColon)
            }
        }
        
        val builder = okhttp3.HttpUrl.Builder()
            .scheme(protocol)
            .host(host)
            .port(finalPort)
        
        if (path.isNotEmpty()) {
            val segments = path.trim('/').split('/')
            for (segment in segments) {
                if (segment.isNotEmpty()) {
                    builder.addPathSegment(segment)
                }
            }
        }
        
        val finalUrl = builder.build().toString().trimEnd('/')
        android.util.Log.d("SettingsManager", "Built URL: $finalUrl")
        return finalUrl
    }
    
    /**
     * 收藏功能相关方法
     */
    fun isPhotoFavorite(photoId: String): Boolean {
        val favorites = getFavoritePhotos()
        return favorites.contains(photoId)
    }
    
    fun addFavoritePhoto(photoId: String) {
        val favorites = getFavoritePhotos().toMutableSet()
        favorites.add(photoId)
        saveFavoritePhotos(favorites)
    }
    
    fun removeFavoritePhoto(photoId: String) {
        val favorites = getFavoritePhotos().toMutableSet()
        favorites.remove(photoId)
        saveFavoritePhotos(favorites)
    }
    
    fun getFavoritePhotos(): Set<String> {
        val favoritesString = prefs.getString(KEY_FAVORITE_PHOTOS, "") ?: ""
        if (favoritesString.isEmpty()) {
            return emptySet()
        }
        return favoritesString.split(',').toSet()
    }
    
    private fun saveFavoritePhotos(favorites: Set<String>) {
        val favoritesString = favorites.joinToString(",")
        prefs.edit().putString(KEY_FAVORITE_PHOTOS, favoritesString).apply()
    }
}
