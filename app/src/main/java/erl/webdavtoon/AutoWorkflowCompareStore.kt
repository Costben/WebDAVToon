// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import java.util.concurrent.ConcurrentHashMap

/**
 * What the reader needs to draw the original-image inset for one generated output file.
 *
 * [sourceUrl] is the workflow input that produced the output; [generatedUrl] is the output itself,
 * kept so the inset can fall back gracefully when the source file is gone.
 */
data class AutoWorkflowCompareInfo(
    val sourceUrl: String?,
    val generatedUrl: String?,
    val taskId: Long?,
    val sourceName: String?,
    val workflowFile: String?,
    val seed: Long?,
)

/**
 * Side table the reader consults for AutoWorkflow sessions.
 *
 * The reader is shared by every album, so it must not know about the AutoWorkflow backend. The
 * output browser fills this table right before opening the reader, keyed by [Photo.id] — the
 * output tree's relative path, which is unique inside one session. A miss means "not an
 * AutoWorkflow image", and the reader behaves exactly as it does for any other album.
 */
internal object AutoWorkflowCompareStore {
    private val entries = ConcurrentHashMap<String, AutoWorkflowCompareInfo>()
    private val deletedTaskIds = ConcurrentHashMap.newKeySet<Long>()

    fun putAll(values: Map<String, AutoWorkflowCompareInfo>) {
        if (values.isEmpty()) return
        entries.putAll(values)
    }

    fun get(photoId: String): AutoWorkflowCompareInfo? = entries[photoId]

    fun markDeletedTasks(taskIds: Collection<Long>) {
        deletedTaskIds.addAll(taskIds)
    }

    fun consumeDeletedTasks(): Set<Long> {
        if (deletedTaskIds.isEmpty()) return emptySet()
        val drained = HashSet<Long>()
        val iterator = deletedTaskIds.iterator()
        while (iterator.hasNext()) {
            val id = iterator.next()
            if (deletedTaskIds.remove(id)) {
                drained.add(id)
            }
        }
        return drained
    }
}
