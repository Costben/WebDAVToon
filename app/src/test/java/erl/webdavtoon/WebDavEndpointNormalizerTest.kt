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
}
