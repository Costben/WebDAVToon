package erl.webdavtoon

import erl.webdavtoon.ui.screen.settings.ServerConfigFormState
import erl.webdavtoon.ui.screen.settings.dialog.validateServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM coverage for [validateServerConfig]. No Android and no uniffi types are touched: the
 * form is a plain data class and the three messages are injected as strings, which is the
 * whole reason the function was written that way.
 */
class ServerConfigValidationTest {

    private val shareRequired = "SMB share is required"
    private val missingHost = "Host is required"
    private val invalidPort = "Invalid port"

    private fun form(
        url: String = "dav.example.com",
        port: String = "443",
        protocol: String = "https",
    ) = ServerConfigFormState(slot = 0, protocol = protocol, url = url, port = port)

    private fun validate(
        url: String = "dav.example.com",
        port: String = "443",
        protocol: String = "https",
    ) = validateServerConfig(
        form = form(url = url, port = port, protocol = protocol),
        smbShareRequiredMessage = shareRequired,
        missingHostMessage = missingHost,
        invalidPortMessage = invalidPort,
    )

    @Test
    fun blankHost_reportsMissingHost() {
        assertEquals(missingHost, validate(url = ""))
        assertEquals(missingHost, validate(url = "   "))
    }

    @Test
    fun portBelowRange_reportsInvalidPort() {
        assertEquals(invalidPort, validate(port = "0"))
        assertEquals(invalidPort, validate(port = "-1"))
    }

    @Test
    fun portAboveRange_reportsInvalidPort() {
        assertEquals(invalidPort, validate(port = "65536"))
    }

    @Test
    fun nonNumericPort_reportsInvalidPort() {
        assertEquals(invalidPort, validate(port = "abc"))
        assertEquals(invalidPort, validate(port = ""))
    }

    @Test
    fun rangeBoundaries_pass() {
        assertNull(validate(port = "1"))
        assertNull(validate(port = "65535"))
        assertNull(validate(port = "443"))
    }

    @Test
    fun smbWithoutShare_reportsShareRequired() {
        val message = validate(url = "nas.local", port = "445", protocol = "smb")
        assertEquals(shareRequired, message)
    }

    @Test
    fun smbWithEmptyShareSegment_reportsShareRequired() {
        assertEquals(
            shareRequired,
            validate(url = "nas.local/", port = "445", protocol = "smb"),
        )
        assertEquals(
            shareRequired,
            validate(url = "smb://nas.local/", port = "445", protocol = "smb"),
        )
    }

    @Test
    fun smbWithShare_passes() {
        assertNull(validate(url = "nas.local/photos", port = "445", protocol = "smb"))
        assertNull(validate(url = "smb://nas.local/photos", port = "445", protocol = "smb"))
        assertNull(validate(url = "smb://nas.local/photos/", port = "445", protocol = "smb"))
    }

    @Test
    fun smbProtocolIsCaseInsensitive() {
        assertEquals(
            shareRequired,
            validate(url = "nas.local", port = "445", protocol = "SMB"),
        )
        assertNull(validate(url = "nas.local/photos", port = "445", protocol = "Smb"))
    }

    @Test
    fun httpsWithoutShareSegment_passes() {
        assertNull(validate(url = "dav.example.com", protocol = "https"))
        assertNull(validate(url = "dav.example.com/remote.php/dav", protocol = "https"))
        assertNull(validate(url = "dav.example.com", protocol = "http"))
    }

    @Test
    fun nullHostAndPortMessages_fallBackToShareRequired() {
        assertEquals(
            shareRequired,
            validateServerConfig(
                form = form(url = ""),
                smbShareRequiredMessage = shareRequired,
            ),
        )
        assertEquals(
            shareRequired,
            validateServerConfig(
                form = form(port = "99999"),
                smbShareRequiredMessage = shareRequired,
            ),
        )
    }

    @Test
    fun hostIsCheckedBeforePort() {
        // Both fields are invalid; the host complaint wins because it is checked first.
        assertEquals(missingHost, validate(url = "", port = "abc"))
    }
}
