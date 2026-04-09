package erl.webdavtoon

data class WebDavSlotConfig(
    val enabled: Boolean = false,
    val protocol: String = "https",
    val url: String = "",
    val port: Int = 443,
    val username: String = "",
    val rememberPassword: Boolean = true,
    val alias: String = ""
)

data class LegacyWebDavSlotConfig(
    val enabled: Boolean = false,
    val protocol: String = "https",
    val url: String = "",
    val port: Int = 443,
    val username: String = "",
    val password: String = "",
    val rememberPassword: Boolean = true,
    val alias: String = ""
) {
    fun toSlotConfig(): WebDavSlotConfig {
        return WebDavSlotConfig(
            enabled = enabled,
            protocol = protocol,
            url = url,
            port = port,
            username = username,
            rememberPassword = rememberPassword,
            alias = alias
        )
    }
}
