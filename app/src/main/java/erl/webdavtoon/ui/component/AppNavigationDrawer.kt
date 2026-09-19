package erl.webdavtoon.ui.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import erl.webdavtoon.ui.UiMode
import erl.webdavtoon.ui.screen.settings.WebDavSlotUi
import kotlinx.coroutines.launch

data class NavigationDrawerActions(
    val onSelectSlot: (Int) -> Unit,
    val onEditSlot: (Int) -> Unit,
    val onDuplicateSlot: (Int) -> Unit,
    val onAddSlot: () -> Unit,
    val onLongClickAddSlot: () -> Unit,
    val onExitPrivacy: () -> Unit,
    val onOpenFavorites: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onCloseDrawer: () -> Unit = {},
)

@Composable
fun AppNavigationDrawer(
    drawerState: DrawerState,
    slots: List<WebDavSlotUi>,
    isPrivacyMode: Boolean,
    actions: NavigationDrawerActions,
    modifier: Modifier = Modifier,
    uiMode: UiMode = UiMode.Miuix,
    drawerEdgeWidthPercent: Int = 33,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun close() {
        actions.onCloseDrawer()
        scope.launch { drawerState.close() }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val maxEdgePx = screenWidthPx * (drawerEdgeWidthPercent.coerceIn(0, 100) / 100f)

    var touchWithinEdge by remember { mutableStateOf(false) }

    val gesturesEnabled = when {
        drawerEdgeWidthPercent <= 0 -> false
        drawerState.isOpen -> true
        else -> touchWithinEdge
    }

    val drawerModifier = modifier.pointerInput(drawerEdgeWidthPercent, drawerState.isOpen) {
        if (drawerEdgeWidthPercent <= 0 || drawerState.isOpen) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val down = event.changes.firstOrNull()
                if (down != null) {
                    touchWithinEdge = down.pressed && down.position.x <= maxEdgePx
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = drawerModifier,
        gesturesEnabled = gesturesEnabled,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = if (uiMode == UiMode.Miuix) {
                    MaterialTheme.colorScheme.surface
                } else MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                DrawerContent(slots, isPrivacyMode, actions, ::close)
            }
        },
        content = content,
    )
}

@Composable
fun DrawerContent(
    slots: List<WebDavSlotUi>,
    isPrivacyMode: Boolean,
    actions: NavigationDrawerActions,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("WebDAVToon", style = MaterialTheme.typography.titleLarge)
            if (isPrivacyMode) {
                Text("Private", style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = actions.onExitPrivacy) {
                    Text("Exit")
                }
            }
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(slots, key = { it.slot }) { slot ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(slot.alias.ifBlank { "Server ${slot.slot}" })
                            Text(
                                "${slot.protocol}://${slot.url}:${slot.port}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    selected = slot.isCurrent,
                    onClick = {
                        actions.onSelectSlot(slot.slot)
                        onClose()
                    },
                    icon = {
                        Text(if (slot.isCurrent) "✓" else "•")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    badge = {
                        Row {
                            IconButton(onClick = { actions.onEditSlot(slot.slot) }) {
                                Text("Edit")
                            }
                            IconButton(onClick = { actions.onDuplicateSlot(slot.slot) }) {
                                Text("Copy")
                            }
                        }
                    },
                )
            }
        }
        Button(
            onClick = actions.onAddSlot,
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = actions.onAddSlot,
                onLongClick = actions.onLongClickAddSlot,
                role = Role.Button,
            ),
        ) {
            Text("+")
            Spacer(Modifier.padding(4.dp))
            Text("Add server")
        }
        NavigationDrawerItem(
            label = { Text("Favorites") },
            selected = false,
            onClick = { actions.onOpenFavorites(); onClose() },
            icon = { Text("★") },
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = false,
            onClick = { actions.onOpenSettings(); onClose() },
            icon = { Text("⚙") },
        )
    }
}
