package erl.webdavtoon

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object WebDavSlotMigration {
    private val gson = Gson()

    fun migrateLegacySlotsJson(json: String, persistentStore: WebDavSecretStore): String {
        if (json.isBlank()) return json

        val type = object : TypeToken<Map<String, LegacyWebDavSlotConfig>>() {}.type
        val legacySlots = runCatching {
            gson.fromJson<Map<String, LegacyWebDavSlotConfig>>(json, type)
        }.getOrNull() ?: return json

        val migratedSlots = legacySlots.mapValues { (slotKey, legacy) ->
            if (legacy.rememberPassword && legacy.password.isNotBlank()) {
                slotKey.toIntOrNull()?.let { persistentStore.put(it, legacy.password) }
            }
            legacy.toSlotConfig()
        }

        return gson.toJson(migratedSlots)
    }
}
