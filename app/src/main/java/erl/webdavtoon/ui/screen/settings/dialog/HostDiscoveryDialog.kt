// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.settings.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import erl.webdavtoon.DiscoveredHost
import erl.webdavtoon.R
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

/** How long an empty result list must persist before the empty state is shown. */
private const val EMPTY_STATE_DELAY_MS = 3_000L

/**
 * LAN host discovery picker, as a COUI bottom sheet.
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

    HostDiscoveryDialogContent(hosts, searching, showEmpty, visible, onSelect, onDismiss)
}

/**
 * `true` once a search has been running with nothing found for
 * [EMPTY_STATE_DELAY_MS].
 *
 * The timer is keyed on the *searching* flag alone. Keying it on the host list
 * as well restarted the countdown on every emission from discovery, so the empty
 * state never became reachable while the flow was still updating.
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

@Composable
private fun HostDiscoveryDialogContent(
    hosts: List<DiscoveredHost>,
    searching: Boolean,
    showEmpty: Boolean,
    visible: Boolean,
    onSelect: (DiscoveredHost) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = visible,
        title = stringResource(R.string.discover_devices),
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (searching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(size = 20.dp)
                        Text(
                            stringResource(R.string.discovery_searching),
                            style = COUITheme.textStyles.subtitle,
                        )
                    }
                }
                if (showEmpty) {
                    Text(
                        stringResource(R.string.discovery_empty),
                        style = COUITheme.textStyles.body2,
                    )
                }
                hosts.forEach { host ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            text = host.displayName,
                            onClick = { onSelect(host) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(host.host, style = COUITheme.textStyles.footnote1)
                    }
                }
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
