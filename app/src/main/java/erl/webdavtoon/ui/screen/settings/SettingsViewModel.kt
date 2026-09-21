package erl.webdavtoon.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import erl.webdavtoon.AppSettingsStore
import erl.webdavtoon.PrivacyModeState
import erl.webdavtoon.R
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.WebDavImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data object ResultChanged : SettingsEvent
    data class ShowMessage(val resId: Int) : SettingsEvent
    data object RequestRecreate : SettingsEvent
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val settingsManager = SettingsManager(context)
    private val appSettings = AppSettingsStore(context)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()
    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            publishSnapshot(loading = false)
        }
        observeStore()
    }

    private fun observeStore() {
        observe(appSettings.observeInt(AppSettingsStore.THEME_ID, 0)) { copy(themeId = it) }
        observe(appSettings.observeBoolean(AppSettingsStore.USE_COUI_DEFAULT_COLORS, false)) { copy(useCouiDefaultColors = it) }
        observe(appSettings.observeString(AppSettingsStore.LANGUAGE, "default")) { copy(language = it) }
        observe(appSettings.observeInt(AppSettingsStore.GRID_COLUMNS, 2)) { copy(gridColumns = it) }
        observe(appSettings.observeInt(AppSettingsStore.SORT_ORDER, 2)) { copy(sortOrder = it) }
        observe(appSettings.observeInt(AppSettingsStore.RECURSIVE_IMAGE_ARRANGEMENT, SettingsManager.RECURSIVE_IMAGE_ARRANGEMENT_GROUPED)) { copy(recursiveImageArrangement = it) }
        observe(appSettings.observeBoolean(AppSettingsStore.WATERFALL_SHOW_FILENAMES, true)) { copy(waterfallShowFilenames = it) }
        observe(appSettings.observeString(AppSettingsStore.WATERFALL_QUALITY_MODE, SettingsManager.WATERFALL_MODE_PERCENT)) { copy(waterfallQualityMode = it) }
        observe(appSettings.observeInt(AppSettingsStore.WATERFALL_PERCENT, 70)) { copy(waterfallPercent = it) }
        observe(appSettings.observeInt(AppSettingsStore.WATERFALL_MAX_WIDTH, 600)) { copy(waterfallMaxWidth = it) }
        observe(appSettings.observeInt(AppSettingsStore.WATERFALL_WIDTH_BUCKET, SettingsManager.DEFAULT_WATERFALL_WIDTH_BUCKET)) {
            copy(waterfallWidthBucket = settingsManager.getWaterfallWidthBucket())
        }
        observe(appSettings.observeInt(AppSettingsStore.GLIDE_MEMORY_CACHE_SCREENS, SettingsManager.DEFAULT_GLIDE_MEMORY_CACHE_SCREENS)) {
            copy(glideMemoryCacheScreens = settingsManager.getGlideMemoryCacheScreens())
        }
        observe(appSettings.observeInt(AppSettingsStore.READER_MAX_ZOOM_PERCENT, 300)) { copy(readerMaxZoomPercent = it) }
        observe(appSettings.observeString(AppSettingsStore.DEFAULT_READER_MODE, SettingsManager.DEFAULT_READER_MODE_WEBTOON)) { copy(defaultReaderMode = it) }
        observe(appSettings.observeString(AppSettingsStore.VIDEO_EXTERNAL_PLAYER_MODE, SettingsManager.VIDEO_EXTERNAL_PLAYER_MODE_SYSTEM_DEFAULT)) { copy(videoExternalPlayerMode = it) }
        observe(appSettings.observeString(AppSettingsStore.AUTO_WORKFLOW_URL, "")) { copy(autoWorkflowUrl = it) }
        observe(appSettings.observeBoolean(AppSettingsStore.ROTATION_LOCKED, false)) { copy(rotationLocked = it) }
        observe(appSettings.observeString(AppSettingsStore.PRIVACY_MODE_EXIT_POLICY, PrivacyModeState.ExitPolicy.ON_BACKGROUND.code)) {
            copy(privacyExitPolicy = PrivacyModeState.ExitPolicy.fromCode(it))
        }
    }

    private fun <T> observe(flow: kotlinx.coroutines.flow.Flow<T>, transform: SettingsUiState.(T) -> SettingsUiState) {
        viewModelScope.launch {
            flow.collect { value -> _uiState.update { state -> state.transform(value) } }
        }
    }

    private fun publishSnapshot(loading: Boolean) {
        val currentSlot = settingsManager.getCurrentSlot()
        val slots = settingsManager.getAllSlotsUnfiltered().map { slot ->
            WebDavSlotUi(
                slot = slot,
                alias = settingsManager.getWebDavAlias(slot),
                protocol = settingsManager.getWebDavProtocol(slot),
                url = settingsManager.getWebDavUrl(slot),
                port = settingsManager.getWebDavPort(slot),
                username = settingsManager.getWebDavUsername(slot),
                domain = settingsManager.getWebDavDomain(slot),
                rememberPassword = settingsManager.isWebDavRememberPassword(slot),
                isPrivate = settingsManager.isWebDavPrivate(slot),
                enabled = settingsManager.isWebDavEnabled(slot),
                hasPassword = settingsManager.getWebDavPassword(slot).isNotBlank(),
                isCurrent = slot == currentSlot,
            )
        }
        _uiState.update {
            SettingsUiState(
                loading = loading,
                slots = slots,
                    currentSlot = currentSlot,
                    themeId = settingsManager.getThemeId(),
                useCouiDefaultColors = settingsManager.useCouiDefaultColors(),
                language = settingsManager.getLanguage(),
                gridColumns = settingsManager.getGridColumns(),
                sortOrder = settingsManager.getSortOrder(),
                recursiveImageArrangement = settingsManager.getRecursiveImageArrangement(),
                waterfallShowFilenames = settingsManager.shouldShowWaterfallFilenames(),
                waterfallQualityMode = settingsManager.getWaterfallQualityMode(),
                waterfallPercent = settingsManager.getWaterfallPercent(),
                waterfallMaxWidth = settingsManager.getWaterfallMaxWidth(),
                waterfallWidthBucket = settingsManager.getWaterfallWidthBucket(),
                glideMemoryCacheScreens = settingsManager.getGlideMemoryCacheScreens(),
                readerMaxZoomPercent = settingsManager.getReaderMaxZoomPercent(),
                defaultReaderMode = settingsManager.getDefaultReaderMode(),
                videoExternalPlayerMode = settingsManager.getVideoExternalPlayerMode(),
                autoWorkflowUrl = settingsManager.getAutoWorkflowUrl(),
                privacyExitPolicy = PrivacyModeState.exitPolicy,
                isPrivacyMode = PrivacyModeState.isPrivacyMode,
                rotationLocked = settingsManager.isRotationLocked(),
            )
        }
    }

    fun refreshSlots() = io { publishSnapshot(loading = false) }

    fun selectSlot(slot: Int) = io {
        settingsManager.setCurrentSlot(slot)
        publishSnapshot(loading = false)
        eventChannel.send(SettingsEvent.ResultChanged)
    }

    fun saveWebDavSlot(slot: Int, alias: String, protocol: String, url: String, port: Int, username: String, password: String, rememberPassword: Boolean, domain: String, isPrivate: Boolean = false) = io {
        settingsManager.saveWebDavConfiguration(slot, alias, protocol, url, port, username, password, rememberPassword, isPrivate = isPrivate, domain = domain)
        publishSnapshot(loading = false)
        eventChannel.send(SettingsEvent.ResultChanged)
        eventChannel.send(SettingsEvent.ShowMessage(R.string.server_saved))
    }

    fun deleteSlot(slot: Int) = io {
        settingsManager.deleteSlot(slot)
        publishSnapshot(loading = false)
        eventChannel.send(SettingsEvent.ResultChanged)
    }

    /** Opening a draft must not switch away from the current server, including on cancel. */
    fun addSlot(): Int {
        return (settingsManager.getAllSlotsUnfiltered().maxOrNull() ?: -1) + 1
    }

    fun setThemeId(id: Int) = io { settingsManager.setThemeId(id); _uiState.update { it.copy(themeId = id) }; eventChannel.send(SettingsEvent.RequestRecreate) }
    fun setUseCouiDefaultColors(enabled: Boolean) = io {
        settingsManager.setUseCouiDefaultColors(enabled)
        _uiState.update { it.copy(useCouiDefaultColors = enabled) }
        eventChannel.send(SettingsEvent.RequestRecreate)
    }
    fun setLanguage(tag: String) = io { settingsManager.setLanguage(tag); _uiState.update { it.copy(language = tag) }; eventChannel.send(SettingsEvent.RequestRecreate) }
    fun setGridColumns(n: Int) = io { settingsManager.setGridColumns(n); _uiState.update { it.copy(gridColumns = n) } }
    fun setSortOrder(n: Int) = io { settingsManager.setSortOrder(n); _uiState.update { it.copy(sortOrder = n) } }
    fun setRecursiveImageArrangement(n: Int) = io { settingsManager.setRecursiveImageArrangement(n); _uiState.update { it.copy(recursiveImageArrangement = settingsManager.getRecursiveImageArrangement()) }; eventChannel.send(SettingsEvent.ResultChanged) }
    fun setWaterfallShowFilenames(b: Boolean) = io { settingsManager.setShowWaterfallFilenames(b); _uiState.update { it.copy(waterfallShowFilenames = b) }; eventChannel.send(SettingsEvent.ResultChanged) }
    fun setWaterfallQualityMode(m: String) = io { settingsManager.setWaterfallQualityMode(m); _uiState.update { it.copy(waterfallQualityMode = m) } }
    fun setWaterfallPercent(p: Int) = io { settingsManager.setWaterfallPercent(p); _uiState.update { it.copy(waterfallPercent = p) } }
    fun setWaterfallMaxWidth(w: Int) = io { settingsManager.setWaterfallMaxWidth(w); _uiState.update { it.copy(waterfallMaxWidth = w) } }
    fun setWaterfallWidthBucket(bucket: Int) = io {
        settingsManager.setWaterfallWidthBucket(bucket)
        _uiState.update { it.copy(waterfallWidthBucket = settingsManager.getWaterfallWidthBucket()) }
        eventChannel.send(SettingsEvent.ResultChanged)
    }
    fun setGlideMemoryCacheScreens(screens: Int) = io {
        settingsManager.setGlideMemoryCacheScreens(screens)
        _uiState.update { it.copy(glideMemoryCacheScreens = settingsManager.getGlideMemoryCacheScreens()) }
        eventChannel.send(SettingsEvent.ShowMessage(R.string.restart_to_apply))
    }
    fun setReaderMaxZoomPercent(p: Int) = io { settingsManager.setReaderMaxZoomPercent(p); _uiState.update { it.copy(readerMaxZoomPercent = p) } }
    fun setDefaultReaderMode(m: String) = io { settingsManager.setDefaultReaderMode(m); _uiState.update { it.copy(defaultReaderMode = settingsManager.getDefaultReaderMode()) } }
    fun setVideoExternalPlayerMode(m: String) = io { settingsManager.setVideoExternalPlayerMode(m); _uiState.update { it.copy(videoExternalPlayerMode = settingsManager.getVideoExternalPlayerMode()) } }
    fun setAutoWorkflowUrl(url: String) = io { settingsManager.setAutoWorkflowUrl(url.trim().trimEnd('/')); _uiState.update { it.copy(autoWorkflowUrl = settingsManager.getAutoWorkflowUrl()) } }
    fun setPrivacyExitPolicy(policy: PrivacyModeState.ExitPolicy) = io { PrivacyModeState.setExitPolicy(context, policy); _uiState.update { it.copy(privacyExitPolicy = policy) } }
    fun setRotationLocked(b: Boolean) = io { settingsManager.setRotationLocked(b); _uiState.update { it.copy(rotationLocked = b) }; eventChannel.send(SettingsEvent.ResultChanged) }

    fun clearCache() = io {
        try {
            WebDavImageLoader.clearCache(context)
            eventChannel.send(SettingsEvent.ShowMessage(R.string.cache_cleared))
        } catch (_: Exception) {
            eventChannel.send(SettingsEvent.ShowMessage(R.string.cache_clear_failed))
        }
    }

    fun startServerConfig(slot: Int): ServerConfigFormState = ServerConfigFormState(
        slot = slot,
        alias = settingsManager.getWebDavAlias(slot),
        protocol = settingsManager.getWebDavProtocol(slot),
        url = settingsManager.getWebDavUrl(slot),
        port = settingsManager.getWebDavPort(slot).toString(),
        username = settingsManager.getWebDavUsername(slot),
        domain = settingsManager.getWebDavDomain(slot),
        rememberPassword = settingsManager.isWebDavRememberPassword(slot),
        isPrivate = settingsManager.isWebDavPrivate(slot),
    )

    fun updateServerConfigForm(form: ServerConfigFormState, transform: ServerConfigFormState.() -> ServerConfigFormState): ServerConfigFormState = form.transform()

    fun validateServerConfigForm(form: ServerConfigFormState): String? {
        if (form.url.isBlank()) return "Host is required"
        val port = form.port.toIntOrNull() ?: return "Port must be a number"
        if (port !in 1..65535) return "Port must be between 1 and 65535"
        if (form.protocol.equals("smb", ignoreCase = true)) {
            val smbPath = form.url.replace("smb://", "", ignoreCase = true).trim().trim('/')
            if (!smbPath.contains('/') || smbPath.substringAfter('/').isBlank()) return "SMB share is required"
        }
        return null
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }
}
