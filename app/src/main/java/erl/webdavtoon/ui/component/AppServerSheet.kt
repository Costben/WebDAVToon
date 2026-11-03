// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.webdavtoon.R
import erl.webdavtoon.ui.screen.settings.WebDavSlotUi
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon as MiuixIcon
import io.github.suqi8.coui.kmp.basic.IconButton as MiuixIconButton
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.layout.BottomSheetDefaults
import io.github.suqi8.coui.kmp.layout.SheetActionButton
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

data class ServerSheetActions(
    val onSelectSlot: (Int) -> Unit,
    val onEditSlot: (Int) -> Unit,
    val onDuplicateSlot: (Int) -> Unit,
    val onAddSlot: () -> Unit,
    val onLongClickAddSlot: () -> Unit,
    val onExitPrivacy: () -> Unit,
    val onOpenFavorites: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * Server picker as a COUI bottom sheet.
 *
 * COUI ships no drawer component, so the former Material3 modal drawer became an
 * [OverlayBottomSheet] - the closest COUI-native surface for a transient list of servers.
 * The sheet opens from the top bar button only.
 *
 * It wears the ColorOS 17 panel: no drag handle, the circled header action of
 * [SheetActionButton], and a 28sp title with a status line inside the content instead of the
 * sheet's centred title row.
 */
@Composable
fun AppServerSheet(
    show: Boolean,
    slots: List<WebDavSlotUi>,
    isPrivacyMode: Boolean,
    actions: ServerSheetActions,
    onDismiss: () -> Unit,
    isLocalMode: Boolean = false,
) {
    OverlayBottomSheet(
        show = show,
        // The panel carries its own toolbar and header, so the sheet draws neither a handle nor a
        // title row; it still reserves the handle strip so the header keeps the native offset.
        showDragHandle = false,
        insideMargin = DpSize(16.dp, 40.dp),
        onDismissRequest = onDismiss,
        startAction = {
            SheetActionButton(
                onClick = onDismiss,
                // The sheet's title row adds its own 16dp padding on top of the panel's inset, so
                // the button would sit 16dp inside the cards. Pull it back onto their edge.
                modifier = Modifier.offset(x = -BottomSheetDefaults.HeaderHorizontalPadding),
            ) {
                MiuixIcon(
                    imageVector = COUIIcons.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = COUITheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    ) {
        ServerSheetContent(slots, isPrivacyMode, actions, onDismiss, isLocalMode = isLocalMode)
    }
}

/**
 * Mirrors the COUI example's bottom-sheet layout: a scrolling column of `SmallTitle` +
 * `Card` groups whose rows are split by `HorizontalDivider(16.dp)`.
 */
@Composable
fun ServerSheetContent(
    slots: List<WebDavSlotUi>,
    isPrivacyMode: Boolean,
    actions: ServerSheetActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isLocalMode: Boolean = false,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .scrollEndHaptic()
            .overScrollVertical(),
    ) {
        item(key = "header") {
            ServerSheetHeader(
                title = stringResource(R.string.app_name),
                statusText = serverStatusText(slots, isLocalMode),
            )
        }
        if (!isLocalMode) {
            item(key = "servers") {
                SmallTitle(
                    text = stringResource(R.string.webdav_server),
                    insideMargin = PaddingValues(16.dp, 8.dp),
                )
                SheetCard {
                    slots.forEachIndexed { index, slot ->
                        if (index > 0) SheetDivider()
                        ServerSlotRow(slot, actions, onDismiss)
                    }
                    if (slots.isNotEmpty()) SheetDivider()
                    BasicComponent(
                        title = stringResource(R.string.add_webdav_server),
                        startAction = {
                            MiuixIcon(
                                imageVector = COUIIcons.Add,
                                contentDescription = null,
                                tint = COUITheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        // BasicComponent's own clickable is left off so the combined handler owns both
                        // the tap (add a server) and the long press (enter privacy mode).
                        onClick = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = actions.onAddSlot,
                                onLongClick = actions.onLongClickAddSlot,
                                role = Role.Button,
                            ),
                    )
                }
            }
        }

        if (isPrivacyMode) {
            item(key = "privacy") {
                SmallTitle(
                    text = stringResource(R.string.privacy_mode),
                    insideMargin = PaddingValues(16.dp, 8.dp),
                )
                SheetCard {
                    BasicComponent(
                        title = stringResource(R.string.exit_privacy_mode),
                        summary = stringResource(R.string.privacy_mode_active_summary),
                        onClick = {
                            actions.onExitPrivacy()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item(key = "other") {
            SmallTitle(
                text = stringResource(R.string.sheet_other_section),
                insideMargin = PaddingValues(16.dp, 8.dp),
            )
            SheetCard {
                BasicComponent(
                    title = stringResource(R.string.favorites),
                    startAction = {
                        MiuixIcon(
                            imageVector = COUIIcons.Favorites,
                            contentDescription = null,
                            tint = COUITheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        actions.onOpenFavorites()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SheetDivider()
                BasicComponent(
                    title = stringResource(R.string.settings),
                    startAction = {
                        MiuixIcon(
                            imageVector = COUIIcons.Settings,
                            contentDescription = null,
                            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        actions.onOpenSettings()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "inset") {
            Spacer(
                Modifier.padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                ),
            )
        }
    }
}

/**
 * The top of the panel: the 28sp title over the `● status` line, as the ColorOS 17 panel opens.
 */
@Composable
private fun ServerSheetHeader(title: String, statusText: String) {
    val dotColor = COUITheme.colorScheme.onSurfaceVariantActions
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(3.dp))
        // The 28sp line box is taller than its ink, so the text is centred inside a pinned-height
        // box the way the native panel positions it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(27.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                modifier = Modifier.wrapContentHeight(unbounded = true),
                fontSize = 28.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = COUITheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(6.dp)) {
                drawCircle(color = dotColor)
            }
            Spacer(Modifier.width(5.dp))
            Text(
                text = statusText,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                color = COUITheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** `● 当前：FN`, `● 未选择服务器`, or the local-mode label when no server can be connected. */
@Composable
private fun serverStatusText(slots: List<WebDavSlotUi>, isLocalMode: Boolean): String {
    if (isLocalMode) return stringResource(R.string.local_photos)
    val current = slots.firstOrNull { it.isCurrent }
        ?: return stringResource(R.string.server_sheet_status_none)
    return stringResource(
        R.string.server_sheet_status_current,
        current.alias.ifBlank { stringResource(R.string.server_slot, current.slot) },
    )
}

@Composable
private fun ServerSlotRow(
    slot: WebDavSlotUi,
    actions: ServerSheetActions,
    onDismiss: () -> Unit,
) {
    BasicComponent(
        title = slot.alias.ifBlank { stringResource(R.string.server_slot, slot.slot) },
        summary = "${slot.protocol}://${slot.url}:${slot.port}",
        summaryColor = BasicComponentDefaults.summaryColor(
            if (slot.isCurrent) {
                COUITheme.colorScheme.primary
            } else {
                COUITheme.colorScheme.onSurfaceVariantSummary
            }
        ),
        // The tick is centred on the row instead of pinned to the title line, and every row keeps
        // the same 24dp leading slot so the titles line up whether or not it is the current one.
        centerStartAction = true,
        startAction = {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (slot.isCurrent) {
                    MiuixIcon(
                        imageVector = COUIIcons.Ok,
                        contentDescription = null,
                        tint = COUITheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        endActions = {
            MiuixIconButton(onClick = { actions.onEditSlot(slot.slot) }) {
                MiuixIcon(
                    imageVector = COUIIcons.Edit,
                    contentDescription = stringResource(R.string.edit),
                    tint = COUITheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            MiuixIconButton(onClick = { actions.onDuplicateSlot(slot.slot) }) {
                MiuixIcon(
                    imageVector = COUIIcons.Copy,
                    contentDescription = stringResource(R.string.duplicate_server),
                    tint = COUITheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        onClick = {
            actions.onSelectSlot(slot.slot)
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A sheet group card, tinted like the COUI example's `secondaryContainer` cards. */
@Composable
private fun SheetCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(color = COUITheme.colorScheme.secondaryContainer),
    ) {
        content()
    }
}

@Composable
private fun SheetDivider() {
    MiuixHorizontalDivider(Modifier.padding(horizontal = 16.dp))
}
