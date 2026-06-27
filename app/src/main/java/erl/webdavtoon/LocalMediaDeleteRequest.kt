package erl.webdavtoon

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object LocalMediaDeleteRequest {
    private const val TAG = "LocalMediaDelete"
    private const val DELETE_CONFIRMATION_POLL_ATTEMPTS = 8
    private const val DELETE_CONFIRMATION_POLL_DELAY_MS = 100L

    fun requiresSystemRequest(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun create(context: Context, photos: List<Photo>): IntentSenderRequest? {
        if (!requiresSystemRequest()) return null

        val uris = localUris(photos)
        if (uris.isEmpty()) return null

        val pendingIntent = MediaStore.createDeleteRequest(
            context.contentResolver,
            ArrayList(uris)
        )
        return IntentSenderRequest.Builder(pendingIntent.intentSender).build()
    }

    suspend fun awaitDeletedPhotos(context: Context, photos: List<Photo>): List<Photo> =
        withContext(Dispatchers.IO) {
            val localPhotos = photos.filter { it.isLocal }
            if (localPhotos.isEmpty()) return@withContext emptyList()

            var deletedPhotos = emptyList<Photo>()
            repeat(DELETE_CONFIRMATION_POLL_ATTEMPTS) { attempt ->
                deletedPhotos = localPhotos.filterNot { mediaExists(context, it.imageUri) }
                if (deletedPhotos.size == localPhotos.size) return@withContext deletedPhotos
                if (attempt < DELETE_CONFIRMATION_POLL_ATTEMPTS - 1) {
                    delay(DELETE_CONFIRMATION_POLL_DELAY_MS)
                }
            }
            deletedPhotos
        }

    private fun localUris(photos: List<Photo>): List<Uri> {
        return photos.asSequence()
            .filter { it.isLocal }
            .map { it.imageUri }
            .distinct()
            .toList()
    }

    private fun mediaExists(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query local media after delete request: $uri", e)
            false
        }
    }
}
