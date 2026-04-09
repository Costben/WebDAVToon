package erl.webdavtoon

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebDavSlotMigrationTest {
    private val gson = Gson()

    @Test
    fun migration_moves_remembered_password_out_of_json() {
        val store = InMemorySecretStore()
        val legacyJson = gson.toJson(
            mapOf(
                "0" to LegacyWebDavSlotConfig(
                    enabled = true,
                    protocol = "https",
                    url = "example.com/dav",
                    port = 443,
                    username = "alice",
                    password = "stored-secret",
                    rememberPassword = true,
                    alias = "NAS"
                )
            )
        )

        val migratedJson = WebDavSlotMigration.migrateLegacySlotsJson(legacyJson, store)
        val migrated = parseMigrated(migratedJson)

        assertEquals("stored-secret", store.get(0))
        assertEquals(
            WebDavSlotConfig(
                enabled = true,
                protocol = "https",
                url = "example.com/dav",
                port = 443,
                username = "alice",
                rememberPassword = true,
                alias = "NAS"
            ),
            migrated["0"]
        )
    }

    @Test
    fun migration_drops_non_remembered_legacy_password() {
        val store = InMemorySecretStore()
        val legacyJson = gson.toJson(
            mapOf(
                "1" to LegacyWebDavSlotConfig(
                    url = "example.com/public",
                    username = "bob",
                    password = "should-not-survive",
                    rememberPassword = false
                )
            )
        )

        val migratedJson = WebDavSlotMigration.migrateLegacySlotsJson(legacyJson, store)
        val migrated = parseMigrated(migratedJson)

        assertNull(store.get(1))
        assertEquals(false, migrated["1"]?.rememberPassword)
        assertEquals("bob", migrated["1"]?.username)
    }

    private fun parseMigrated(json: String): Map<String, WebDavSlotConfig> {
        val type = object : TypeToken<Map<String, WebDavSlotConfig>>() {}.type
        return gson.fromJson(json, type)
    }

    private class InMemorySecretStore : WebDavSecretStore {
        private val values = mutableMapOf<Int, String>()

        override fun get(slot: Int): String? = values[slot]

        override fun put(slot: Int, password: String) {
            values[slot] = password
        }

        override fun remove(slot: Int) {
            values.remove(slot)
        }
    }
}
