package erl.webdavtoon.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * Fixed height for a folder-card label line.
 *
 * Miuix's `footnote1` leaves `lineHeight` unspecified, and Miuix's `Text`
 * ignores both `style.lineHeight` and its own `lineHeight` argument when it
 * measures the line box. The box therefore follows the fallback font metrics,
 * so a Latin-only name ("Alpha91") is shorter than one containing CJK glyphs
 * ("るとんにき"). In the staggered folder grid that difference accumulates
 * down each column and desyncs the rows.
 *
 * Pinning the label to an explicit height makes every folder card identical
 * regardless of the script in its name. It is expressed in sp so it still
 * grows with the user's font scale.
 */
val FolderCardLabelHeight: Dp
    @Composable get() = with(LocalDensity.current) { FolderCardLabelHeightSp.toDp() }

private val FolderCardLabelHeightSp = 20.sp
