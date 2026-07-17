package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the pure SMB share-picker helpers in [ServerConfigDialogHelper]:
 * form-field extraction ([ServerConfigDialogHelper.smbEnumParams]), share
 * backfill ([ServerConfigDialogHelper.applySelectedShare]), and the
 * round-trip guarantee that a backfilled host survives
 * [WebDavEndpointNormalizer.normalize] with the selected share as the first
 * path segment (which is exactly what rust-core parse_smb_endpoint reads).
 */
class SmbShareEnumHelperTest {

    // --- smbEnumParams: host extraction ---

    @Test
    fun enumParams_bareHost() {
        val p = ServerConfigDialogHelper.smbEnumParams("nas.lan", "445", "user", "pw", "")
        assertEquals("nas.lan", p.host)
        assertEquals(445, p.port)
    }

    @Test
    fun enumParams_stripsTypedShareAndSubpath() {
        val p = ServerConfigDialogHelper.smbEnumParams("nas.lan/media/comics", "445", "u", "p", "")
        assertEquals("nas.lan", p.host)
    }

    @Test
    fun enumParams_stripsScheme() {
        val p = ServerConfigDialogHelper.smbEnumParams("smb://nas.lan/media", "445", "u", "p", "")
        assertEquals("nas.lan", p.host)
    }

    @Test
    fun enumParams_ipv6BracketWithShare() {
        val p = ServerConfigDialogHelper.smbEnumParams("[::1]/media", "445", "u", "p", "")
        assertEquals("::1", p.host)
        assertEquals(445, p.port)
    }

    @Test
    fun enumParams_ipv6BracketWithPort() {
        val p = ServerConfigDialogHelper.smbEnumParams("[fe80::1]:1445/media", "", "u", "p", "")
        assertEquals("fe80::1", p.host)
        assertEquals(1445, p.port)
    }

    @Test
    fun enumParams_hostEmbeddedPortWinsOverPortField() {
        val p = ServerConfigDialogHelper.smbEnumParams("nas.lan:1445/media", "445", "u", "p", "")
        assertEquals("nas.lan", p.host)
        assertEquals(1445, p.port)
    }

    // --- smbEnumParams: port fallback ---

    @Test
    fun enumParams_portFieldUsedWhenNoEmbeddedPort() {
        val p = ServerConfigDialogHelper.smbEnumParams("nas.lan", "1445", "u", "p", "")
        assertEquals(1445, p.port)
    }

    @Test
    fun enumParams_blankPortFallsBackTo445() {
        val p = ServerConfigDialogHelper.smbEnumParams("nas.lan", "", "u", "p", "")
        assertEquals(445, p.port)
    }

    @Test
    fun enumParams_invalidPortFallsBackTo445() {
        val p = ServerConfigDialogHelper.smbEnumParams("nas.lan", "abc", "u", "p", "")
        assertEquals(445, p.port)
        val outOfRange = ServerConfigDialogHelper.smbEnumParams("nas.lan", "70000", "u", "p", "")
        assertEquals(445, outOfRange.port)
    }

    // --- smbEnumParams: credentials ---

    @Test
    fun enumParams_blankDomainBecomesNull() {
        val p = ServerConfigDialogHelper.smbEnumParams("nas.lan", "445", "u", "p", "  ")
        assertEquals(null, p.domain)
        val q = ServerConfigDialogHelper.smbEnumParams("nas.lan", "445", "u", "p", "WORKGROUP")
        assertEquals("WORKGROUP", q.domain)
    }

    // --- applySelectedShare ---

    @Test
    fun applyShare_bareHost() {
        assertEquals("nas.lan/media", ServerConfigDialogHelper.applySelectedShare("nas.lan", "media"))
    }

    @Test
    fun applyShare_replacesShareAndSubpath() {
        assertEquals(
            "nas.lan/photos",
            ServerConfigDialogHelper.applySelectedShare("nas.lan/media/comics/ongoing", "photos")
        )
    }

    @Test
    fun applyShare_preservesBracketedIpv6() {
        assertEquals("[::1]/media", ServerConfigDialogHelper.applySelectedShare("[::1]/old", "media"))
        assertEquals(
            "[fe80::1]:1445/media",
            ServerConfigDialogHelper.applySelectedShare("[fe80::1]:1445/old/sub", "media")
        )
    }

    @Test
    fun applyShare_stripsSchemePrefix() {
        assertEquals(
            "nas.lan/media",
            ServerConfigDialogHelper.applySelectedShare("smb://nas.lan/old", "media")
        )
    }

    @Test
    fun applyShare_preservesEmbeddedPort() {
        assertEquals(
            "nas.lan:1445/media",
            ServerConfigDialogHelper.applySelectedShare("nas.lan:1445/old", "media")
        )
    }

    // --- Round-trip: applySelectedShare -> normalize -> share segment ---

    private fun shareSegmentOf(endpoint: String): String {
        // Mirrors rust-core parse_smb_endpoint: strip scheme, drop host part,
        // first path segment is the share.
        val rest = endpoint.removePrefix("smb://")
        val path = rest.substringAfter('/', "")
        return path.trim('/').substringBefore('/')
    }

    @Test
    fun roundTrip_defaultPort() {
        val host = ServerConfigDialogHelper.applySelectedShare("nas.lan", "media")
        val endpoint = WebDavEndpointNormalizer.normalize("smb", host, 445)
        assertEquals("smb://nas.lan/media", endpoint)
        assertEquals("media", shareSegmentOf(endpoint))
    }

    @Test
    fun roundTrip_customPort() {
        val host = ServerConfigDialogHelper.applySelectedShare("nas.lan:1445/old/sub", "photos")
        val endpoint = WebDavEndpointNormalizer.normalize("smb", host, 445)
        assertEquals("smb://nas.lan:1445/photos", endpoint)
        assertEquals("photos", shareSegmentOf(endpoint))
    }

    @Test
    fun roundTrip_replacesPreviousShare() {
        val host = ServerConfigDialogHelper.applySelectedShare("nas.lan/media/comics", "backup")
        val endpoint = WebDavEndpointNormalizer.normalize("smb", host, 445)
        assertEquals("smb://nas.lan/backup", endpoint)
        assertEquals("backup", shareSegmentOf(endpoint))
    }

    @Test
    fun roundTrip_shareNameWithSpace() {
        val host = ServerConfigDialogHelper.applySelectedShare("nas.lan", "Time Machine")
        val endpoint = WebDavEndpointNormalizer.normalize("smb", host, 445)
        assertEquals("smb://nas.lan/Time Machine", endpoint)
        assertEquals("Time Machine", shareSegmentOf(endpoint))
    }
}
