package erl.webdavtoon.ui.screen.settings.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import erl.webdavtoon.DiscoveredHost
import erl.webdavtoon.ui.LocalUiMode
import erl.webdavtoon.ui.UiMode
import kotlinx.coroutines.delay

/** How long an empty result list must persist before the empty state is shown. */
private const val EMPTY_STATE_DELAY_MS = 3_000L

/**
 * LAN host discovery picker. Dispatches by [LocalUiMode]: the Miuix track uses
 * an `OverlayBottomSheet`, the Material track an `AlertDialog`. Both render the
 * same rows, mirroring `item_discovered_host.xml` (name over endpoint).
 */
@Composable
fun HostDiscoveryDialog(
    hosts: List<DiscoveredHost>,
    searching: Boolean,
    visible: Boolean,
    onSelect: (DiscoveredHost) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val showEmpty = rememberEmptyStateShown(searching = searching, hostCount = hosts.size)

    when (LocalUiMode.current) {
        UiMode.Miuix -> HostDiscoveryMiuix(hosts, searching, showEmpty, visible, onSelect, onDismiss)
        UiMode.Material -> HostDiscoveryMaterial(hosts, searching, showEmpty, onSelect, onDismiss)
    }
}

/**
 * `true` once a search has been running with nothing found for
 * [EMPTY_STATE_DELAY_MS].
 *
 * The timer is keyed on the *searching* flag alone. Keying it on the host list
 * as well — as the first pass did — restarted the countdown on every emission
 * from discovery, so the empty state never became reachable while the flow was
 * still updating.
 */
@Composable
internal fun rememberEmptyStateShown(searching: Boolean, hostCount: Int): Boolean {
    var delayElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(searching) {
        delayElapsed = false
        if (searching) {
            delay(EMPTY_STATE_DELAY_MS)
            delayElapsed = true
        }
    }
    return delayElapsed && searching && hostCount == 0
}
