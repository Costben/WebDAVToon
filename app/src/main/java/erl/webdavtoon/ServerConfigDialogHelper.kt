package erl.webdavtoon

import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import erl.webdavtoon.databinding.DialogServerConfigWebdavBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared behavior for the server config dialog (Settings + Drawer entry
 * points): protocol dropdown incl. smb/ftp, per-protocol port auto-fill and
 * host hint, SMB domain field visibility, SMB share validation, and the
 * Test Connection flow through the protocol-aware Rust FFI.
 */
object ServerConfigDialogHelper {

    val PROTOCOLS = arrayOf("http", "https", "smb", "ftp")

    fun setupProtocolField(
        activity: AppCompatActivity,
        binding: DialogServerConfigWebdavBinding,
        initialProtocol: String
    ) {
        val adapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, PROTOCOLS)
        binding.protocolEdit.setAdapter(adapter)
        binding.protocolEdit.setText(initialProtocol, false)
        applyProtocolToForm(activity, binding, initialProtocol, autoFillPort = false)

        binding.protocolEdit.setOnItemClickListener { _, _, position, _ ->
            applyProtocolToForm(activity, binding, PROTOCOLS[position], autoFillPort = true)
        }
    }

    /**
     * Pure form-value mapping for a [DiscoveredHost]: protocol dropdown text,
     * host field text (IPv6 literals bracketed for URL use), port field text.
     * Deliberately free of Android classes so plain JUnit can cover it.
     */
    data class DiscoveredFormValues(
        val protocol: String,
        val host: String,
        val port: String
    )

    fun formValuesFor(host: DiscoveredHost): DiscoveredFormValues = DiscoveredFormValues(
        // The dropdown offers http/https/smb/ftp; a discovered "webdav"
        // service is an HTTP(S) endpoint underneath.
        protocol = when (host.protocol) {
            "webdav" -> if (host.port == 443) "https" else "http"
            else -> host.protocol
        },
        host = bracketIpv6Literal(host.host),
        port = host.port.toString()
    )

    /** Brackets an unbracketed IPv6 literal; IPv4 and hostnames pass through. */
    fun bracketIpv6Literal(host: String): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]" else host

    /**
     * Applies a discovered host to the form and re-runs the protocol linkage
     * (domain-field visibility, host hint) exactly like a dropdown selection.
     * The port comes from discovery, so no default-port auto-fill.
     */
    fun applyDiscoveredHost(
        activity: AppCompatActivity,
        binding: DialogServerConfigWebdavBinding,
        host: DiscoveredHost
    ) {
        val values = formValuesFor(host)
        binding.protocolEdit.setText(values.protocol, false)
        binding.hostEdit.setText(values.host)
        binding.portEdit.setText(values.port)
        applyProtocolToForm(activity, binding, values.protocol, autoFillPort = false)
    }

    private fun applyProtocolToForm(
        activity: AppCompatActivity,
        binding: DialogServerConfigWebdavBinding,
        protocol: String,
        autoFillPort: Boolean
    ) {
        binding.domainLayout.visibility =
            if (protocol == "smb") android.view.View.VISIBLE else android.view.View.GONE
        binding.hostLayout.hint = activity.getString(
            if (protocol == "smb") R.string.host_hint_smb else R.string.host_hint
        )
        if (autoFillPort) {
            // Overwrite only when the field still holds another protocol's
            // default, so a user-entered custom port survives the switch.
            val currentPort = binding.portEdit.text.toString().toIntOrNull()
            val defaults = PROTOCOLS.map { WebDavEndpointNormalizer.defaultPortFor(it) }
            if (currentPort == null || currentPort in defaults) {
                binding.portEdit.setText(WebDavEndpointNormalizer.defaultPortFor(protocol).toString())
            }
        }
    }

    /**
     * Validates the form. Returns an error message to show, or null when OK.
     */
    fun validate(activity: AppCompatActivity, binding: DialogServerConfigWebdavBinding): String? {
        val protocol = binding.protocolEdit.text.toString()
        if (protocol == "smb") {
            val url = binding.hostEdit.text.toString()
                .replace("smb://", "", ignoreCase = true)
                .trim()
                .trim('/')
            if (!url.contains('/') || url.substringAfter('/').isBlank()) {
                return activity.getString(R.string.smb_share_required)
            }
        }
        return null
    }

    /** Builds the RemoteConfig for Test Connection from current form values. */
    fun buildTestConfig(binding: DialogServerConfigWebdavBinding): uniffi.rust_core.RemoteConfig {
        val protocol = binding.protocolEdit.text.toString()
        val port = binding.portEdit.text.toString().toIntOrNull()
            ?: WebDavEndpointNormalizer.defaultPortFor(protocol)
        val endpoint = WebDavEndpointNormalizer.normalize(
            protocol = protocol,
            rawUrl = binding.hostEdit.text.toString(),
            port = port
        )
        val remoteProtocol = when (protocol.lowercase()) {
            "smb" -> uniffi.rust_core.RemoteProtocol.SMB
            "ftp" -> uniffi.rust_core.RemoteProtocol.FTP
            else -> uniffi.rust_core.RemoteProtocol.WEB_DAV
        }
        return uniffi.rust_core.RemoteConfig(
            protocol = remoteProtocol,
            endpoint = endpoint,
            username = binding.usernameEdit.text.toString(),
            password = binding.passwordEdit.text.toString(),
            domain = binding.domainEdit.text.toString().takeIf { it.isNotEmpty() }
        )
    }

    fun runConnectionTest(
        activity: AppCompatActivity,
        binding: DialogServerConfigWebdavBinding
    ) {
        validate(activity, binding)?.let { error ->
            Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
            return
        }
        val config = buildTestConfig(binding)
        Toast.makeText(activity, activity.getString(R.string.testing_connection), Toast.LENGTH_SHORT).show()

        activity.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val repo = WebDAVToonApplication.rustRepository
                        ?: throw IllegalStateException(
                            "Rust repository not initialized: ${WebDAVToonApplication.rustInitError ?: "unknown"}"
                        )
                    repo.testRemote(config)
                }

                MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.connection_test_result))
                    .setMessage(result)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } catch (e: Exception) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.connection_failed))
                    .setMessage(activity.getString(R.string.error_prefix, e.message ?: "Unknown error"))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }
}
