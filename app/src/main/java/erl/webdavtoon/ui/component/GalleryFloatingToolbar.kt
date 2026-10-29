// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.FloatingToolbar

/**
 * COUI's [FloatingToolbar] laid out at [GalleryChrome] density.
 *
 * Use it for overlay chrome whose size has to match the gallery's: the toolbar's own corners,
 * colours and shadow stay COUI's, only the density it is measured at changes.
 */
@Composable
fun GalleryFloatingToolbar(
    modifier: Modifier = Modifier,
    color: Color,
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    GalleryChrome {
        FloatingToolbar(
            modifier = modifier,
            color = color,
            cornerRadius = cornerRadius,
            outSidePadding = PaddingValues(0.dp),
            shadowElevation = 6.dp,
            content = content,
        )
    }
}

/**
 * A [GalleryFloatingToolbar] placed the way ColorOS places its bottom bar capsule.
 *
 * The gallery's capsule is 56dp tall with 28dp corners, sits 16dp in from each side and ends 24dp
 * above the screen edge under gesture navigation — the host's 8dp plus the 16dp the capsule is
 * centred by inside the bar's 88dp box. Measured on the PKT110 that is 196px, 56px and 84px at the
 * gallery's 3.5 px/dp; the padding here is inside the density scope so it lands on those pixels
 * whatever the system display size is set to.
 */
@Composable
fun GalleryBottomCapsule(
    modifier: Modifier = Modifier,
    color: Color,
    content: @Composable () -> Unit,
) {
    GalleryChrome {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = BottomFloatingNavigationBarDefaults.navigationBarPadding() + 16.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            FloatingToolbar(
                modifier = modifier,
                color = color,
                cornerRadius = 28.dp,
                outSidePadding = PaddingValues(0.dp),
                shadowElevation = 6.dp,
                content = content,
            )
        }
    }
}
