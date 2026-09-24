// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the license texts the `syncOssAssets` Gradle task shipped into the APK assets.
 *
 * The license files run to tens of kilobytes each, so every text is read once off the main thread
 * and kept for the rest of the screen's life; the state holder also survives configuration changes.
 */
class LicensesViewModel : ViewModel() {

    private val cache = HashMap<String, String>()

    /** Returns the already loaded text for [assetPath], or `null` when it still has to be read. */
    fun cachedText(assetPath: String): String? = cache[assetPath]

    /** Reads [assetPath] from the APK assets on the IO dispatcher and caches the result. */
    suspend fun loadText(context: Context, assetPath: String): String {
        cache[assetPath]?.let { return it }
        val text = withContext(Dispatchers.IO) {
            context.assets.open(assetPath).use { stream -> stream.bufferedReader().readText() }
        }
        cache[assetPath] = text
        return text
    }
}
