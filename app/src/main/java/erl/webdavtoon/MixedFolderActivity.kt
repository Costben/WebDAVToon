// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import erl.webdavtoon.ui.screen.waterfall.MixedWaterfallActions
import erl.webdavtoon.ui.screen.waterfall.MixedWaterfallItemUi
import erl.webdavtoon.ui.screen.waterfall.MixedWaterfallScreen
import erl.webdavtoon.ui.screen.waterfall.MixedWaterfallViewModel
import erl.webdavtoon.ui.component.DeleteConfirmDialog
import erl.webdavtoon.ui.theme.WebDAVToonTheme
import kotlinx.coroutines.launch

class MixedFolderActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private val viewModel: MixedWaterfallViewModel by viewModels()

    private var showDeleteConfirmDialog by mutableStateOf(false)
    private var pendingDeleteRequest: PendingMixedDelete? = null

    private data class PendingMixedDelete(
        val photos: List<Photo>,
        val folders: List<Folder>,
        val localPhotos: List<Photo>,
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        viewModel.updateStoragePermission(allGranted)
        if (!allGranted) {
            Toast.makeText(this, getString(R.string.storage_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    private val localMediaDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pending = pendingDeleteRequest ?: return@registerForActivityResult
        pendingDeleteRequest = null
        if (result.resultCode != RESULT_OK) return@registerForActivityResult

        lifecycleScope.launch {
            val deletedLocalPhotos = LocalMediaDeleteRequest.awaitDeletedPhotos(
                context = this@MixedFolderActivity,
                photos = pending.localPhotos
            )
            val deletedCount = viewModel.executeDelete(
                selectedPhotos = pending.photos,
                selectedFolders = pending.folders,
                alreadyDeletedLocalPhotos = deletedLocalPhotos
            )
            val requestedCount = pending.photos.size + pending.folders.size
            showDeleteToast(deletedCount, requestedCount)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        settingsManager = SettingsManager(this)
        LogManager.initialize(this)
        applyRotationLock()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !isNightModeActive()

        val folderPath = intent.getStringExtra("EXTRA_FOLDER_PATH") ?: ""
        val isWebDav = intent.getBooleanExtra("EXTRA_IS_WEBDAV", false)
        val isFavorites = intent.getBooleanExtra("EXTRA_IS_FAVORITES", false)

        LibraryState.update(
            serverType = if (isFavorites) "favorites" else if (isWebDav) "webdav" else "local",
            rootFolderPath = if (isFavorites) "favorites" else folderPath
        )

        viewModel.init(folderPath, isWebDav, isFavorites)

        setContent {
            val navOwner = androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner(parent = null)
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner provides navOwner,
            ) {
                val uiState by viewModel.uiState.collectAsState()

                val actions = remember {
                    MixedWaterfallActions(
                        onBackClick = { finish() },
                        onItemClick = { item ->
                            val state = viewModel.uiState.value
                            if (state.isSelectionMode) {
                                viewModel.toggleSelection(item.key)
                            } else when (item) {
                                is MixedWaterfallItemUi.FolderItem -> {
                                    lifecycleScope.launch {
                                        try {
                                            val target = FolderNavigationResolver.resolveTarget(
                                                this@MixedFolderActivity,
                                                item.folder.path,
                                                !item.folder.isLocal
                                            )
                                            FolderNavigationResolver.start(this@MixedFolderActivity, target)
                                        } catch (e: Exception) {
                                            startActivity(Intent(this@MixedFolderActivity, SubFolderActivity::class.java).apply {
                                                putExtra("EXTRA_FOLDER_PATH", item.folder.path)
                                                putExtra("EXTRA_IS_WEBDAV", !item.folder.isLocal)
                                            })
                                        }
                                    }
                                }
                                is MixedWaterfallItemUi.MediaItem -> {
                                    if (item.isVideo) {
                                        ExternalVideoOpener.open(
                                            this@MixedFolderActivity,
                                            item.photo.imageUri.toString(),
                                            item.photo.title,
                                            !item.photo.isLocal,
                                            settingsManager
                                        )
                                    } else {
                                        val imageOnly = state.items
                                            .filterIsInstance<MixedWaterfallItemUi.MediaItem>()
                                            .filterNot { it.isVideo }
                                            .map { it.photo }
                                        val imageIndex = imageOnly.indexOfFirst { it.id == item.photo.id }
                                        if (imageIndex != -1) {
                                            val session = ReaderSessions.create(
                                                ReaderSessionSource.MIXED_FOLDER,
                                                photos = imageOnly
                                            )
                                            startActivity(Intent(this@MixedFolderActivity, PhotoViewActivity::class.java).apply {
                                                putExtra(ReaderSessions.EXTRA_SESSION_ID, session.id)
                                                putExtra("EXTRA_CURRENT_INDEX", imageIndex)
                                                putExtra("EXTRA_IS_FAVORITES", state.isFavorites)
                                            })
                                        }
                                    }
                                }
                            }
                        },
                        onItemLongClick = { item ->
                            viewModel.enterSelectionMode(item.key)
                        },
                        onRefresh = {
                            viewModel.loadContent(forceRefresh = true)
                        },
                        onColumnsChange = { cols ->
                            viewModel.setColumns(cols)
                        },
                        onSearchQueryChange = { query ->
                            viewModel.setSearchKeyword(query)
                        },
                        onClearSearch = {
                            viewModel.setSearchKeyword("")
                        },
                        onSetSortOrder = { order ->
                            viewModel.setSortOrder(order)
                        },
                        onToggleRotationLock = {
                            viewModel.toggleRotationLock()
                            applyRotationLock()
                        },
                        onOpenSettings = {
                            startActivity(Intent(this@MixedFolderActivity, SettingsActivity::class.java))
                        },
                        onOpenRecursiveBrowser = {
                            startActivity(Intent(this@MixedFolderActivity, MainActivity::class.java).apply {
                                putExtra("EXTRA_FOLDER_PATH", folderPath)
                                putExtra("EXTRA_IS_WEBDAV", isWebDav)
                                putExtra("EXTRA_RECURSIVE", true)
                            })
                        },
                        onToggleSelectAll = {
                            val state = viewModel.uiState.value
                            if (state.isAllSelected) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.selectAll()
                            }
                        },
                        onToggleFavorite = {
                            val state = viewModel.uiState.value
                            viewModel.batchToggleFavorite(state.selectedPhotos, state.selectedFolders)
                        },
                        onDeleteClick = {
                            showDeleteConfirmDialog = true
                        },
                        onShareClick = {
                            val state = viewModel.uiState.value
                            MediaShareHelper.sharePhotos(
                                context = this@MixedFolderActivity,
                                scope = lifecycleScope,
                                settingsManager = settingsManager,
                                photos = state.selectedPhotos
                            )
                        },
                        onExitSelectionMode = {
                            viewModel.exitSelectionMode()
                        },
                        onDimensionsResolved = { photoId, width, height ->
                            viewModel.updateResolvedDimensions(photoId, width, height)
                        },
                        onFolderVisibilityChanged = { folder, visible ->
                            viewModel.onFolderPreviewVisible(folder, visible)
                        },
                        onFolderPreviewsRequested = {
                            viewModel.requestMissingFolderPreviews()
                        }
                    )
                }

                WebDAVToonTheme(
                    themeId = settingsManager.getThemeId(),
                    useCouiDefaultColors = settingsManager.useCouiDefaultColors(),
                ) {
                    MixedWaterfallScreen(
                        uiState = uiState,
                        actions = actions,
                    )

                    if (showDeleteConfirmDialog) {
                        DeleteConfirmDialog(
                            message = stringResource(R.string.delete_items_message, uiState.selectedCount),
                            onConfirm = {
                                showDeleteConfirmDialog = false
                                confirmDeleteItems(uiState.selectedPhotos, uiState.selectedFolders)
                            },
                            onDismiss = { showDeleteConfirmDialog = false }
                        )
                    }

                    io.github.suqi8.coui.kmp.utils.COUIPopupUtils.COUIPopupHost()
                }
            }
        }

        checkPermissionsAndLoad(isFavorites, isWebDav)
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.uiState.value.isFavorites && !viewModel.uiState.value.isSelectionMode) {
            viewModel.loadContent()
        }
    }

    private fun confirmDeleteItems(selectedPhotos: List<Photo>, selectedFolders: List<Folder>) {
        val localPhotos = selectedPhotos.filter { it.isLocal }
        if (localPhotos.isNotEmpty() && LocalMediaDeleteRequest.requiresSystemRequest()) {
            val request = runCatching {
                LocalMediaDeleteRequest.create(this, localPhotos)
            }.getOrNull()
            if (request == null) {
                Toast.makeText(this, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
                return
            }
            pendingDeleteRequest = PendingMixedDelete(selectedPhotos, selectedFolders, localPhotos)
            localMediaDeleteLauncher.launch(request)
        } else {
            lifecycleScope.launch {
                val deletedCount = viewModel.executeDelete(selectedPhotos, selectedFolders)
                val requestedCount = selectedPhotos.size + selectedFolders.size
                showDeleteToast(deletedCount, requestedCount)
            }
        }
    }

    private fun showDeleteToast(deletedCount: Int, requestedCount: Int) {
        if (deletedCount == 0) {
            Toast.makeText(this, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val message = if (deletedCount == requestedCount) {
            getString(R.string.deleted_items_count, deletedCount)
        } else {
            getString(R.string.deleted_items_partial, deletedCount, requestedCount)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val imageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val videoGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            imageGranted && videoGranted
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissionsAndLoad(isFavorites: Boolean, isWebDav: Boolean) {
        if (isFavorites || isWebDav) {
            return
        }
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (!hasStoragePermission()) {
            viewModel.updateStoragePermission(false)
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun applyRotationLock() {
        requestedOrientation = if (settingsManager.isRotationLocked()) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyRotationLock()
    }

    private fun isNightModeActive(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}

