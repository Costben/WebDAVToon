package erl.webdavtoon.ui.screen.settings

import erl.webdavtoon.PrivacyModeState
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.ui.UiMode

data class WebDavSlotUi(
    val slot: Int,
    val alias: String,
    val protocol: String,
    val url: String,
    val port: Int,
    val username: String,
    val domain: String,
    val rememberPassword: Boolean,
    val isPrivate: Boolean,
    val enabled: Boolean,
    val hasPassword: Boolean,
    val isCurrent: Boolean,
)

data class SettingsUiState(
    val loading: Boolean = true,
    val slots: List<WebDavSlotUi> = emptyList(),
    val currentSlot: Int = 0,
    val uiMode: UiMode = UiMode.Miuix,
    val themeId: Int = 0,
    val language: String = "default",
    val gridColumns: Int = 2,
    val drawerEdgeWidthPercent: Int = SettingsManager.DEFAULT_DRAWER_EDGE_WIDTH_PERCENT,
    val sortOrder: Int = 2,
    val recursiveImageArrangement: Int = SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GROUPED,
    val waterfallShowFilenames: Boolean = true,
    val waterfallQualityMode: String = SettingsManager.WATERFALL_MODE_PERCENT,
    val waterfallPercent: Int = 70,
    val waterfallMaxWidth: Int = 600,
    val readerMaxZoomPercent: Int = 300,
    val defaultReaderMode: String = SettingsManager.DEFAULT_READER_MODE_WEBTOON,
    val videoExternalPlayerMode: String = SettingsManager.VIDEO_EXTERNAL_PLAYER_MODE_SYSTEM_DEFAULT,
    val autoWorkflowUrl: String = "",
    val privacyExitPolicy: PrivacyModeState.ExitPolicy = PrivacyModeState.ExitPolicy.ON_BACKGROUND,
    val isPrivacyMode: Boolean = false,
    val rotationLocked: Boolean = false,
)

/** Constants exposed to the Compose settings slices without duplicate literals. */
object SettingsDefaults {
    const val WATERFALL_MODE_PERCENT = SettingsManager.WATERFALL_MODE_PERCENT
    const val WATERFALL_MODE_MAX_WIDTH = SettingsManager.WATERFALL_MODE_MAX_WIDTH
    const val SORT_NAME_ASC = SettingsManager.SORT_NAME_ASC
    const val SORT_NAME_DESC = SettingsManager.SORT_NAME_DESC
    const val SORT_DATE_DESC = SettingsManager.SORT_DATE_DESC
    const val SORT_DATE_ASC = SettingsManager.SORT_DATE_ASC
    const val SORT_RANDOM_FOLDERS = SettingsManager.SORT_RANDOM_FOLDERS
    const val RECURSIVE_IMAGE_ARRANGEMENT_GROUPED = SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GROUPED
    const val RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_DESC = SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_DESC
    const val RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_ASC = SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_ASC
    const val DEFAULT_READER_MODE_WEBTOON = SettingsManager.DEFAULT_READER_MODE_WEBTOON
    const val DEFAULT_READER_MODE_CARD = SettingsManager.DEFAULT_READER_MODE_CARD
    const val VIDEO_EXTERNAL_PLAYER_MODE_SYSTEM_DEFAULT = SettingsManager.VIDEO_EXTERNAL_PLAYER_MODE_SYSTEM_DEFAULT
    const val VIDEO_EXTERNAL_PLAYER_MODE_CHOOSER = SettingsManager.VIDEO_EXTERNAL_PLAYER_MODE_CHOOSER
}

data class ServerConfigFormState(
    val slot: Int,
    val alias: String = "",
    val protocol: String = "https",
    val url: String = "",
    val port: String = "443",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val rememberPassword: Boolean = true,
    val isPrivate: Boolean = false,
    val showPassword: Boolean = false,
    val testing: Boolean = false,
    val testResult: String? = null,
    val error: String? = null,
)

fun privacyExitPolicyRes(policy: PrivacyModeState.ExitPolicy): Int = when (policy) {
    PrivacyModeState.ExitPolicy.ON_BACKGROUND -> erl.webdavtoon.R.string.privacy_exit_policy_on_background
    PrivacyModeState.ExitPolicy.ON_PROCESS_DEATH -> erl.webdavtoon.R.string.privacy_exit_policy_on_process_death
    PrivacyModeState.ExitPolicy.MANUAL_ONLY -> erl.webdavtoon.R.string.privacy_exit_policy_manual_only
}

fun recursiveImageArrangementRes(arrangement: Int): Int = when (arrangement) {
    SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_DESC -> erl.webdavtoon.R.string.recursive_image_arrangement_global_date_desc
    SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_ASC -> erl.webdavtoon.R.string.recursive_image_arrangement_global_date_asc
    else -> erl.webdavtoon.R.string.recursive_image_arrangement_grouped
}

fun defaultReaderModeRes(mode: String): Int = when (mode) {
    SettingsManager.DEFAULT_READER_MODE_CARD -> erl.webdavtoon.R.string.default_reader_mode_card
    else -> erl.webdavtoon.R.string.default_reader_mode_webtoon
}

fun privacyExitPolicyLabel(policy: PrivacyModeState.ExitPolicy): String = when (policy) {
    PrivacyModeState.ExitPolicy.ON_BACKGROUND -> "On app background"
    PrivacyModeState.ExitPolicy.ON_PROCESS_DEATH -> "On process close"
    PrivacyModeState.ExitPolicy.MANUAL_ONLY -> "Manual only"
}

fun recursiveImageArrangementLabel(arrangement: Int): String = when (arrangement) {
    SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_DESC -> "Global date (newest first)"
    SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GLOBAL_DATE_ASC -> "Global date (oldest first)"
    else -> "Grouped by folder"
}

fun defaultReaderModeLabel(mode: String): String = when (mode) {
    SettingsManager.DEFAULT_READER_MODE_CARD -> "Card"
    else -> "Webtoon"
}
