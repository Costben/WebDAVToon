package erl.webdavtoon

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FavoritePhotoStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized = false

    @Volatile
    private var favorites: List<FavoritePhotoEntity> = emptyList()

    init {
        ensureInitialized()
    }

    fun isFavorite(photoId: String): Boolean {
        ensureInitialized()
        return favorites.any { it.id == photoId }
    }

    fun add(photo: Photo) {
        ensureInitialized()
        val entity = photo.toFavoriteEntity()
        favorites = favorites.filterNot { it.id == entity.id } + entity
        scope.launch {
            AppDatabase.getInstance(appContext).favoritePhotoDao().insert(entity)
        }
    }

    fun remove(photoId: String) {
        ensureInitialized()
        favorites = favorites.filterNot { it.id == photoId }
        scope.launch {
            AppDatabase.getInstance(appContext).favoritePhotoDao().deleteById(photoId)
        }
    }

    fun getAll(): List<Photo> {
        ensureInitialized()
        return favorites.map { it.toPhoto() }
    }

    fun refresh() {
        favorites = runBlocking(Dispatchers.IO) {
            AppDatabase.getInstance(appContext).favoritePhotoDao().getAll()
        }
        initialized = true
    }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            refresh()
        }
    }

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

    private fun FavoritePhotoEntity.toPhoto(): Photo {
        val uri = Uri.parse(imageUri)
        val mediaType = detectMediaTypeByName(title)
            ?: detectMediaTypeByUri(uri)
            ?: MediaType.IMAGE

        return Photo(
            id = id,
            imageUri = uri,
            title = title,
            width = width,
            height = height,
            isLocal = isLocal,
            dateModified = dateModified,
            size = size,
            folderPath = folderPath,
            mediaType = mediaType
        )
    }

    companion object {
        @Volatile
        private var instance: FavoritePhotoStore? = null

        fun getInstance(context: Context): FavoritePhotoStore {
            return instance ?: synchronized(this) {
                instance ?: FavoritePhotoStore(context).also { instance = it }
            }
        }
    }
}
