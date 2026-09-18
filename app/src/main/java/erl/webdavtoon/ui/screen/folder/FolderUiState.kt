package erl.webdavtoon.ui.screen.folder

import erl.webdavtoon.Folder
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.ui.UiMode
import erl.webdavtoon.ui.screen.settings.WebDavSlotUi

data class FolderItemUi(
    val path: String,
    val name: String,
    val isLocal: Boolean,
    val photoCount: Int,
    val previewUris: List<String>,
    val hasSubFolders: Boolean,
    val isSelected: Boolean = false,
)

enum class RefreshStatus {
    Idle,
    Refreshing,
    Completed,
}

data class FolderUiState(
    val rawFolders: List<Folder> = emptyList(),
    val folders: List<FolderItemUi> = emptyList(),
    val loading: Boolean = true,
    val isRefreshing: Boolean = false,
    val refreshStatus: RefreshStatus = RefreshStatus.Idle,
    val error: String? = null,
    val searchKeyword: String = "",
    val isSearching: Boolean = false,
    val sortOrder: Int = SettingsManager.SORT_DATE_DESC,
    val gridColumns: Int = 2,
    val isSelectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val selectedCount: Int = 0,
    val storagePermissionGranted: Boolean = true,
    val rotationLocked: Boolean = false,
    val uiMode: UiMode = UiMode.Miuix,
    val currentSlot: Int = 0,
    val slots: List<WebDavSlotUi> = emptyList(),
    val isWebDavEnabled: Boolean = true,
    val isPrivacyMode: Boolean = false,
)
