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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import erl.webdavtoon.DiscoveredHost
import erl.webdavtoon.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Miuix track of the LAN host discovery picker, as a bottom sheet. */
@Composable
internal fun HostDiscoveryMiuix(
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
                            style = MiuixTheme.textStyles.subtitle,
                        )
                    }
                }
                if (showEmpty) {
                    Text(
                        stringResource(R.string.discovery_empty),
                        style = MiuixTheme.textStyles.body2,
                    )
                }
                hosts.forEach { host ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            text = host.displayName,
                            onClick = { onSelect(host) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(host.host, style = MiuixTheme.textStyles.footnote1)
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
