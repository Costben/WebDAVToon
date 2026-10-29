// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Filter
import io.github.suqi8.coui.kmp.icon.extended.Layers
import io.github.suqi8.coui.kmp.icon.extended.MoreCircle

/**
 * The single row of top-bar action icons shared by every screen.
 *
 * COUI icons all declare a 24dp intrinsic size, but their glyphs fill that box very
 * unevenly: `Sidebar` covered 709px on the reference device while `More` covered 156px,
 * so the group read as three unrelated icons at three different sizes. The trio below was
 * picked by rendering candidates at real 24dp size and keeping the three whose glyph
 * coverage matches (`trio.png` in `.artifacts/adb/2026-09-21/icons-and-corners/`).
 *
 * `Layers` is the drawer metaphor for the server panel, which slides up from the bottom
 * rather than in from the side.
 */
object TopBarIcons {
    /**
     * The glyph size inside the 40dp halo. COUI's own default is 24dp, which fills 60% of the
     * circle; the system gallery on the same device draws an 18.6dp glyph in a 36dp circle, i.e.
     * 52%. 20dp keeps that proportion without dropping the touch target.
     */
    val Size = 20.dp

    /** Opens the server panel (a bottom sheet, hence the stacked-drawer glyph). */
    val Panel = COUIIcons.Light.Layers

    /** Expands the filter/search field. */
    val Filter = COUIIcons.Light.Filter

    /** Opens the overflow menu. */
    val More = COUIIcons.Light.MoreCircle
}

@Composable
fun TopBarActionIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(TopBarIcons.Size),
        tint = tint,
    )
}

/**
 * A toolbar icon button wearing the ColorOS 17 halo: a 40dp circle with a radial fill, a
 * material rim and an outside glow, springing to 0.89 while pressed.
 *
 * COUI's [HighlightedIconButton] draws the halo itself, so the 40dp action slot is the only
 * thing the caller has to supply. Wrapping it in [io.github.suqi8.coui.kmp.basic.IconButton]
 * would hand the halo a 48dp rounded-square ripple instead.
 */
@Composable
fun HighlightedTopBarAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
) {
    HighlightedIconButton(
        onClick = onClick,
        modifier = modifier.couiTopAppBarActionItem(),
    ) {
        TopBarActionIcon(icon, contentDescription, tint = iconTint)
    }
}
