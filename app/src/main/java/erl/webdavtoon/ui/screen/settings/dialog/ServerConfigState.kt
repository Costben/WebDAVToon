package erl.webdavtoon.ui.screen.settings.dialog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import erl.webdavtoon.DiscoveredHost
import erl.webdavtoon.NetworkDiscovery
import erl.webdavtoon.PrivacyModeState
import erl.webdavtoon.R
import erl.webdavtoon.ServerConfigDialogHelper
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.WebDAVToonApplication
import erl.webdavtoon.WebDavEndpointNormalizer
import erl.webdavtoon.ui.screen.settings.ServerConfigFormState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the server configuration dialog. Pure data: no Compose and no
 * Miuix imports, so the pure functions below stay unit-testable on the JVM.
 */
data class ServerConfigDialogState(
    val slot: Int = 0,
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
    val protocols: List<String> = ServerConfigDialogHelper.PROTOCOLS.toList(),
    val shares: List<String>? = null,
    val enumeratingShares: Boolean = false,
    val discoveringHosts: Boolean = false,
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val isPrivacyMode: Boolean = false,
) {
    /** `true` when the host-discovery sheet should be on screen. */
    val discoveryVisible: Boolean get() = discoveringHosts || discoveredHosts.isNotEmpty()

    fun selectProtocol(newProtocol: String): ServerConfigDialogState {
        val oldDefaultPort = WebDavEndpointNormalizer.defaultPortFor(protocol).toString()
        val shouldUpdatePort = port.isBlank() || port == oldDefaultPort
        val newPort = if (shouldUpdatePort) {
            WebDavEndpointNormalizer.defaultPortFor(newProtocol).toString()
        } else {
            port
        }
        return copy(protocol = newProtocol, port = newPort)
    }

    fun form(): ServerConfigFormState = ServerConfigFormState(
        slot = slot,
        alias = alias,
        protocol = protocol,
        url = url,
        port = port,
        username = username,
        password = password,
        domain = domain,
        rememberPassword = rememberPassword,
        isPrivate = isPrivate,
        showPassword = showPassword,
        testing = testing,
        testResult = testResult,
        error = error,
    )
}

sealed interface ServerConfigEvent {
    data class Message(val resId: Int) : ServerConfigEvent
    data class MessageText(val text: String) : ServerConfigEvent
    data class Saved(val slot: Int) : ServerConfigEvent
    data object Dismissed : ServerConfigEvent
}

sealed interface ServerConfigAction {
    data class Update(val transform: ServerConfigDialogState.() -> ServerConfigDialogState) : ServerConfigAction
    data object ToggleShowPassword : ServerConfigAction
    data object TestConnection : ServerConfigAction
    data object EnumerateShares : ServerConfigAction
    data object DiscoverHosts : ServerConfigAction
    data object DismissDiscovery : ServerConfigAction
    data class ApplyHost(val host: DiscoveredHost) : ServerConfigAction
    data class ApplyShare(val name: String) : ServerConfigAction
    data object Save : ServerConfigAction
}

/**
 * Validates the form and returns the message to show, or null when the input is
 * acceptable.
 *
 * Semantics come from [ServerConfigDialogHelper.validate] — for SMB the host
 * must carry a non-empty share segment — plus the port range that the XML
 * dialog only enforced through `inputType="number"`.
 *
 * The three messages are injected so this stays free of Android types. Host and
 * port each get their own slot: reporting an SMB-specific complaint for a plain
 * HTTPS port typo is what this split exists to prevent.
 */
fun validateServerConfig(
    form: ServerConfigFormState,
    smbShareRequiredMessage: String,
    missingHostMessage: String? = null,
    invalidPortMessage: String? = null,
): String? {
    if (form.url.isBlank()) {
        return missingHostMessage ?: smbShareRequiredMessage
    }
    if (form.port.toIntOrNull() !in 1..65535) {
        return invalidPortMessage ?: smbShareRequiredMessage
    }
    if (form.protocol.equals("smb", ignoreCase = true)) {
        val smbPath = form.url
            .replace("smb://", "", ignoreCase = true)
            .trim()
            .trim('/')
        if (!smbPath.contains('/') || smbPath.substringAfter('/').isBlank()) {
            return smbShareRequiredMessage
        }
    }
    return null
}

/** Maps the form onto the protocol-aware Rust FFI configuration. */
fun buildRemoteConfig(form: ServerConfigFormState) = uniffi.rust_core.RemoteConfig(
    when (form.protocol.lowercase()) {
        "smb" -> uniffi.rust_core.RemoteProtocol.SMB
        "ftp" -> uniffi.rust_core.RemoteProtocol.FTP
        else -> uniffi.rust_core.RemoteProtocol.WEB_DAV
    },
    WebDavEndpointNormalizer.normalize(
        form.protocol,
        form.url,
        form.port.toIntOrNull() ?: WebDavEndpointNormalizer.defaultPortFor(form.protocol),
    ),
    form.username,
    form.password,
    form.domain.takeIf { it.isNotBlank() },
)

fun smbEnumParamsOf(form: ServerConfigFormState) = ServerConfigDialogHelper.smbEnumParams(
    form.url,
    form.port,
    form.username,
    form.password,
    form.domain,
)

internal fun formatTestConnectionError(rawError: String?): String? {
    if (rawError == null) return null
    return when {
        rawError.contains("401") || rawError.contains("Not Authorized", ignoreCase = true) || rawError.contains("Unauthorized", ignoreCase = true) ->
            "认证失败 (401 Unauthorized)：请检查用户名与密码是否正确\n$rawError"
        rawError.contains("Connection refused", ignoreCase = true) || rawError.contains("ECONNREFUSED") ->
            "连接被拒绝：请检查服务器 IP 和端口是否正确\n$rawError"
        rawError.contains("timed out", ignoreCase = true) || rawError.contains("ETIMEDOUT") ->
            "连接超时：无法访问该网络地址，请检查局域网连接或防火墙\n$rawError"
        else -> rawError
    }
}

/**
 * Backend seam for the dialog's side effects. [RustServerConfigBackend] is the
 * production implementation; tests and previews inject a fake so discovery and
 * share enumeration can be exercised without a real server.
 */
interface ServerConfigBackend {
    suspend fun testConnection(config: uniffi.rust_core.RemoteConfig): String
    suspend fun listSmbShares(host: String, port: Int, username: String, password: String, domain: String?): List<String>
    fun discoverHosts(): Flow<List<DiscoveredHost>>
}

object RustServerConfigBackend : ServerConfigBackend {
    override suspend fun testConnection(config: uniffi.rust_core.RemoteConfig): String =
        WebDAVToonApplication.rustRepository?.testRemote(config)
            ?: throw IllegalStateException("Rust repository not initialized")

    override suspend fun listSmbShares(
        host: String,
        port: Int,
        username: String,
        password: String,
        domain: String?,
    ): List<String> = uniffi.rust_core.listSmbShares(host, port.toUShort(), username, password, domain)
        .map { it.name }

    override fun discoverHosts(): Flow<List<DiscoveredHost>> =
        NetworkDiscovery(WebDAVToonApplication.appContext).discover()
}

/**
 * `@JvmOverloads` is load-bearing: a Kotlin default parameter alone generates only
 * `(Application, ServerConfigBackend)` plus a synthetic marker overload, none of which
 * `AndroidViewModelFactory`'s fixed-signature reflection `(Application, SavedStateHandle)`
 * → `(SavedStateHandle)` → `(Application)` → `()` can match. Without the real
 * `(Application)` overload `by viewModels()` throws at the moment the dialog is opened,
 * which no compiler check would catch. The KMP/JS target is not built here, so the
 * `@JvmOverloads` `actual`-declaration concern does not apply.
 */
class ServerConfigViewModel @JvmOverloads constructor(
    app: Application,
    private val backend: ServerConfigBackend = RustServerConfigBackend,
) : AndroidViewModel(app) {

    private val settings = SettingsManager(app)
    private val _state = MutableStateFlow(ServerConfigDialogState())
    val state = _state.asStateFlow()
    private val eventsChannel = Channel<ServerConfigEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    /**
     * Discovery runs until it is cancelled. `NetworkDiscovery.discover()` is a
     * callbackFlow whose `awaitClose` tears down the NSD listeners, so dropping
     * the job is what actually stops the LAN scan.
     */
    private var discoveryJob: Job? = null

    fun load(slot: Int) {
        _state.value = ServerConfigDialogState(
            slot = slot,
            alias = settings.getWebDavAlias(slot),
            protocol = settings.getWebDavProtocol(slot),
            url = settings.getWebDavUrl(slot),
            port = settings.getWebDavPort(slot).toString(),
            username = settings.getWebDavUsername(slot),
            // Prefilled here rather than by the UI: the Compose layer must not read
            // SettingsManager, and this ViewModel already owns the credential-backed
            // SettingsManager instance. Matches the legacy dialog, which seeded the field
            // through `getWebDavPassword()`. The field stays masked because
            // `showPassword` defaults to false.
            password = settings.getWebDavPassword(slot),
            domain = settings.getWebDavDomain(slot),
            rememberPassword = settings.isWebDavRememberPassword(slot),
            isPrivate = settings.isWebDavPrivate(slot),
            isPrivacyMode = PrivacyModeState.isPrivacyMode,
        )
    }

    fun update(transform: ServerConfigDialogState.() -> ServerConfigDialogState) = _state.update(transform)

    fun toggleShowPassword() = update { copy(showPassword = !showPassword) }

    fun testConnection() = viewModelScope.launch(Dispatchers.IO) {
        val form = state.value.form()
        validationMessage(form)?.let { message ->
            eventsChannel.send(ServerConfigEvent.MessageText(message))
            return@launch
        }
        update { copy(testing = true, error = null, testResult = null) }
        runCatching { backend.testConnection(buildRemoteConfig(form)) }
            .onSuccess { result -> update { copy(testing = false, testResult = result) } }
            .onFailure { failure -> update { copy(testing = false, error = formatTestConnectionError(failure.message)) } }
    }

    fun enumerateShares() = viewModelScope.launch(Dispatchers.IO) {
        val params = smbEnumParamsOf(state.value.form())
        if (params.host.isBlank()) {
            eventsChannel.send(ServerConfigEvent.Message(R.string.smb_enum_requires_host_user))
            return@launch
        }
        update { copy(enumeratingShares = true, shares = null) }
        runCatching {
            backend.listSmbShares(params.host, params.port, params.username, params.password, params.domain)
        }
            .onSuccess { shares -> update { copy(enumeratingShares = false, shares = shares) } }
            .onFailure { failure -> update { copy(enumeratingShares = false, error = failure.message) } }
    }

    fun discoverHosts() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            update { copy(discoveringHosts = true) }
            backend.discoverHosts().collect { hosts -> update { copy(discoveredHosts = hosts) } }
        }
    }

    fun dismissDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        update { copy(discoveringHosts = false, discoveredHosts = emptyList()) }
    }

    fun applyDiscoveredHost(host: DiscoveredHost) {
        val values = ServerConfigDialogHelper.formValuesFor(host)
        discoveryJob?.cancel()
        discoveryJob = null
        update {
            copy(
                protocol = values.protocol,
                url = values.host,
                port = values.port,
                discoveringHosts = false,
                discoveredHosts = emptyList(),
            )
        }
    }

    fun applySelectedShare(name: String) = update {
        copy(url = ServerConfigDialogHelper.applySelectedShare(url, name), shares = null)
    }

    fun save() = viewModelScope.launch(Dispatchers.IO) {
        val snapshot = state.value
        validationMessage(snapshot.form())?.let { message ->
            eventsChannel.send(ServerConfigEvent.MessageText(message))
            return@launch
        }
        settings.saveWebDavConfiguration(
            slot = snapshot.slot,
            alias = snapshot.alias,
            protocol = snapshot.protocol,
            url = snapshot.url,
            port = snapshot.port.toInt(),
            username = snapshot.username,
            password = snapshot.password,
            rememberPassword = snapshot.rememberPassword,
            isPrivate = snapshot.isPrivate,
            domain = snapshot.domain,
            // Select a new server only after validation, never while opening its draft.
            switchToSlotOnSave = snapshot.slot !in settings.getAllSlotsUnfiltered(),
        )
        eventsChannel.send(ServerConfigEvent.Saved(snapshot.slot))
    }

    private fun validationMessage(form: ServerConfigFormState): String? {
        val context = getApplication<Application>()
        return validateServerConfig(
            form = form,
            smbShareRequiredMessage = context.getString(R.string.smb_share_required),
            // Borrowed from the share-enumeration flow: it is the only
            // "host is required" string in values/. A dedicated key would read
            // better, but adding one is outside this slice's file list.
            missingHostMessage = context.getString(R.string.smb_enum_requires_host_user),
            // No "port out of range" string exists either. Name the offending
            // field through error_prefix rather than borrowing the SMB share
            // message, which would be plainly wrong for HTTP/FTP.
            invalidPortMessage = context.getString(
                R.string.error_prefix,
                context.getString(R.string.port_hint),
            ),
        )
    }
}
