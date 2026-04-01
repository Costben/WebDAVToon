package erl.webdavtoon

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object MigrationJson {
    private val gson = Gson()

    fun encodeSlots(slots: Map<String, Any>): String = gson.toJson(slots)

    fun decodeFavoritePhotos(json: String): Map<String, FavoritePhotoEntity> {
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val legacy: Map<String, String> = gson.fromJson(json, type)
            legacy.mapNotNull { (id, value) ->
                try {
                    val photo = gson.fromJson(value, Photo::class.java)
                    if (photo != null) {
                        id to FavoritePhotoEntity(
                            id = photo.id,
                            imageUri = photo.imageUri.toString(),
                            title = photo.title,
                            width = photo.width,
                            height = photo.height,
                            isLocal = photo.isLocal,
                            dateModified = photo.dateModified,
                            size = photo.size,
                            folderPath = photo.folderPath
                        )
                    } else null
                } catch (_: Exception) {
                    null
                }
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
