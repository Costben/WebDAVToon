package erl.webdavtoon.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import erl.webdavtoon.BuildConfig
import erl.webdavtoon.R
import erl.webdavtoon.ui.UiMode
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreenMiuix(
    uiState: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings),
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(MiuixIcons.Light.Back, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { paddingValues ->
        if (uiState.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            SettingsContent(uiState, actions, paddingValues)
        }
    }
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    actions: SettingsActions,
    paddingValues: PaddingValues,
) {
    val gridItems = (1..4).map { count ->
        DropdownItem(text = stringResource(R.string.columns_suffix, count))
    }
    val sortValues = intArrayOf(
        SettingsDefaults.SORT_NAME_ASC,
        SettingsDefaults.SORT_NAME_DESC,
        SettingsDefaults.SORT_DATE_DESC,
        SettingsDefaults.SORT_DATE_ASC,
        SettingsDefaults.SORT_RANDOM_FOLDERS,
    )
    val sortItems = listOf(
        DropdownItem(stringResource(R.string.sort_name_asc)),
        DropdownItem(stringResource(R.string.sort_name_desc)),
        DropdownItem(stringResource(R.string.sort_date_desc)),
        DropdownItem(stringResource(R.string.sort_date_asc)),
        DropdownItem(stringResource(R.string.sort_random_folders)),
    )
    val recursiveValues = intArrayOf(
        SettingsDefaults.RECURSIVE_IMAGE_ARRANGEMENT_GROUPED,
        SettingsDefaults.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_DESC,
        SettingsDefaults.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_ASC,
    )
    val recursiveItems = listOf(
        DropdownItem(stringResource(R.string.recursive_image_arrangement_grouped)),
        DropdownItem(stringResource(R.string.recursive_image_arrangement_global_date_desc)),
        DropdownItem(stringResource(R.string.recursive_image_arrangement_global_date_asc)),
    )
    val qualityModes = listOf(SettingsDefaults.WATERFALL_MODE_PERCENT, SettingsDefaults.WATERFALL_MODE_MAX_WIDTH)
    val qualityItems = listOf(
        DropdownItem(stringResource(R.string.thumbnail_quality_percent_mode)),
        DropdownItem(stringResource(R.string.thumbnail_quality_max_width_mode)),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsGroup(stringResource(R.string.webdav_server)) {
            uiState.slots.forEach { slot ->
                val currentServerDescription = stringResource(R.string.current_server)
                BasicComponent(
                    title = slot.alias.ifBlank { stringResource(R.string.slot_name, slot.slot) },
                    summary = if (slot.url.isBlank()) stringResource(R.string.not_configured)
                    else stringResource(R.string.server_endpoint_format, slot.protocol, slot.url, slot.port),
                    startAction = {
                        SettingsIcon(
                            icon = MiuixIcons.Light.CloudFill,
                            descriptionRes = R.string.webdav_server,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    },
                    endActions = {
                        // The marker slot is always laid out (even when empty) so the summary
                        // column keeps a constant width; otherwise selecting a row would shrink
                        // it and re-wrap the server address.
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(10.dp)
                                .then(
                                    if (slot.isCurrent) {
                                        Modifier
                                            .background(CurrentServerDotColor, CircleShape)
                                            .semantics { contentDescription = currentServerDescription }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                        IconButton(onClick = { actions.onEditSlot(slot.slot) }) {
                            Icon(MiuixIcons.Light.Edit, contentDescription = stringResource(R.string.edit_server))
                        }
                        IconButton(onClick = { actions.onDeleteSlot(slot.slot) }) {
                            Icon(MiuixIcons.Light.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    },
                    onClick = { actions.onSelectSlot(slot.slot) },
                    insideMargin = SlotRowInsideMargin,
                )
            }
            ArrowPreference(
                title = stringResource(R.string.add_webdav_server),
                startAction = { SettingsIcon(MiuixIcons.Light.AddCircle, R.string.add_webdav_server) },
                onClick = actions.onAddSlot,
            )
        }

        SettingsGroup(stringResource(R.string.display)) {
            SwitchPreference(
                checked = uiState.uiMode == UiMode.Material,
                onCheckedChange = { material -> actions.onSetUiMode(if (material) UiMode.Material else UiMode.Miuix) },
                title = stringResource(R.string.material_ui_mode),
                summary = stringResource(R.string.ui_mode_switch_summary),
            )
            ArrowPreference(title = stringResource(R.string.theme), summary = themeName(uiState.themeId), onClick = actions.onPickTheme)
            ArrowPreference(title = stringResource(R.string.language), summary = languageName(uiState.language), onClick = actions.onPickLanguage)
            OverlaySpinnerPreference(
                items = gridItems,
                selectedIndex = (uiState.gridColumns - 1).coerceIn(0, 3),
                title = stringResource(R.string.grid_columns),
                onSelectedIndexChange = { actions.onSetGridColumns(it + 1) },
            )
            SliderPreference(
                value = uiState.drawerEdgeWidthPercent.toFloat(),
                onValueChange = { actions.onSetDrawerEdgeWidth(it.toInt()) },
                title = stringResource(R.string.drawer_edge_width),
                valueRange = 0f..100f,
                steps = 99,
            )
        }

        SettingsGroup(stringResource(R.string.display_options)) {
            OverlaySpinnerPreference(
                items = sortItems,
                selectedIndex = sortValues.indexOf(uiState.sortOrder).coerceAtLeast(0),
                title = stringResource(R.string.sort_order),
                onSelectedIndexChange = { actions.onSetSortOrder(sortValueForIndex(it)) },
            )
            OverlaySpinnerPreference(
                items = recursiveItems,
                selectedIndex = recursiveValues.indexOf(uiState.recursiveImageArrangement).coerceAtLeast(0),
                title = stringResource(R.string.recursive_image_arrangement),
                summary = stringResource(recursiveImageArrangementRes(uiState.recursiveImageArrangement)),
                onSelectedIndexChange = { actions.onSetRecursiveImageArrangement(recursiveValues[it]) },
            )
            SwitchPreference(
                checked = uiState.waterfallShowFilenames,
                onCheckedChange = actions.onSetWaterfallShowFilenames,
                title = stringResource(R.string.waterfall_show_filenames),
            )
            OverlaySpinnerPreference(
                items = qualityItems,
                selectedIndex = qualityModes.indexOf(uiState.waterfallQualityMode).coerceAtLeast(0),
                title = stringResource(R.string.thumbnail_quality),
                onSelectedIndexChange = { actions.onSetWaterfallQualityMode(qualityModes[it]) },
            )
            if (uiState.waterfallQualityMode == SettingsDefaults.WATERFALL_MODE_MAX_WIDTH) {
                SliderPreference(
                    value = uiState.waterfallMaxWidth.toFloat(),
                    onValueChange = { actions.onSetWaterfallMaxWidth(it.toInt()) },
                    title = stringResource(R.string.thumbnail_quality_value_label),
                    valueRange = 200f..2000f,
                    steps = 1799,
                )
            } else {
                SliderPreference(
                    value = uiState.waterfallPercent.toFloat(),
                    onValueChange = { actions.onSetWaterfallPercent(it.toInt()) },
                    title = stringResource(R.string.thumbnail_quality_value_label),
                    valueRange = 10f..100f,
                    steps = 89,
                )
            }
        }

        SettingsGroup(stringResource(R.string.reader_video_editing)) {
            SliderPreference(
                value = uiState.readerMaxZoomPercent.toFloat(),
                onValueChange = { actions.onSetReaderMaxZoom(it.toInt()) },
                title = stringResource(R.string.reader_max_zoom),
                valueRange = 100f..500f,
                steps = 399,
            )
            ArrowPreference(title = stringResource(R.string.default_reader_mode), summary = stringResource(defaultReaderModeRes(uiState.defaultReaderMode)), onClick = actions.onPickDefaultReaderMode)
            ArrowPreference(title = stringResource(R.string.video_external_player_mode), summary = videoPlayerModeName(uiState.videoExternalPlayerMode), onClick = actions.onPickVideoExternalPlayerMode)
            ArrowPreference(title = stringResource(R.string.comfyui_server), summary = uiState.autoWorkflowUrl.ifBlank { stringResource(R.string.not_configured) }, onClick = actions.onEditAutoWorkflowUrl)
        }

        SettingsGroup(stringResource(R.string.advanced)) {
            ArrowPreference(title = stringResource(R.string.clear_cache), summary = stringResource(R.string.clear_cache_summary), onClick = actions.onClearCache)
            if (uiState.isPrivacyMode) {
                ArrowPreference(title = stringResource(R.string.privacy_exit_policy_title), summary = stringResource(privacyExitPolicyRes(uiState.privacyExitPolicy)), onClick = actions.onPickPrivacyExitPolicy)
            }
            ArrowPreference(title = stringResource(R.string.about), summary = stringResource(R.string.app_version_format, BuildConfig.VERSION_NAME), enabled = false)
            SwitchPreference(checked = uiState.rotationLocked, onCheckedChange = actions.onSetRotationLocked, title = stringResource(R.string.rotation_lock))
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    SmallTitle(text = title)
    Card(modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun SettingsIcon(icon: ImageVector, descriptionRes: Int, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = stringResource(descriptionRes), modifier = modifier)
}

/** Marker for the slot the app is currently connected to. */
private val CurrentServerDotColor = Color(0xFF34C759)

/**
 * Slot rows keep the leading icon aligned with other rows but trim the trailing inset so the
 * Delete action button lines up with the trailing chevron column used by arrow preferences.
 */
private val SlotRowInsideMargin = PaddingValues(start = 16.dp, top = 16.dp, end = 1.dp, bottom = 16.dp)

private fun sortValueForIndex(index: Int): Int = when (index) {
    0 -> SettingsDefaults.SORT_NAME_ASC
    1 -> SettingsDefaults.SORT_NAME_DESC
    2 -> SettingsDefaults.SORT_DATE_DESC
    3 -> SettingsDefaults.SORT_DATE_ASC
    4 -> SettingsDefaults.SORT_RANDOM_FOLDERS
    else -> SettingsDefaults.SORT_DATE_DESC
}

@Composable
private fun themeName(themeId: Int): String = stringResource(
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
private fun languageName(language: String): String = stringResource(
    when (language) {
        "zh" -> R.string.language_chinese
        "en" -> R.string.language_english
        else -> R.string.follow_system
    },
)

@Composable
private fun videoPlayerModeName(mode: String): String = stringResource(
    if (mode == SettingsDefaults.VIDEO_EXTERNAL_PLAYER_MODE_CHOOSER) R.string.video_external_player_mode_chooser
    else R.string.video_external_player_mode_system_default,
)
