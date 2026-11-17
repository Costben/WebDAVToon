// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A task row from the AutoWorkflow database, as attached to one generated image. */
data class AutoWorkflowTask(
    val id: Long,
    val filename: String,
    val status: String,
    val workflowFile: String?,
    val seed: Long?,
    val comfyTaskId: String?,
    val createdAt: String?,
    val sourcePath: String?,
    val targetPath: String?,
    val sourceImageUrl: String?,
    val sourceThumbUrl: String?,
    val targetImageUrl: String?,
    val targetThumbUrl: String?,
)

/** One image file inside the ComfyUI output tree. [task] is null when no row matched the file. */
data class AutoWorkflowImage(
    val name: String,
    val relPath: String,
    val absPath: String,
    val size: Long,
    val mtime: String,
    val imageUrl: String?,
    val thumbUrl: String?,
    val task: AutoWorkflowTask?,
    val matchKind: String?,
) {
    val isLinked: Boolean get() = task != null
}

data class AutoWorkflowFolder(
    val name: String,
    val relPath: String,
    val directImageCount: Int,
    val subfolderCount: Int,
    val taskCount: Int,
    val directTaskCount: Int,
    val firstTaskId: Long?,
    val lastTaskId: Long?,
)

data class AutoWorkflowCrumb(
    val name: String,
    val relPath: String,
)

data class AutoWorkflowPage(
    val relPath: String,
    val absPath: String,
    val rootPath: String,
    val parentRelPath: String,
    val breadcrumbs: List<AutoWorkflowCrumb>,
    val folders: List<AutoWorkflowFolder>,
    val images: List<AutoWorkflowImage>,
    val totalImages: Int,
    val hasMore: Boolean,
    val linkedInPage: Int,
    val folderCount: Int,
    val taskCount: Int,
)

/**
 * Read-only client for the AutoWorkflow backend output explorer.
 *
 * The backend walks the real ComfyUI output folder and annotates every file with the task that
 * produced it (task id, workflow, seed, original input image). This client only issues GETs and
 * resolves the relative `/api/assets/image?...` URLs against the configured server address.
 */
class AutoWorkflowClient(baseUrl: String) {

    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = normalizedBaseUrl.isNotEmpty()

    fun absoluteUrl(relative: String?): String? = absoluteAssetUrl(normalizedBaseUrl, relative)

    suspend fun browse(
        relPath: String,
        offset: Int = 0,
        limit: Int = PAGE_SIZE,
    ): Result<AutoWorkflowPage> = withContext(Dispatchers.IO) {
        runCatching {
            if (normalizedBaseUrl.isEmpty()) {
                throw IOException("AutoWorkflow server is not configured")
            }
            val encodedPath = URLEncoder.encode(relPath, "UTF-8").replace("+", "%20")
            val url = buildString {
                append(normalizedBaseUrl)
                append("/api/output/browse?rel_path=")
                append(encodedPath)
                append("&offset=")
                append(offset.coerceAtLeast(0))
                append("&limit=")
                append(limit)
            }
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${extractDetail(body) ?: response.message}")
                }
                parsePage(body, normalizedBaseUrl)
            }
        }.onFailure { error ->
            Log.e(TAG, "browse failed relPath=$relPath", error)
        }
    }

    companion object {
        private const val TAG = "AutoWorkflowClient"
        const val PAGE_SIZE = 120

        /** Backend sends relative `/api/...` URLs; everything the app loads must be absolute. */
        internal fun absoluteAssetUrl(baseUrl: String, relative: String?): String? {
            if (relative.isNullOrBlank()) return null
            if (relative.startsWith("http://", ignoreCase = true) ||
                relative.startsWith("https://", ignoreCase = true)
            ) {
                return relative
            }
            val base = baseUrl.trim().trimEnd('/')
            if (base.isEmpty()) return relative
            return if (relative.startsWith("/")) base + relative else "$base/$relative"
        }

        private fun extractDetail(body: String): String? = runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            root.stringOrNull("detail")
        }.getOrNull()

        internal fun parsePage(json: String, baseUrl: String): AutoWorkflowPage {
            val root = JsonParser.parseString(json).asJsonObject
            val breadcrumbs = root.arrayOrEmpty("breadcrumbs").mapNotNull { element ->
                val obj = element.asObjectOrNull() ?: return@mapNotNull null
                AutoWorkflowCrumb(
                    name = obj.stringOrNull("name").orEmpty(),
                    relPath = obj.stringOrNull("rel_path").orEmpty(),
                )
            }
            val folders = root.arrayOrEmpty("folders").mapNotNull { element ->
                val obj = element.asObjectOrNull() ?: return@mapNotNull null
                AutoWorkflowFolder(
                    name = obj.stringOrNull("name").orEmpty(),
                    relPath = obj.stringOrNull("rel_path").orEmpty(),
                    directImageCount = obj.intOrZero("direct_image_count"),
                    subfolderCount = obj.intOrZero("subfolder_count"),
                    taskCount = obj.intOrZero("task_count"),
                    directTaskCount = obj.intOrZero("direct_task_count"),
                    firstTaskId = obj.longOrNull("first_task_id"),
                    lastTaskId = obj.longOrNull("last_task_id"),
                )
            }
            val images = root.arrayOrEmpty("images").mapNotNull { element ->
                val obj = element.asObjectOrNull() ?: return@mapNotNull null
                AutoWorkflowImage(
                    name = obj.stringOrNull("name").orEmpty(),
                    relPath = obj.stringOrNull("rel_path").orEmpty(),
                    absPath = obj.stringOrNull("abs_path").orEmpty(),
                    size = obj.longOrNull("size") ?: 0L,
                    mtime = obj.stringOrNull("mtime").orEmpty(),
                    imageUrl = absoluteAssetUrl(baseUrl, obj.stringOrNull("image_url")),
                    thumbUrl = absoluteAssetUrl(baseUrl, obj.stringOrNull("thumb_url")),
                    task = obj.objectOrNull("task")?.let { parseTask(it, baseUrl) },
                    matchKind = obj.stringOrNull("match_kind"),
                )
            }
            return AutoWorkflowPage(
                relPath = root.stringOrNull("rel_path").orEmpty(),
                absPath = root.stringOrNull("abs_path").orEmpty(),
                rootPath = root.stringOrNull("root_path").orEmpty(),
                parentRelPath = root.stringOrNull("parent_rel_path").orEmpty(),
                breadcrumbs = breadcrumbs,
                folders = folders,
                images = images,
                totalImages = root.intOrZero("total_images"),
                hasMore = root.boolOrFalse("has_more"),
                linkedInPage = root.intOrZero("linked_in_page"),
                folderCount = root.intOrZero("folder_count"),
                taskCount = root.intOrZero("task_count"),
            )
        }

        private fun parseTask(obj: JsonObject, baseUrl: String): AutoWorkflowTask = AutoWorkflowTask(
            id = obj.longOrNull("id") ?: 0L,
            filename = obj.stringOrNull("filename").orEmpty(),
            status = obj.stringOrNull("status").orEmpty(),
            workflowFile = obj.stringOrNull("workflow_file"),
            seed = obj.longOrNull("seed"),
            comfyTaskId = obj.stringOrNull("comfy_task_id"),
            createdAt = obj.stringOrNull("created_at"),
            sourcePath = obj.stringOrNull("source_path"),
            targetPath = obj.stringOrNull("target_path"),
            sourceImageUrl = absoluteAssetUrl(baseUrl, obj.stringOrNull("source_image_url")),
            sourceThumbUrl = absoluteAssetUrl(baseUrl, obj.stringOrNull("source_thumb_url")),
            targetImageUrl = absoluteAssetUrl(baseUrl, obj.stringOrNull("target_image_url")),
            targetThumbUrl = absoluteAssetUrl(baseUrl, obj.stringOrNull("target_thumb_url")),
        )
    }
}

private fun JsonObject.stringOrNull(name: String): String? {
    val element = get(name) ?: return null
    if (element.isJsonNull) return null
    return runCatching { element.asString }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun JsonObject.longOrNull(name: String): Long? {
    val element = get(name) ?: return null
    if (element.isJsonNull) return null
    return runCatching { element.asLong }.getOrNull()
}

private fun JsonObject.intOrZero(name: String): Int {
    val element = get(name) ?: return 0
    if (element.isJsonNull) return 0
    return runCatching { element.asInt }.getOrDefault(0)
}

private fun JsonObject.boolOrFalse(name: String): Boolean {
    val element = get(name) ?: return false
    if (element.isJsonNull) return false
    return runCatching { element.asBoolean }.getOrDefault(false)
}

private fun JsonObject.arrayOrEmpty(name: String): List<JsonElement> {
    val element = get(name) ?: return emptyList()
    if (element.isJsonNull || !element.isJsonArray) return emptyList()
    return element.asJsonArray.toList()
}

private fun JsonObject.objectOrNull(name: String): JsonObject? {
    val element = get(name) ?: return null
    if (!element.isJsonObject) return null
    return element.asJsonObject
}

private fun JsonElement.asObjectOrNull(): JsonObject? =
    if (isJsonObject) asJsonObject else null

/** Last path segment of a Windows or POSIX path, or null when there is none. */
internal fun autoWorkflowFileName(path: String): String? =
    path.substringAfterLast('/').substringAfterLast('\\').trim().takeIf { it.isNotEmpty() }
