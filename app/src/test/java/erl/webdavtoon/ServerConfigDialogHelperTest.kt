package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerConfigDialogHelperTest {

    @Test
    fun smb_host_maps_with_protocol_passthrough() {
        val values = ServerConfigDialogHelper.formValuesFor(
            DiscoveredHost(protocol = "smb", displayName = "NAS", host = "192.168.1.10", port = 445)
        )
        assertEquals("smb", values.protocol)
        assertEquals("192.168.1.10", values.host)
        assertEquals("445", values.port)
    }

    @Test
    fun ftp_host_maps_with_protocol_passthrough() {
        val values = ServerConfigDialogHelper.formValuesFor(
            DiscoveredHost(protocol = "ftp", displayName = "Router", host = "192.168.1.1", port = 21)
        )
        assertEquals("ftp", values.protocol)
        assertEquals("192.168.1.1", values.host)
        assertEquals("21", values.port)
    }

    @Test
    fun webdav_maps_to_http_on_plain_port() {
        val values = ServerConfigDialogHelper.formValuesFor(
            DiscoveredHost(protocol = "webdav", displayName = "Dav", host = "dav.local", port = 8080)
        )
        assertEquals("http", values.protocol)
        assertEquals("dav.local", values.host)
        assertEquals("8080", values.port)
    }

    @Test
    fun webdav_maps_to_https_on_443() {
        val values = ServerConfigDialogHelper.formValuesFor(
            DiscoveredHost(protocol = "webdav", displayName = "Dav", host = "dav.local", port = 443)
        )
        assertEquals("https", values.protocol)
        assertEquals("443", values.port)
    }

    @Test
    fun ipv6_literal_is_bracketed() {
        val values = ServerConfigDialogHelper.formValuesFor(
            DiscoveredHost(protocol = "smb", displayName = "NAS", host = "fe80::1", port = 445)
        )
        assertEquals("[fe80::1]", values.host)
    }

    @Test
    fun full_ipv6_literal_is_bracketed() {
        assertEquals(
            "[2001:db8::abcd:1234]",
            ServerConfigDialogHelper.bracketIpv6Literal("2001:db8::abcd:1234")
        )
    }

    @Test
    fun already_bracketed_ipv6_is_untouched() {
        assertEquals("[fe80::1]", ServerConfigDialogHelper.bracketIpv6Literal("[fe80::1]"))
    }

    @Test
    fun ipv4_and_hostname_are_untouched() {
        assertEquals("192.168.1.10", ServerConfigDialogHelper.bracketIpv6Literal("192.168.1.10"))
        assertEquals("nas.local", ServerConfigDialogHelper.bracketIpv6Literal("nas.local"))
    }

    @Test
    fun port_is_stringified() {
        val values = ServerConfigDialogHelper.formValuesFor(
            DiscoveredHost(protocol = "ftp", displayName = "F", host = "10.0.0.2", port = 2121)
        )
        assertEquals("2121", values.port)
    }
}
