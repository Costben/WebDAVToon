// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import erl.webdavtoon.GestureAction
import erl.webdavtoon.GestureType
import erl.webdavtoon.GestureZone
import erl.webdavtoon.R
import erl.webdavtoon.ReaderGestureControlConfig
import erl.webdavtoon.actionFor
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * Full-screen editor for the reader's 3x3 gesture zones.
 *
 * Mirrors the layout the reader resolves taps against (see `zoneAt`), so the zone the user
 * picks here is the zone they hit in the reader. Only single tap and long press are
 * configurable: double tap stays with the image's own zoom.
 */
@Composable
fun GestureControlOverlay(
    config: ReaderGestureControlConfig,
    onEnabledChange: (Boolean) -> Unit,
    onActionChange: (GestureZone, GestureType, GestureAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedZone by remember { mutableStateOf(GestureZone.CENTER) }
    var editingType by remember { mutableStateOf<GestureType?>(null) }

    val noRipple = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = noRipple,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(COUITheme.colorScheme.surface)
                .clickable(
                    interactionSource = noRipple,
                    indication = null,
                    onClick = {},
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.gesture_control_title),
                    style = COUITheme.textStyles.title3,
                    color = COUITheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = config.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val pendingType = editingType
            if (pendingType == null) {
                GestureZoneGrid(
                    config = config,
                    selectedZone = selectedZone,
                    onSelectZone = { selectedZone = it },
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = zoneName(selectedZone),
                    style = COUITheme.textStyles.title3,
                    color = COUITheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(4.dp))

                GestureSettingRow(
                    label = stringResource(R.string.gesture_control_single_tap),
                    value = actionLabel(config.actionFor(selectedZone, GestureType.SINGLE_TAP)),
                    onClick = { editingType = GestureType.SINGLE_TAP },
                )
                GestureSettingRow(
                    label = stringResource(R.string.gesture_control_long_press),
                    value = actionLabel(config.actionFor(selectedZone, GestureType.LONG_PRESS)),
                    onClick = { editingType = GestureType.LONG_PRESS },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = noRipple,
                            indication = null,
                            onClick = { editingType = null },
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = COUIIcons.Light.ChevronBackward,
                        contentDescription = stringResource(R.string.back),
                        tint = COUITheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.gesture_control_choose_action) + " · " +
                            zoneName(selectedZone) + " · " + gestureTypeName(pendingType),
                        style = COUITheme.textStyles.title3,
                        color = COUITheme.colorScheme.onSurface,
                    )
                }

                val currentAction = config.actionFor(selectedZone, pendingType)
                GestureAction.entries.forEach { action ->
                    GestureActionRow(
                        label = actionLabel(action),
                        selected = action == currentAction,
                        onClick = {
                            onActionChange(selectedZone, pendingType, action)
                            editingType = null
                        },
                    )
                }
            }
        }
    }
}

/** The 3x3 grid; each cell previews the zone's single tap and long press. */
@Composable
private fun GestureZoneGrid(
    config: ReaderGestureControlConfig,
    selectedZone: GestureZone,
    onSelectZone: (GestureZone) -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth()) {
        GestureZone.entries.chunked(3).forEach { rowZones ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowZones.forEach { zone ->
                    val isSelected = zone == selectedZone
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) {
                                    COUITheme.colorScheme.secondaryContainer
                                } else {
                                    COUITheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                }
                            )
                            .clickable(
                                interactionSource = noRipple,
                                indication = null,
                                onClick = { onSelectZone(zone) },
                            )
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = zoneName(zone),
                            style = COUITheme.textStyles.footnote2,
                            color = COUITheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = gridActionLabel(config.actionFor(zone, GestureType.SINGLE_TAP)),
                            style = COUITheme.textStyles.footnote2,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = gridActionLabel(config.actionFor(zone, GestureType.LONG_PRESS)),
                            style = COUITheme.textStyles.footnote2,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun GestureSettingRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = noRipple,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.body1,
            color = COUITheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = COUITheme.textStyles.body2,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
    }
}

@Composable
private fun GestureActionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = noRipple,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.body1,
            color = if (selected) {
                COUITheme.colorScheme.primary
            } else {
                COUITheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = COUIIcons.Light.Ok,
                contentDescription = null,
                tint = COUITheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun zoneName(zone: GestureZone): String = stringResource(
    when (zone) {
        GestureZone.TOP_LEFT -> R.string.gesture_control_zone_top_left
        GestureZone.TOP_CENTER -> R.string.gesture_control_zone_top_center
        GestureZone.TOP_RIGHT -> R.string.gesture_control_zone_top_right
        GestureZone.CENTER_LEFT -> R.string.gesture_control_zone_center_left
        GestureZone.CENTER -> R.string.gesture_control_zone_center
        GestureZone.CENTER_RIGHT -> R.string.gesture_control_zone_center_right
        GestureZone.BOTTOM_LEFT -> R.string.gesture_control_zone_bottom_left
        GestureZone.BOTTOM_CENTER -> R.string.gesture_control_zone_bottom_center
        GestureZone.BOTTOM_RIGHT -> R.string.gesture_control_zone_bottom_right
    }
)

@Composable
private fun gestureTypeName(type: GestureType): String = stringResource(
    when (type) {
        GestureType.SINGLE_TAP -> R.string.gesture_control_single_tap
        GestureType.DOUBLE_TAP -> R.string.gesture_control_double_tap
        GestureType.LONG_PRESS -> R.string.gesture_control_long_press
    }
)

/** Full-length action name, used in the picker and the per-zone setting rows. */
@Composable
private fun actionLabel(action: GestureAction): String = stringResource(
    when (action) {
        GestureAction.NONE -> R.string.gesture_control_action_none
        GestureAction.PHOTO_INFO -> R.string.gesture_control_action_photo_info
        GestureAction.START_SLIDESHOW -> R.string.gesture_control_action_start_slideshow
        GestureAction.TOGGLE_IMMERSIVE -> R.string.gesture_control_action_toggle_immersive
        GestureAction.PREVIOUS_PAGE -> R.string.gesture_control_action_previous_page
        GestureAction.NEXT_PAGE -> R.string.gesture_control_action_next_page
    }
)

/** Compact action name, used inside the cramped grid cells. */
@Composable
private fun gridActionLabel(action: GestureAction): String = stringResource(
    when (action) {
        GestureAction.NONE -> R.string.gesture_control_grid_action_none
        GestureAction.PHOTO_INFO -> R.string.gesture_control_grid_action_photo_info
        GestureAction.START_SLIDESHOW -> R.string.gesture_control_grid_action_start_slideshow
        GestureAction.TOGGLE_IMMERSIVE -> R.string.gesture_control_grid_action_toggle_immersive
        GestureAction.PREVIOUS_PAGE -> R.string.gesture_control_grid_action_previous_page
        GestureAction.NEXT_PAGE -> R.string.gesture_control_grid_action_next_page
    }
)
