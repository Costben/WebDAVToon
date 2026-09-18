package erl.webdavtoon

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MediaShareHelper {
    fun sharePhotos(
        context: Context,
        scope: CoroutineScope,
        settingsManager: SettingsManager,
        photos: List<Photo>,
    ) {
        if (photos.isEmpty()) return
        scope.launch {
            runCatching {
                val shareIntent = if (photos.all { it.isLocal }) {
                    buildFileShareIntent(context, photos, photos.map { it.imageUri })
                } else {
                    val shareFiles = withContext(Dispatchers.IO) {
                        photos.map { photo ->
                            if (photo.isLocal) photo.imageUri else downloadRemotePhotoForShare(context, settingsManager, photo)
                        }
                    }
                    buildFileShareIntent(context, photos, shareFiles)
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)))
            }.onFailure { error ->
                android.util.Log.e("MediaShareHelper", "Share failed", error)
                Toast.makeText(context, context.getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildFileShareIntent(context: Context, selectedPhotos: List<Photo>, uris: List<Uri>): Intent {
        return if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = shareMimeType(selectedPhotos.first())
                putExtra(Intent.EXTRA_STREAM, uris.first())
                clipData = ClipData.newUri(context.contentResolver, selectedPhotos.first().title, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            val shareUris = ArrayList(uris)
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = if (selectedPhotos.all { it.mediaType == MediaType.IMAGE }) "image/*" else "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
                clipData = ClipData.newUri(context.contentResolver, selectedPhotos.first().title, shareUris.first()).apply {
                    shareUris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun downloadRemotePhotoForShare(context: Context, settingsManager: SettingsManager, photo: Photo): Uri {
        val shareDir = File(context.cacheDir, "shared_media").apply { mkdirs() }
        val safeName = photo.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "shared_media" }
        val targetFile = File(shareDir, safeName)
        val uriString = photo.imageUri.toString()
        val fetchUrl = RemoteMediaUrlResolver.resolveForHttp(settingsManager, uriString)
            ?: throw java.io.IOException("Media is not reachable from the current server: $uriString")
        val requestBuilder = okhttp3.Request.Builder().url(fetchUrl)
        if (RemoteMediaUrlResolver.needsBasicAuth(uriString)) {
            requestBuilder.addHeader(
                "Authorization",
                okhttp3.Credentials.basic(settingsManager.getWebDavUsername(), settingsManager.getWebDavPassword())
            )
        }
        okhttp3.OkHttpClient().newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body ?: throw java.io.IOException("Empty response body")
            FileOutputStream(targetFile).use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
        return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", targetFile)
    }

    private fun shareMimeType(photo: Photo): String {
        return when (photo.mediaType) {
            MediaType.IMAGE -> "image/*"
            MediaType.VIDEO -> detectVideoMimeType(photo.title)
                ?: detectVideoMimeType(photo.imageUri.toString())
                ?: "video/*"
        }
    }
}
