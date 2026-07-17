package erl.webdavtoon

import android.net.Uri
import android.view.View
import android.widget.ImageView

internal class FolderPreviewBinder(
    private val previewImageViews: List<ImageView>,
    private val folderIcon: ImageView,
    private val logTag: String
) {

    private var boundPath: String? = null
    private var boundPreviewUris: List<Uri> = emptyList()
    private val slotVersions = IntArray(previewImageViews.size)

    fun bind(folder: Folder) {
        val sameFolder = boundPath == folder.path
        if (!sameFolder) {
            clearSlots()
        }

        val newPreviewUris = folder.previewUris.take(previewImageViews.size)
        folderIcon.visibility = if (newPreviewUris.isEmpty()) View.VISIBLE else View.GONE

        previewImageViews.forEachIndexed { index, imageView ->
            val oldUri = boundPreviewUris.getOrNull(index).takeIf { sameFolder }
            val newUri = newPreviewUris.getOrNull(index)
            when {
                oldUri == newUri && newUri != null -> {
                    imageView.alpha = 1f
                    imageView.visibility = View.VISIBLE
                }

                newUri == null -> hideSlot(index, imageView, animate = sameFolder && oldUri != null)
                else -> loadSlot(index, imageView, newUri, animate = sameFolder && oldUri != null)
            }
        }

        boundPath = folder.path
        boundPreviewUris = newPreviewUris
    }

    fun clear() {
        boundPath = null
        boundPreviewUris = emptyList()
        clearSlots()
        folderIcon.visibility = View.VISIBLE
    }

    private fun clearSlots() {
        previewImageViews.forEachIndexed { index, imageView ->
            slotVersions[index]++
            imageView.animate().cancel()
            WebDavImageLoader.clear(imageView)
            imageView.alpha = 1f
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.visibility = View.INVISIBLE
        }
    }

    private fun hideSlot(index: Int, imageView: ImageView, animate: Boolean) {
        val version = ++slotVersions[index]
        imageView.animate().cancel()
        if (!animate || imageView.drawable == null) {
            WebDavImageLoader.clear(imageView)
            imageView.alpha = 1f
            imageView.visibility = View.INVISIBLE
            return
        }

        imageView.animate()
            .alpha(0f)
            .setDuration(PREVIEW_TRANSITION_DURATION_MS.toLong())
            .withEndAction {
                if (slotVersions[index] == version) {
                    WebDavImageLoader.clear(imageView)
                    imageView.alpha = 1f
                    imageView.visibility = View.INVISIBLE
                }
            }
            .start()
    }

    private fun loadSlot(index: Int, imageView: ImageView, uri: Uri, animate: Boolean) {
        slotVersions[index]++
        imageView.animate().cancel()
        imageView.alpha = 1f
        imageView.visibility = View.VISIBLE

        val uriString = uri.toString()
        val mediaType = detectMediaTypeByUri(uri) ?: MediaType.IMAGE
        val isRemote = RemoteMediaUrlResolver.isRemoteMediaUri(uriString)
        val shouldCrossFade = animate && imageView.drawable != null
        android.util.Log.d(
            logTag,
            "folder preview bind uri=$uriString mediaType=$mediaType isRemote=$isRemote animate=$shouldCrossFade"
        )

        if (mediaType == MediaType.VIDEO) {
            if (isRemote) {
                WebDavImageLoader.loadWebDavVideoThumbnail(
                    imageView.context,
                    uri,
                    imageView,
                    progressBar = null,
                    isFolderPreview = true,
                    preserveCurrentDrawable = shouldCrossFade,
                    crossFadeDurationMs = if (shouldCrossFade) PREVIEW_TRANSITION_DURATION_MS else 0
                )
            } else {
                WebDavImageLoader.loadLocalVideoThumbnail(
                    imageView.context,
                    uri,
                    imageView,
                    progressBar = null,
                    isFolderPreview = true,
                    preserveCurrentDrawable = shouldCrossFade,
                    crossFadeDurationMs = if (shouldCrossFade) PREVIEW_TRANSITION_DURATION_MS else 0
                )
            }
            return
        }

        if (isRemote) {
            WebDavImageLoader.loadWebDavImage(
                imageView.context,
                uri,
                imageView,
                progressBar = null,
                limitSize = true,
                isWaterfall = false,
                isFolderPreview = true,
                preserveCurrentDrawable = shouldCrossFade,
                crossFadeDurationMs = if (shouldCrossFade) PREVIEW_TRANSITION_DURATION_MS else 0
            )
        } else {
            WebDavImageLoader.loadLocalImage(
                imageView.context,
                uri,
                imageView,
                progressBar = null,
                limitSize = true,
                isWaterfall = false,
                isFolderPreview = true,
                preserveCurrentDrawable = shouldCrossFade,
                crossFadeDurationMs = if (shouldCrossFade) PREVIEW_TRANSITION_DURATION_MS else 0
            )
        }
    }

    private companion object {
        const val PREVIEW_TRANSITION_DURATION_MS = 220
    }
}
