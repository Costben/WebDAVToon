package erl.webdavtoon

import android.net.Uri

object RemoteFolderSynthesizer {

    fun synthesizeFromRecursivePhotos(
        currentFolderPath: String,
        photos: List<Photo>,
        endpoint: String,
        sortOrder: Int
    ): List<Folder> {
        if (photos.isEmpty()) return emptyList()

        data class Aggregate(
            val childPath: String,
            val childName: String,
            val previewUris: MutableList<Uri> = mutableListOf(),
            var hasSubFolders: Boolean = false,
            var latestModified: Long = 0L
        )

        val normalizedCurrent = currentFolderPath.trim('/').let { trimmed ->
            if (trimmed.isEmpty()) "" else "$trimmed/"
        }
        val normalizedEndpoint = endpoint.trimEnd('/')
        val aggregates = linkedMapOf<String, Aggregate>()
        val sortedPhotos = FolderPreviewOrdering.sortPhotos(photos, sortOrder)

        sortedPhotos.forEach { photo ->
            val relative = photo.imageUri.toString()
                .removePrefix(normalizedEndpoint)
                .trimStart('/')
                .let { path ->
                    when {
                        normalizedCurrent.isEmpty() -> path
                        path.startsWith(normalizedCurrent) -> path.removePrefix(normalizedCurrent)
                        else -> return@forEach
                    }
                }

            val segments = relative.split('/').filter { it.isNotEmpty() }
            if (segments.size < 2) return@forEach

            val childName = segments.first()
            val childPath = if (normalizedCurrent.isEmpty()) {
                "$childName/"
            } else {
                "$normalizedCurrent$childName/"
            }

            val aggregate = aggregates.getOrPut(childPath) {
                Aggregate(
                    childPath = childPath,
                    childName = childName
                )
            }
            if (aggregate.previewUris.size < 4) {
                aggregate.previewUris.add(photo.imageUri)
            }
            aggregate.hasSubFolders = aggregate.hasSubFolders || segments.size > 2
            aggregate.latestModified = maxOf(aggregate.latestModified, photo.dateModified)
        }

        return aggregates.values.map { aggregate ->
            Folder(
                path = aggregate.childPath,
                name = aggregate.childName,
                isLocal = false,
                previewUris = aggregate.previewUris,
                hasSubFolders = aggregate.hasSubFolders,
                dateModified = aggregate.latestModified
            )
        }
    }
}
