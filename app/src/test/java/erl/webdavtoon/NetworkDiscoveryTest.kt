package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkDiscoveryTest {

    @Test
    fun serviceType_mapping_and_defaultPort_fallbacks_are_applied() {
        val smb = NsdServiceSnapshot(
            serviceName = "NAS",
            serviceType = "_smb._tcp.",
            host = "192.168.31.10",
            port = 0,
        )
        val ftp = NsdServiceSnapshot(
            serviceName = "FTP",
            serviceType = "_ftp._tcp",
            host = "192.168.31.11",
            port = 0,
        )
        val webdav = NsdServiceSnapshot(
            serviceName = "DAV",
            serviceType = "_webdav._tcp.",
            host = "192.168.31.12",
            port = 8080,
        )

        assertEquals("smb", protocolForServiceType(smb.serviceType))
        assertEquals("ftp", protocolForServiceType(ftp.serviceType))
        assertEquals("webdav", protocolForServiceType(webdav.serviceType))
        assertEquals(445, smb.toDiscoveredHost()?.port)
        assertEquals(21, ftp.toDiscoveredHost()?.port)
        assertEquals(8080, webdav.toDiscoveredHost()?.port)
        assertNull(
            NsdServiceSnapshot(
                serviceName = "DAV-missing-port",
                serviceType = "_webdav._tcp",
                host = "192.168.31.13",
                port = 0,
            ).toDiscoveredHost(),
        )
    }

    @Test
    fun dedup_by_protocol_host_port_prevents_duplicates_from_reannounce() {
        val state = DiscoveryState()
        val service = NsdServiceSnapshot(
            serviceName = "Synology-NAS",
            serviceType = "_smb._tcp",
            host = "192.168.31.20",
            port = 445,
        )

        state.upsert(service)
        state.upsert(service)

        assertEquals(
            listOf(
                DiscoveredHost(
                    protocol = "smb",
                    displayName = "Synology-NAS",
                    host = "192.168.31.20",
                    port = 445,
                ),
            ),
            state.snapshot(),
        )
    }

    @Test
    fun onServiceLost_removes_only_the_matching_entry() {
        val state = DiscoveryState()
        val smb = NsdServiceSnapshot(
            serviceName = "NAS",
            serviceType = "_smb._tcp",
            host = "192.168.31.21",
            port = 445,
        )
        val ftp = NsdServiceSnapshot(
            serviceName = "FTP",
            serviceType = "_ftp._tcp",
            host = "192.168.31.22",
            port = 21,
        )

        state.upsert(smb)
        state.upsert(ftp)
        state.remove(smb.copy(host = null, port = 0))

        assertEquals(
            listOf(
                DiscoveredHost(
                    protocol = "ftp",
                    displayName = "FTP",
                    host = "192.168.31.22",
                    port = 21,
                ),
            ),
            state.snapshot(),
        )
    }

    @Test
    fun ipv6_literal_passes_through_unchanged() {
        val service = NsdServiceSnapshot(
            serviceName = "NAS-v6",
            serviceType = "_smb._tcp",
            host = "fe80::1234:5678:9abc:def0",
            port = 445,
        )

        assertEquals("fe80::1234:5678:9abc:def0", service.toDiscoveredHost()?.host)
    }
}
