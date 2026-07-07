package erl.webdavtoon

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FolderNavigationResolver {

    sealed class Target {
        data class FolderGrid(val path: String, val isWebDav: Boolean) : Target()
        data class MixedWaterfall(val path: String, val isWebDav: Boolean) : Target()
        data class MediaWaterfall(val path: String, val isWebDav: Boolean) : Target()
    }

    suspend fun resolve(
        context: Context,
        settingsManager: SettingsManager,
        folderPath: String,
        isWebDav: Boolean,
        forceRefresh: Boolean = false
    ): Target = withContext(Dispatchers.IO) {
        val repository: PhotoRepository = if (isWebDav) {
            RustWebDavPhotoRepository(settingsManager)
        } else {
            LocalPhotoRepository(context)
        }

        val folders = repository.getFolders(folderPath, forceRefresh)
            .asSequence()
            .filterNot { it.path.startsWith("virtual://internal_photos") }
            .filterNot { folder ->
                !folder.isLocal && (
                    folder.name.startsWith(".") ||
                        folder.path.trim('/').split('/').any { it.startsWith(".") }
                    )
            }
            .toMutableList()

        val directMedia = repository.getPhotos(
            folderPath = folderPath,
            recursive = false,
            forceRefresh = forceRefresh
        )

        if (isWebDav && folders.isEmpty()) {
            val recursiveMedia = repository.getPhotos(
                folderPath = folderPath,
                recursive = true,
                forceRefresh = forceRefresh
            )
            folders.addAll(
                RemoteFolderSynthesizer.synthesizeFromRecursivePhotos(
                    currentFolderPath = folderPath,
                    photos = recursiveMedia,
                    endpoint = settingsManager.getFullWebDavUrl(),
                    sortOrder = settingsManager.getSortOrder()
                )
            )
        }

        val realChildFolders = folders.filterNot { it.path.startsWith("virtual://internal_photos") }
        when {
            realChildFolders.isNotEmpty() && directMedia.isNotEmpty() -> Target.MixedWaterfall(folderPath, isWebDav)
            realChildFolders.isNotEmpty() -> Target.FolderGrid(folderPath, isWebDav)
            else -> Target.MediaWaterfall(folderPath, isWebDav)
        }
    }

    fun start(activity: Activity, target: Target) {
        activity.startActivity(createIntent(activity, target))
    }

    fun createIntent(context: Context, target: Target): Intent {
        return when (target) {
            is Target.FolderGrid -> Intent(context, SubFolderActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", target.path)
                putExtra("EXTRA_IS_WEBDAV", target.isWebDav)
            }

            is Target.MixedWaterfall -> Intent(context, MixedFolderActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", target.path)
                putExtra("EXTRA_IS_WEBDAV", target.isWebDav)
            }

            is Target.MediaWaterfall -> Intent(context, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", target.path)
                putExtra("EXTRA_IS_WEBDAV", target.isWebDav)
                putExtra("EXTRA_RECURSIVE", false)
            }
        }
    }
}
