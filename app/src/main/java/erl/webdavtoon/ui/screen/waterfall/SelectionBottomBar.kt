package erl.webdavtoon.ui.screen.waterfall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.webdavtoon.R
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Surface
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.SelectAll
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(28.dp),
            color = COUITheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, COUITheme.colorScheme.dividerLine),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                    onClick = onToggleSelectAll,
                )

                // Favorite
                MiuixActionItem(
                    painter = painterResource(R.drawable.ic_heart_filled),
                    label = stringResource(R.string.favorite),
                    tint = if (hasSelection) normalColor else disabledColor,
                    enabled = hasSelection,
                    onClick = onToggleFavorite,
                )

                // Share
                MiuixActionItem(
                    painter = painterResource(R.drawable.ic_ior_share),
                    label = stringResource(R.string.share),
                    tint = if (hasSelection) normalColor else disabledColor,
                    enabled = hasSelection,
                    onClick = onShare,
                )

                // Delete
                MiuixActionItem(
                    icon = COUIIcons.Delete,
                    label = stringResource(R.string.delete),
                    tint = if (hasSelection) errorColor else disabledColor,
                    enabled = hasSelection,
                    onClick = onDelete,
                )

                // Close / Dismiss
                MiuixActionItem(
                    icon = COUIIcons.Close,
                    label = stringResource(R.string.cancel),
                    tint = normalColor,
                    enabled = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun RowScope.MiuixActionItem(
    icon: ImageVector? = null,
    painter: Painter? = null,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = tint,
            )
        } else if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = tint,
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = COUITheme.textStyles.footnote2.copy(fontSize = 11.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
