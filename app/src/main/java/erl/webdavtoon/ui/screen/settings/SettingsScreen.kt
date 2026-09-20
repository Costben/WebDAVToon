package erl.webdavtoon.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import erl.webdavtoon.ui.LocalUiMode
import erl.webdavtoon.ui.UiMode

/** Shared settings actions for both Miuix and Material presentation tracks. */
data class SettingsActions(
    val onSelectSlot: (Int) -> Unit,
    val onAddSlot: () -> Unit,
    val onDeleteSlot: (Int) -> Unit,
    val onEditSlot: (Int) -> Unit,
    val onRefreshSlots: () -> Unit,
    val onSetUiMode: (UiMode) -> Unit,
    val onPickTheme: () -> Unit,
    val onPickLanguage: () -> Unit,
    val onSetGridColumns: (Int) -> Unit,
    val onSetDrawerEdgeWidth: (Int) -> Unit,
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
    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingsScreenMiuix(uiState, actions, modifier)
        UiMode.Material -> SettingsScreenMaterial(uiState, actions, modifier)
    }
}
