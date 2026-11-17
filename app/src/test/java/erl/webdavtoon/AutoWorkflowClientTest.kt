// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

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
}
