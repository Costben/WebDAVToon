package erl.webdavtoon.ui.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Settings
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
 */
@Composable
fun AppServerSheet(
    show: Boolean,
    slots: List<WebDavSlotUi>,
    isPrivacyMode: Boolean,
    actions: ServerSheetActions,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.app_name),
        onDismissRequest = onDismiss,
        startAction = {
            MiuixIconButton(onClick = onDismiss) {
                MiuixIcon(
                    imageVector = COUIIcons.Light.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = COUITheme.colorScheme.onBackground,
                )
            }
        },
    ) {
        ServerSheetContent(slots, isPrivacyMode, actions, onDismiss)
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
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .scrollEndHaptic()
            .overScrollVertical(),
    ) {
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
                            imageVector = COUIIcons.Light.Add,
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
                            imageVector = COUIIcons.Light.Favorites,
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
                            imageVector = COUIIcons.Light.Settings,
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
        startAction = {
            if (slot.isCurrent) {
                MiuixIcon(
                    imageVector = COUIIcons.Light.Ok,
                    contentDescription = null,
                    tint = COUITheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Spacer(Modifier.size(20.dp))
            }
        },
        endActions = {
            MiuixIconButton(onClick = { actions.onEditSlot(slot.slot) }) {
                MiuixIcon(
                    imageVector = COUIIcons.Light.Edit,
                    contentDescription = stringResource(R.string.edit),
                    tint = COUITheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            MiuixIconButton(onClick = { actions.onDuplicateSlot(slot.slot) }) {
                MiuixIcon(
                    imageVector = COUIIcons.Light.Copy,
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
