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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
    val previewUrls: List<String> = emptyList(),
)

data class AutoWorkflowCrumb(
    val name: String,
    val relPath: String,
)

/**
 * Outcome of one reviewer batch action (`POST /api/reviewer/tasks/batch-actions`).
 *
 * `decision` is `preview_only` until the same request is repeated with `confirm = true`; the
 * backend refuses to mutate anything before that second step.
 */
data class AutoWorkflowActionResult(
    val action: String,
    val decision: String,
    val message: String,
    val executed: Boolean,
    val matchedCount: Int,
    val affectedTaskIds: List<Long>,
    val newTaskIds: List<Long>,
) {
    val isPreview: Boolean get() = decision == "preview_only"
    val isBlocked: Boolean get() = decision == "blocked"
}

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
 * Client for the AutoWorkflow backend output explorer.
 *
 * Browsing walks the real ComfyUI output folder and annotates every file with the task that
 * produced it (task id, workflow, seed, original input image) through a GET. Mutations — resubmit
 * and delete — go through the reviewer batch-actions endpoint and are always previewed first.
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
        sort: String = DEFAULT_SORT,
    ): Result<AutoWorkflowPage> = withContext(Dispatchers.IO) {
        runCatching {
            if (normalizedBaseUrl.isEmpty()) {
                throw IOException("AutoWorkflow server is not configured")
            }
            val url = buildBrowseUrl(normalizedBaseUrl, relPath, offset, limit, sort)
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

    /** Reads saved reviewer prompt presets without modifying them on the backend. */
    suspend fun fetchPromptPresets(): Result<List<PromptPreset>> = withContext(Dispatchers.IO) {
        runCatching {
            if (normalizedBaseUrl.isEmpty()) {
                throw IOException("AutoWorkflow server is not configured")
            }
            val request = Request.Builder()
                .url("$normalizedBaseUrl/api/reviews/prompt-presets")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${extractDetail(body) ?: response.message}")
                }
                parsePromptPresets(body)
            }
        }.onFailure { error ->
            Log.e(TAG, "fetchPromptPresets failed", error)
        }
    }

    /**
     * Asks the reviewer endpoint to re-run [taskIds] through either the edit flow (original image
     * in) or the upscale flow (current output in).
     *
     * With `confirm = false` the backend only answers `preview_only` and touches nothing; the caller
     * repeats the same call with `confirm = true` once the user has seen the preview.
     */
    suspend fun resubmit(
        taskIds: List<Long>,
        flow: String,
        deleteOldOutput: Boolean,
        confirm: Boolean,
        promptText: String? = null,
    ): Result<AutoWorkflowActionResult> = batchAction(
        taskIds = taskIds,
        action = ACTION_RESUBMIT,
        confirm = confirm,
        flow = flow,
        deleteOldOutput = deleteOldOutput,
        promptText = promptText,
    )

    /** Deletes the output, the relations and the task rows behind [taskIds]. */
    suspend fun deleteTasks(taskIds: List<Long>): Result<AutoWorkflowActionResult> = batchAction(
        taskIds = taskIds,
        action = ACTION_DELETE,
        confirm = true,
    )

    private suspend fun batchAction(
        taskIds: List<Long>,
        action: String,
        confirm: Boolean,
        flow: String = FLOW_EDIT,
        deleteOldOutput: Boolean = false,
        promptText: String? = null,
    ): Result<AutoWorkflowActionResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (normalizedBaseUrl.isEmpty()) {
                throw IOException("AutoWorkflow server is not configured")
            }
            val payload = buildBatchActionPayload(
                taskIds = taskIds,
                action = action,
                confirm = confirm,
                flow = flow,
                deleteOldOutput = deleteOldOutput,
                promptText = promptText,
            )
            val request = Request.Builder()
                .url("$normalizedBaseUrl/api/reviewer/tasks/batch-actions")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${extractDetail(body) ?: response.message}")
                }
                parseActionResult(body)
            }
        }.onFailure { error ->
            Log.e(TAG, "batch action failed action=$action taskIds=$taskIds", error)
        }
    }

    companion object {
        private const val TAG = "AutoWorkflowClient"
        const val PAGE_SIZE = 120
        const val SORT_MTIME_DESC = "mtime_desc"
        const val SORT_MTIME_ASC = "mtime_asc"
        const val SORT_NAME_ASC = "name_asc"
        const val SORT_NAME_DESC = "name_desc"
        const val DEFAULT_SORT = SORT_MTIME_DESC

        internal fun buildBrowseUrl(
            baseUrl: String,
            relPath: String,
            offset: Int = 0,
            limit: Int = PAGE_SIZE,
            sort: String = DEFAULT_SORT,
        ): String {
            val base = baseUrl.trim().trimEnd('/')
            val encodedPath = URLEncoder.encode(relPath, "UTF-8").replace("+", "%20")
            val encodedSort = URLEncoder.encode(sort.ifBlank { DEFAULT_SORT }, "UTF-8")
            return buildString {
                append(base)
                append("/api/output/browse?rel_path=")
                append(encodedPath)
                append("&offset=")
                append(offset.coerceAtLeast(0))
                append("&limit=")
                append(limit)
                append("&sort=")
                append(encodedSort)
            }
        }

        const val ACTION_RESUBMIT = "resubmit"
        const val ACTION_DELETE = "delete"
        const val FLOW_EDIT = "edit"
        const val FLOW_UPSCALE = "upscale"
        private const val DELETE_MODE_OUTPUT_AND_RELATIONS = "output_and_relations"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        internal fun buildBatchActionPayload(
            taskIds: List<Long>,
            action: String,
            confirm: Boolean,
            flow: String = FLOW_EDIT,
            deleteOldOutput: Boolean = false,
            promptText: String? = null,
        ): JsonObject = JsonObject().apply {
            add(
                "task_ids",
                com.google.gson.JsonArray().apply { taskIds.forEach { add(it) } },
            )
            addProperty("action", action)
            addProperty("confirm", confirm)
            addProperty("flow", flow)
            addProperty("delete_old_output", deleteOldOutput)
            addProperty("delete_mode", DELETE_MODE_OUTPUT_AND_RELATIONS)
            addProperty("prompt_mode", "create")
            val trimmedPrompt = promptText?.trim().orEmpty()
            if (trimmedPrompt.isNotEmpty()) {
                addProperty("prompt_text", trimmedPrompt)
            }
        }

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
                    previewUrls = obj.arrayOrEmpty("preview_urls").mapNotNull { element ->
                        absoluteAssetUrl(baseUrl, element.asStringOrNull())
                    },
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

        internal fun parseActionResult(json: String): AutoWorkflowActionResult {
            val root = JsonParser.parseString(json).asJsonObject
            return AutoWorkflowActionResult(
                action = root.stringOrNull("action").orEmpty(),
                decision = root.stringOrNull("decision").orEmpty(),
                message = root.stringOrNull("message").orEmpty(),
                executed = root.boolOrFalse("executed"),
                matchedCount = root.intOrZero("matched_count"),
                affectedTaskIds = root.longArrayOrEmpty("affected_task_ids"),
                newTaskIds = root.longArrayOrEmpty("new_task_ids"),
            )
        }

        internal fun parsePromptPresets(json: String): List<PromptPreset> {
            val root = JsonParser.parseString(json).asJsonObject
            return root.arrayOrEmpty("presets").mapNotNull { element ->
                val obj = element.asObjectOrNull() ?: return@mapNotNull null
                val name = obj.stringOrNull("name") ?: return@mapNotNull null
                val content = obj.stringOrNull("content") ?: return@mapNotNull null
                PromptPreset(name = name, content = content)
            }
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

private fun JsonObject.longArrayOrEmpty(name: String): List<Long> =
    arrayOrEmpty(name).mapNotNull { element ->
        if (element.isJsonNull) null else runCatching { element.asLong }.getOrNull()
    }

private fun JsonObject.objectOrNull(name: String): JsonObject? {
    val element = get(name) ?: return null
    if (!element.isJsonObject) return null
    return element.asJsonObject
}

private fun JsonElement.asObjectOrNull(): JsonObject? =
    if (isJsonObject) asJsonObject else null

private fun JsonElement.asStringOrNull(): String? {
    if (isJsonNull) return null
    return runCatching { asString }.getOrNull()?.takeIf { it.isNotBlank() }
}

/** Last path segment of a Windows or POSIX path, or null when there is none. */
internal fun autoWorkflowFileName(path: String): String? =
    path.substringAfterLast('/').substringAfterLast('\\').trim().takeIf { it.isNotEmpty() }
