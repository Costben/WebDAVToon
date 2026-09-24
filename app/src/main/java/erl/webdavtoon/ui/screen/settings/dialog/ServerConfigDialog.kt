// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.settings.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import erl.webdavtoon.R
import erl.webdavtoon.ui.component.CouiFlatMenu
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * Server configuration dialog (COUI). Same field order, SMB-only conditionals and bottom actions as before.
 */
@Composable
fun ServerConfigDialog(
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
            ServerConfigDialogContent(state, onAction, onDismiss)
        },
    )
}

@Composable
private fun ServerConfigDialogContent(
    state: ServerConfigDialogState,
    onAction: (ServerConfigAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val isSmb = state.protocol.equals("smb", ignoreCase = true)
    val passwordTransformation = if (state.showPassword) {
        VisualTransformation.None
    } else {
        MaskVisualTransformation()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextField(
                value = state.alias,
                onValueChange = { value -> onAction(ServerConfigAction.Update { copy(alias = value) }) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.alias_hint),
                singleLine = true,
            )
            var protocolExpanded by remember { mutableStateOf(false) }
            val protocolDescription = "${stringResource(R.string.protocol_hint)}: ${state.protocol}"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TextField(
                        value = state.protocol,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.protocol_hint),
                        singleLine = true,
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .semantics { contentDescription = protocolDescription }
                            .clickable(role = Role.Button) { protocolExpanded = true }
                    ) {
                        Text(
                            text = "\u25BE",
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                        )
                    }
                    CouiFlatMenu(
                        expanded = protocolExpanded,
                        entries = listOf(
                            DropdownEntry(
                                items = state.protocols.map { proto ->
                                    DropdownItem(
                                        text = proto,
                                        selected = proto == state.protocol,
                                        onClick = {
                                            onAction(ServerConfigAction.Update { selectProtocol(proto) })
                                            protocolExpanded = false
                                        },
                                    )
                                },
                            )
                        ),
                        onDismissRequest = { protocolExpanded = false },
                    )
                }
                TextButton(
                    text = stringResource(R.string.discover_devices),
                    onClick = { onAction(ServerConfigAction.DiscoverHosts) },
                    enabled = !state.discoveringHosts,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
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
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabeledCheckbox(
                    checked = state.rememberPassword,
                    label = stringResource(R.string.remember_password),
                    onCheckedChange = { value ->
                        onAction(ServerConfigAction.Update { copy(rememberPassword = value) })
                    },
                )
                LabeledSwitch(
                    checked = state.showPassword,
                    label = stringResource(R.string.password_hint),
                    onCheckedChange = { onAction(ServerConfigAction.ToggleShowPassword) },
                )
            }
            if (state.isPrivacyMode) {
                LabeledCheckbox(
                    checked = state.isPrivate,
                    label = stringResource(R.string.private_server),
                    onCheckedChange = { value ->
                        onAction(ServerConfigAction.Update { copy(isPrivate = value) })
                    },
                )
            }

            TextButton(
                text = stringResource(R.string.test_connection),
                onClick = { onAction(ServerConfigAction.TestConnection) },
                enabled = !state.testing,
                modifier = Modifier.fillMaxWidth(),
            )

            ServerConfigStatus(
                testing = state.testing,
                enumeratingShares = state.enumeratingShares,
                shares = state.shares,
                testResult = state.testResult,
                error = state.error,
                onShareSelected = { name -> onAction(ServerConfigAction.ApplyShare(name)) },
            )
        }

        DialogButtonBar(
            negative = DialogButtonBarAction(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
            ),
            positive = DialogButtonBarAction(
                text = stringResource(R.string.save),
                onClick = { onAction(ServerConfigAction.Save) },
            ),
            hasContentAbove = true,
        )
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
        Text(label, style = COUITheme.textStyles.body2)
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
        Text(label, style = COUITheme.textStyles.body2)
    }
}

/**
 * Connection-test status block: progress and
 * result, SMB share picker, and the error line routed through
 * `R.string.error_prefix`.
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
            CircularProgressIndicator(size = 20.dp)
            Text(
                stringResource(
                    if (testing) R.string.testing_connection else R.string.smb_enumerating_shares,
                ),
                style = COUITheme.textStyles.body2,
            )
        }
    }

    if (shares != null) {
        if (shares.isEmpty()) {
            Text(
                stringResource(R.string.smb_enum_failed_hint),
                style = COUITheme.textStyles.footnote1,
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
            Text(testResult, style = COUITheme.textStyles.body2)
        }
    }

    if (error != null) {
        HorizontalDivider()
        Text(
            stringResource(R.string.error_prefix, error),
            style = COUITheme.textStyles.body2,
            color = COUITheme.colorScheme.error,
        )
    }
}
