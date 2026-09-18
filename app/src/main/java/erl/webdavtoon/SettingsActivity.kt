package erl.webdavtoon

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import erl.webdavtoon.ui.screen.settings.SettingsActions
import erl.webdavtoon.ui.screen.settings.SettingsEvent
import erl.webdavtoon.ui.screen.settings.SettingsScreen
import erl.webdavtoon.ui.screen.settings.SettingsViewModel
import erl.webdavtoon.ui.screen.settings.dialog.ServerConfigAction
import erl.webdavtoon.ui.screen.settings.dialog.ServerConfigDialog
import erl.webdavtoon.ui.screen.settings.dialog.ServerConfigEvent
import erl.webdavtoon.ui.screen.settings.dialog.ServerConfigViewModel
import erl.webdavtoon.ui.theme.WebDAVToonTheme
import kotlinx.coroutines.launch

/**
 * Compose host for the settings screen.
 *
 * The Activity contributes only what the [SettingsActions] contract cannot express: the
 * theme/locale bootstrap ordering, the rotation-lock window flag, edge-to-edge, the
 * `RESULT_OK` protocol the five calling Activities rely on, and the few side effects that
 * must happen process-wide rather than in the ViewModel.
 *
 * [SettingsViewModel] and [ServerConfigViewModel] are both reachable through `by viewModels()`
 * with no custom factory: the latter carries an `@JvmOverloads` constructor precisely so the
 * fixed-signature reflection lookup can find an `(Application)` overload.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private val viewModel: SettingsViewModel by viewModels()
    private val serverConfigViewModel: ServerConfigViewModel by viewModels()

    /** Drives the clear-cache confirmation; the ViewModel call is destructive and irreversible. */
    private var showClearCacheConfirm by mutableStateOf(false)

    /** Slot whose server-config dialog is open; `null` means closed. One state, no boolean twin. */
    private var serverConfigSlot by mutableStateOf<Int?>(null)

    /**
     * Slot requested before `setContent` exists — `EXTRA_SHOW_ADD_SERVER` arrives in `onCreate`
     * while `ServerConfigViewModel.load()` would be thrown away by the first recomposition.
     * The Compose side consumes it once.
     */
    private var pendingConfigSlot by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: besides the theme it pins the app locale through
        // Locale.setDefault + resources.updateConfiguration.
        ThemeHelper.applyTheme(this)
        settingsManager = SettingsManager(this)
        LogManager.initialize(this)
        applyRotationLock()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightNavigationBars = !isNightModeActive()

        settingsManager.setServerType("webdav")

        if (intent.getBooleanExtra(EXTRA_SHOW_ADD_SERVER, false)) {
            val nextSlot = (settingsManager.getAllSlotsUnfiltered().maxOrNull() ?: -1) + 1
            settingsManager.setCurrentSlot(nextSlot)
            // The dialog cannot be loaded yet: setContent has not run, so anything written into
            // ServerConfigViewModel here would be rebuilt away by the first composition.
            pendingConfigSlot = nextSlot
        }

        setContent {
            val navOwner = androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner(parent = null)
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner provides navOwner,
            ) {
                val uiState by viewModel.uiState.collectAsState()
                val cfgState by serverConfigViewModel.state.collectAsState()
                WebDAVToonTheme(uiMode = uiState.uiMode) {
                    SettingsScreen(uiState = uiState, actions = settingsActions())
                    if (showClearCacheConfirm) {
                        ClearCacheConfirmDialog(
                            onConfirm = {
                                showClearCacheConfirm = false
                                viewModel.clearCache()
                            },
                            onDismiss = { showClearCacheConfirm = false },
                        )
                    }
                    LaunchedEffect(Unit) {
                        pendingConfigSlot?.let { slot ->
                            openServerConfig(slot)
                            pendingConfigSlot = null
                        }
                    }
                    serverConfigSlot?.let { slot ->
                        ServerConfigDialog(
                            state = cfgState,
                            visible = true,
                            onAction = { action -> handleServerConfigAction(action) },
                            onDismiss = { serverConfigSlot = null },
                        )
                    }
                    if (uiState.uiMode == erl.webdavtoon.ui.UiMode.Miuix) {
                        top.yukonga.miuix.kmp.utils.MiuixPopupUtils.MiuixPopupHost()
                    }
                }
            }
        }

        // The single result-code mechanism: callers read only RESULT_OK and no extras.
        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    SettingsEvent.ResultChanged -> setResult(RESULT_OK)
                    is SettingsEvent.ShowMessage -> Toast.makeText(
                        this@SettingsActivity,
                        event.resId,
                        Toast.LENGTH_SHORT,
                    ).show()
                    SettingsEvent.RequestRecreate -> recreate()
                }
            }
        }

        // Kept separate from the settings collector: the server dialog owns its own persist path
        // and must not be routed through SettingsViewModel.
        lifecycleScope.launch {
            serverConfigViewModel.events.collect { event ->
                when (event) {
                    is ServerConfigEvent.Saved -> {
                        serverConfigSlot = null
                        // The dialog saves through its own SettingsManager, so nothing else
                        // republishes the slot list for us.
                        viewModel.refreshSlots()
                        setResult(RESULT_OK)
                        Toast.makeText(
                            this@SettingsActivity,
                            R.string.server_saved,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    is ServerConfigEvent.Message -> Toast.makeText(
                        this@SettingsActivity,
                        event.resId,
                        Toast.LENGTH_SHORT,
                    ).show()
                    is ServerConfigEvent.MessageText -> Toast.makeText(
                        this@SettingsActivity,
                        event.text,
                        Toast.LENGTH_SHORT,
                    ).show()
                    ServerConfigEvent.Dismissed -> serverConfigSlot = null
                }
            }
        }
    }

    private fun openServerConfig(slot: Int) {
        serverConfigViewModel.load(slot)
        serverConfigSlot = slot
    }

    /** Exhaustive by design: a new [ServerConfigAction] must stop compiling until it is routed. */
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

    private fun settingsActions(): SettingsActions = SettingsActions(
        onSelectSlot = { viewModel.selectSlot(it) },
        onAddSlot = {
            val slot = viewModel.addSlot()
            openServerConfig(slot)
        },
        onDeleteSlot = { viewModel.deleteSlot(it) },
        onEditSlot = { slot -> openServerConfig(slot) },
        onRefreshSlots = { viewModel.refreshSlots() },
        onSetUiMode = { viewModel.setUiMode(it) },
        // TODO(2.5b): theme picker dialog, then viewModel.setThemeId(id).
        onPickTheme = { },
        // TODO(2.5b): language picker dialog. Its path must also Toast
        // R.string.language_changed_tip; RequestRecreate covers the recreate() half.
        onPickLanguage = { },
        onSetGridColumns = { viewModel.setGridColumns(it) },
        onSetDrawerEdgeWidth = { percent ->
            viewModel.setDrawerEdgeWidthPercent(percent)
            // Process-wide and immediate: without this the new trigger area only applies
            // after the host Activity is recreated.
            ExpandedEdgeDrawerLayout.setWidthPercent(percent.coerceIn(0, 100))
        },
        onSetSortOrder = { newOrder ->
            val previousOrder = settingsManager.getSortOrder()
            SmbSortHint.maybeShowPreviewHint(this, settingsManager, previousOrder, newOrder)
            viewModel.setSortOrder(newOrder)
        },
        onSetRecursiveImageArrangement = { viewModel.setRecursiveImageArrangement(it) },
        onSetWaterfallShowFilenames = { viewModel.setWaterfallShowFilenames(it) },
        onSetWaterfallQualityMode = { viewModel.setWaterfallQualityMode(it) },
        onSetWaterfallPercent = { viewModel.setWaterfallPercent(it) },
        onSetWaterfallMaxWidth = { viewModel.setWaterfallMaxWidth(it) },
        onSetReaderMaxZoom = { viewModel.setReaderMaxZoomPercent(it) },
        // TODO(2.5b): reader mode picker dialog.
        onPickDefaultReaderMode = { },
        // TODO(2.5b): external video player picker dialog.
        onPickVideoExternalPlayerMode = { },
        // TODO(2.5b): URL editor dialog; it must reject invalid input through
        // EditService.isValidUrl before calling setAutoWorkflowUrl.
        onEditAutoWorkflowUrl = { },
        // TODO(2.5b): privacy exit policy picker dialog.
        onPickPrivacyExitPolicy = { },
        onSetRotationLocked = { locked ->
            viewModel.setRotationLocked(locked)
            // Applied from the known value rather than re-read: the ViewModel writes off the
            // main thread, so reading it back here would race.
            applyRotationLock(locked)
        },
        onClearCache = { showClearCacheConfirm = true },
        onBack = { finish() },
    )

    private fun applyRotationLock(locked: Boolean = settingsManager.isRotationLocked()) {
        requestedOrientation = if (locked) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun isNightModeActive(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    private companion object {
        const val EXTRA_SHOW_ADD_SERVER = "EXTRA_SHOW_ADD_SERVER"
    }
}

@Composable
private fun ClearCacheConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_cache)) },
        text = { Text(stringResource(R.string.clear_cache_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
