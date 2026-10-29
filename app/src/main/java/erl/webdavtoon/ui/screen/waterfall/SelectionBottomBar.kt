// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.waterfall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.webdavtoon.R
import erl.webdavtoon.ui.component.GalleryBottomCapsule
import erl.webdavtoon.ui.component.rememberStrongHaptic
import erl.webdavtoon.ui.component.rememberTapHaptic
import erl.webdavtoon.ui.theme.LocalDarkTheme
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.FavoritesFill
import io.github.suqi8.coui.kmp.icon.extended.SelectAll
import io.github.suqi8.coui.kmp.icon.extended.Share
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * HyperOS / Miuix styled floating capsule selection bar for waterfall grid.
 */
@Composable
fun SelectionBottomBarMiuix(
    selectedCount: Int,
    isAllSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSelection = selectedCount > 0
    val normalColor = COUITheme.colorScheme.onSurface
    val disabledColor = COUITheme.colorScheme.disabledOnSurface
    val errorColor = COUITheme.colorScheme.error
    val tapHaptic = rememberTapHaptic()
    val strongHaptic = rememberStrongHaptic()
    val isDark = LocalDarkTheme.current || isSystemInDarkTheme()
    val toolbarBackgroundColor = if (isDark) {
        Color(0xF51C1C1E)
    } else {
        Color(0xF5F5F5F7)
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        GalleryBottomCapsule(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            color = toolbarBackgroundColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 48dp slots inside a 56dp capsule, as the gallery's tab row: the 4dp here
                    // and the 48dp in MiuixActionItem add up to the capsule's 56dp.
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Select All / Deselect All
                MiuixActionItem(
                    icon = COUIIcons.SelectAll,
                    label = stringResource(
                        if (isAllSelected) R.string.unselect_all else R.string.select_all
                    ),
                    tint = normalColor,
                    enabled = true,
                    onClick = {
                        tapHaptic()
                        onToggleSelectAll()
                    },
                )

                // Favorite
                MiuixActionItem(
                    icon = COUIIcons.Light.FavoritesFill,
                    label = stringResource(R.string.favorite),
                    tint = if (hasSelection) normalColor else disabledColor,
                    enabled = hasSelection,
                    onClick = {
                        tapHaptic()
                        onToggleFavorite()
                    },
                )

                // Share
                MiuixActionItem(
                    icon = COUIIcons.Light.Share,
                    label = stringResource(R.string.share),
                    tint = if (hasSelection) normalColor else disabledColor,
                    enabled = hasSelection,
                    onClick = {
                        tapHaptic()
                        onShare()
                    },
                )

                // Delete
                MiuixActionItem(
                    icon = COUIIcons.Delete,
                    label = stringResource(R.string.delete),
                    tint = if (hasSelection) errorColor else disabledColor,
                    enabled = hasSelection,
                    onClick = {
                        strongHaptic()
                        onDelete()
                    },
                )

                // Close / Dismiss
                MiuixActionItem(
                    icon = COUIIcons.Close,
                    label = stringResource(R.string.cancel),
                    tint = normalColor,
                    enabled = true,
                    onClick = {
                        tapHaptic()
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun RowScope.MiuixActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            // The gallery's tab slot measures 168px — 48dp at its 3.5 px/dp — with a 24dp icon
            // box and a 10sp label.
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = COUITheme.textStyles.footnote2.copy(fontSize = 10.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
