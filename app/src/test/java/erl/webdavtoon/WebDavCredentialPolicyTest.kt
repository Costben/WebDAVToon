package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebDavCredentialPolicyTest {
    @Test
    fun remembered_password_persists_and_resolves_without_session() {
        val persistent = InMemorySecretStore()
        val session = InMemorySecretStore()
        val policy = WebDavCredentialPolicy(persistent, session)

        policy.savePassword(slot = 1, rememberPassword = true, password = "secret")
        session.remove(1)

        assertEquals("secret", policy.resolvePassword(slot = 1, rememberPassword = true))
        assertEquals("secret", persistent.get(1))
    }

    @Test
    fun non_remembered_password_stays_in_session_only() {
        val persistent = InMemorySecretStore()
        val session = InMemorySecretStore()
        val policy = WebDavCredentialPolicy(persistent, session)

        policy.savePassword(slot = 2, rememberPassword = false, password = "ephemeral")

        assertEquals("ephemeral", session.get(2))
        assertNull(persistent.get(2))

        session.remove(2)
        assertEquals("", policy.resolvePassword(slot = 2, rememberPassword = false))
    }

    @Test
    fun disabling_remember_password_clears_persistent_secret_but_keeps_session_access() {
        val persistent = InMemorySecretStore()
        val session = InMemorySecretStore()
        val policy = WebDavCredentialPolicy(persistent, session)

        policy.savePassword(slot = 3, rememberPassword = true, password = "carry-over")
        policy.updateRememberPreference(slot = 3, currentPassword = "carry-over", rememberPassword = false)

        assertNull(persistent.get(3))
        assertEquals("carry-over", policy.resolvePassword(slot = 3, rememberPassword = false))
    }
}
