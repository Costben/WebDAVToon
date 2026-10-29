// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import io.github.suqi8.coui.kmp.utils.rememberGalleryDensity

/**
 * Lays [content] out at the ColorOS Gallery's density instead of the system's.
 *
 * The Gallery rewrites its configuration's `densityDpi` before inflating anything, so its toolbar
 * keys, bottom bar and accessory balls measure the same pixels whatever display size the user
 * picked. This scope reproduces that for the chrome we size to match it: on the PKT110 (system
 * 620dpi, gallery 560dpi) a 40dp action key measures 140px and a 56dp ball 196px, the pixels
 * `com.coloros.gallery3d` reports, where the system density would have drawn 155px and 217px.
 *
 * Everything else — content text, list rows, window insets and the outer layout — stays outside
 * the scope so it keeps following the system density.
 */
@Composable
fun GalleryChrome(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalDensity provides rememberGalleryDensity(),
        content = content,
    )
}
