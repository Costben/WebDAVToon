package erl.webdavtoon.ui.screen.settings.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import erl.webdavtoon.DiscoveredHost
import erl.webdavtoon.R

/** Material 3 track of the LAN host discovery picker. */
@Composable
internal fun HostDiscoveryMaterial(
    hosts: List<DiscoveredHost>,
    searching: Boolean,
    showEmpty: Boolean,
    onSelect: (DiscoveredHost) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.discover_devices)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (searching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(R.string.discovery_searching),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (showEmpty) {
                    Text(
                        stringResource(R.string.discovery_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                hosts.forEach { host ->
                    TextButton(
                        onClick = { onSelect(host) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Name over address, matching item_discovered_host.xml.
                        // No host:port format string exists in values/, so the
                        // bare fields are shown instead of interpolated text.
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(host.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(host.host, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
