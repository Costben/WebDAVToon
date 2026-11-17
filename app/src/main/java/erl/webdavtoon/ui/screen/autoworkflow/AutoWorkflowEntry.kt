// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.autoworkflow

/**
 * The home-grid entry that opens the AutoWorkflow output browser.
 *
 * The entry is a synthetic folder: it has no file system path of its own, so it carries a
 * `virtual://` scheme like the app's other aggregated entries. Marking it local keeps the
 * folder grid from trying to fetch a WebDAV preview for it.
 */
object AutoWorkflowEntry {
    const val PATH = "virtual://autoworkflow"
}
