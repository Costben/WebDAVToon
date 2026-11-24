// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import erl.webdavtoon.ui.component.GalleryTopAppBar
import erl.webdavtoon.ui.component.GlassTopBarScaffold
import erl.webdavtoon.ui.component.HighlightedTopBarAction
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.NotificationSwipeAction
import io.github.suqi8.coui.kmp.basic.NotificationSwipeBox
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.rememberNotificationSwipeState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.AddCircle
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.CloudFill
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlaySpinnerPreference
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

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
    val onEditTerminalHubUrl: () -> Unit,
    val onPickPrivacyExitPolicy: () -> Unit,
    val onSetRotationLocked: (Boolean) -> Unit,
    val onSetLocalModeEnabled: (Boolean) -> Unit,
    val onPickHomeFolder: () -> Unit,
    val onClearCache: () -> Unit,
    val onOpenLicenses: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    GlassTopBarScaffold(
        modifier = modifier,
        topBar = {
            GalleryTopAppBar(
                title = stringResource(R.string.settings),
                navigationIcon = {
                    HighlightedTopBarAction(
                        icon = COUIIcons.Light.Back,
                        contentDescription = stringResource(R.string.back),
                        onClick = actions.onBack,
                    )
                },
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

    val listState = rememberLazyListState()
    BindScrollToTop(listState)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding() + 12.dp,
            bottom = paddingValues.calculateBottomPadding(),
        ),
    ) {
        item(key = "localMode") {
            SettingsGroup(stringResource(R.string.local_mode)) {
                SwitchPreference(
                    checked = uiState.localModeEnabled,
                    onCheckedChange = actions.onSetLocalModeEnabled,
                    title = stringResource(R.string.local_mode),
                    summary = stringResource(R.string.local_mode_summary),
                )
                if (uiState.localModeEnabled) {
                    SettingsRowDivider()
                    val homeFolderSummary = if (uiState.localHomeFolderPath.isBlank()) {
                        stringResource(R.string.home_folder_default)
                    } else {
                        val lastSegment = uiState.localHomeFolderPath.trimEnd('/').split('/').lastOrNull { it.isNotEmpty() } ?: uiState.localHomeFolderPath
                        android.net.Uri.decode(lastSegment)
                    }
                    ArrowPreference(
                        title = stringResource(R.string.home_folder),
                        summary = homeFolderSummary,
                        onClick = actions.onPickHomeFolder,
                    )
                }
            }
        }

        if (!uiState.localModeEnabled) {
            item(key = "server") {
                ServerSwipeSection(
                    slots = uiState.slots,
                    onSelectSlot = actions.onSelectSlot,
                    onEditSlot = actions.onEditSlot,
                    onDeleteSlot = actions.onDeleteSlot,
                    onAddSlot = actions.onAddSlot,
                )
            }
        }

        item(key = "localExternalServices") {
            SettingsGroup(stringResource(R.string.local_external_services)) {
                ArrowPreference(
                    title = stringResource(R.string.comfyui_server),
                    summary = uiState.autoWorkflowUrl.ifBlank { stringResource(R.string.not_configured) },
                    onClick = actions.onEditAutoWorkflowUrl,
                )
                SettingsRowDivider()
                ArrowPreference(
                    title = stringResource(R.string.terminal_hub_server),
                    summary = uiState.terminalHubUrl.ifBlank { stringResource(R.string.not_configured) },
                    onClick = actions.onEditTerminalHubUrl,
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
                        modifier = SliderRowModifier,
                        title = stringResource(R.string.thumbnail_quality_max_width_mode),
                        valueText = stringResource(R.string.px_value, uiState.waterfallMaxWidth),
                        valueRange = 200f..2000f,
                        steps = 1799,
                        insideMargin = SliderRowInsideMargin,
                    )
                } else {
                    SliderPreference(
                        value = uiState.waterfallPercent.toFloat(),
                        onValueChange = { actions.onSetWaterfallPercent(it.toInt()) },
                        modifier = SliderRowModifier,
                        title = stringResource(R.string.thumbnail_quality_percent_mode),
                        valueText = stringResource(R.string.percent_value, uiState.waterfallPercent),
                        valueRange = 10f..100f,
                        steps = 89,
                        insideMargin = SliderRowInsideMargin,
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
                    modifier = SliderRowModifier,
                    title = stringResource(R.string.reader_max_zoom),
                    valueText = stringResource(R.string.percent_value, uiState.readerMaxZoomPercent),
                    valueRange = 100f..500f,
                    steps = 399,
                    insideMargin = SliderRowInsideMargin,
                )
                SettingsRowDivider()
                ArrowPreference(title = stringResource(R.string.default_reader_mode), summary = stringResource(defaultReaderModeRes(uiState.defaultReaderMode)), onClick = actions.onPickDefaultReaderMode)
                SettingsRowDivider()
                ArrowPreference(title = stringResource(R.string.video_external_player_mode), summary = videoPlayerModeName(uiState.videoExternalPlayerMode), onClick = actions.onPickVideoExternalPlayerMode)
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
                ArrowPreference(title = stringResource(R.string.oss_licenses), summary = stringResource(R.string.oss_licenses_summary), onClick = actions.onOpenLicenses)
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

/**
 * Renders the WebDAV server slots as standalone ColorOS 17 [NotificationSwipeBox] cards:
 * swipe left to reveal the Edit and Delete actions, swipe right to collapse them, with only one
 * slot open at a time. The "Add WebDAV server" row stays in its own card directly underneath.
 */
@OptIn(ExperimentalCOUIApi::class)
@Composable
private fun ServerSwipeSection(
    slots: List<WebDavSlotUi>,
    onSelectSlot: (Int) -> Unit,
    onEditSlot: (Int) -> Unit,
    onDeleteSlot: (Int) -> Unit,
    onAddSlot: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var revealedSlot by remember { mutableStateOf<Int?>(null) }
    val cardColor = CardDefaults.defaultColors().color
    val deleteTint = COUITheme.colorScheme.error
    val currentServerDescription = stringResource(R.string.current_server)
    val editDescription = stringResource(R.string.edit_server)
    val deleteDescription = stringResource(R.string.delete)

    SmallTitle(text = stringResource(R.string.webdav_server))
    if (slots.isNotEmpty()) {
        // SettingsContent sits inside GlassTopBarScaffold's `.layerBackdrop(backdrop)`, so clear
        // LocalBackdrop here to prevent the MetaBall surface from self-sampling its own layer
        // while keeping the full AGSL metaball melt and material edge highlight on `cardColor`.
        CompositionLocalProvider(LocalBackdrop provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                slots.forEach { slot ->
                    key(slot.slot) {
                        val swipeState = rememberNotificationSwipeState()
                        LaunchedEffect(swipeState.isRevealed) {
                            if (swipeState.isRevealed) {
                                revealedSlot = slot.slot
                            } else if (revealedSlot == slot.slot) {
                                revealedSlot = null
                            }
                        }
                        LaunchedEffect(revealedSlot) {
                            if (revealedSlot != null && revealedSlot != slot.slot && swipeState.isRevealed) {
                                swipeState.close()
                            }
                        }

                        val swipeActions = remember(
                            slot.slot,
                            editDescription,
                            deleteDescription,
                            deleteTint,
                            onEditSlot,
                            onDeleteSlot,
                        ) {
                            listOf(
                                NotificationSwipeAction(
                                    icon = COUIIcons.Light.Edit,
                                    description = editDescription,
                                    onClick = { onEditSlot(slot.slot) },
                                ),
                                NotificationSwipeAction(
                                    icon = null,
                                    description = deleteDescription,
                                    onClick = { onDeleteSlot(slot.slot) },
                                    color = deleteTint,
                                ),
                            )
                        }

                        NotificationSwipeBox(
                            actions = swipeActions,
                            state = swipeState,
                            minHeight = ServerSwipeCardMinHeight,
                            containerColor = cardColor,
                            rimColor = Color.Transparent,
                            dismissOnFullSwipe = false,
                        ) {
                            BasicComponent(
                                title = slot.alias.ifBlank { stringResource(R.string.slot_name, slot.slot) },
                                summary = if (slot.url.isBlank()) stringResource(R.string.not_configured)
                                else stringResource(R.string.server_endpoint_format, slot.protocol, slot.url, slot.port),
                                modifier = Modifier.align(Alignment.Center),
                                startAction = {
                                    SettingsIcon(
                                        icon = COUIIcons.Light.CloudFill,
                                        descriptionRes = R.string.webdav_server,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                },
                                endActions = {
                                    Box(
                                        modifier = Modifier
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
                                },
                                onClick = {
                                    if (swipeState.isRevealed) {
                                        scope.launch { swipeState.close() }
                                    } else {
                                        onSelectSlot(slot.slot)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = if (slots.isNotEmpty()) 8.dp else 0.dp, bottom = 16.dp),
    ) {
        ArrowPreference(
            title = stringResource(R.string.add_webdav_server),
            startAction = { SettingsIcon(COUIIcons.Light.AddCircle, R.string.add_webdav_server) },
            onClick = onAddSlot,
        )
    }
}

/** Marker for the slot the app is currently connected to. */
private val CurrentServerDotColor = Color(0xFF34C759)

/** Keeps swipe action pills tall enough to feel substantial even on single-line server cards. */
private val ServerSwipeCardMinHeight = 72.dp

/**
 * Slider rows stack a 36dp touch target and the component's 10dp vertical inside margin above the
 * track, which opens a gap the COUI demo does not show. The track already carries 8dp of its own
 * above and below inside the touch target, so the row reads tighter with the inside margin kept
 * horizontal only.
 */
private val SliderRowInsideMargin = PaddingValues(horizontal = 16.dp, vertical = 0.dp)

/**
 * Dropping the vertical inside margin leaves the row at the component's 48dp minimum, and the COUI
 * title line box is tall enough that the text then sits about 2dp under the divider above. The top
 * inset goes on the row instead, so the text gets its breathing room without pushing the track down.
 */
private val SliderRowModifier = Modifier.padding(top = 14.dp)

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
