// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

/**
 * Pure, Android-free helpers shared by the Compose server-config dialog.
 *
 * The former View/XML half of this object (TextInputLayout binding, protocol
 * dropdown wiring, MaterialAlertDialogBuilder result dialogs) was deleted when
 * the app moved fully to COUI: the dialog is now
 * `ui/screen/settings/dialog/ServerConfigDialog.kt` driven by
 * `ServerConfigState` / `ServerConfigViewModel`.
 */
object ServerConfigDialogHelper {

    val PROTOCOLS = arrayOf("http", "https", "smb", "ftp")

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
}
