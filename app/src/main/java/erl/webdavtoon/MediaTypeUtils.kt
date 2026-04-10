package erl.webdavtoon

import android.net.Uri
import java.util.Locale
import java.util.concurrent.TimeUnit

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
private val videoExtensions = setOf("mp4", "mkv", "mov", "avi", "webm", "m4v", "3gp", "ts", "m2ts")
private val videoMimeTypes = mapOf(
    "mp4" to "video/mp4",
    "m4v" to "video/mp4",
    "mov" to "video/quicktime",
    "mkv" to "video/x-matroska",
    "webm" to "video/webm",
    "avi" to "video/x-msvideo",
    "3gp" to "video/3gpp",
    "ts" to "video/mp2t",
    "m2ts" to "video/mp2t"
)

fun detectMediaTypeByName(name: String): MediaType? {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        extension in imageExtensions -> MediaType.IMAGE
        extension in videoExtensions -> MediaType.VIDEO
        else -> null
    }
}

fun detectMediaTypeByUri(uri: Uri): MediaType? {
    val asString = uri.toString().lowercase(Locale.ROOT)

    if (asString.contains("/video/")) return MediaType.VIDEO
    if (asString.contains("/images/")) return MediaType.IMAGE

    val path = uri.path.orEmpty()
    if (path.isNotBlank()) {
        detectMediaTypeByName(path.substringAfterLast('/'))?.let { return it }
    }

    return detectMediaTypeByName(asString.substringAfterLast('/'))
}

fun isSupportedMediaName(name: String): Boolean = detectMediaTypeByName(name) != null

fun detectVideoMimeType(nameOrUri: String): String? {
    val extension = nameOrUri.substringAfterLast('.', "").substringBefore('?').lowercase(Locale.ROOT)
    return videoMimeTypes[extension]
}

fun isAviVideo(nameOrUri: String): Boolean = detectVideoMimeType(nameOrUri) == "video/x-msvideo"

fun formatVideoDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0L) return ""

    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
