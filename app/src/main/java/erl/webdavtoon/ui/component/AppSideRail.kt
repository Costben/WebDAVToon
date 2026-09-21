package erl.webdavtoon.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import erl.webdavtoon.R
import erl.webdavtoon.ui.screen.settings.WebDavSlotUi
import io.github.suqi8.coui.kmp.basic.NavigationRail
import io.github.suqi8.coui.kmp.basic.NavigationRailItem
import io.github.suqi8.coui.kmp.basic.Text as MiuixText
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.Folder
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun AppSideRail(
    slots: List<WebDavSlotUi>,
    isPrivacyMode: Boolean,
    actions: ServerSheetActions,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier,
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MiuixText("WebDAVToon", style = COUITheme.textStyles.footnote2)
                if (isPrivacyMode) {
                    MiuixText("Private", style = COUITheme.textStyles.footnote2)
                }
            }
        },
    ) {
        slots.forEach { slot ->
            NavigationRailItem(
                selected = slot.isCurrent,
                onClick = { actions.onSelectSlot(slot.slot) },
                icon = COUIIcons.Light.Folder,
                label = slot.alias.ifBlank { "${slot.slot}" },
            )
        }
        NavigationRailItem(
            selected = false,
            onClick = actions.onAddSlot,
            icon = COUIIcons.Light.Add,
            label = stringResource(R.string.add_webdav_server),
        )
        NavigationRailItem(
            selected = false,
            onClick = actions.onOpenFavorites,
            icon = COUIIcons.Light.Favorites,
            label = stringResource(R.string.favorites),
        )
        NavigationRailItem(
            selected = false,
            onClick = actions.onOpenSettings,
            icon = COUIIcons.Light.Settings,
            label = stringResource(R.string.settings),
        )
    }
}

/**
 * Wide screens get the persistent COUI navigation rail; narrow screens get the COUI
 * server bottom sheet, which opens from the top bar button only.
 */
@Composable
fun AppAdaptiveNavigationScaffold(
    showServerSheet: Boolean,
    onDismissServerSheet: () -> Unit,
    slots: List<WebDavSlotUi>,
    isPrivacyMode: Boolean,
    actions: ServerSheetActions,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    if (wide) {
        Row(modifier.fillMaxSize()) {
            AppSideRail(slots, isPrivacyMode, actions)
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    } else {
        Box(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) { content() }
            AppServerSheet(
                show = showServerSheet,
                slots = slots,
                isPrivacyMode = isPrivacyMode,
                actions = actions,
                onDismiss = onDismissServerSheet,
            )
        }
    }
}
