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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import erl.webdavtoon.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix track of the server configuration dialog. Field-for-field parity with
 * [ServerConfigDialogMaterial]: same order, same SMB-only conditionals, same
 * three bottom actions.
 */
@Composable
fun ServerConfigDialogMiuix(
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

    OverlayDialog(
        show = visible,
        title = stringResource(R.string.webdav_config, state.slot),
        onDismissRequest = onDismiss,
        content = {
            ServerConfigDialogMiuixContent(state, onAction, onDismiss)
        },
    )
}

@Composable
private fun ServerConfigDialogMiuixContent(
    state: ServerConfigDialogState,
    onAction: (ServerConfigAction) -> Unit,
    onDismiss: () -> Unit,
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
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextField(
            value = state.alias,
            onValueChange = { value -> onAction(ServerConfigAction.Update { copy(alias = value) }) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.alias_hint),
            singleLine = true,
        )
        TextField(
            value = state.protocol,
            onValueChange = { value -> onAction(ServerConfigAction.Update { copy(protocol = value) }) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.protocol_hint),
            singleLine = true,
        )
        TextButton(
            text = stringResource(R.string.discover_devices),
            onClick = { onAction(ServerConfigAction.DiscoverHosts) },
            enabled = !state.discoveringHosts,
        )
        TextField(
            value = state.url,
            onValueChange = { value -> onAction(ServerConfigAction.Update { copy(url = value) }) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(if (isSmb) R.string.host_hint_smb else R.string.host_hint),
            singleLine = true,
        )
        if (isSmb) {
            TextButton(
                text = stringResource(R.string.smb_list_shares),
                onClick = { onAction(ServerConfigAction.EnumerateShares) },
                enabled = !state.enumeratingShares,
            )
        }
        TextField(
            value = state.port,
            onValueChange = { value -> onAction(ServerConfigAction.Update { copy(port = value) }) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.port_hint),
            singleLine = true,
        )
        TextField(
            value = state.username,
            onValueChange = { value -> onAction(ServerConfigAction.Update { copy(username = value) }) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.username_hint),
            singleLine = true,
        )
        if (isSmb) {
            TextField(
                value = state.domain,
                onValueChange = { value -> onAction(ServerConfigAction.Update { copy(domain = value) }) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.domain_hint),
                singleLine = true,
            )
        }
        TextField(
            value = state.password,
            onValueChange = { value -> onAction(ServerConfigAction.Update { copy(password = value) }) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.password_hint),
            singleLine = true,
            visualTransformation = passwordTransformation,
        )

        // The password hint doubles as the reveal-label here: values/ has no
        // dedicated `show_password` key and adding one is out of scope.
        LabeledSwitch(
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

        ServerConfigMiuixStatus(
            testing = state.testing,
            enumeratingShares = state.enumeratingShares,
            shares = state.shares,
            testResult = state.testResult,
            error = state.error,
            onShareSelected = { name -> onAction(ServerConfigAction.ApplyShare(name)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                text = stringResource(R.string.test_connection),
                onClick = { onAction(ServerConfigAction.TestConnection) },
                enabled = !state.testing,
            )
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
            )
            TextButton(
                text = stringResource(R.string.save),
                onClick = { onAction(ServerConfigAction.Save) },
            )
        }
    }
}

@Composable
private fun LabeledCheckbox(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = { onCheckedChange(!checked) },
        )
        Text(label, style = MiuixTheme.textStyles.body2)
    }
}

@Composable
private fun LabeledSwitch(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MiuixTheme.textStyles.body2)
    }
}

/**
 * Miuix counterpart of the Material status block: connection-test progress and
 * result, SMB share picker, and the error line routed through
 * `R.string.error_prefix`.
 */
@Composable
private fun ServerConfigMiuixStatus(
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
            CircularProgressIndicator(size = 20.dp)
            Text(
                stringResource(
                    if (testing) R.string.testing_connection else R.string.smb_enumerating_shares,
                ),
                style = MiuixTheme.textStyles.body2,
            )
        }
    }

    if (shares != null) {
        if (shares.isEmpty()) {
            Text(
                stringResource(R.string.smb_enum_failed_hint),
                style = MiuixTheme.textStyles.footnote1,
            )
        } else {
            SmallTitle(text = stringResource(R.string.smb_choose_share))
            Card(modifier = Modifier.fillMaxWidth()) {
                shares.forEach { share ->
                    TextButton(
                        text = share,
                        onClick = { onShareSelected(share) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (testResult != null) {
        SmallTitle(text = stringResource(R.string.connection_test_result))
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(testResult, style = MiuixTheme.textStyles.body2)
        }
    }

    if (error != null) {
        HorizontalDivider()
        Text(
            stringResource(R.string.error_prefix, error),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.error,
        )
    }
}
