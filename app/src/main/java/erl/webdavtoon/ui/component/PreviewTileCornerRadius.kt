// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Corner radius for a single photo tile inside a folder-card preview mosaic.
 *
 * The mosaic container is already clipped to the card corner radius, which only
 * rounds the outermost corners of the composite. Rounding each tile as well makes
 * the individual photos read as separate, evenly rounded thumbnails instead of a
 * single hard-edged collage.
 *
 * Kept equal to the card corner radius so a tile sitting in a card corner has a
 * concentric-looking arc.
 */
val PreviewTileCornerRadius = 12.dp

/**
 * Placeholder layer behind one mosaic tile — exactly tile-sized and tile-rounded, so it reads
 * as a soft shadow ring hugging the thumbnail rather than as a panel behind the whole mosaic.
 *
 * It is per tile, never a single sheet behind the mosaic: with a sheet, the gaps between tiles
 * showed a second background colour and the mosaic looked like a card inside a card.
 */
val PreviewTilePlaceholder = Color(0x10000000)
