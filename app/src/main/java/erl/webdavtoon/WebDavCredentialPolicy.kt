package erl.webdavtoon

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface WebDavSecretStore {
    fun get(slot: Int): String?
    fun put(slot: Int, password: String)
    fun remove(slot: Int)
}

object WebDavSessionPasswordStore : WebDavSecretStore {
    private val passwords = ConcurrentHashMap<Int, String>()

    override fun get(slot: Int): String? = passwords[slot]

    override fun put(slot: Int, password: String) {
        if (password.isBlank()) {
            passwords.remove(slot)
        } else {
            passwords[slot] = password
        }
    }

    override fun remove(slot: Int) {
        passwords.remove(slot)
    }
}

class WebDavCredentialPolicy(
    private val persistentStore: WebDavSecretStore,
    private val sessionStore: WebDavSecretStore
) {
    fun resolvePassword(slot: Int, rememberPassword: Boolean): String {
        val sessionPassword = sessionStore.get(slot)
        if (sessionPassword != null) {
            return sessionPassword
        }

        if (!rememberPassword) {
            return ""
        }

        val persistedPassword = persistentStore.get(slot) ?: return ""
        sessionStore.put(slot, persistedPassword)
        return persistedPassword
    }

    fun savePassword(slot: Int, rememberPassword: Boolean, password: String) {
        if (password.isBlank()) {
            sessionStore.remove(slot)
            persistentStore.remove(slot)
            return
        }

        sessionStore.put(slot, password)
        if (rememberPassword) {
            persistentStore.put(slot, password)
        } else {
            persistentStore.remove(slot)
        }
    }

    fun updateRememberPreference(slot: Int, currentPassword: String, rememberPassword: Boolean) {
        if (currentPassword.isBlank()) {
            if (!rememberPassword) {
                persistentStore.remove(slot)
                sessionStore.remove(slot)
            }
            return
        }

        sessionStore.put(slot, currentPassword)
        if (rememberPassword) {
            persistentStore.put(slot, currentPassword)
        } else {
            persistentStore.remove(slot)
        }
    }

    fun deletePassword(slot: Int) {
        sessionStore.remove(slot)
        persistentStore.remove(slot)
    }
}

class AndroidKeystoreWebDavPasswordStore(context: Context) : WebDavSecretStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(slot: Int): String? {
        val stored = preferences.getString(preferenceKey(slot), null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    override fun put(slot: Int, password: String) {
        if (password.isBlank()) {
            remove(slot)
            return
        }

        preferences.edit()
            .putString(preferenceKey(slot), encrypt(password))
            .commit()
    }

    override fun remove(slot: Int) {
        preferences.edit().remove(preferenceKey(slot)).commit()
    }

    private fun preferenceKey(slot: Int): String = "webdav_password_slot_$slot"

    private fun encrypt(password: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))
        val payload = iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_SIZE_BYTES) { "Invalid encrypted payload" }

        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val encrypted = payload.copyOfRange(IV_SIZE_BYTES, payload.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )

        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val parameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "webdav_secret_store"
        private const val KEY_ALIAS = "webdavtoon_webdav_password_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALGORITHM_AES = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_SIZE_BYTES = 12
    }
}
