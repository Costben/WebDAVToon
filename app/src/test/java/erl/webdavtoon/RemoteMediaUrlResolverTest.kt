package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMediaUrlResolverTest {

    @Test
    fun scheme_classification() {
        assertTrue(RemoteMediaUrlResolver.isProxyScheme("smb://nas/share/a.jpg"))
        assertTrue(RemoteMediaUrlResolver.isProxyScheme("FTP://host/a.jpg"))
        assertFalse(RemoteMediaUrlResolver.isProxyScheme("https://host/a.jpg"))

        assertTrue(RemoteMediaUrlResolver.isRemoteMediaUri("http://host/a.jpg"))
        assertTrue(RemoteMediaUrlResolver.isRemoteMediaUri("smb://nas/share/a.jpg"))
        assertFalse(RemoteMediaUrlResolver.isRemoteMediaUri("content://media/1"))
        assertFalse(RemoteMediaUrlResolver.isRemoteMediaUri("/storage/emulated/0/a.jpg"))

        assertTrue(RemoteMediaUrlResolver.needsBasicAuth("https://host/a.jpg"))
        assertFalse(RemoteMediaUrlResolver.needsBasicAuth("smb://nas/share/a.jpg"))
        assertFalse(RemoteMediaUrlResolver.needsBasicAuth("ftp://host/a.jpg"))
    }

    @Test
    fun compute_rel_path_strips_current_slot_prefix() {
        assertEquals(
            "comics/ch01/p1.jpg",
            RemoteMediaUrlResolver.computeRelPath(
                "smb://nas.lan/media",
                "smb://nas.lan/media/comics/ch01/p1.jpg"
            )
        )
        assertEquals(
            "a.png",
            RemoteMediaUrlResolver.computeRelPath(
                "ftp://192.168.31.100:2121/pub/",
                "ftp://192.168.31.100:2121/pub/a.png"
            )
        )
    }

    @Test
    fun compute_rel_path_rejects_other_slots_and_empty_paths() {
        // Favorite minted by a different slot
        assertNull(
            RemoteMediaUrlResolver.computeRelPath(
                "smb://nas.lan/media",
                "smb://other-nas/media/comics/p1.jpg"
            )
        )
        // Degenerate: URI equals the base
        assertNull(
            RemoteMediaUrlResolver.computeRelPath(
                "smb://nas.lan/media",
                "smb://nas.lan/media/"
            )
        )
        assertNull(RemoteMediaUrlResolver.computeRelPath("", "smb://nas.lan/media/a.jpg"))
    }

    @Test
    fun encode_rel_path_escapes_segments_but_keeps_separators() {
        assertEquals(
            "comics/ch%2001/p%231.jpg",
            RemoteMediaUrlResolver.encodeRelPath("comics/ch 01/p#1.jpg")
        )
        assertEquals(
            "a%2Bb/c.jpg",
            RemoteMediaUrlResolver.encodeRelPath("a+b/c.jpg")
        )
    }
}
