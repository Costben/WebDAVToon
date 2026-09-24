// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import android.Manifest
import android.content.Context
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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import erl.webdavtoon.ui.screen.media.MediaWaterfallActions
import erl.webdavtoon.ui.screen.media.MediaWaterfallItemUi
import erl.webdavtoon.ui.screen.media.MediaWaterfallScreen
import erl.webdavtoon.ui.screen.media.MediaWaterfallViewModel
import erl.webdavtoon.ui.screen.settings.dialog.ServerConfigAction
import erl.webdavtoon.ui.screen.settings.dialog.ServerConfigDialog
import erl.webdavtoon.ui.screen.settings.dialog.ServerConfigEvent
import erl.webdavtoon.ui.screen.settings.dialog.ServerConfigViewModel
import erl.webdavtoon.ui.component.ComfyUiEditDialog
import erl.webdavtoon.ui.component.DeleteConfirmDialog
import erl.webdavtoon.ui.theme.WebDAVToonTheme
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import kotlinx.coroutines.launch

/**
 * Media-only waterfall page. The page is fully Compose-driven; this Activity
 * only hosts navigation, permissions, biometrics, and Activity results.
 *
 * Extends [FragmentActivity] (not appcompat) because `BiometricPrompt` requires it.
 */
class MainActivity : FragmentActivity() {

    private lateinit var settingsManager: SettingsManager
    private val viewModel: MediaWaterfallViewModel by viewModels()
    private val serverConfigViewModel: ServerConfigViewModel by viewModels()

    private var showDeleteConfirmDialog by mutableStateOf(false)
    private var serverConfigSlot by mutableStateOf<Int?>(null)
    private var comfyUiDialogState by mutableStateOf<EditDialogHelper.DialogState?>(null)

    private data class InfoDialog(val title: String, val message: String)

    private var infoDialog by mutableStateOf<InfoDialog?>(null)

    private var folderPath: String = ""
    private var isRemote: Boolean = false
    private var isRecursive: Boolean = false

    private data class PendingLocalMediaDelete(
        val photos: List<Photo>,
        val sourcePhotos: List<Photo>,
    )

    private var pendingLocalMediaDelete: PendingLocalMediaDelete? = null

    private val localMediaDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pending = pendingLocalMediaDelete ?: return@registerForActivityResult
        pendingLocalMediaDelete = null
        if (result.resultCode != RESULT_OK) return@registerForActivityResult

        lifecycleScope.launch {
            val deletedLocalPhotos = LocalMediaDeleteRequest.awaitDeletedPhotos(
                context = this@MainActivity,
                photos = pending.photos,
            )
            val deletedCount = viewModel.executeDelete(pending.photos, deletedLocalPhotos)
            val requestedCount = pending.photos.size
            showDeleteToast(deletedCount, requestedCount)
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.load(forceRefresh = true)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        viewModel.updateStoragePermission(allGranted)
        if (!allGranted) {
            Toast.makeText(this, getString(R.string.storage_permission_required), Toast.LENGTH_LONG).show()
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

        folderPath = intent.getStringExtra("EXTRA_FOLDER_PATH") ?: ""
        isRemote = intent.getBooleanExtra("EXTRA_IS_WEBDAV", false)
        isRecursive = intent.getBooleanExtra("EXTRA_RECURSIVE", false)

        LibraryState.update(
            serverType = if (isRemote) "webdav" else "local",
            rootFolderPath = folderPath,
        )

        viewModel.init(folderPath, isRemote, isRecursive)

        setContent {
            val navOwner = androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner(parent = null)
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner provides navOwner,
            ) {
                val uiState by viewModel.uiState.collectAsState()
                val cfgState by serverConfigViewModel.state.collectAsState()

                val actions = remember {
                    MediaWaterfallActions(
                        onBackClick = { finish() },
                        onItemClick = { item -> onItemClick(item) },
                        onItemLongClick = { item -> viewModel.enterSelectionMode(item.key) },
                        onRefresh = { viewModel.load(forceRefresh = true) },
                        onLoadMore = { viewModel.loadNextPage() },
                        onColumnsChange = { columns -> viewModel.setColumns(columns) },
                        onSearchQueryChange = { keyword -> viewModel.setSearchKeyword(keyword) },
                        onClearSearch = { viewModel.setSearchKeyword("") },
                        onSetSortOrder = { order -> viewModel.setSortOrder(order) },
                        onToggleRandomizePhotos = { viewModel.toggleRandomizePhotos() },
                        onToggleRotationLock = {
                            viewModel.toggleRotationLock()
                            applyRotationLock()
                        },
                        onOpenSettings = {
                            settingsLauncher.launch(Intent(this@MainActivity, SettingsActivity::class.java))
                        },
                        onToggleSelectAll = {
                            if (viewModel.uiState.value.isAllSelected) viewModel.clearSelection() else viewModel.selectAll()
                        },
                        onToggleFavorite = {
                            viewModel.batchToggleFavorite(viewModel.uiState.value.selectedPhotos)
                        },
                        onDeleteClick = { showDeleteConfirmDialog = true },
                        onShareClick = {
                            MediaShareHelper.sharePhotos(
                                context = this@MainActivity,
                                scope = lifecycleScope,
                                settingsManager = settingsManager,
                                photos = viewModel.uiState.value.selectedPhotos,
                            )
                        },
                        onInfoClick = { showSelectedPhotoDetails() },
                        onEditClick = { editSelectedPhotos() },
                        onExitSelectionMode = { viewModel.exitSelectionMode() },
                        onDimensionsResolved = { photoId, width, height ->
                            viewModel.updateResolvedDimensions(photoId, width, height)
                        },
                        onSelectSlot = { slot -> onSelectSlot(slot) },
                        onEditSlot = { slot -> openServerConfig(slot) },
                        onDuplicateSlot = { slot -> duplicateSlot(slot) },
                        onAddSlot = {
                            val nextSlot = (settingsManager.getAllSlotsUnfiltered().maxOrNull() ?: -1) + 1
                            openServerConfig(nextSlot)
                        },
                        onEnterPrivacy = { tryEnterPrivacyMode { viewModel.refreshSlots() } },
                        onExitPrivacy = {
                            PrivacyModeState.exit(this@MainActivity)
                            viewModel.refreshSlots()
                        },
                        onOpenFavorites = {
                            startActivity(Intent(this@MainActivity, MixedFolderActivity::class.java).apply {
                                putExtra("EXTRA_IS_FAVORITES", true)
                            })
                        },
                    )
                }

                WebDAVToonTheme(
                    themeId = uiState.themeId,
                    useCouiDefaultColors = uiState.useCouiDefaultColors,
                ) {
                    MediaWaterfallScreen(
                        uiState = uiState,
                        actions = actions,
                    )

                    if (showDeleteConfirmDialog) {
                        DeleteConfirmDialog(
                            message = stringResource(R.string.delete_items_message, uiState.selectedCount),
                            onConfirm = {
                                showDeleteConfirmDialog = false
                                confirmDeleteSelected()
                            },
                            onDismiss = { showDeleteConfirmDialog = false },
                        )
                    }

                    serverConfigSlot?.let {
                        ServerConfigDialog(
                            state = cfgState,
                            visible = true,
                            onAction = { action -> handleServerConfigAction(action) },
                            onDismiss = { serverConfigSlot = null },
                        )
                    }

                    comfyUiDialogState?.let { dialogState ->
                        ComfyUiEditDialog(
                            state = dialogState,
                            onSubmit = { workflow, prompt ->
                                val form = dialogState as? EditDialogHelper.DialogState.Form
                                comfyUiDialogState = null
                                if (form != null) {
                                    EditDialogHelper.submit(
                                        activity = this@MainActivity,
                                        form = form,
                                        workflow = workflow,
                                        prompt = prompt,
                                        onSubmitted = { viewModel.exitSelectionMode() },
                                    )
                                }
                            },
                            onDismiss = { comfyUiDialogState = null },
                        )
                    }

                    infoDialog?.let { dialog ->
                        OverlayDialog(
                            show = true,
                            title = dialog.title,
                            summary = dialog.message,
                            onDismissRequest = { infoDialog = null },
                            content = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(
                                        text = stringResource(R.string.ok),
                                        onClick = { infoDialog = null },
                                    )
                                }
                            },
                        )
                    }

                    io.github.suqi8.coui.kmp.utils.COUIPopupUtils.COUIPopupHost()
                }
            }
        }

        lifecycleScope.launch {
            serverConfigViewModel.events.collect { event ->
                when (event) {
                    is ServerConfigEvent.Saved -> {
                        serverConfigSlot = null
                        viewModel.refreshSlots()
                        viewModel.load(forceRefresh = true)
                        Toast.makeText(this@MainActivity, R.string.server_saved, Toast.LENGTH_SHORT).show()
                    }
                    is ServerConfigEvent.Message ->
                        Toast.makeText(this@MainActivity, event.resId, Toast.LENGTH_SHORT).show()
                    is ServerConfigEvent.MessageText ->
                        Toast.makeText(this@MainActivity, event.text, Toast.LENGTH_SHORT).show()
                    ServerConfigEvent.Dismissed -> serverConfigSlot = null
                }
            }
        }

        checkPermissionsAndLoad()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSlots()
    }

    private fun onItemClick(item: MediaWaterfallItemUi) {
        val state = viewModel.uiState.value
        if (state.isSelectionMode) {
            viewModel.toggleSelection(item.key)
            return
        }
        if (item.isVideo) {
            ExternalVideoOpener.open(this, item.photo.imageUri.toString(), item.photo.title, !item.photo.isLocal, settingsManager)
            return
        }

        val imageOnly = state.items.filterNot { it.isVideo }.map { it.photo }
        val imageIndex = imageOnly.indexOfFirst { it.id == item.photo.id }
        if (imageIndex == -1) return

        val session = ReaderSessions.create(
            source = ReaderSessionSource.MAIN_ACTIVITY,
            photos = imageOnly,
        )
        startActivity(Intent(this, PhotoViewActivity::class.java).apply {
            putExtra(ReaderSessions.EXTRA_SESSION_ID, session.id)
            putExtra("EXTRA_CURRENT_INDEX", imageIndex)
            putExtra("EXTRA_IS_FAVORITES", false)
        })
    }

    private fun confirmDeleteSelected() {
        val selectedPhotos = viewModel.uiState.value.selectedPhotos
        if (selectedPhotos.isEmpty()) return

        if (!isRemote && selectedPhotos.all { it.isLocal } && LocalMediaDeleteRequest.requiresSystemRequest()) {
            val request = runCatching { LocalMediaDeleteRequest.create(this, selectedPhotos) }.getOrNull()
            if (request == null) {
                Toast.makeText(this, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
                return
            }
            pendingLocalMediaDelete = PendingLocalMediaDelete(selectedPhotos, selectedPhotos)
            localMediaDeleteLauncher.launch(request)
            return
        }

        lifecycleScope.launch {
            val deletedCount = viewModel.executeDelete(selectedPhotos)
            showDeleteToast(deletedCount, selectedPhotos.size)
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

    private fun editSelectedPhotos() {
        val selectedPhotos = viewModel.uiState.value.selectedPhotos
        if (selectedPhotos.isEmpty()) return
        EditDialogHelper.show(
            activity = this,
            selectedPhotos = selectedPhotos,
            settingsManager = settingsManager,
            onStateChange = { comfyUiDialogState = it },
            onSubmitted = { viewModel.exitSelectionMode() },
        )
    }

    private fun showSelectedPhotoDetails() {
        val selectedPhotos = viewModel.uiState.value.selectedPhotos
        if (selectedPhotos.isEmpty()) return

        if (selectedPhotos.size == 1) {
            showPhotoDetailsDialog(selectedPhotos.first())
            return
        }

        val previewPhotos = selectedPhotos.take(10)
        val message = buildString {
            previewPhotos.forEachIndexed { index, photo ->
                if (index > 0) append("\n\n")
                append(formatPhotoDetails(photo))
            }
            if (selectedPhotos.size > 10) {
                append("\n\n")
                append(getString(R.string.photo_details_more_ellipsis))
            }
        }

        infoDialog = InfoDialog(
            title = getString(R.string.photo_details_selected_title, selectedPhotos.size),
            message = message,
        )
    }

    private fun showPhotoDetailsDialog(photo: Photo) {
        infoDialog = InfoDialog(
            title = getString(R.string.photo_details),
            message = formatPhotoDetails(photo),
        )
    }

    private fun formatPhotoDetails(photo: Photo): String {
        return getString(R.string.file_name_prefix, photo.title) + "\n" +
            getString(R.string.file_size_prefix, android.text.format.Formatter.formatFileSize(this, photo.size)) + "\n" +
            getString(R.string.file_dimension_prefix, photo.width, photo.height) + "\n" +
            getString(R.string.local_prefix, photo.isLocal)
    }

    private fun onSelectSlot(slot: Int) {
        if (settingsManager.getCurrentSlot() == slot) return
        viewModel.selectSlot(slot)
        Toast.makeText(this, getString(R.string.switched_server), Toast.LENGTH_SHORT).show()
    }

    private fun handleServerConfigAction(action: ServerConfigAction) {
        when (action) {
            is ServerConfigAction.Update -> serverConfigViewModel.update(action.transform)
            ServerConfigAction.ToggleShowPassword -> serverConfigViewModel.toggleShowPassword()
            ServerConfigAction.TestConnection -> serverConfigViewModel.testConnection()
            ServerConfigAction.EnumerateShares -> serverConfigViewModel.enumerateShares()
            ServerConfigAction.DiscoverHosts -> serverConfigViewModel.discoverHosts()
            ServerConfigAction.DismissDiscovery -> serverConfigViewModel.dismissDiscovery()
            is ServerConfigAction.ApplyHost -> serverConfigViewModel.applyDiscoveredHost(action.host)
            is ServerConfigAction.ApplyShare -> serverConfigViewModel.applySelectedShare(action.name)
            ServerConfigAction.Save -> serverConfigViewModel.save()
        }
    }

    private fun openServerConfig(slot: Int) {
        serverConfigSlot = slot
        serverConfigViewModel.load(slot)
    }

    private fun duplicateSlot(sourceSlot: Int) {
        val slots = settingsManager.getAllSlotsUnfiltered()
        val newSlot = (slots.maxOrNull() ?: -1) + 1
        settingsManager.saveWebDavConfiguration(
            slot = newSlot,
            alias = "${settingsManager.getWebDavAlias(sourceSlot)} Copy",
            protocol = settingsManager.getWebDavProtocol(sourceSlot),
            url = settingsManager.getWebDavUrl(sourceSlot),
            port = settingsManager.getWebDavPort(sourceSlot),
            username = settingsManager.getWebDavUsername(sourceSlot),
            password = settingsManager.getWebDavPassword(sourceSlot),
            rememberPassword = settingsManager.isWebDavRememberPassword(sourceSlot),
            enabled = settingsManager.isWebDavEnabled(sourceSlot),
            switchToSlotOnSave = false,
            isPrivate = settingsManager.isWebDavPrivate(sourceSlot),
            domain = settingsManager.getWebDavDomain(sourceSlot),
        )
        Toast.makeText(this, getString(R.string.server_duplicated), Toast.LENGTH_SHORT).show()
        viewModel.refreshSlots()
        openServerConfig(newSlot)
    }

    private fun tryEnterPrivacyMode(onSuccess: () -> Unit) {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (km?.isDeviceSecure != true) return

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    PrivacyModeState.enter(this@MainActivity)
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = Unit
                override fun onAuthenticationFailed() = Unit
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: Throwable) {
            // Silent failure
        }
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

    private fun checkPermissionsAndLoad() {
        if (isRemote) return

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

object PhotoCache {
    private val photos = java.util.concurrent.CopyOnWriteArrayList<Photo>()

    fun setPhotos(list: List<Photo>) {
        photos.clear()
        photos.addAll(list)
    }

    fun getPhotos(): List<Photo> = photos.toList()

    fun clear() {
        photos.clear()
    }
}

