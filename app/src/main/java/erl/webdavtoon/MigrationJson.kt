package erl.webdavtoon

import com.google.gson.Gson
import com.google.gson.JsonParser

object MigrationJson {
    private val gson = Gson()

    fun encodeSlots(slots: Map<String, Any>): String = gson.toJson(slots)

    fun decodeFavoritePhotos(json: String): Map<String, FavoritePhotoEntity> {
        return try {
            val root = JsonParser.parseString(json)
            check(root.isJsonObject) { "Favorite photos JSON must be an object" }
            root.asJsonObject.entrySet().mapNotNull { (id, value) ->
                try {
                    val photo = when {
                        value.isJsonNull -> null
                        value.isJsonPrimitive && value.asJsonPrimitive.isString ->
                            gson.fromJson(value.asString, Photo::class.java)
                        else -> gson.fromJson(value, Photo::class.java)
                    }
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
