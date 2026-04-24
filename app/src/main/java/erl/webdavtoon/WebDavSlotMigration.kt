package erl.webdavtoon

import com.google.gson.Gson
import com.google.gson.JsonParser

object WebDavSlotMigration {
    private val gson = Gson()

    fun migrateLegacySlotsJson(json: String, persistentStore: WebDavSecretStore): String {
        if (json.isBlank()) return json

        val root = runCatching {
            val parsed = JsonParser.parseString(json)
            check(parsed.isJsonObject) { "Legacy WebDAV slots JSON must be an object" }
            parsed.asJsonObject
        }.getOrNull() ?: return json

        val migratedSlots = runCatching {
            root.entrySet().associate { (slotKey, value) ->
                val legacy = gson.fromJson(value, LegacyWebDavSlotConfig::class.java)
                if (legacy.rememberPassword && legacy.password.isNotBlank()) {
                    slotKey.toIntOrNull()?.let { persistentStore.put(it, legacy.password) }
                }
                slotKey to legacy.toSlotConfig()
            }
        }.getOrNull() ?: return json

        return gson.toJson(migratedSlots)
    }
}
