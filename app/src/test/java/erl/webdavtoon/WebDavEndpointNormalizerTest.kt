package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavEndpointNormalizerTest {
    @Test
    fun normalize_omits_default_https_port() {
        val endpoint = WebDavEndpointNormalizer.normalize(
            protocol = "https",
            rawUrl = "example.com/library",
            port = 443
        )

        assertEquals("https://example.com/library", endpoint)
    }

    @Test
    fun normalize_preserves_non_default_port() {
        val endpoint = WebDavEndpointNormalizer.normalize(
            protocol = "https",
            rawUrl = "example.com/library/",
            port = 8443
        )

        assertEquals("https://example.com:8443/library", endpoint)
    }

    @Test
    fun normalize_prefers_port_embedded_in_host() {
        val endpoint = WebDavEndpointNormalizer.normalize(
            protocol = "http",
            rawUrl = "http://example.com:8080/root/path",
            port = 80
        )

        assertEquals("http://example.com:8080/root/path", endpoint)
    }

    @Test
    fun normalize_smb_hides_default_port_and_keeps_share_path() {
        val endpoint = WebDavEndpointNormalizer.normalize(
            protocol = "smb",
            rawUrl = "smb://nas.lan/media/comics/",
            port = 445
        )

        assertEquals("smb://nas.lan/media/comics", endpoint)
    }

    @Test
    fun normalize_smb_preserves_custom_port() {
        val endpoint = WebDavEndpointNormalizer.normalize(
            protocol = "smb",
            rawUrl = "nas.lan/media",
            port = 1445
        )

        assertEquals("smb://nas.lan:1445/media", endpoint)
    }

    @Test
    fun normalize_ftp_hides_default_port_and_prefers_embedded_port() {
        assertEquals(
            "ftp://192.168.31.100/pub",
            WebDavEndpointNormalizer.normalize("ftp", "ftp://192.168.31.100/pub", 21)
        )
        assertEquals(
            "ftp://192.168.31.100:2121/pub",
            WebDavEndpointNormalizer.normalize("ftp", "192.168.31.100:2121/pub", 21)
        )
    }

    @Test
    fun normalize_http_still_hides_both_legacy_default_ports() {
        // Historical behavior: 80 and 443 are both hidden regardless of the
        // http/https scheme. Existing slots depend on this staying stable.
        assertEquals(
            "http://example.com/dav",
            WebDavEndpointNormalizer.normalize("http", "example.com/dav", 443)
        )
    }

    @Test
    fun default_port_covers_all_protocols() {
        assertEquals(80, WebDavEndpointNormalizer.defaultPortFor("http"))
        assertEquals(443, WebDavEndpointNormalizer.defaultPortFor("https"))
        assertEquals(445, WebDavEndpointNormalizer.defaultPortFor("smb"))
        assertEquals(21, WebDavEndpointNormalizer.defaultPortFor("ftp"))
    }
}
