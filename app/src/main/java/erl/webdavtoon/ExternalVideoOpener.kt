package erl.webdavtoon

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URI

object ExternalVideoOpener {

    fun open(context: Context, mediaUri: String, mediaTitle: String, isRemote: Boolean, settingsManager: SettingsManager): Boolean {
        val targetUri = buildLaunchUri(mediaUri, isRemote, settingsManager)
        val mimeType = detectVideoMimeType(mediaTitle)
            ?: detectVideoMimeType(mediaUri)
            ?: "video/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(Intent.createChooser(intent, "打开外部播放器"))
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "未找到可用的外部播放器", Toast.LENGTH_SHORT).show()
            false
        } catch (e: Throwable) {
            android.util.Log.e("ExternalVideoOpener", "open external player failed uri=$mediaUri", e)
            Toast.makeText(context, "打开外部播放器失败", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun buildLaunchUri(mediaUri: String, isRemote: Boolean, settingsManager: SettingsManager): Uri {
        if (!isRemote || !mediaUri.startsWith("http", ignoreCase = true)) {
            return Uri.parse(mediaUri)
        }

        val username = settingsManager.getWebDavUsername()
        val password = settingsManager.getWebDavPassword()
        if (username.isBlank() || password.isBlank()) {
            return Uri.parse(mediaUri)
        }

        val decodedPassword = runCatching { Uri.decode(password) }.getOrDefault(password)

        val withUserInfo = runCatching {
            val parsed = URI(mediaUri)
            URI(
                parsed.scheme,
                "$username:$decodedPassword",
                parsed.host,
                parsed.port,
                parsed.path,
                parsed.query,
                parsed.fragment
            ).toString()
        }.getOrDefault(mediaUri)

        return Uri.parse(withUserInfo)
    }
}
