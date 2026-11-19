// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing contract for `/api/output/browse`.
 *
 * The backend sends `/api/...` URLs relative to itself, so every asset URL the app loads has to
 * be resolved against the configured server address while absolute URLs stay untouched.
 */
class AutoWorkflowClientTest {

    private val baseUrl = "http://192.168.31.179:8105"

    private val pageJson = """
        {
          "rel_path": "Douyin/黄绿紫",
          "abs_path": "D:/ComfyUI/output/Douyin/黄绿紫",
          "root_path": "D:/ComfyUI/output",
          "parent_rel_path": "Douyin",
          "breadcrumbs": [
            {"name": "output", "rel_path": ""},
            {"name": "Douyin", "rel_path": "Douyin"},
            {"name": "黄绿紫", "rel_path": "Douyin/黄绿紫"}
          ],
          "folders": [
            {
              "name": "光月定大青蛙",
              "rel_path": "Douyin/光月定大青蛙",
              "direct_image_count": 449,
              "subfolder_count": 0,
              "task_count": 449,
              "direct_task_count": 449,
              "first_task_id": 27695,
              "last_task_id": 45768
            }
          ],
          "images": [
            {
              "name": "[45768]_黄绿紫_769573_00001.png",
              "rel_path": "Douyin/黄绿紫/[45768]_黄绿紫_769573_00001.png",
              "abs_path": "D:/ComfyUI/output/Douyin/黄绿紫/[45768]_黄绿紫_769573_00001.png",
              "size": 2048576,
              "mtime": "2026-11-16T20:15:33.123456",
              "image_url": "/api/assets/image?path=Douyin%2F黄绿紫%2Fout.png",
              "thumb_url": "/api/assets/thumb?path=Douyin%2F黄绿紫%2Fout.png",
              "match_kind": "filename",
              "task": {
                "id": 45768,
                "filename": "黄绿紫_7695733740576378810_8.jpg",
                "status": "done",
                "workflow_file": "黄绿紫.json",
                "seed": 769573,
                "comfy_task_id": "9f1c",
                "created_at": "2026-11-16T20:14:02",
                "source_path": "D:/inputs/Douyin/黄绿紫_7695733740576378810_8.jpg",
                "target_path": "D:/ComfyUI/output/Douyin/黄绿紫/[45768]_黄绿紫_769573_00001.png",
                "source_image_url": "/api/assets/image?path=input.jpg",
                "source_thumb_url": "http://192.168.31.179:8105/api/assets/thumb?path=input.jpg",
                "target_image_url": "/api/assets/image?path=out.png",
                "target_thumb_url": "/api/assets/thumb?path=out.png"
              }
            },
            {
              "name": "loose.png",
              "rel_path": "Douyin/黄绿紫/loose.png",
              "abs_path": "D:/ComfyUI/output/Douyin/黄绿紫/loose.png",
              "size": 1024,
              "mtime": "2026-11-10T09:00:00",
              "image_url": "/api/assets/image?path=loose.png",
              "thumb_url": null,
              "match_kind": null,
              "task": null
            }
          ],
          "total_images": 17,
          "has_more": true,
          "linked_in_page": 1,
          "folder_count": 1,
          "task_count": 450
        }
    """.trimIndent()

    @Test
    fun `parses page, folders and pagination`() {
        val page = AutoWorkflowClient.parsePage(pageJson, baseUrl)

        assertEquals("Douyin/黄绿紫", page.relPath)
        assertEquals("Douyin", page.parentRelPath)
        assertEquals(3, page.breadcrumbs.size)
        assertEquals("", page.breadcrumbs.first().relPath)
        assertEquals(17, page.totalImages)
        assertTrue(page.hasMore)
        assertEquals(1, page.linkedInPage)
        assertEquals(1, page.folderCount)
        assertEquals(450, page.taskCount)

        val folder = page.folders.single()
        assertEquals("光月定大青蛙", folder.name)
        assertEquals("Douyin/光月定大青蛙", folder.relPath)
        assertEquals(449, folder.directImageCount)
        assertEquals(449, folder.taskCount)
        assertEquals(27695L, folder.firstTaskId)
        assertEquals(45768L, folder.lastTaskId)
    }

    @Test
    fun `parses linked task and resolves relative asset urls`() {
        val page = AutoWorkflowClient.parsePage(pageJson, baseUrl)
        val linked = page.images.first()
        val task = requireNotNull(linked.task)

        assertTrue(linked.isLinked)
        assertEquals("filename", linked.matchKind)
        assertEquals(45768L, task.id)
        assertEquals("黄绿紫.json", task.workflowFile)
        assertEquals(769573L, task.seed)
        assertEquals("done", task.status)
        assertEquals("D:/inputs/Douyin/黄绿紫_7695733740576378810_8.jpg", task.sourcePath)

        assertEquals("$baseUrl/api/assets/image?path=Douyin%2F黄绿紫%2Fout.png", linked.imageUrl)
        assertEquals("$baseUrl/api/assets/thumb?path=Douyin%2F黄绿紫%2Fout.png", linked.thumbUrl)
        assertEquals("$baseUrl/api/assets/image?path=input.jpg", task.sourceImageUrl)
        assertEquals("$baseUrl/api/assets/thumb?path=out.png", task.targetThumbUrl)
        // Already absolute: must not be prefixed twice.
        assertEquals(
            "http://192.168.31.179:8105/api/assets/thumb?path=input.jpg",
            task.sourceThumbUrl,
        )
    }

    @Test
    fun `unlinked image has no task and null thumb`() {
        val page = AutoWorkflowClient.parsePage(pageJson, baseUrl)
        val loose = page.images.last()

        assertFalse(loose.isLinked)
        assertNull(loose.task)
        assertNull(loose.thumbUrl)
        assertEquals(1024L, loose.size)
        assertEquals("$baseUrl/api/assets/image?path=loose.png", loose.imageUrl)
    }

    @Test
    fun `absolute asset urls stay absolute and blank ones stay null`() {
        assertEquals(
            "https://example.com/a.png",
            AutoWorkflowClient.absoluteAssetUrl(baseUrl, "https://example.com/a.png"),
        )
        assertEquals("$baseUrl/api/x", AutoWorkflowClient.absoluteAssetUrl("$baseUrl/", "/api/x"))
        assertEquals("$baseUrl/api/x", AutoWorkflowClient.absoluteAssetUrl(baseUrl, "api/x"))
        assertNull(AutoWorkflowClient.absoluteAssetUrl(baseUrl, null))
        assertNull(AutoWorkflowClient.absoluteAssetUrl(baseUrl, "  "))
    }

    @Test
    fun `parses reviewer batch action preview and executed results`() {
        val preview = AutoWorkflowClient.parseActionResult(
            """
            {
              "action": "resubmit",
              "decision": "preview_only",
              "message": "Preview: will create one pending child task.",
              "executed": false,
              "requires_human_review": true,
              "matched_count": 0,
              "affected_task_ids": [43803],
              "new_task_ids": []
            }
            """.trimIndent(),
        )
        assertTrue(preview.isPreview)
        assertFalse(preview.isBlocked)
        assertFalse(preview.executed)
        assertEquals("resubmit", preview.action)
        assertEquals(listOf(43803L), preview.affectedTaskIds)
        assertTrue(preview.newTaskIds.isEmpty())

        val executed = AutoWorkflowClient.parseActionResult(
            """
            {
              "action": "delete",
              "decision": "accepted",
              "message": "Deleted 2 tasks.",
              "executed": true,
              "matched_count": 2,
              "affected_task_ids": [11, 12],
              "new_task_ids": [21, 22]
            }
            """.trimIndent(),
        )
        assertFalse(executed.isPreview)
        assertTrue(executed.executed)
        assertEquals(2, executed.matchedCount)
        assertEquals(listOf(11L, 12L), executed.affectedTaskIds)
        assertEquals(listOf(21L, 22L), executed.newTaskIds)
    }

    @Test
    fun `file name survives both windows and posix source paths`() {
        assertEquals(
            "黄绿紫_7695733740576378810_8.jpg",
            autoWorkflowFileName("D:\\inputs\\Douyin\\黄绿紫_7695733740576378810_8.jpg"),
        )
        assertEquals(
            "黄绿紫_7695733740576378810_8.jpg",
            autoWorkflowFileName("D:/inputs/Douyin/黄绿紫_7695733740576378810_8.jpg"),
        )
        assertNull(autoWorkflowFileName(""))
    }

    @Test
    fun `builds batch action payload with optional prompt text`() {
        val withPrompt = AutoWorkflowClient.buildBatchActionPayload(
            taskIds = listOf(43803L),
            action = AutoWorkflowClient.ACTION_RESUBMIT,
            confirm = false,
            flow = AutoWorkflowClient.FLOW_EDIT,
            deleteOldOutput = true,
            promptText = "  new prompt  ",
        )
        assertEquals("new prompt", withPrompt.get("prompt_text").asString)
        assertTrue(withPrompt.get("delete_old_output").asBoolean)

        val withoutPrompt = AutoWorkflowClient.buildBatchActionPayload(
            taskIds = listOf(43803L),
            action = AutoWorkflowClient.ACTION_RESUBMIT,
            confirm = false,
            flow = AutoWorkflowClient.FLOW_UPSCALE,
            deleteOldOutput = false,
            promptText = "   ",
        )
        assertFalse(withoutPrompt.has("prompt_text"))
    }

    @Test
    fun `builds batch delete payload with normalized delete mode`() {
        val keepSource = AutoWorkflowClient.buildBatchActionPayload(
            taskIds = listOf(43803L),
            action = AutoWorkflowClient.ACTION_DELETE,
            confirm = true,
            deleteMode = AutoWorkflowClient.DELETE_MODE_OUTPUT_AND_RELATIONS,
        )
        assertEquals("output_and_relations", keepSource.get("delete_mode").asString)

        val deleteSource = AutoWorkflowClient.buildBatchActionPayload(
            taskIds = listOf(43803L),
            action = AutoWorkflowClient.ACTION_DELETE,
            confirm = true,
            deleteMode = AutoWorkflowClient.DELETE_MODE_INPUT_AND_OUTPUT,
        )
        assertEquals("input_and_output", deleteSource.get("delete_mode").asString)

        val fallback = AutoWorkflowClient.buildBatchActionPayload(
            taskIds = listOf(43803L),
            action = AutoWorkflowClient.ACTION_DELETE,
            confirm = true,
            deleteMode = "unexpected_mode",
        )
        assertEquals("output_and_relations", fallback.get("delete_mode").asString)
    }

    @Test
    fun `builds browse url with sort parameter`() {
        val url = AutoWorkflowClient.buildBrowseUrl(
            baseUrl = "http://127.0.0.1:8105/",
            relPath = "FLJ/subdir",
            offset = 120,
            limit = 60,
            sort = AutoWorkflowClient.SORT_NAME_ASC,
        )
        assertEquals(
            "http://127.0.0.1:8105/api/output/browse?rel_path=FLJ%2Fsubdir&offset=120&limit=60&sort=name_asc",
            url,
        )
    }

    @Test
    fun `sorts autoworkflow folders by name and task order`() {
        val folders = listOf(
            AutoWorkflowFolder("webui_input", "webui_input", 21, 0, 44, 44, 100L, 45569L),
            AutoWorkflowFolder("Douyin", "Douyin", 0, 417, 4787, 0, 10L, 43000L),
            AutoWorkflowFolder("FLJ", "FLJ", 0, 10, 2597, 0, 200L, 44000L),
        )
        assertEquals(
            listOf("Douyin", "FLJ", "webui_input"),
            erl.webdavtoon.ui.screen.autoworkflow.sortAutoWorkflowFolders(
                folders,
                SettingsManager.SORT_NAME_ASC,
            ).map { it.name },
        )
        assertEquals(
            listOf("webui_input", "FLJ", "Douyin"),
            erl.webdavtoon.ui.screen.autoworkflow.sortAutoWorkflowFolders(
                folders,
                SettingsManager.SORT_NAME_DESC,
            ).map { it.name },
        )
        assertEquals(
            listOf("webui_input", "FLJ", "Douyin"),
            erl.webdavtoon.ui.screen.autoworkflow.sortAutoWorkflowFolders(
                folders,
                SettingsManager.SORT_DATE_DESC,
            ).map { it.name },
        )
        assertEquals(
            listOf("Douyin", "webui_input", "FLJ"),
            erl.webdavtoon.ui.screen.autoworkflow.sortAutoWorkflowFolders(
                folders,
                SettingsManager.SORT_DATE_ASC,
            ).map { it.name },
        )
    }

    @Test
    fun `parses reviewer prompt presets`() {
        val presets = AutoWorkflowClient.parsePromptPresets(
            """
            {
              "count": 2,
              "presets": [
                { "name": "全裸simple", "content": "人物全裸，不穿鞋" },
                { "name": "空内容", "content": "   " },
                { "name": "M字开腿", "content": "人物M字开腿" }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(2, presets.size)
        assertEquals("全裸simple", presets[0].name)
        assertEquals("人物全裸，不穿鞋", presets[0].content)
        assertEquals("M字开腿", presets[1].name)
    }

    @Test
    fun `encodes and parses scroll offsets with LRU limit`() {
        val raw = linkedMapOf(
            "" to 0,
            "FLJ" to 1200,
            "Douyin" to 3400,
            "GOod" to -5,
            "webui_input" to 960,
        )
        val encoded = encodeAutoWorkflowScrollOffsets(raw, maxEntries = 2)
        val parsed = parseAutoWorkflowScrollOffsets(encoded)
        assertEquals(mapOf("Douyin" to 3400, "webui_input" to 960), parsed)
        assertEquals(emptyMap<String, Int>(), parseAutoWorkflowScrollOffsets("{bad json"))
    }

    @Test
    fun `removes deleted tasks locally without dropping remaining images`() {
        val page = AutoWorkflowClient.parsePage(pageJson, baseUrl)
        val remaining = erl.webdavtoon.ui.screen.autoworkflow.removeTasksFromImages(
            page.images,
            setOf(45768L),
        )
        assertEquals(1, remaining.size)
        assertEquals("loose.png", remaining.single().name)
    }

    @Test
    fun `follow zoom grid state preserves initial scroll offset until measured`() {
        val state = erl.webdavtoon.ui.screen.waterfall.FollowZoomGridState(
            initialColumns = 2,
            minColumns = 1,
            maxColumns = 4,
            initialScrollOffset = 1480,
        )
        state.clampScroll()
        assertEquals(1480, state.scrollOffset)

        state.viewportHeight = 800
        state.itemsContentHeight = 1600
        state.clampScroll()
        assertEquals(800, state.scrollOffset)
    }

    @Test
    fun `parses terminal hub status, actions and logs`() {
        val status = AutoWorkflowClient.parseTerminalHubStatus(
            """
            {
              "hub_reachable": true,
              "hub_url": "http://127.0.0.1:7801",
              "manager": {
                "mode_key": "live",
                "mode_label": "Production Live Write",
                "api_port": 7801,
                "log_dir": "C:/Ai/AutoWorkflow/logs/terminal_hub_winui",
                "manager_log": "C:/Ai/AutoWorkflow/logs/terminal_hub_winui/terminal_hub_winui_manager.log"
              },
              "autoworkflow_services": ["ComfyUI", "AWF Worker", "AWF Frontend"],
              "protected_services": ["AWF Backend"],
              "services": [
                {
                  "name": "AWF Backend",
                  "running": true,
                  "healthy": true,
                  "status_state": "ONLINE",
                  "health_detail": "pid=1234",
                  "pid": 1234,
                  "log_file": "C:/logs/AWF Backend.log",
                  "is_autoworkflow": true,
                  "is_protected": true
                },
                {
                  "name": "ComfyUI",
                  "running": false,
                  "healthy": false,
                  "status_state": "OFFLINE",
                  "health_detail": "未运行",
                  "pid": null,
                  "log_file": "C:/logs/ComfyUI.log",
                  "is_autoworkflow": true,
                  "is_protected": false
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(status.hubReachable)
        assertEquals("http://127.0.0.1:7801", status.hubUrl)
        assertEquals("Production Live Write", status.manager?.modeLabel)
        assertEquals(listOf("ComfyUI", "AWF Worker", "AWF Frontend"), status.autoWorkflowServices)
        assertEquals(listOf("AWF Backend"), status.protectedServices)
        assertEquals(2, status.services.size)
        assertTrue(status.services[0].isProtected)
        assertEquals(1234, status.services[0].pid)
        assertFalse(status.services[1].running)
        assertNull(status.services[1].pid)

        val actionResult = AutoWorkflowClient.parseTerminalHubActionResult(
            """
            {
              "status": "success",
              "name": "ComfyUI",
              "action": "start",
              "message": "ComfyUI started pid=5678"
            }
            """.trimIndent(),
        )
        assertEquals("success", actionResult.status)
        assertEquals("ComfyUI", actionResult.name)
        assertEquals("ComfyUI started pid=5678", actionResult.message)

        val logResult = AutoWorkflowClient.parseTerminalHubLogResult(
            """
            {
              "status": "success",
              "name": "ComfyUI",
              "log_file": "C:/logs/ComfyUI.log",
              "lines": 200,
              "content": "line-1\nline-2"
            }
            """.trimIndent(),
        )
        assertEquals("ComfyUI", logResult.name)
        assertEquals(200, logResult.lines)
        assertEquals("line-1\nline-2", logResult.content)
    }

    @Test
    fun `parses direct terminal hub status and normalizes bare host`() {
        assertEquals("http://192.168.31.199:7801", AutoWorkflowClient.normalizeBaseUrl(" 192.168.31.199:7801/ "))
        assertEquals("", AutoWorkflowClient.normalizeBaseUrl("   "))

        val directStatus = AutoWorkflowClient.parseTerminalHubStatus(
            """
            {
              "_manager": {
                "mode_key": "live",
                "mode_label": "Production Live Write",
                "api_port": 7801
              },
              "AWF Backend": {
                "running": true,
                "healthy": true,
                "status_state": "ONLINE",
                "health_detail": "running",
                "pid": 20000,
                "log_file": "C:/logs/AWF Backend.log"
              },
              "ComfyUI": {
                "running": false,
                "healthy": false,
                "status_state": "OFFLINE",
                "health_detail": "stopped",
                "pid": null,
                "log_file": "C:/logs/ComfyUI.log"
              },
              "Openlist": {
                "running": true,
                "healthy": true,
                "status_state": "ONLINE",
                "health_detail": "running",
                "pid": 43888,
                "log_file": "C:/logs/Openlist.log"
              }
            }
            """.trimIndent(),
            fallbackHubUrl = "http://127.0.0.1:7801",
        )
        assertTrue(directStatus.hubReachable)
        assertEquals("http://127.0.0.1:7801", directStatus.hubUrl)
        assertEquals(3, directStatus.services.size)
        assertEquals(listOf("AWF Backend", "ComfyUI"), directStatus.autoWorkflowServices)
        val backendService = directStatus.services.first { it.name == "AWF Backend" }
        assertTrue(backendService.isAutoWorkflow)
        assertFalse(backendService.isProtected)
    }

    @Test
    fun `direct status includes all four autoworkflow services and excludes unrelated ones`() {
        val status = AutoWorkflowClient.parseTerminalHubStatus(
            """
            {
              "_manager": {
                "mode_key": "live",
                "mode_label": "Production Live Write",
                "api_port": 7801
              },
              "ComfyUI": { "running": false, "healthy": false, "status_state": "OFFLINE" },
              "Openlist": { "running": true, "healthy": true, "status_state": "ONLINE" },
              "AWF Worker": { "running": false, "healthy": false, "status_state": "OFFLINE" },
              "AWF Frontend": { "running": false, "healthy": false, "status_state": "OFFLINE" },
              "AWF Backend": { "running": false, "healthy": false, "status_state": "OFFLINE" }
            }
            """.trimIndent(),
            fallbackHubUrl = "http://192.168.31.199:7801",
        )

        assertEquals(
            listOf("AWF Backend", "AWF Frontend", "AWF Worker", "ComfyUI"),
            status.autoWorkflowServices,
        )
        assertTrue(status.protectedServices.isEmpty())
        assertFalse(status.services.first { it.name == "Openlist" }.isAutoWorkflow)
        assertTrue(status.services.filter { it.name != "Openlist" }.all { it.isAutoWorkflow && !it.isProtected })
    }

    @Test
    fun `orders group targets for start, stop and restart`() {
        assertEquals(
            listOf("AWF Backend", "AWF Frontend", "AWF Worker", "ComfyUI"),
            AutoWorkflowClient.orderedGroupTargets(AutoWorkflowClient.HUB_ACTION_START, null),
        )
        assertEquals(
            listOf("ComfyUI", "AWF Worker", "AWF Frontend", "AWF Backend"),
            AutoWorkflowClient.orderedGroupTargets(AutoWorkflowClient.HUB_ACTION_STOP, null),
        )
        assertEquals(
            listOf("AWF Backend", "AWF Frontend", "AWF Worker", "ComfyUI"),
            AutoWorkflowClient.orderedGroupTargets(AutoWorkflowClient.HUB_ACTION_RESTART, null),
        )
    }

    @Test
    fun `direct group actions control all four services in order including partial failures`() = runBlocking {
        val recordedCalls = mutableListOf<Pair<String, String>>()
        val failWorkerOnStart = AtomicBoolean(false)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.bufferedReader().readText()
            when {
                path.startsWith("/api/terminal-hub/") -> {
                    val bytes = """{"detail":"Not Found"}""".toByteArray()
                    exchange.sendResponseHeaders(404, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                path == "/api/status" -> {
                    val json = """
                        {
                          "_manager": { "mode_key": "live", "mode_label": "Production Live Write", "api_port": 7801 },
                          "AWF Backend": { "running": false, "healthy": false, "status_state": "OFFLINE" },
                          "AWF Frontend": { "running": false, "healthy": false, "status_state": "OFFLINE" },
                          "AWF Worker": { "running": false, "healthy": false, "status_state": "OFFLINE" },
                          "ComfyUI": { "running": false, "healthy": false, "status_state": "OFFLINE" },
                          "Openlist": { "running": true, "healthy": true, "status_state": "ONLINE" }
                        }
                    """.trimIndent().toByteArray()
                    exchange.sendResponseHeaders(200, json.size.toLong())
                    exchange.responseBody.use { it.write(json) }
                }
                path in setOf("/api/start", "/api/stop", "/api/restart") -> {
                    val name = JsonParser.parseString(body).asJsonObject.get("name").asString
                    recordedCalls += path to name
                    if (failWorkerOnStart.get() && path == "/api/start" && name == "AWF Worker") {
                        val err = """{"status":"error","message":"worker failed"}""".toByteArray()
                        exchange.sendResponseHeaders(500, err.size.toLong())
                        exchange.responseBody.use { it.write(err) }
                    } else {
                        val ok = """{"status":"success","message":"$name ok"}""".toByteArray()
                        exchange.sendResponseHeaders(200, ok.size.toLong())
                        exchange.responseBody.use { it.write(ok) }
                    }
                }
                else -> {
                    val err = """{"detail":"unexpected"}""".toByteArray()
                    exchange.sendResponseHeaders(404, err.size.toLong())
                    exchange.responseBody.use { it.write(err) }
                }
            }
        }
        server.start()
        try {
            val client = AutoWorkflowClient("http://127.0.0.1:${server.address.port}")
            val status = client.fetchTerminalHubStatus().getOrThrow()
            assertEquals(
                listOf("AWF Backend", "AWF Frontend", "AWF Worker", "ComfyUI"),
                status.autoWorkflowServices,
            )

            recordedCalls.clear()
            val startResult = client.controlAutoWorkflowGroup(
                AutoWorkflowClient.HUB_ACTION_START,
                status.autoWorkflowServices,
            ).getOrThrow()
            assertEquals("success", startResult.status)
            assertEquals(
                listOf(
                    "/api/start" to "AWF Backend",
                    "/api/start" to "AWF Frontend",
                    "/api/start" to "AWF Worker",
                    "/api/start" to "ComfyUI",
                ),
                recordedCalls,
            )

            recordedCalls.clear()
            val stopResult = client.controlAutoWorkflowGroup(
                AutoWorkflowClient.HUB_ACTION_STOP,
                status.autoWorkflowServices,
            ).getOrThrow()
            assertEquals("success", stopResult.status)
            assertEquals(
                listOf(
                    "/api/stop" to "ComfyUI",
                    "/api/stop" to "AWF Worker",
                    "/api/stop" to "AWF Frontend",
                    "/api/stop" to "AWF Backend",
                ),
                recordedCalls,
            )

            recordedCalls.clear()
            val restartResult = client.controlAutoWorkflowGroup(
                AutoWorkflowClient.HUB_ACTION_RESTART,
                status.autoWorkflowServices,
            ).getOrThrow()
            assertEquals("success", restartResult.status)
            assertEquals(
                listOf(
                    "/api/restart" to "AWF Backend",
                    "/api/restart" to "AWF Frontend",
                    "/api/restart" to "AWF Worker",
                    "/api/restart" to "ComfyUI",
                ),
                recordedCalls,
            )

            recordedCalls.clear()
            failWorkerOnStart.set(true)
            val partialResult = client.controlAutoWorkflowGroup(
                AutoWorkflowClient.HUB_ACTION_START,
                status.autoWorkflowServices,
            ).getOrThrow()
            assertEquals("partial", partialResult.status)
            assertEquals(
                listOf(
                    "/api/start" to "AWF Backend",
                    "/api/start" to "AWF Frontend",
                    "/api/start" to "AWF Worker",
                    "/api/start" to "ComfyUI",
                ),
                recordedCalls,
            )
        } finally {
            server.stop(0)
        }
    }
}
