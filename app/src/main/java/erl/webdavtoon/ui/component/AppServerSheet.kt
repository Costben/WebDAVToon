package erl.webdavtoon.ui.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import erl.webdavtoon.R
import erl.webdavtoon.ui.screen.settings.WebDavSlotUi
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Button as MiuixButton
import io.github.suqi8.coui.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon as MiuixIcon
import io.github.suqi8.coui.kmp.basic.IconButton as MiuixIconButton
import io.github.suqi8.coui.kmp.basic.Text as MiuixText
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.max

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
        title = "WebDAVToon",
        onDismissRequest = onDismiss,
        content = {
            ServerSheetContent(slots, isPrivacyMode, actions, onDismiss)
        },
    )
}

@Composable
fun ServerSheetContent(
    slots: List<WebDavSlotUi>,
    isPrivacyMode: Boolean,
    actions: ServerSheetActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isPrivacyMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixText(
                    "Private",
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.primary,
                )
                MiuixButton(onClick = actions.onExitPrivacy) {
                    MiuixText("Exit", color = COUITheme.colorScheme.onPrimary)
                }
            }
        }
        MiuixHorizontalDivider()
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(slots, key = { it.slot }) { slot ->
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
        }
        MiuixButton(
            onClick = actions.onAddSlot,
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = actions.onAddSlot,
                onLongClick = actions.onLongClickAddSlot,
                role = Role.Button,
            ),
        ) {
            MiuixIcon(
                imageVector = COUIIcons.Light.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(4.dp))
            MiuixText(stringResource(R.string.add_webdav_server))
        }
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
            onClick = { actions.onOpenFavorites(); onDismiss() },
            modifier = Modifier.fillMaxWidth(),
        )
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
            onClick = { actions.onOpenSettings(); onDismiss() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Left-edge swipe that opens the server sheet.
 *
 * Carried over from the drawer era: the system back gesture owns the outer ~24dp of the
 * left edge and Android 15+ no longer lets apps exclude those edges, so the strip always
 * reaches past the system gesture inset. A horizontal drag starting inside the strip that
 * crosses the touch slop opens the sheet; vertical drags from the same strip stay with the
 * grid. [edgeWidthPercent] <= 0 disables the gesture.
 */
@Composable
fun ServerSheetEdgeSwipe(
    onOpen: () -> Unit,
    edgeWidthPercent: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val configuredEdgePx = screenWidthPx * (edgeWidthPercent.coerceIn(0, 100) / 100f)
    val systemGestureInsetPx = with(density) {
        WindowInsets.systemGestures.getLeft(this, layoutDirection).toFloat()
    }
    val edgeWidthPx = if (edgeWidthPercent <= 0) {
        0f
    } else {
        max(configuredEdgePx, systemGestureInsetPx + with(density) { 32.dp.toPx() })
    }

    Box(
        modifier = modifier.pointerInput(edgeWidthPx) {
            if (edgeWidthPx <= 0f) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.position.x > edgeWidthPx) return@awaitEachGesture
                var overSlop = 0f
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    overSlop = over
                    if (over > 0f) change.consume()
                } ?: return@awaitEachGesture
                if (overSlop > 0f) {
                    drag.consume()
                    onOpen()
                }
            }
        }
    ) {
        content()
    }
}
