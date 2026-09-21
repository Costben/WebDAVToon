package erl.webdavtoon.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.webdavtoon.BuildConfig
import erl.webdavtoon.R
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.AddCircle
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.CloudFill
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlaySpinnerPreference
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

/** Shared settings actions for the COUI settings screen. */
data class SettingsActions(
    val onSelectSlot: (Int) -> Unit,
    val onAddSlot: () -> Unit,
    val onDeleteSlot: (Int) -> Unit,
    val onEditSlot: (Int) -> Unit,
    val onRefreshSlots: () -> Unit,
    val onPickTheme: () -> Unit,
    val onSetUseCouiDefaultColors: (Boolean) -> Unit,
    val onPickLanguage: () -> Unit,
    val onSetGridColumns: (Int) -> Unit,
    val onSetSortOrder: (Int) -> Unit,
    val onSetRecursiveImageArrangement: (Int) -> Unit,
    val onSetWaterfallShowFilenames: (Boolean) -> Unit,
    val onSetWaterfallQualityMode: (String) -> Unit,
    val onSetWaterfallPercent: (Int) -> Unit,
    val onSetWaterfallMaxWidth: (Int) -> Unit,
    val onSetWaterfallWidthBucket: (Int) -> Unit,
    val onSetGlideMemoryCacheScreens: (Int) -> Unit,
    val onSetReaderMaxZoom: (Int) -> Unit,
    val onPickDefaultReaderMode: () -> Unit,
    val onPickVideoExternalPlayerMode: () -> Unit,
    val onEditAutoWorkflowUrl: () -> Unit,
    val onPickPrivacyExitPolicy: () -> Unit,
    val onSetRotationLocked: (Boolean) -> Unit,
    val onClearCache: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
fun SettingsScreen(
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
                        Icon(COUIIcons.Light.Back, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        containerColor = COUITheme.colorScheme.surface,
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
    val bucketValues = SettingsDefaults.WATERFALL_WIDTH_BUCKET_VALUES
    val bucketItems = bucketValues.map { bucket ->
        DropdownItem(
            if (bucket <= 0) stringResource(R.string.waterfall_width_bucket_off)
            else stringResource(R.string.px_suffix, bucket)
        )
    }
    val memoryCacheValues = SettingsDefaults.GLIDE_MEMORY_CACHE_SCREEN_VALUES
    val memoryCacheItems = memoryCacheValues.map { DropdownItem(stringResource(R.string.screens_suffix, it)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding() + 12.dp,
            bottom = paddingValues.calculateBottomPadding(),
        ),
    ) {
        item(key = "server") {
            SettingsGroup(stringResource(R.string.webdav_server)) {
                uiState.slots.forEachIndexed { index, slot ->
                    if (index > 0) SettingsRowDivider()
                    val currentServerDescription = stringResource(R.string.current_server)
                    BasicComponent(
                        title = slot.alias.ifBlank { stringResource(R.string.slot_name, slot.slot) },
                        summary = if (slot.url.isBlank()) stringResource(R.string.not_configured)
                        else stringResource(R.string.server_endpoint_format, slot.protocol, slot.url, slot.port),
                        startAction = {
                            SettingsIcon(
                                icon = COUIIcons.Light.CloudFill,
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
                                Icon(COUIIcons.Light.Edit, contentDescription = stringResource(R.string.edit_server))
                            }
                            IconButton(onClick = { actions.onDeleteSlot(slot.slot) }) {
                                Icon(COUIIcons.Light.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        },
                        onClick = { actions.onSelectSlot(slot.slot) },
                        insideMargin = SlotRowInsideMargin,
                    )
                }
                if (uiState.slots.isNotEmpty()) SettingsRowDivider()
                ArrowPreference(
                    title = stringResource(R.string.add_webdav_server),
                    startAction = { SettingsIcon(COUIIcons.Light.AddCircle, R.string.add_webdav_server) },
                    onClick = actions.onAddSlot,
                )
            }
        }

        item(key = "display") {
            SettingsGroup(stringResource(R.string.display)) {
                ArrowPreference(
                    title = stringResource(R.string.theme),
                    summary = themeName(uiState.themeId),
                    onClick = actions.onPickTheme,
                    enabled = !uiState.useCouiDefaultColors,
                )
                SettingsRowDivider()
                SwitchPreference(
                    checked = uiState.useCouiDefaultColors,
                    onCheckedChange = actions.onSetUseCouiDefaultColors,
                    title = stringResource(R.string.use_coui_default_colors),
                    summary = stringResource(R.string.use_coui_default_colors_summary),
                )
                SettingsRowDivider()
                ArrowPreference(title = stringResource(R.string.language), summary = languageName(uiState.language), onClick = actions.onPickLanguage)
                SettingsRowDivider()
                OverlaySpinnerPreference(
                    items = gridItems,
                    selectedIndex = (uiState.gridColumns - 1).coerceIn(0, 3),
                    title = stringResource(R.string.grid_columns),
                    onSelectedIndexChange = { actions.onSetGridColumns(it + 1) },
                )
            }
        }

        item(key = "displayOptions") {
            SettingsGroup(stringResource(R.string.display_options)) {
                OverlaySpinnerPreference(
                    items = sortItems,
                    selectedIndex = sortValues.indexOf(uiState.sortOrder).coerceAtLeast(0),
                    title = stringResource(R.string.sort_order),
                    onSelectedIndexChange = { actions.onSetSortOrder(sortValueForIndex(it)) },
                )
                SettingsRowDivider()
                OverlaySpinnerPreference(
                    items = recursiveItems,
                    selectedIndex = recursiveValues.indexOf(uiState.recursiveImageArrangement).coerceAtLeast(0),
                    title = stringResource(R.string.recursive_image_arrangement),
                    summary = stringResource(recursiveImageArrangementRes(uiState.recursiveImageArrangement)),
                    onSelectedIndexChange = { actions.onSetRecursiveImageArrangement(recursiveValues[it]) },
                )
                SettingsRowDivider()
                SwitchPreference(
                    checked = uiState.waterfallShowFilenames,
                    onCheckedChange = actions.onSetWaterfallShowFilenames,
                    title = stringResource(R.string.waterfall_show_filenames),
                )
                SettingsRowDivider()
                OverlaySpinnerPreference(
                    items = qualityItems,
                    selectedIndex = qualityModes.indexOf(uiState.waterfallQualityMode).coerceAtLeast(0),
                    title = stringResource(R.string.thumbnail_quality),
                    onSelectedIndexChange = { actions.onSetWaterfallQualityMode(qualityModes[it]) },
                )
                SettingsRowDivider()
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
        }

        item(key = "performance") {
            SettingsGroup(stringResource(R.string.performance)) {
                OverlaySpinnerPreference(
                    items = bucketItems,
                    selectedIndex = bucketValues.indexOf(uiState.waterfallWidthBucket).coerceAtLeast(0),
                    title = stringResource(R.string.waterfall_width_bucket),
                    summary = stringResource(R.string.waterfall_width_bucket_summary),
                    onSelectedIndexChange = { actions.onSetWaterfallWidthBucket(bucketValues[it]) },
                )
                SettingsRowDivider()
                OverlaySpinnerPreference(
                    items = memoryCacheItems,
                    selectedIndex = memoryCacheValues.indexOf(uiState.glideMemoryCacheScreens).coerceAtLeast(0),
                    title = stringResource(R.string.glide_memory_cache),
                    summary = stringResource(R.string.glide_memory_cache_summary),
                    onSelectedIndexChange = { actions.onSetGlideMemoryCacheScreens(memoryCacheValues[it]) },
                )
            }
        }

        item(key = "reader") {
            SettingsGroup(stringResource(R.string.reader_video_editing)) {
                SliderPreference(
                    value = uiState.readerMaxZoomPercent.toFloat(),
                    onValueChange = { actions.onSetReaderMaxZoom(it.toInt()) },
                    title = stringResource(R.string.reader_max_zoom),
                    valueRange = 100f..500f,
                    steps = 399,
                )
                SettingsRowDivider()
                ArrowPreference(title = stringResource(R.string.default_reader_mode), summary = stringResource(defaultReaderModeRes(uiState.defaultReaderMode)), onClick = actions.onPickDefaultReaderMode)
                SettingsRowDivider()
                ArrowPreference(title = stringResource(R.string.video_external_player_mode), summary = videoPlayerModeName(uiState.videoExternalPlayerMode), onClick = actions.onPickVideoExternalPlayerMode)
                SettingsRowDivider()
                ArrowPreference(title = stringResource(R.string.comfyui_server), summary = uiState.autoWorkflowUrl.ifBlank { stringResource(R.string.not_configured) }, onClick = actions.onEditAutoWorkflowUrl)
            }
        }

        item(key = "advanced") {
            SettingsGroup(stringResource(R.string.advanced), bottomMargin = 0.dp) {
                ArrowPreference(title = stringResource(R.string.clear_cache), summary = stringResource(R.string.clear_cache_summary), onClick = actions.onClearCache)
                SettingsRowDivider()
                if (uiState.isPrivacyMode) {
                    ArrowPreference(title = stringResource(R.string.privacy_exit_policy_title), summary = stringResource(privacyExitPolicyRes(uiState.privacyExitPolicy)), onClick = actions.onPickPrivacyExitPolicy)
                    SettingsRowDivider()
                }
                ArrowPreference(title = stringResource(R.string.about), summary = stringResource(R.string.app_version_format, BuildConfig.VERSION_NAME), enabled = false)
                SettingsRowDivider()
                SwitchPreference(checked = uiState.rotationLocked, onCheckedChange = actions.onSetRotationLocked, title = stringResource(R.string.rotation_lock))
            }
        }

        item(key = "tail") { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

/**
 * Mirrors the example app's settings layout: a [SmallTitle] followed by a [Card] that carries the
 * page's horizontal 16dp inset itself, plus a 16dp gap before the next group.
 */
@Composable
private fun SettingsGroup(
    title: String,
    bottomMargin: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    SmallTitle(text = title)
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = bottomMargin),
        content = content,
    )
}

/** The example app insets every in-card divider by the row's own 16dp content inset. */
@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}


@Composable
private fun SettingsIcon(icon: ImageVector, descriptionRes: Int, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = stringResource(descriptionRes), modifier = modifier)
}

/** Marker for the slot the app is currently connected to. */
private val CurrentServerDotColor = Color(0xFF34C759)

/**
 * Slot rows keep COUI's standard 16dp leading / 10dp vertical inside margin (matching
 * `BasicComponentDefaults.InsideMargin`) but trim the trailing inset so the Delete action button
 * lines up with the trailing chevron column used by arrow preferences.
 */
private val SlotRowInsideMargin = PaddingValues(start = 16.dp, top = 10.dp, end = 1.dp, bottom = 10.dp)

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
