package erl.webdavtoon.ui.screen.settings.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import erl.webdavtoon.DiscoveredHost
import erl.webdavtoon.ServerConfigDialogHelper
import erl.webdavtoon.ui.theme.WebDAVToonTheme

/**
 * Static, backend-free proof that the dialogs render.
 *
 * [ServerConfigDialogState] is pure data, so a filled-in form needs no ViewModel, no
 * `SettingsManager` and no Rust bridge. `WebDAVToonTheme` supplies the COUI theme, so the
 * previews never hand-roll a `CompositionLocalProvider`.
 */
private fun previewServerConfigState() = ServerConfigDialogState(
    slot = 1,
    alias = "NAS Photos",
    protocol = "https",
    url = "dav.example.com/remote.php/dav",
    port = "443",
    username = "reader",
    // A placeholder, never a resolved credential: the UI layer must not read the
    // credential store and a preview must not embed a real secret.
    password = "preview-secret",
    domain = "",
    rememberPassword = true,
    isPrivate = false,
    showPassword = false,
    testing = false,
    testResult = null,
    error = null,
    protocols = ServerConfigDialogHelper.PROTOCOLS.toList(),
    shares = null,
    enumeratingShares = false,
    discoveringHosts = false,
    discoveredHosts = emptyList(),
    isPrivacyMode = false,
)

private val previewDiscoveredHosts = listOf(
    DiscoveredHost(protocol = "webdav", displayName = "NAS", host = "192.168.1.10", port = 5005),
)

@Preview(name = "ServerConfig", showBackground = true, widthDp = 400, heightDp = 720)
@Composable
private fun ServerConfigDialogPreview() {
    WebDAVToonTheme {
        ServerConfigDialog(
            state = previewServerConfigState(),
            visible = true,
            onAction = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "HostDiscovery", showBackground = true, widthDp = 400, heightDp = 720)
@Composable
private fun HostDiscoveryDialogPreview() {
    WebDAVToonTheme {
        HostDiscoveryDialog(
            hosts = previewDiscoveredHosts,
            searching = false,
            visible = true,
            onSelect = {},
            onDismiss = {},
        )
    }
}
