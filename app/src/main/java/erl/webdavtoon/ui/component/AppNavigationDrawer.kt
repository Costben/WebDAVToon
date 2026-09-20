package erl.webdavtoon.ui.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import erl.webdavtoon.R
import erl.webdavtoon.ui.UiMode
import erl.webdavtoon.ui.screen.settings.WebDavSlotUi
import kotlin.math.max
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Settings

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
    val configuredEdgePx = screenWidthPx * (drawerEdgeWidthPercent.coerceIn(0, 100) / 100f)

    // The system back gesture owns the outer ~24dp of the left edge and swallows any swipe that
    // starts there, and Android 15+ no longer lets apps exclude the back-gesture edges (verified on
    // Android 16: exclusion rects touching the edge are ignored). So a swipe that starts inside that
    // zone can never reach the app. What the app can control is the part of the edge that follows it:
    // keep the configured strip, but always reach past the system gesture inset so an edge swipe just
    // inside the zone still opens the drawer. 0% keeps the gesture disabled.
    val layoutDirection = LocalLayoutDirection.current
    val systemGestureInsetPx = with(density) {
        WindowInsets.systemGestures.getLeft(this, layoutDirection).toFloat()
    }
    val edgeWidthPx = if (drawerEdgeWidthPercent <= 0) {
        0f
    } else {
        max(configuredEdgePx, systemGestureInsetPx + with(density) { 32.dp.toPx() })
    }

    // Material3's own drawer gesture cannot honour an edge width (it would open from anywhere in the
    // content), and gating `gesturesEnabled` on "did this gesture start inside the edge" is always one
    // recomposition too late - the drag that should have started never sees an enabled gesture. So
    // Material3 only handles dragging the already-open drawer closed, and the edge swipe to open is
    // detected here: a horizontal drag that starts inside the edge area and crosses the horizontal
    // touch slop to the right. Vertical drags from the same strip stay with the grid.
    val drawerModifier = modifier
        .pointerInput(edgeWidthPx) {
            if (edgeWidthPx <= 0f) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (drawerState.isOpen || down.position.x > edgeWidthPx) return@awaitEachGesture
                var overSlop = 0f
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    overSlop = over
                    if (over > 0f) change.consume()
                } ?: return@awaitEachGesture
                if (overSlop > 0f) {
                    drag.consume()
                    scope.launch { drawerState.open() }
                }
            }
        }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = drawerModifier,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerState = drawerState,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "WebDAVToon",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isPrivacyMode) {
                Text(
                    "Private",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = actions.onExitPrivacy) {
                    Text("Exit", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(slots, key = { it.slot }) { slot ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(
                                slot.alias.ifBlank { stringResource(R.string.server_slot, slot.slot) },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${slot.protocol}://${slot.url}:${slot.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (slot.isCurrent) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                    selected = slot.isCurrent,
                    onClick = {
                        actions.onSelectSlot(slot.slot)
                        onClose()
                    },
                    icon = {
                        if (slot.isCurrent) {
                            Icon(
                                imageVector = MiuixIcons.Light.Ok,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Spacer(Modifier.size(20.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        unselectedContainerColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    badge = {
                        Row {
                            IconButton(onClick = { actions.onEditSlot(slot.slot) }) {
                                Icon(
                                    imageVector = MiuixIcons.Light.Edit,
                                    contentDescription = stringResource(R.string.edit),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(onClick = { actions.onDuplicateSlot(slot.slot) }) {
                                Icon(
                                    imageVector = MiuixIcons.Light.Copy,
                                    contentDescription = stringResource(R.string.duplicate_server),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                )
            }
        }
        Button(
            onClick = actions.onAddSlot,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = actions.onAddSlot,
                onLongClick = actions.onLongClickAddSlot,
                role = Role.Button,
            ),
        ) {
            Icon(
                imageVector = MiuixIcons.Light.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.padding(4.dp))
            Text(stringResource(R.string.add_webdav_server))
        }
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.favorites)) },
            selected = false,
            onClick = { actions.onOpenFavorites(); onClose() },
            icon = {
                Icon(
                    imageVector = MiuixIcons.Light.Favorites,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            },
            colors = NavigationDrawerItemDefaults.colors(
                unselectedContainerColor = Color.Transparent,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.settings)) },
            selected = false,
            onClick = { actions.onOpenSettings(); onClose() },
            icon = {
                Icon(
                    imageVector = MiuixIcons.Light.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            },
            colors = NavigationDrawerItemDefaults.colors(
                unselectedContainerColor = Color.Transparent,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}
