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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import erl.webdavtoon.ui.screen.folder.FolderItemUi
import erl.webdavtoon.ui.screen.folder.FolderScreen
import erl.webdavtoon.ui.screen.folder.FolderScreenActions
import erl.webdavtoon.ui.screen.folder.FolderViewModel
import erl.webdavtoon.ui.screen.settings.dialog.ServerConfigAction
import erl.webdavtoon.ui.screen.settings.dialog.ServerConfigDialog
import erl.webdavtoon.ui.screen.settings.dialog.ServerConfigEvent
import erl.webdavtoon.ui.screen.settings.dialog.ServerConfigViewModel
import erl.webdavtoon.ui.component.DeleteConfirmDialog
import erl.webdavtoon.ui.theme.WebDAVToonTheme
import kotlinx.coroutines.launch

class FolderViewActivity : FragmentActivity() {

    private lateinit var settingsManager: SettingsManager
    private val viewModel: FolderViewModel by viewModels()
    private val serverConfigViewModel: ServerConfigViewModel by viewModels()

    private var showDeleteConfirmDialog by mutableStateOf(false)
    private var serverConfigSlot by mutableStateOf<Int?>(null)
    private var pendingFolderNavigationPath: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, getString(R.string.storage_permission_required), Toast.LENGTH_LONG).show()
        }
        viewModel.loadFolders()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.loadFolders(forceRefresh = true)
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

        if (WebDAVToonApplication.rustRepository == null) {
            Toast.makeText(this, getString(R.string.rust_core_not_initialized), Toast.LENGTH_LONG).show()
        }

        LibraryState.update("webdav", "")

        setContent {
            val navOwner = androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner(parent = null)
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner provides navOwner,
            ) {
                val uiState by viewModel.uiState.collectAsState()
                val cfgState by serverConfigViewModel.state.collectAsState()

                WebDAVToonTheme(
                    themeId = uiState.themeId,
                    useCouiDefaultColors = uiState.useCouiDefaultColors,
                ) {
                    FolderScreen(
                        uiState = uiState,
                        actions = createFolderActions(),
                    )

                    if (showDeleteConfirmDialog) {
                        DeleteConfirmDialog(
                            message = stringResource(R.string.delete_folders_message, uiState.selectedCount),
                            onConfirm = {
                                showDeleteConfirmDialog = false
                                viewModel.deleteSelected { deletedCount ->
                                    Toast.makeText(
                                        this@FolderViewActivity,
                                        getString(R.string.deleted_folders_count, deletedCount),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
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
                        viewModel.loadFolders(forceRefresh = true)
                        Toast.makeText(
                            this@FolderViewActivity,
                            R.string.server_saved,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is ServerConfigEvent.Message -> {
                        Toast.makeText(
                            this@FolderViewActivity,
                            event.resId,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is ServerConfigEvent.MessageText -> {
                        Toast.makeText(
                            this@FolderViewActivity,
                            event.text,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    ServerConfigEvent.Dismissed -> {
                        serverConfigSlot = null
                    }
                }
            }
        }

        checkPermissionsAndLoad()
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
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (hasStoragePermission()) {
            viewModel.loadFolders()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun duplicateSlot(sourceSlot: Int) {
        val slots = settingsManager.getAllSlotsUnfiltered()
        val newSlot = (slots.maxOrNull() ?: -1) + 1
        val sourceAlias = settingsManager.getWebDavAlias(sourceSlot)
        val newAlias = "$sourceAlias Copy"

        settingsManager.saveWebDavConfiguration(
            slot = newSlot,
            alias = newAlias,
            protocol = settingsManager.getWebDavProtocol(sourceSlot),
            url = settingsManager.getWebDavUrl(sourceSlot),
            port = settingsManager.getWebDavPort(sourceSlot),
            username = settingsManager.getWebDavUsername(sourceSlot),
            password = settingsManager.getWebDavPassword(sourceSlot),
            rememberPassword = settingsManager.isWebDavRememberPassword(sourceSlot),
            enabled = settingsManager.isWebDavEnabled(sourceSlot),
            switchToSlotOnSave = false,
            isPrivate = settingsManager.isWebDavPrivate(sourceSlot),
            domain = settingsManager.getWebDavDomain(sourceSlot)
        )

        Toast.makeText(this, getString(R.string.server_duplicated), Toast.LENGTH_SHORT).show()
        viewModel.refreshSlots()
        openServerConfig(newSlot)
    }

    private fun createFolderActions(): FolderScreenActions = FolderScreenActions(
        onFolderClick = { folder -> onFolderClick(folder) },
        onToggleSelection = { path -> viewModel.toggleSelection(path) },
        onPreviewVisibilityChanged = { path, visible -> viewModel.onFolderPreviewVisible(path, visible) },
        onSearchQueryChange = { query -> viewModel.setSearchKeyword(query) },
        onClearSearch = { viewModel.setSearchKeyword("") },
        onToggleSearch = { },
        onSetSortOrder = { order ->
            val previousOrder = settingsManager.getSortOrder()
            SmbSortHint.maybeShowPreviewHint(this, settingsManager, previousOrder, order)
            viewModel.setSortOrder(order)
        },
        onSetGridColumns = { columns -> viewModel.setGridColumns(columns) },
        onToggleRotationLock = {
            viewModel.toggleRotationLock()
            applyRotationLock(settingsManager.isRotationLocked())
        },
        onRefresh = {
            viewModel.resetShuffleSeed()
            viewModel.loadFolders(forceRefresh = true)
        },
        onOpenSettings = {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        },
        onClearSelection = { viewModel.clearSelection() },
        onSelectAll = { viewModel.selectAll() },
        onDeleteSelected = { showDeleteConfirmDialog = true },
        onSelectSlot = { slot ->
            if (settingsManager.getCurrentSlot() != slot) {
                viewModel.selectSlot(slot)
                Toast.makeText(this, getString(R.string.switched_server), Toast.LENGTH_SHORT).show()
            }
        },
        onEditSlot = { slot -> openServerConfig(slot) },
        onDuplicateSlot = { slot -> duplicateSlot(slot) },
        onAddSlot = {
            val nextSlot = (settingsManager.getAllSlotsUnfiltered().maxOrNull() ?: -1) + 1
            openServerConfig(nextSlot)
        },
        onEnterPrivacy = {
            tryEnterPrivacyMode {
                viewModel.refreshSlots()
                viewModel.loadFolders(forceRefresh = true)
            }
        },
        onExitPrivacy = {
            PrivacyModeState.exit(this)
            viewModel.refreshSlots()
            viewModel.loadFolders(forceRefresh = true)
        },
        onOpenFavorites = {
            val intent = Intent(this, MixedFolderActivity::class.java).apply {
                putExtra("EXTRA_IS_FAVORITES", true)
            }
            startActivity(intent)
        },
        onOpenRecursiveBrowser = {
            val isWebDav = settingsManager.isWebDavEnabled()
            val path = if (isWebDav) "/" else ""
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", path)
                putExtra("EXTRA_IS_WEBDAV", isWebDav)
                putExtra("EXTRA_RECURSIVE", true)
            }
            startActivity(intent)
        },
        onBack = { finish() },
    )

    private fun onFolderClick(folder: FolderItemUi) {
        val isInternalPhotos = folder.path.startsWith("virtual://internal_photos")
        val realPath = if (isInternalPhotos) folder.path.substringAfter("path=") else folder.path

        if (folder.path == "virtual://local_all") {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", "")
                putExtra("EXTRA_IS_WEBDAV", false)
                putExtra("EXTRA_RECURSIVE", true)
            }
            startActivity(intent)
            return
        }

        if (folder.path == "virtual://local_root") {
            openSubFolder("", false)
            return
        }

        if (isInternalPhotos || !folder.hasSubFolders) {
            openPhotoList(realPath, !folder.isLocal)
            return
        }

        openFolderResolved(realPath, !folder.isLocal)
    }

    private fun openPhotoList(path: String, isWebDav: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("EXTRA_FOLDER_PATH", path)
            putExtra("EXTRA_IS_WEBDAV", isWebDav)
            putExtra("EXTRA_RECURSIVE", false)
        }
        startActivity(intent)
    }

    private fun openSubFolder(path: String, isWebDav: Boolean) {
        val intent = Intent(this, SubFolderActivity::class.java).apply {
            putExtra("EXTRA_FOLDER_PATH", path)
            putExtra("EXTRA_IS_WEBDAV", isWebDav)
        }
        startActivity(intent)
    }

    private fun openFolderResolved(path: String, isWebDav: Boolean) {
        if (pendingFolderNavigationPath != null) return
        pendingFolderNavigationPath = path
        lifecycleScope.launch {
            try {
                val target = FolderNavigationResolver.resolve(
                    context = this@FolderViewActivity,
                    settingsManager = settingsManager,
                    folderPath = path,
                    isWebDav = isWebDav
                )
                android.util.Log.i(
                    "FolderViewActivity",
                    "resolvedFolderNavigation path=$path target=${target.javaClass.simpleName}"
                )
                FolderNavigationResolver.start(this@FolderViewActivity, target)
            } catch (e: Exception) {
                android.util.Log.e("FolderViewActivity", "Folder navigation resolve failed path=$path", e)
                openSubFolder(path, isWebDav)
            } finally {
                pendingFolderNavigationPath = null
            }
        }
    }

    private fun tryEnterPrivacyMode(onSuccess: () -> Unit) {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (km?.isDeviceSecure != true) {
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    PrivacyModeState.enter(this@FolderViewActivity)
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

    private fun applyRotationLock(locked: Boolean = settingsManager.isRotationLocked()) {
        requestedOrientation = if (locked) {
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

