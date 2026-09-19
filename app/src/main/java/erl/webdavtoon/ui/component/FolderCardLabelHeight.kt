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

/**
 * Fixed height for the folder-card subtitle line (Miuix `footnote1`).
 *
 * Same reasoning as [FolderCardLabelHeight]: the subtitle uses Miuix `footnote1`,
 * whose `lineHeight` is unspecified, so its box would follow the fallback font
 * metrics. Pinning it keeps every folder card an identical height so the
 * staggered grid cannot accumulate a left/right column drift.
 */
val FolderCardSubtitleHeight: Dp
    @Composable get() = with(LocalDensity.current) { FolderCardSubtitleHeightSp.toDp() }

private val FolderCardSubtitleHeightSp = 19.sp

/**
 * Fixed height for the folder-card title line in the Material UI mode.
 *
 * Material's `titleMedium` box can still grow with fallback glyph metrics for
 * mixed scripts, so it is pinned here as well to keep folder cards uniform.
 */
val FolderCardMaterialTitleHeight: Dp
    @Composable get() = with(LocalDensity.current) { FolderCardMaterialTitleHeightSp.toDp() }

private val FolderCardMaterialTitleHeightSp = 24.sp

/**
 * Fixed height for the folder-card subtitle line in the Material UI mode.
 *
 * See [FolderCardMaterialTitleHeight].
 */
val FolderCardMaterialSubtitleHeight: Dp
    @Composable get() = with(LocalDensity.current) { FolderCardMaterialSubtitleHeightSp.toDp() }

private val FolderCardMaterialSubtitleHeightSp = 16.sp
