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

    private fun applyProtocolToForm(
        activity: AppCompatActivity,
        binding: DialogServerConfigWebdavBinding,
        protocol: String,
        autoFillPort: Boolean
    ) {
        binding.domainLayout.visibility =
            if (protocol == "smb") android.view.View.VISIBLE else android.view.View.GONE
        binding.listSharesButton.visibility =
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

    /**
     * Inputs for SMB share enumeration extracted from the raw form fields.
     * [host] is the bare server (no scheme, no share path, no IPv6 brackets).
     */
    data class SmbEnumParams(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val domain: String?
    )

    /**
     * Extracts share-enumeration parameters from the raw form text. Pure
     * (no Android types) so it stays unit-testable on the JVM.
     *
     * The host field may already hold `host/share[/sub]`, `smb://host/...`,
     * `host:port/...`, or a bracketed IPv6 literal `[::1]/share` — only the
     * bare host survives. A port embedded in the host wins over [portText]
     * (mirroring WebDavEndpointNormalizer), which falls back to 445.
     */
    fun smbEnumParams(
        hostText: String,
        portText: String,
        username: String,
        password: String,
        domain: String
    ): SmbEnumParams {
        val stripped = hostText.trim()
            .replace("smb://", "", ignoreCase = true)
            .trimStart('/')
        // IPv6 literals contain no '/', so the first slash always ends the host part.
        val hostPart = stripped.substringBefore('/')

        var host = hostPart
        var embeddedPort: Int? = null
        if (hostPart.startsWith("[")) {
            val close = hostPart.indexOf(']')
            if (close != -1) {
                host = hostPart.substring(1, close)
                hostPart.substring(close + 1).removePrefix(":").toIntOrNull()?.let { embeddedPort = it }
            }
        } else {
            val colon = hostPart.lastIndexOf(':')
            if (colon != -1) {
                hostPart.substring(colon + 1).toIntOrNull()?.let {
                    embeddedPort = it
                    host = hostPart.substring(0, colon)
                }
            }
        }

        val port = (embeddedPort ?: portText.trim().toIntOrNull())
            ?.takeIf { it in 1..65535 } ?: 445

        return SmbEnumParams(
            host = host,
            port = port,
            username = username.trim(),
            password = password,
            domain = domain.trim().takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Rewrites the host field to `host/shareName`, dropping any previously
     * typed share/subpath while preserving the host exactly as entered
     * (bracketed IPv6 and embedded port included). Pure and unit-testable.
     */
    fun applySelectedShare(hostText: String, shareName: String): String {
        val stripped = hostText.trim()
            .replace("smb://", "", ignoreCase = true)
            .trimStart('/')
        val hostPart = stripped.substringBefore('/')
        return "$hostPart/$shareName"
    }

    /**
     * Enumerates shares on the SMB host via the Rust FFI and lets the user
     * pick one; the pick replaces the share segment of the host field. On
     * failure (auth error, SRVSVC disabled, ...) shows the error plus a hint
     * that the share name can still be typed manually — never blocks the
     * dialog.
     */
    fun runShareEnumeration(
        activity: AppCompatActivity,
        binding: DialogServerConfigWebdavBinding
    ) {
        val params = smbEnumParams(
            hostText = binding.hostEdit.text.toString(),
            portText = binding.portEdit.text.toString(),
            username = binding.usernameEdit.text.toString(),
            password = binding.passwordEdit.text.toString(),
            domain = binding.domainEdit.text.toString()
        )
        if (params.host.isBlank() || params.username.isBlank()) {
            Toast.makeText(
                activity,
                activity.getString(R.string.smb_enum_requires_host_user),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        binding.listSharesButton.isEnabled = false
        Toast.makeText(activity, activity.getString(R.string.smb_enumerating_shares), Toast.LENGTH_SHORT).show()

        activity.lifecycleScope.launch {
            try {
                val shares = withContext(Dispatchers.IO) {
                    uniffi.rust_core.listSmbShares(
                        host = params.host,
                        port = params.port.toUShort(),
                        username = params.username,
                        password = params.password,
                        domain = params.domain
                    )
                }
                if (shares.isEmpty()) {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(activity.getString(R.string.smb_choose_share))
                        .setMessage(activity.getString(R.string.smb_enum_failed_hint))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                } else {
                    val labels = shares.map { share ->
                        if (share.remark.isNotEmpty()) "${share.name} (${share.remark})" else share.name
                    }.toTypedArray()
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(activity.getString(R.string.smb_choose_share))
                        .setItems(labels) { _, which ->
                            binding.hostEdit.setText(
                                applySelectedShare(binding.hostEdit.text.toString(), shares[which].name)
                            )
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            } catch (e: Exception) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.connection_failed))
                    .setMessage(
                        activity.getString(R.string.error_prefix, e.message ?: "Unknown error") +
                            "\n\n" + activity.getString(R.string.smb_enum_failed_hint)
                    )
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } finally {
                binding.listSharesButton.isEnabled = true
            }
        }
    }
}
