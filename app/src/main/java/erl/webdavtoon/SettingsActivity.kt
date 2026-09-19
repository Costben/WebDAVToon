package erl.webdavtoon

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import erl.webdavtoon.ui.screen.settings.SettingsActions
import erl.webdavtoon.ui.screen.settings.SettingsDefaults
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

    /** Picker dialog states. */
    private var showThemePicker by mutableStateOf(false)
    private var showLanguagePicker by mutableStateOf(false)
    private var showDefaultReaderModePicker by mutableStateOf(false)
    private var showVideoExternalPlayerModePicker by mutableStateOf(false)
    private var showAutoWorkflowUrlDialog by mutableStateOf(false)
    private var showPrivacyExitPolicyPicker by mutableStateOf(false)

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
                    if (showThemePicker) {
                        ThemePickerDialog(
                            currentThemeId = uiState.themeId,
                            onSelect = { viewModel.setThemeId(it) },
                            onDismiss = { showThemePicker = false },
                        )
                    }
                    if (showLanguagePicker) {
                        LanguagePickerDialog(
                            currentLang = uiState.language,
                            onSelect = {
                                viewModel.setLanguage(it)
                                Toast.makeText(this@SettingsActivity, R.string.language_changed_tip, Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { showLanguagePicker = false },
                        )
                    }
                    if (showDefaultReaderModePicker) {
                        DefaultReaderModePickerDialog(
                            currentMode = uiState.defaultReaderMode,
                            onSelect = { viewModel.setDefaultReaderMode(it) },
                            onDismiss = { showDefaultReaderModePicker = false },
                        )
                    }
                    if (showVideoExternalPlayerModePicker) {
                        VideoExternalPlayerModePickerDialog(
                            currentMode = uiState.videoExternalPlayerMode,
                            onSelect = { viewModel.setVideoExternalPlayerMode(it) },
                            onDismiss = { showVideoExternalPlayerModePicker = false },
                        )
                    }
                    if (showAutoWorkflowUrlDialog) {
                        InputDialog(
                            title = stringResource(R.string.comfyui_server),
                            initialValue = uiState.autoWorkflowUrl,
                            hint = "http://192.168.1.x:8188",
                            onConfirm = { viewModel.setAutoWorkflowUrl(it) },
                            onDismiss = { showAutoWorkflowUrlDialog = false },
                        )
                    }
                    if (showPrivacyExitPolicyPicker) {
                        PrivacyExitPolicyDialog(
                            currentPolicy = uiState.privacyExitPolicy,
                            onSelect = { viewModel.setPrivacyExitPolicy(it) },
                            onDismiss = { showPrivacyExitPolicyPicker = false },
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
        onPickTheme = { showThemePicker = true },
        onPickLanguage = { showLanguagePicker = true },
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
        onPickDefaultReaderMode = { showDefaultReaderModePicker = true },
        onPickVideoExternalPlayerMode = { showVideoExternalPlayerModePicker = true },
        onEditAutoWorkflowUrl = { showAutoWorkflowUrlDialog = true },
        onPickPrivacyExitPolicy = { showPrivacyExitPolicyPicker = true },
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

@Composable
private fun ThemePickerDialog(
    currentThemeId: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = listOf(
        ThemeHelper.THEME_FOLLOW_DEVICE to stringResource(R.string.theme_follow_device),
        0 to stringResource(R.string.theme_midnight_blue),
        1 to stringResource(R.string.theme_midnight_blue),
        2 to stringResource(R.string.theme_forest_green),
        3 to stringResource(R.string.theme_crimson_red),
        4 to stringResource(R.string.theme_sunset_orange),
        5 to stringResource(R.string.theme_ocean_teal),
        6 to stringResource(R.string.theme_deep_purple),
        7 to stringResource(R.string.theme_rose_pink),
        8 to stringResource(R.string.theme_coffee_brown),
        9 to stringResource(R.string.theme_neutral_grey),
    )
    SingleChoiceDialog(
        title = stringResource(R.string.theme),
        items = items,
        selectedItem = currentThemeId,
        onItemSelected = onSelect,
        onDismiss = onDismiss,
    )
}

@Composable
private fun LanguagePickerDialog(
    currentLang: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = listOf(
        "default" to stringResource(R.string.follow_system),
        "zh" to stringResource(R.string.language_chinese),
        "en" to stringResource(R.string.language_english),
    )
    SingleChoiceDialog(
        title = stringResource(R.string.language),
        items = items,
        selectedItem = currentLang,
        onItemSelected = onSelect,
        onDismiss = onDismiss,
    )
}

@Composable
private fun DefaultReaderModePickerDialog(
    currentMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = listOf(
        SettingsDefaults.DEFAULT_READER_MODE_WEBTOON to stringResource(R.string.default_reader_mode_webtoon),
        SettingsDefaults.DEFAULT_READER_MODE_CARD to stringResource(R.string.default_reader_mode_card),
    )
    SingleChoiceDialog(
        title = stringResource(R.string.default_reader_mode),
        items = items,
        selectedItem = currentMode,
        onItemSelected = onSelect,
        onDismiss = onDismiss,
    )
}

@Composable
private fun VideoExternalPlayerModePickerDialog(
    currentMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = listOf(
        SettingsDefaults.VIDEO_EXTERNAL_PLAYER_MODE_SYSTEM_DEFAULT to stringResource(R.string.video_external_player_mode_system_default),
        SettingsDefaults.VIDEO_EXTERNAL_PLAYER_MODE_CHOOSER to stringResource(R.string.video_external_player_mode_chooser),
    )
    SingleChoiceDialog(
        title = stringResource(R.string.video_external_player_mode),
        items = items,
        selectedItem = currentMode,
        onItemSelected = onSelect,
        onDismiss = onDismiss,
    )
}

@Composable
private fun PrivacyExitPolicyDialog(
    currentPolicy: PrivacyModeState.ExitPolicy,
    onSelect: (PrivacyModeState.ExitPolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = listOf(
        PrivacyModeState.ExitPolicy.ON_BACKGROUND to stringResource(R.string.privacy_exit_policy_on_background),
        PrivacyModeState.ExitPolicy.ON_PROCESS_DEATH to stringResource(R.string.privacy_exit_policy_on_process_death),
        PrivacyModeState.ExitPolicy.MANUAL_ONLY to stringResource(R.string.privacy_exit_policy_manual_only),
    )
    SingleChoiceDialog(
        title = stringResource(R.string.privacy_exit_policy_title),
        items = items,
        selectedItem = currentPolicy,
        onItemSelected = onSelect,
        onDismiss = onDismiss,
    )
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    items: List<Pair<T, String>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items) { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (value == selectedItem),
                                onClick = {
                                    onItemSelected(value)
                                    onDismiss()
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (value == selectedItem),
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun InputDialog(
    title: String,
    initialValue: String,
    hint: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(text.trim())
                onDismiss()
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
