package erl.webdavtoon

object WebDavEndpointNormalizer {
    fun normalize(protocol: String, rawUrl: String, port: Int): String {
        var normalizedUrl = rawUrl.trim()
            .replace("http://", "", ignoreCase = true)
            .replace("https://", "", ignoreCase = true)

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

        return if (finalPort == 80 || finalPort == 443) {
            "$protocol://$host$pathPart"
        } else {
            "$protocol://$host:$finalPort$pathPart"
        }
    }
}
