package erl.webdavtoon

object WebDavEndpointNormalizer {

    /** Default ports hidden from the canonical endpoint string, per protocol. */
    private fun defaultPortsFor(protocol: String): Set<Int> = when (protocol.lowercase()) {
        "smb" -> setOf(445)
        "ftp" -> setOf(21)
        // http/https keep the historical behavior (both 80 and 443 hidden for
        // either scheme) so existing slots produce byte-identical endpoints.
        else -> setOf(80, 443)
    }

    fun defaultPortFor(protocol: String): Int = when (protocol.lowercase()) {
        "smb" -> 445
        "ftp" -> 21
        "http" -> 80
        else -> 443
    }

    fun normalize(protocol: String, rawUrl: String, port: Int): String {
        var normalizedUrl = rawUrl.trim()
            .replace("http://", "", ignoreCase = true)
            .replace("https://", "", ignoreCase = true)
            .replace("smb://", "", ignoreCase = true)
            .replace("ftp://", "", ignoreCase = true)

        if (normalizedUrl.endsWith('/')) {
            normalizedUrl = normalizedUrl.dropLast(1)
        }

        val firstSlash = normalizedUrl.indexOf('/')
        val hostPart = if (firstSlash != -1) normalizedUrl.substring(0, firstSlash) else normalizedUrl
        val pathPart = if (firstSlash != -1) normalizedUrl.substring(firstSlash) else ""

        var host = hostPart
        var finalPort = port

        if (host.contains(':')) {
            val parts = host.split(':', limit = 2)
            if (parts.size == 2) {
                host = parts[0]
                parts[1].toIntOrNull()?.let { finalPort = it }
            }
        }

        return if (finalPort in defaultPortsFor(protocol)) {
            "$protocol://$host$pathPart"
        } else {
            "$protocol://$host:$finalPort$pathPart"
        }
    }
}
