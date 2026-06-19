package erl.webdavtoon

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class EditConfig(
    val workflows: List<String>,
    val promptPresets: List<PromptPreset>,
    val defaultWorkflow: String
)

data class PromptPreset(
    val name: String,
    val content: String
)

data class EditSubmitResult(
    val taskId: Long?,
    val decision: String,
    val message: String,
    val filename: String?
)

class EditService(
    private val context: Context,
    baseUrl: String,
    private val settingsManager: SettingsManager = SettingsManager(context)
) {
    private val appContext = context.applicationContext
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun fetchConfig(): Result<EditConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$normalizedBaseUrl/api/uploads/config")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                parseConfig(response.body?.string().orEmpty())
            }
        }.onFailure { error ->
            Log.e(TAG, "fetchConfig failed", error)
        }
    }

    suspend fun submitEdit(
        photo: Photo,
        workflow: String,
        prompt: String
    ): Result<EditSubmitResult> = withContext(Dispatchers.IO) {
        runCatching {
            val uploadFile = prepareUploadFile(photo)
            try {
                val fileBody = uploadFile.asRequestBody(detectImageMediaType(uploadFile.name))
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", uploadFile.name, fileBody)
                    .addFormDataPart("workflow_file", workflow)
                    .addFormDataPart("prompt", prompt)
                    .build()
                val request = Request.Builder()
                    .url("$normalizedBaseUrl/api/uploads/submit")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    parseSubmitResult(rawBody).also { result ->
                        if (result.decision != "accepted") {
                            throw IOException(result.message.ifBlank { result.decision })
                        }
                    }
                }
            } finally {
                if (uploadFile.parentFile?.name == CACHE_DIR) {
                    uploadFile.delete()
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "submitEdit failed photo=${photo.title}", error)
        }
    }

    private fun parseConfig(json: String): EditConfig {
        val root = JsonParser.parseString(json).asJsonObject
        val workflows = root.getAsJsonArray("workflows")
            ?.mapNotNull { it.asString }
            .orEmpty()
            .filter { it.endsWith(".json", ignoreCase = true) }
        val defaults = root.getAsJsonObject("defaults")
        val defaultWorkflow = defaults?.get("edit_workflow")?.asString
            ?.takeIf { it in workflows }
            ?: workflows.firstOrNull().orEmpty()
        val promptPresets = root.getAsJsonArray("prompt_presets")
            ?.mapNotNull { item ->
                val obj = item.asJsonObject
                PromptPreset(
                    name = obj.get("name")?.asString.orEmpty(),
                    content = obj.get("content")?.asString.orEmpty()
                ).takeIf { it.name.isNotBlank() || it.content.isNotBlank() }
            }
            .orEmpty()
        return EditConfig(
            workflows = workflows,
            promptPresets = promptPresets,
            defaultWorkflow = defaultWorkflow
        )
    }

    private fun parseSubmitResult(json: String): EditSubmitResult {
        val root = JsonParser.parseString(json).asJsonObject
        return EditSubmitResult(
            taskId = root.get("task_id")?.takeIf { !it.isJsonNull }?.asLong,
            decision = root.get("decision")?.asString.orEmpty(),
            message = root.get("message")?.asString.orEmpty(),
            filename = root.get("filename")?.takeIf { !it.isJsonNull }?.asString
        )
    }

    private fun prepareUploadFile(photo: Photo): File {
        return if (photo.isLocal) {
            copyLocalPhotoToCache(photo)
        } else {
            downloadRemotePhotoToCache(photo)
        }
    }

    private fun copyLocalPhotoToCache(photo: Photo): File {
        val targetFile = createCacheFile(photo)
        appContext.contentResolver.openInputStream(photo.imageUri)?.use { input ->
            FileOutputStream(targetFile).use { output -> input.copyTo(output) }
        } ?: throw IOException("Unable to open local image: ${photo.imageUri}")
        return targetFile
    }

    private fun downloadRemotePhotoToCache(photo: Photo): File {
        val targetFile = createCacheFile(photo)
        val requestBuilder = Request.Builder()
            .url(FileUtils.encodeWebDavUrl(photo.imageUri.toString()))
            .get()
        val username = settingsManager.getWebDavUsername()
        val password = settingsManager.getWebDavPassword()
        if (username.isNotBlank() || password.isNotBlank()) {
            requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            FileOutputStream(targetFile).use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
        return targetFile
    }

    private fun createCacheFile(photo: Photo): File {
        val cacheDir = File(appContext.cacheDir, CACHE_DIR).apply { mkdirs() }
        val safeName = sanitizeFilename(photo.title).ifBlank { "image" }
        val extension = safeName.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.let { ".$it" }
            ?: ".jpg"
        val stem = safeName.removeSuffix(extension).ifBlank { "image" }
        return File.createTempFile("edit_${stem}_", extension, cacheDir)
    }

    private fun sanitizeFilename(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun detectImageMediaType(name: String): okhttp3.MediaType? {
        val mimeType = when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        return mimeType.toMediaTypeOrNull()
    }

    companion object {
        private const val TAG = "EditService"
        private const val CACHE_DIR = "comfyui_edit"

        fun isValidUrl(url: String): Boolean {
            return runCatching {
                val uri = Uri.parse(url.trim())
                (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
            }.getOrDefault(false)
        }
    }
}
