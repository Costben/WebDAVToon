package erl.webdavtoon

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.net.URI
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ExternalVideoOpener {

    fun open(
        context: Context,
        mediaUri: String,
        mediaTitle: String,
        isRemote: Boolean,
        settingsManager: SettingsManager
    ): Boolean {
        val targetUri = buildLaunchUri(context, mediaUri, isRemote, settingsManager)
        val mimeType = detectVideoMimeType(mediaTitle)
            ?: detectVideoMimeType(mediaUri)
            ?: "video/*"
        val launchMode = settingsManager.getVideoExternalPlayerMode()

        val baseIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val launchIntent = if (launchMode == SettingsManager.VIDEO_EXTERNAL_PLAYER_MODE_CHOOSER) {
            Intent.createChooser(baseIntent, "Open external player")
        } else {
            baseIntent
        }

        return try {
            android.util.Log.d(
                "ExternalVideoOpener",
                "open mode=$launchMode uri=$mediaUri remote=$isRemote"
            )
            context.startActivity(launchIntent)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No external player found", Toast.LENGTH_SHORT).show()
            false
        } catch (e: Throwable) {
            android.util.Log.e("ExternalVideoOpener", "open external player failed uri=$mediaUri", e)
            Toast.makeText(context, "Failed to open external player", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun buildLaunchUri(
        context: Context,
        mediaUri: String,
        isRemote: Boolean,
        settingsManager: SettingsManager
    ): Uri {
        if (!isRemote || !mediaUri.startsWith("http", ignoreCase = true)) {
            return buildSharableLocalUri(context, mediaUri)
        }

        return Uri.parse(
            buildRemoteLaunchUrl(
                mediaUri = mediaUri,
                username = settingsManager.getWebDavUsername(),
                password = settingsManager.getWebDavPassword()
            )
        )
    }

    private fun buildSharableLocalUri(context: Context, mediaUri: String): Uri {
        val parsed = Uri.parse(mediaUri)
        return when {
            parsed.scheme.equals("content", ignoreCase = true) -> parsed
            parsed.scheme.equals("file", ignoreCase = true) -> {
                parsed.path?.let { path ->
                    runCatching {
                        FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.fileprovider",
                            File(path).canonicalFile
                        )
                    }.getOrDefault(parsed)
                } ?: parsed
            }

            parsed.scheme.isNullOrBlank() -> {
                val file = File(mediaUri)
                if (file.exists()) {
                    runCatching {
                        FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.fileprovider",
                            file.canonicalFile
                        )
                    }.getOrDefault(parsed)
                } else {
                    parsed
                }
            }

            else -> parsed
        }
    }

    internal fun buildRemoteLaunchUrl(
        mediaUri: String,
        username: String,
        password: String
    ): String {
        val encodedUrl = FileUtils.encodeWebDavUrl(mediaUri)
        if (username.isBlank() || password.isBlank()) {
            return encodedUrl
        }

        val decodedPassword = decodeCredentialComponent(password)
        return runCatching {
            val parsed = URI(encodedUrl)
            URI(
                parsed.scheme,
                "$username:$decodedPassword",
                parsed.host,
                parsed.port,
                parsed.path,
                parsed.query,
                parsed.fragment
            ).toASCIIString()
        }.getOrElse { error ->
            android.util.Log.w(
                "ExternalVideoOpener",
                "Failed to build authenticated remote launch url; falling back to encoded url only",
                error
            )
            encodedUrl
        }
    }

    private fun decodeCredentialComponent(value: String): String {
        return runCatching {
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }
}
