package erl.webdavtoon.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import erl.webdavtoon.ui.screen.settings.WebDavSlotUi

@Composable
fun AppSideRail(
    slots: List<WebDavSlotUi>,
    isPrivacyMode: Boolean,
    actions: NavigationDrawerActions,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("WebDAVToon", style = MaterialTheme.typography.labelSmall)
                if (isPrivacyMode) Text("Private", style = MaterialTheme.typography.labelSmall)
            }
        },
    ) {
        slots.forEach { slot ->
            NavigationRailItem(
                selected = slot.isCurrent,
                onClick = { actions.onSelectSlot(slot.slot) },
                icon = { Text(if (slot.isCurrent) "✓" else "•") },
                label = { Text(slot.alias.ifBlank { "${slot.slot}" }) },
            )
        }
        NavigationRailItem(
            selected = false,
            onClick = actions.onAddSlot,
            icon = { Text("+") },
        )
        NavigationRailItem(
            selected = false,
            onClick = actions.onOpenFavorites,
            icon = { Text("★") },
        )
        NavigationRailItem(
            selected = false,
            onClick = actions.onOpenSettings,
            icon = { Text("⚙") },
        )
    }
}

@Composable
fun AppAdaptiveNavigationScaffold(
    drawerState: androidx.compose.material3.DrawerState,
    slots: List<WebDavSlotUi>,
    isPrivacyMode: Boolean,
    actions: NavigationDrawerActions,
    modifier: Modifier = Modifier,
    drawerEdgeWidthPercent: Int = 33,
    content: @Composable () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    if (wide) {
        Row(modifier.fillMaxSize()) {
            AppSideRail(slots, isPrivacyMode, actions)
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    } else {
        AppNavigationDrawer(
            drawerState = drawerState,
            slots = slots,
            isPrivacyMode = isPrivacyMode,
            actions = actions,
            modifier = modifier,
            drawerEdgeWidthPercent = drawerEdgeWidthPercent,
            content = content,
        )
    }
}
