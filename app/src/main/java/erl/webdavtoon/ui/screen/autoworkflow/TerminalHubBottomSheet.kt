// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.autoworkflow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.webdavtoon.AutoWorkflowClient
import erl.webdavtoon.R
import erl.webdavtoon.TerminalHubServiceStatus
import erl.webdavtoon.TerminalHubStatus
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.layout.BottomSheetDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.layout.SheetActionButton
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

private val StatusGreen = Color(0xFF34C759)
private val StatusRed = Color(0xFFFF3B30)
private val StatusAmber = Color(0xFFFF9F0A)
private val StatusNeutral = Color(0xFF8E8E93)

data class TerminalHubActions(
    val onRefresh: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onServiceAction: (name: String, action: String) -> Unit,
    val onGroupAction: (action: String) -> Unit,
    val onOpenLog: (name: String) -> Unit,
    val onSetLogLines: (lines: Int) -> Unit,
    val onRefreshLog: () -> Unit,
    val onCloseLog: () -> Unit,
    val onMessageShown: () -> Unit,
)

@Composable
fun TerminalHubBottomSheet(
    show: Boolean,
    state: TerminalHubUiState,
    actions: TerminalHubActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showStopGroupConfirm by remember { mutableStateOf(false) }
    var restartBackendTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        actions.onMessageShown()
    }

    OverlayBottomSheet(
        show = show,
        showDragHandle = false,
        insideMargin = DpSize(16.dp, 24.dp),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TerminalHubSheetTopBar(
                loading = state.loading && state.status != null,
                onDismiss = onDismiss,
                onRefresh = { if (!state.loading) actions.onRefresh() },
            )
            TerminalHubSheetBody(
                state = state,
                actions = actions,
                onRequestStopGroup = { showStopGroupConfirm = true },
                onRequestRestartProtected = { name -> restartBackendTarget = name },
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }

    if (show && showStopGroupConfirm) {
        val groupNames = state.status?.autoWorkflowServices.orEmpty().joinToString("、")
        OverlayDialog(
            show = true,
            title = stringResource(R.string.terminal_hub_stop_group_title),
            summary = stringResource(R.string.terminal_hub_stop_group_confirm, groupNames),
            onDismissRequest = { showStopGroupConfirm = false },
        ) {
            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = stringResource(R.string.cancel),
                    onClick = { showStopGroupConfirm = false },
                ),
                positive = DialogButtonBarAction(
                    text = stringResource(R.string.terminal_hub_stop_group),
                    recommended = true,
                    onClick = {
                        showStopGroupConfirm = false
                        actions.onGroupAction(AutoWorkflowClient.HUB_ACTION_STOP)
                    },
                ),
                hasContentAbove = true,
            )
        }
    }

    if (show) {
        restartBackendTarget?.let { target ->
            OverlayDialog(
                show = true,
                title = stringResource(R.string.terminal_hub_restart_backend_title),
                summary = stringResource(R.string.terminal_hub_restart_backend_confirm),
                onDismissRequest = { restartBackendTarget = null },
            ) {
                DialogButtonBar(
                    negative = DialogButtonBarAction(
                        text = stringResource(R.string.cancel),
                        onClick = { restartBackendTarget = null },
                    ),
                    positive = DialogButtonBarAction(
                        text = stringResource(R.string.terminal_hub_restart),
                        recommended = true,
                        onClick = {
                            restartBackendTarget = null
                            actions.onServiceAction(target, AutoWorkflowClient.HUB_ACTION_RESTART)
                        },
                    ),
                    hasContentAbove = true,
                )
            }
        }

        state.logTargetName?.let { targetName ->
            TerminalHubLogDialog(
                targetName = targetName,
                lines = state.logLines,
                loading = state.logLoading,
                error = state.logError,
                content = state.logResult?.content,
                logFile = state.logResult?.logFile,
                onSelectLines = actions.onSetLogLines,
                onRefresh = actions.onRefreshLog,
                onCopy = { text ->
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("terminal_hub_log", text))
                    Toast.makeText(context, context.getString(R.string.terminal_hub_copied), Toast.LENGTH_SHORT).show()
                },
                onDismiss = actions.onCloseLog,
            )
        }
    }
}

@Composable
private fun TerminalHubSheetBody(
    state: TerminalHubUiState,
    actions: TerminalHubActions,
    onRequestStopGroup: () -> Unit,
    onRequestRestartProtected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .scrollEndHaptic()
            .overScrollVertical(),
    ) {
        item(key = "header") {
            TerminalHubSheetHeader(
                state = state,
                onOpenManagerLog = { actions.onOpenLog(AutoWorkflowClient.HUB_MANAGER_LOG_NAME) },
            )
        }

        when {
            !state.isConfigured -> {
                item(key = "unconfigured") {
                    HubDiagnosticCard(
                        message = stringResource(R.string.terminal_hub_configure_first),
                        hint = stringResource(R.string.terminal_hub_connect_hint),
                        primaryLabel = stringResource(R.string.autoworkflow_open_settings),
                        onPrimary = actions.onOpenSettings,
                    )
                }
            }

            state.loading && state.status == null -> {
                item(key = "loading") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        if (state.serverUrl.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.terminal_hub_server_label, state.serverUrl),
                                style = COUITheme.textStyles.footnote1,
                                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }

            state.error != null && state.status == null -> {
                item(key = "error") {
                    val serverLabel = state.serverUrl.takeIf { it.isNotBlank() }?.let {
                        stringResource(R.string.terminal_hub_server_label, it) + "\n"
                    }.orEmpty()
                    HubDiagnosticCard(
                        message = serverLabel + state.error,
                        hint = stringResource(R.string.terminal_hub_connect_hint),
                        primaryLabel = stringResource(R.string.autoworkflow_retry),
                        onPrimary = actions.onRefresh,
                        secondaryLabel = stringResource(R.string.autoworkflow_open_settings),
                        onSecondary = actions.onOpenSettings,
                    )
                }
            }

            else -> {
                val status = state.status
                if (status != null) {
                    item(key = "hubOverview") {
                        HubOverviewCard(
                            status = status,
                            errorBanner = state.error,
                            onOpenManagerLog = { actions.onOpenLog(AutoWorkflowClient.HUB_MANAGER_LOG_NAME) },
                        )
                    }
                    item(key = "awfGroup") {
                        AutoWorkflowGroupCard(
                            status = status,
                            busy = state.groupActionRunning || state.busyServiceName != null,
                            onStartAll = { actions.onGroupAction(AutoWorkflowClient.HUB_ACTION_START) },
                            onStopAll = onRequestStopGroup,
                            onRestartAll = { actions.onGroupAction(AutoWorkflowClient.HUB_ACTION_RESTART) },
                        )
                    }
                    item(key = "servicesHeader") {
                        SmallTitle(
                            text = stringResource(R.string.terminal_hub_all_services),
                            insideMargin = PaddingValues(0.dp, 8.dp),
                        )
                    }
                    if (status.services.isEmpty()) {
                        item(key = "emptyServices") {
                            SheetCard {
                                Text(
                                    text = stringResource(R.string.terminal_hub_empty_services),
                                    style = COUITheme.textStyles.body2,
                                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    } else {
                        item(key = "servicesCard") {
                            SheetCard {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    status.services.forEachIndexed { index, service ->
                                        if (index > 0) {
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        }
                                        ServiceRow(
                                            service = service,
                                            hubReachable = status.hubReachable,
                                            busy = state.busyServiceName == service.name,
                                            anyBusy = state.busyServiceName != null || state.groupActionRunning,
                                            onStart = {
                                                actions.onServiceAction(service.name, AutoWorkflowClient.HUB_ACTION_START)
                                            },
                                            onStop = {
                                                actions.onServiceAction(service.name, AutoWorkflowClient.HUB_ACTION_STOP)
                                            },
                                            onRestart = {
                                                if (service.isProtected) {
                                                    onRequestRestartProtected(service.name)
                                                } else {
                                                    actions.onServiceAction(service.name, AutoWorkflowClient.HUB_ACTION_RESTART)
                                                }
                                            },
                                            onViewLogs = { actions.onOpenLog(service.name) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "inset") {
            Spacer(
                Modifier.padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding() + 8.dp,
                ),
            )
        }
    }
}

@Composable
private fun TerminalHubSheetTopBar(
    loading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BottomSheetDefaults.HeaderHeight),
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            SheetActionButton(onClick = onDismiss) {
                Icon(
                    imageVector = COUIIcons.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = COUITheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.terminal_hub_title),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = BottomSheetDefaults.ActionButtonSize + 12.dp),
            fontSize = COUITheme.textStyles.title4.fontSize,
            fontWeight = FontWeight(550),
            textAlign = TextAlign.Center,
            color = COUITheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            SheetActionButton(onClick = onRefresh) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        imageVector = COUIIcons.Light.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        tint = COUITheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalHubSheetHeader(
    state: TerminalHubUiState,
    onOpenManagerLog: () -> Unit,
) {
    val status = state.status
    val dotColor = when {
        !state.isConfigured -> StatusNeutral
        state.loading && status == null -> StatusAmber
        state.error != null && status == null -> StatusRed
        status?.hubReachable == true -> StatusGreen
        status != null -> StatusRed
        else -> StatusNeutral
    }
    val statusText = when {
        !state.isConfigured -> stringResource(R.string.terminal_hub_not_configured)
        state.loading && status == null -> stringResource(R.string.autoworkflow_running)
        state.error != null && status == null -> stringResource(R.string.terminal_hub_status_offline)
        status?.hubReachable == true -> stringResource(R.string.terminal_hub_status_online)
        status != null -> stringResource(R.string.terminal_hub_status_offline)
        else -> stringResource(R.string.terminal_hub_status_offline)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Canvas(modifier = Modifier.size(6.dp)) {
                    drawCircle(color = dotColor)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = COUITheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.isConfigured && status != null) {
                ActionChip(
                    label = stringResource(R.string.terminal_hub_manager_log),
                    enabled = true,
                    onClick = onOpenManagerLog,
                )
            }
        }
        if (state.serverUrl.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.terminal_hub_server_label, state.serverUrl),
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun HubDiagnosticCard(
    message: String,
    hint: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    SheetCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = message,
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = primaryLabel)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    ActionChip(
                        label = secondaryLabel,
                        enabled = true,
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        onClick = onSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HubOverviewCard(
    status: TerminalHubStatus,
    errorBanner: String?,
    onOpenManagerLog: () -> Unit,
) {
    val modeLabel = status.manager?.modeLabel?.takeIf { it.isNotBlank() }
        ?: status.manager?.modeKey.orEmpty()
    if (modeLabel.isBlank() && status.hubUrl.isNullOrBlank() && errorBanner.isNullOrBlank()) return

    SheetCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (modeLabel.isNotBlank()) {
                Text(
                    text = stringResource(R.string.terminal_hub_mode_prefix, modeLabel),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurface,
                )
            }
            status.hubUrl?.let { url ->
                Text(
                    text = "Terminal Hub: $url",
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (!status.hubReachable || !errorBanner.isNullOrBlank()) {
                Text(
                    text = errorBanner ?: stringResource(R.string.terminal_hub_connect_hint),
                    style = COUITheme.textStyles.footnote2,
                    color = StatusRed,
                )
                ActionChip(
                    label = stringResource(R.string.terminal_hub_manager_log),
                    enabled = true,
                    onClick = onOpenManagerLog,
                )
            }
        }
    }
}

@Composable
private fun AutoWorkflowGroupCard(
    status: TerminalHubStatus,
    busy: Boolean,
    onStartAll: () -> Unit,
    onStopAll: () -> Unit,
    onRestartAll: () -> Unit,
) {
    val groupSet = remember(status.autoWorkflowServices) {
        status.autoWorkflowServices.map { it.lowercase() }.toSet()
    }
    val groupServices = remember(status.services, groupSet) {
        status.services.filter { it.name.lowercase() in groupSet }
    }
    val runningCount = groupServices.count { it.running }
    val totalCount = groupServices.size

    SmallTitle(
        text = stringResource(R.string.terminal_hub_autoworkflow_group),
        insideMargin = PaddingValues(0.dp, 8.dp),
    )
    SheetCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.terminal_hub_autoworkflow_summary, runningCount, totalCount),
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurface,
            )
            if (groupServices.isNotEmpty()) {
                Text(
                    text = groupServices.joinToString(" · ") { it.name },
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionChip(
                    label = stringResource(R.string.terminal_hub_start_group),
                    enabled = status.hubReachable && !busy && totalCount > 0,
                    highlighted = true,
                    modifier = Modifier.weight(1f),
                    onClick = onStartAll,
                )
                ActionChip(
                    label = stringResource(R.string.terminal_hub_stop_group),
                    enabled = status.hubReachable && !busy && runningCount > 0,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                    onClick = onStopAll,
                )
                ActionChip(
                    label = stringResource(R.string.terminal_hub_restart_group),
                    enabled = status.hubReachable && !busy && totalCount > 0,
                    modifier = Modifier.weight(1f),
                    onClick = onRestartAll,
                )
            }
        }
    }
}

@Composable
private fun ServiceRow(
    service: TerminalHubServiceStatus,
    hubReachable: Boolean,
    busy: Boolean,
    anyBusy: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onViewLogs: () -> Unit,
) {
    val stateUpper = service.statusState.uppercase()
    val dotColor = when {
        service.running && service.healthy -> StatusGreen
        service.running -> StatusAmber
        stateUpper == "ERROR" -> StatusRed
        else -> StatusNeutral
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Text(
                    text = service.name,
                    style = COUITheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Text(
                    text = stateUpper.ifBlank { if (service.running) "ONLINE" else "OFFLINE" },
                    style = COUITheme.textStyles.footnote2,
                    color = dotColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        val subtitle = buildList {
            service.pid?.let { add(stringResource(R.string.terminal_hub_pid, it)) }
            if (service.healthDetail.isNotBlank()) add(service.healthDetail)
        }.joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (service.isProtected) {
            Text(
                text = stringResource(R.string.terminal_hub_protected_hint),
                style = COUITheme.textStyles.footnote2,
                color = StatusAmber,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionChip(
                label = stringResource(R.string.terminal_hub_start),
                enabled = hubReachable && !anyBusy && !service.running,
                highlighted = !service.running,
                modifier = Modifier.weight(1f),
                onClick = onStart,
            )
            ActionChip(
                label = stringResource(R.string.terminal_hub_stop),
                enabled = hubReachable && !anyBusy && service.running && !service.isProtected,
                destructive = service.running && !service.isProtected,
                modifier = Modifier.weight(1f),
                onClick = onStop,
            )
            ActionChip(
                label = stringResource(R.string.terminal_hub_restart),
                enabled = hubReachable && !anyBusy,
                modifier = Modifier.weight(1f),
                onClick = onRestart,
            )
            ActionChip(
                label = stringResource(R.string.terminal_hub_logs),
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = onViewLogs,
            )
        }
    }
}

@Composable
private fun SheetCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(color = COUITheme.colorScheme.secondaryContainer),
    ) {
        content()
    }
}

@Composable
private fun ActionChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    destructive: Boolean = false,
    selected: Boolean = false,
) {
    val container = when {
        !enabled -> COUITheme.colorScheme.surface.copy(alpha = 0.5f)
        selected || highlighted -> COUITheme.colorScheme.primary
        destructive -> StatusRed.copy(alpha = 0.14f)
        else -> COUITheme.colorScheme.surface
    }
    val textColor = when {
        !enabled -> COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
        selected || highlighted -> COUITheme.colorScheme.onPrimary
        destructive -> StatusRed
        else -> COUITheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TerminalHubLogDialog(
    targetName: String,
    lines: Int,
    loading: Boolean,
    error: String?,
    content: String?,
    logFile: String?,
    onSelectLines: (Int) -> Unit,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val displayTitleName = if (targetName == AutoWorkflowClient.HUB_MANAGER_LOG_NAME) {
        stringResource(R.string.terminal_hub_manager_log)
    } else {
        targetName
    }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    LaunchedEffect(content) {
        if (!content.isNullOrEmpty()) {
            verticalScroll.scrollTo(verticalScroll.maxValue)
        }
    }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.terminal_hub_log_title, displayTitleName),
        onDismissRequest = onDismiss,
        messageBottomPadding = DialogDefaults.MessageBottomPaddingWithCustomContent,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!logFile.isNullOrBlank()) {
                    Text(
                        text = logFile,
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(100, 200, 500).forEach { count ->
                        ActionChip(
                            label = stringResource(R.string.terminal_hub_log_lines, count),
                            enabled = !loading,
                            selected = lines == count,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectLines(count) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionChip(
                        label = stringResource(R.string.refresh),
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                        onClick = onRefresh,
                    )
                    ActionChip(
                        label = stringResource(R.string.terminal_hub_copy_log),
                        enabled = !content.isNullOrBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = { onCopy(content.orEmpty()) },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 340.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(COUITheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
                        .padding(10.dp),
                ) {
                    when {
                        loading && content == null -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }

                        error != null -> {
                            Text(
                                text = error,
                                style = COUITheme.textStyles.footnote1,
                                color = StatusRed,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }

                        content.isNullOrBlank() -> {
                            Text(
                                text = stringResource(R.string.terminal_hub_log_empty),
                                style = COUITheme.textStyles.footnote1,
                                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }

                        else -> {
                            SelectionContainer {
                                Text(
                                    text = content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = COUITheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(verticalScroll)
                                        .horizontalScroll(horizontalScroll),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = stringResource(R.string.refresh),
                    onClick = { if (!loading) onRefresh() },
                ),
                positive = DialogButtonBarAction(
                    text = stringResource(R.string.autoworkflow_close),
                    recommended = true,
                    onClick = onDismiss,
                ),
                hasContentAbove = true,
            )
        }
    }
}
