package erl.webdavtoon

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FavoriteFolderStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    @Volatile
    private var initialized = false

    @Volatile
    private var favorites: List<FavoriteFolderEntity> = emptyList()

    init {
        ensureInitialized()
    }

    fun isFavorite(folder: Folder): Boolean {
        ensureInitialized()
        return favorites.any { entity ->
            entity.matches(folder) && entity.isPrivate == PrivacyModeState.isPrivacyMode
        }
    }

    fun add(folder: Folder) {
        ensureInitialized()
        val entity = folder.toFavoriteEntity().copy(isPrivate = PrivacyModeState.isPrivacyMode)
        favorites = favorites.filterNot { it.sameIdentity(entity) } + entity
        scope.launch {
            AppDatabase.getInstance(appContext).favoriteFolderDao().insert(entity)
        }
    }

    fun remove(folder: Folder) {
        ensureInitialized()
        val isPrivate = PrivacyModeState.isPrivacyMode
        favorites = favorites.filterNot { entity ->
            entity.matches(folder) && entity.isPrivate == isPrivate
        }
        scope.launch {
            AppDatabase.getInstance(appContext).favoriteFolderDao().deleteByIdentity(
                path = folder.path,
                isLocal = folder.isLocal,
                sourceSlot = folder.sourceSlot,
                isPrivate = isPrivate
            )
        }
    }

    fun getAll(): List<Folder> {
        ensureInitialized()
        return favorites
            .filter { it.isPrivate == PrivacyModeState.isPrivacyMode }
            .map { it.toFolder() }
    }

    fun refresh() {
        favorites = runBlocking(Dispatchers.IO) {
            AppDatabase.getInstance(appContext).favoriteFolderDao().getAll()
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

    private fun Folder.toFavoriteEntity(): FavoriteFolderEntity = FavoriteFolderEntity(
        path = path,
        name = name,
        isLocal = isLocal,
        previewUrisJson = gson.toJson(previewUris.map(Uri::toString)),
        hasSubFolders = hasSubFolders,
        dateModified = dateModified,
        sourceSlot = sourceSlot
    )

    private fun FavoriteFolderEntity.toFolder(): Folder {
        val previewUris = runCatching {
            gson.fromJson(previewUrisJson, Array<String>::class.java)
                .orEmpty()
                .map(Uri::parse)
        }.getOrDefault(emptyList())

        return Folder(
            path = path,
            name = name,
            isLocal = isLocal,
            previewUris = previewUris,
            hasSubFolders = hasSubFolders,
            dateModified = dateModified,
            sourceSlot = sourceSlot
        )
    }

    private fun FavoriteFolderEntity.matches(folder: Folder): Boolean {
        return path == folder.path &&
            isLocal == folder.isLocal &&
            sourceSlot == folder.sourceSlot
    }

    private fun FavoriteFolderEntity.sameIdentity(other: FavoriteFolderEntity): Boolean {
        return path == other.path &&
            isLocal == other.isLocal &&
            sourceSlot == other.sourceSlot &&
            isPrivate == other.isPrivate
    }

    companion object {
        @Volatile
        private var instance: FavoriteFolderStore? = null

        fun getInstance(context: Context): FavoriteFolderStore {
            return instance ?: synchronized(this) {
                instance ?: FavoriteFolderStore(context).also { instance = it }
            }
        }
    }
}
