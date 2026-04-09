package erl.webdavtoon

import android.content.Context
import android.util.Log

object WebDavCredentialMigration {
    suspend fun migrateIfNeeded(context: Context) {
        val appSettings = AppSettingsStore(context)
        if (appSettings.isWebDavSecretMigrated()) return

        val existingJson = appSettings.getOrDefaultString(AppSettingsStore.WEBDAV_SLOTS_JSON, "")
        if (existingJson.isBlank()) {
            appSettings.markWebDavSecretMigrated()
            return
        }

        val secureStore = AndroidKeystoreWebDavPasswordStore(context)
        val migratedJson = WebDavSlotMigration.migrateLegacySlotsJson(existingJson, secureStore)
        appSettings.putStringSync(AppSettingsStore.WEBDAV_SLOTS_JSON, migratedJson)
        appSettings.markWebDavSecretMigrated()
        Log.i("WebDavCredentialMigration", "Migrated WebDAV secrets out of slots JSON")
    }
}
