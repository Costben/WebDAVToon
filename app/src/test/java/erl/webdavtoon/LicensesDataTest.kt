// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import erl.webdavtoon.ui.screen.settings.LicenseEntry
import erl.webdavtoon.ui.screen.settings.LicensesData
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the invariants `LicensesScreen` relies on, and — most importantly — that every asset it
 * reads at runtime is a file the `syncOssAssets` Gradle task really copies out of the repo root.
 */
class LicensesDataTest {

    private val entries: List<LicenseEntry> =
        LicensesData.appEntries + LicensesData.thirdPartyEntries

    @Test
    fun ids_are_unique_and_not_blank() {
        assertTrue("no app license entry", LicensesData.appEntries.isNotEmpty())
        assertTrue("no third-party entries", LicensesData.thirdPartyEntries.isNotEmpty())

        val ids = entries.map { it.id }
        assertEquals("duplicate entry id", ids.size, ids.toSet().size)
        assertTrue("blank entry id", ids.none { it.isBlank() })
    }

    @Test
    fun every_entry_has_a_title_spdx_id_and_components() {
        entries.forEach { entry ->
            assertTrue("blank title for ${entry.id}", entry.title.isNotBlank())
            assertTrue("blank SPDX id for ${entry.id}", entry.spdxId.isNotBlank())
            assertTrue("no components listed for ${entry.id}", entry.components.isNotEmpty())
            assertTrue("blank component name in ${entry.id}", entry.components.none { it.isBlank() })
        }
    }

    @Test
    fun every_entry_names_an_asset_holding_its_text() {
        entries.forEach { entry ->
            assertTrue(
                "asset outside the synced directory for ${entry.id}: ${entry.assetPath}",
                entry.assetPath.startsWith(LicensesData.ASSETS_DIR),
            )
            assertTrue(
                "asset is not a text file for ${entry.id}: ${entry.assetPath}",
                entry.assetPath.endsWith(".txt") || entry.assetPath.endsWith(".md"),
            )
        }
    }

    @Test
    fun every_named_asset_exists_in_the_repo_root() {
        val repoRoot = repoRoot()
        assertNotNull("repo root not found from ${File("").absolutePath}", repoRoot)

        entries.map { it.assetPath }.distinct().forEach { assetPath ->
            val source = sourceFile(repoRoot!!, assetPath)
            assertTrue("no repo file feeding $assetPath (looked for $source)", source.isFile)
            assertTrue("repo file feeding $assetPath is empty", source.length() > 0L)
        }
    }

    @Test
    fun the_app_entry_ships_the_repo_licence() {
        assertEquals("licenses/GPL-3.0.txt", LicensesData.appEntries.single().assetPath)
    }

    private fun repoRoot(): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull {
                File(it, "THIRD-PARTY-NOTICES.md").isFile && File(it, "licenses").isDirectory
            }

    /** Mirrors the renames `syncOssAssets` applies to the two repo-root files. */
    private fun sourceFile(repoRoot: File, assetPath: String): File = when (assetPath) {
        LicensesData.APP_LICENSE_ASSET -> File(repoRoot, "LICENSE")
        LicensesData.NOTICES_ASSET -> File(repoRoot, "THIRD-PARTY-NOTICES.md")
        else -> File(repoRoot, assetPath)
    }
}
