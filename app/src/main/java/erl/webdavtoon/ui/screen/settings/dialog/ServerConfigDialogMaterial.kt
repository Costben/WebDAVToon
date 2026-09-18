package erl.webdavtoon.ui.screen.settings.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import erl.webdavtoon.R

/**
 * Material 3 track of the server configuration dialog. Field order mirrors
 * `dialog_server_config_webdav.xml`, and the shell uses
 * [MaterialTheme.shapes.extraLarge] so it matches the settings cards.
 */
@Composable
fun ServerConfigDialogMaterial(
    state: ServerConfigDialogState,
    visible: Boolean,
    onAction: (ServerConfigAction) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    if (state.discoveryVisible) {
        HostDiscoveryDialog(
            hosts = state.discoveredHosts,
            searching = state.discoveringHosts,
            visible = true,
            onSelect = { host -> onAction(ServerConfigAction.ApplyHost(host)) },
            onDismiss = { onAction(ServerConfigAction.DismissDiscovery) },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.webdav_config, state.slot)) },
        text = {
            ServerConfigDialogMaterialContent(state, onAction)
        },
        confirmButton = {
            TextButton(onClick = { onAction(ServerConfigAction.Save) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = { onAction(ServerConfigAction.TestConnection) },
                    enabled = !state.testing,
                ) {
                    Text(stringResource(R.string.test_connection))
                }
            }
        },
    )
}

@Composable
private fun ServerConfigDialogMaterialContent(
    state: ServerConfigDialogState,
    onAction: (ServerConfigAction) -> Unit,
) {
    val isSmb = state.protocol.equals("smb", ignoreCase = true)
    val passwordTransformation = if (state.showPassword) {
        VisualTransformation.None
    } else {
        PasswordVisualTransformation()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.alias,
            onValueChange = { value -> onAction(ServerConfigAction.Update { copy(alias = value) }) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.alias_hint)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.protocol,
            onValueChange = { value -> onAction(ServerConfigAction.Update { copy(protocol = value) }) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.protocol_hint)) },
            singleLine = true,
        )
        TextButton(
            onClick = { onAction(ServerConfigAction.DiscoverHosts) },
            enabled = !state.discoveringHosts,
        ) {
            Text(stringResource(R.string.discover_devices))
        }
        OutlinedTextField(
            value = state.url,
            onValueChange = { value -> onAction(ServerConfigAction.Update { copy(url = value) }) },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(stringResource(if (isSmb) R.string.host_hint_smb else R.string.host_hint))
            },
            singleLine = true,
        )
        if (isSmb) {
            TextButton(
                onClick = { onAction(ServerConfigAction.EnumerateShares) },
                enabled = !state.enumeratingShares,
            ) {
                Text(stringResource(R.string.smb_list_shares))
            }
        }
        OutlinedTextField(
            value = state.port,
            onValueChange = { value -> onAction(ServerConfigAction.Update { copy(port = value) }) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.port_hint)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = { value -> onAction(ServerConfigAction.Update { copy(username = value) }) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.username_hint)) },
            singleLine = true,
        )
        if (isSmb) {
            OutlinedTextField(
                value = state.domain,
                onValueChange = { value -> onAction(ServerConfigAction.Update { copy(domain = value) }) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.domain_hint)) },
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = state.password,
            onValueChange = { value -> onAction(ServerConfigAction.Update { copy(password = value) }) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.password_hint)) },
            singleLine = true,
            visualTransformation = passwordTransformation,
        )

        LabeledCheckbox(
            checked = state.showPassword,
            label = stringResource(R.string.password_hint),
            onCheckedChange = { onAction(ServerConfigAction.ToggleShowPassword) },
        )
        LabeledCheckbox(
            checked = state.rememberPassword,
            label = stringResource(R.string.remember_password),
            onCheckedChange = { value ->
                onAction(ServerConfigAction.Update { copy(rememberPassword = value) })
            },
        )
        if (state.isPrivacyMode) {
            LabeledCheckbox(
                checked = state.isPrivate,
                label = stringResource(R.string.private_server),
                onCheckedChange = { value ->
                    onAction(ServerConfigAction.Update { copy(isPrivate = value) })
                },
            )
        }

        ServerConfigStatus(
            testing = state.testing,
            enumeratingShares = state.enumeratingShares,
            shares = state.shares,
            testResult = state.testResult,
            error = state.error,
            onShareSelected = { name -> onAction(ServerConfigAction.ApplyShare(name)) },
        )
    }
}

@Composable
private fun LabeledCheckbox(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Renders the side-effect state (connection test, share enumeration) that both
 * tracks expose. Shares arrive as a plain list of names; picking one rewrites
 * the host field through [onShareSelected].
 */
@Composable
private fun ServerConfigStatus(
    testing: Boolean,
    enumeratingShares: Boolean,
    shares: List<String>?,
    testResult: String?,
    error: String?,
    onShareSelected: (String) -> Unit,
) {
    if (testing || enumeratingShares) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator()
            Text(
                stringResource(
                    if (testing) R.string.testing_connection else R.string.smb_enumerating_shares,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (shares != null) {
        if (shares.isEmpty()) {
            Text(
                stringResource(R.string.smb_enum_failed_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                stringResource(R.string.smb_choose_share),
                style = MaterialTheme.typography.titleSmall,
            )
            shares.forEach { share ->
                TextButton(onClick = { onShareSelected(share) }) {
                    Text(share)
                }
            }
        }
    }

    if (testResult != null) {
        Text(
            stringResource(R.string.connection_test_result),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(testResult, style = MaterialTheme.typography.bodyMedium)
    }

    if (error != null) {
        HorizontalDivider()
        Text(
            stringResource(R.string.error_prefix, error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
