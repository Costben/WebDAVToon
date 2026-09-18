package erl.webdavtoon.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import erl.webdavtoon.BuildConfig
import erl.webdavtoon.R
import erl.webdavtoon.ui.UiMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenMaterial(
    uiState: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    TextButton(onClick = actions.onBack) {
                        Text(stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { paddingValues ->
        if (uiState.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            SettingsMaterialContent(uiState, actions, paddingValues)
        }
    }
}

@Composable
private fun SettingsMaterialContent(
    uiState: SettingsUiState,
    actions: SettingsActions,
    paddingValues: PaddingValues,
) {
    val sortValues = intArrayOf(
        SettingsDefaults.SORT_NAME_ASC,
        SettingsDefaults.SORT_NAME_DESC,
        SettingsDefaults.SORT_DATE_DESC,
        SettingsDefaults.SORT_DATE_ASC,
        SettingsDefaults.SORT_RANDOM_FOLDERS,
    )
    val sortLabels = listOf(
        stringResource(R.string.sort_name_asc),
        stringResource(R.string.sort_name_desc),
        stringResource(R.string.sort_date_desc),
        stringResource(R.string.sort_date_asc),
        stringResource(R.string.sort_random_folders),
    )
    val recursiveValues = intArrayOf(
        SettingsDefaults.RECURSIVE_IMAGE_ARRANGEMENT_GROUPED,
        SettingsDefaults.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_DESC,
        SettingsDefaults.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_ASC,
    )
    val recursiveLabels = listOf(
        stringResource(R.string.recursive_image_arrangement_grouped),
        stringResource(R.string.recursive_image_arrangement_global_date_desc),
        stringResource(R.string.recursive_image_arrangement_global_date_asc),
    )
    val qualityModes = listOf(
        SettingsDefaults.WATERFALL_MODE_PERCENT,
        SettingsDefaults.WATERFALL_MODE_MAX_WIDTH,
    )
    val qualityLabels = listOf(
        stringResource(R.string.thumbnail_quality_percent_mode),
        stringResource(R.string.thumbnail_quality_max_width_mode),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MaterialSettingsGroup(stringResource(R.string.webdav_server)) {
            uiState.slots.forEach { slot ->
                MaterialListRow(
                    title = slot.alias.ifBlank { stringResource(R.string.slot_name, slot.slot) },
                    summary = if (slot.url.isBlank()) stringResource(R.string.not_configured)
                    else stringResource(R.string.server_endpoint_format, slot.protocol, slot.url, slot.port),
                    onClick = { actions.onSelectSlot(slot.slot) },
                    divider = true,
                    trailing = {
                        val editDescription = stringResource(R.string.edit_server)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (slot.isCurrent) Text(
                                text = stringResource(R.string.current_server),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TextButton(
                                onClick = { actions.onEditSlot(slot.slot) },
                                modifier = Modifier.semantics { contentDescription = editDescription },
                            ) {
                                Text(stringResource(R.string.edit_server))
                            }
                            TextButton(onClick = { actions.onDeleteSlot(slot.slot) }) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    },
                )
            }
            MaterialListRow(
                title = stringResource(R.string.add_webdav_server),
                onClick = actions.onAddSlot,
                divider = false,
            )
        }

        MaterialSettingsGroup(stringResource(R.string.display)) {
            MaterialListRow(
                title = stringResource(R.string.material_ui_mode),
                summary = stringResource(R.string.ui_mode_switch_summary),
                divider = true,
                trailing = {
                    Switch(
                        checked = uiState.uiMode == UiMode.Material,
                        onCheckedChange = { material ->
                            actions.onSetUiMode(if (material) UiMode.Material else UiMode.entries.first { it != UiMode.Material })
                        },
                    )
                },
            )
            MaterialListRow(stringResource(R.string.theme), materialThemeName(uiState.themeId), actions.onPickTheme, true)
            MaterialListRow(stringResource(R.string.language), materialLanguageName(uiState.language), actions.onPickLanguage, true)
            MaterialSelectionRow(
                title = stringResource(R.string.grid_columns),
                labels = (1..4).map { stringResource(R.string.columns_suffix, it) },
                selectedIndex = (uiState.gridColumns - 1).coerceIn(0, 3),
                onSelected = { actions.onSetGridColumns(it + 1) },
                divider = false,
            )
        }

        MaterialSettingsGroup(stringResource(R.string.display_options)) {
            MaterialSliderRow(
                title = stringResource(R.string.drawer_edge_width),
                initialValue = uiState.drawerEdgeWidthPercent.toFloat(),
                valueRange = 0f..100f,
                steps = 99,
                divider = true,
                onFinished = { actions.onSetDrawerEdgeWidth(it.toInt()) },
            )
            MaterialSelectionRow(
                title = stringResource(R.string.sort_order),
                labels = sortLabels,
                selectedIndex = sortValues.indexOf(uiState.sortOrder).coerceAtLeast(0),
                onSelected = { actions.onSetSortOrder(sortValues[it]) },
                divider = true,
            )
            MaterialSelectionRow(
                title = stringResource(R.string.recursive_image_arrangement),
                summary = recursiveImageArrangementLabel(uiState.recursiveImageArrangement),
                labels = recursiveLabels,
                selectedIndex = recursiveValues.indexOf(uiState.recursiveImageArrangement).coerceAtLeast(0),
                onSelected = { actions.onSetRecursiveImageArrangement(recursiveValues[it]) },
                divider = true,
            )
            MaterialListRow(
                title = stringResource(R.string.waterfall_show_filenames),
                divider = true,
                trailing = {
                    Switch(
                        checked = uiState.waterfallShowFilenames,
                        onCheckedChange = actions.onSetWaterfallShowFilenames,
                    )
                },
            )
            MaterialSelectionRow(
                title = stringResource(R.string.thumbnail_quality),
                labels = qualityLabels,
                selectedIndex = qualityModes.indexOf(uiState.waterfallQualityMode).coerceAtLeast(0),
                onSelected = { actions.onSetWaterfallQualityMode(qualityModes[it]) },
                divider = uiState.waterfallQualityMode == SettingsDefaults.WATERFALL_MODE_MAX_WIDTH ||
                    uiState.waterfallQualityMode == SettingsDefaults.WATERFALL_MODE_PERCENT,
            )
            if (uiState.waterfallQualityMode == SettingsDefaults.WATERFALL_MODE_MAX_WIDTH) {
                MaterialSliderRow(
                    title = stringResource(R.string.thumbnail_quality_value_label),
                    initialValue = uiState.waterfallMaxWidth.toFloat(),
                    valueRange = 200f..2000f,
                    steps = 1799,
                    divider = false,
                    onFinished = { actions.onSetWaterfallMaxWidth(it.toInt()) },
                )
            } else if (uiState.waterfallQualityMode == SettingsDefaults.WATERFALL_MODE_PERCENT) {
                MaterialSliderRow(
                    title = stringResource(R.string.thumbnail_quality_value_label),
                    initialValue = uiState.waterfallPercent.toFloat(),
                    valueRange = 10f..100f,
                    steps = 89,
                    divider = false,
                    onFinished = { actions.onSetWaterfallPercent(it.toInt()) },
                )
            }
        }

        MaterialSettingsGroup(stringResource(R.string.reader_video_editing)) {
            MaterialSliderRow(
                title = stringResource(R.string.reader_max_zoom),
                initialValue = uiState.readerMaxZoomPercent.toFloat(),
                valueRange = 100f..500f,
                steps = 399,
                divider = true,
                onFinished = { actions.onSetReaderMaxZoom(it.toInt()) },
            )
            MaterialListRow(stringResource(R.string.default_reader_mode), defaultReaderModeLabel(uiState.defaultReaderMode), actions.onPickDefaultReaderMode, true)
            MaterialListRow(stringResource(R.string.video_external_player_mode), materialVideoPlayerModeName(uiState.videoExternalPlayerMode), actions.onPickVideoExternalPlayerMode, true)
            MaterialListRow(stringResource(R.string.comfyui_server), uiState.autoWorkflowUrl.ifBlank { stringResource(R.string.not_configured) }, actions.onEditAutoWorkflowUrl, false)
        }

        MaterialSettingsGroup(stringResource(R.string.advanced)) {
            MaterialListRow(stringResource(R.string.clear_cache), stringResource(R.string.clear_cache_summary), actions.onClearCache, true)
            if (uiState.isPrivacyMode) {
                MaterialListRow(stringResource(R.string.privacy_exit_policy_title), privacyExitPolicyLabel(uiState.privacyExitPolicy), actions.onPickPrivacyExitPolicy, true)
            }
            MaterialListRow(
                title = stringResource(R.string.about),
                summary = stringResource(R.string.app_version_format, BuildConfig.VERSION_NAME),
                enabled = false,
                divider = true,
            )
            MaterialListRow(
                title = stringResource(R.string.rotation_lock),
                divider = false,
                trailing = { Switch(checked = uiState.rotationLocked, onCheckedChange = actions.onSetRotationLocked) },
            )
        }
    }
}

@Composable
private fun MaterialSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(content = content)
    }
}

@Composable
private fun MaterialListRow(
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing,
        modifier = if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier,
        colors = androidx.compose.material3.ListItemDefaults.colors(
            headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            supportingColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        ),
    )
    if (divider) HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialSelectionRow(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    divider: Boolean,
    summary: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary ?: labels[selectedIndex]) },
            trailingContent = { RadioButton(selected = true, onClick = { expanded = true }) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    },
                )
            }
        }
    }
    if (divider) HorizontalDivider()
}

@Composable
private fun MaterialSliderRow(
    title: String,
    initialValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    divider: Boolean,
    onFinished: (Float) -> Unit,
) {
    var value by remember(initialValue) { mutableFloatStateOf(initialValue) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { onFinished(value) },
                valueRange = valueRange,
                steps = steps,
            )
        },
    )
    if (divider) HorizontalDivider()
}

@Composable
private fun materialThemeName(themeId: Int): String = stringResource(
    when (themeId) {
        -1 -> R.string.theme_follow_device
        0, 1 -> R.string.theme_midnight_blue
        2 -> R.string.theme_forest_green
        3 -> R.string.theme_crimson_red
        4 -> R.string.theme_sunset_orange
        5 -> R.string.theme_ocean_teal
        6 -> R.string.theme_deep_purple
        7 -> R.string.theme_rose_pink
        8 -> R.string.theme_coffee_brown
        9 -> R.string.theme_neutral_grey
        else -> R.string.theme_unknown
    },
)

@Composable
private fun materialLanguageName(language: String): String = stringResource(
    when (language) {
        "zh" -> R.string.language_chinese
        "en" -> R.string.language_english
        else -> R.string.follow_system
    },
)

@Composable
private fun materialVideoPlayerModeName(mode: String): String = stringResource(
    if (mode == SettingsDefaults.VIDEO_EXTERNAL_PLAYER_MODE_CHOOSER) R.string.video_external_player_mode_chooser
    else R.string.video_external_player_mode_system_default,
)
