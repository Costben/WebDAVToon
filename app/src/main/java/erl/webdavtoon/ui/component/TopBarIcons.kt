package erl.webdavtoon.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Icon
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
    val Size = 24.dp

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
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(TopBarIcons.Size),
    )
}
