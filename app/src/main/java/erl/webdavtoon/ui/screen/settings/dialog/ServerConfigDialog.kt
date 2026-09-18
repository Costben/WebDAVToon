package erl.webdavtoon.ui.screen.settings.dialog

import androidx.compose.runtime.Composable
import erl.webdavtoon.ui.LocalUiMode
import erl.webdavtoon.ui.UiMode

/**
 * Server configuration dialog. Dispatches to the Miuix or Material track by the
 * ambient [LocalUiMode], matching `SettingsScreen` and the rest of the
 * dual-track UI.
 *
 * [visible] drives the underlying dialog primitive; the caller owns it so the
 * dialog can actually be dismissed (a hard-coded `true` would leave it stuck on
 * screen forever).
 */
@Composable
fun ServerConfigDialog(
    state: ServerConfigDialogState,
    visible: Boolean,
    onAction: (ServerConfigAction) -> Unit,
    onDismiss: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ServerConfigDialogMiuix(state, visible, onAction, onDismiss)
        UiMode.Material -> ServerConfigDialogMaterial(state, visible, onAction, onDismiss)
    }
}
