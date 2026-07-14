package erl.webdavtoon

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedWaterfallIdentityTest {

    @Test
    fun `folder identity distinguishes WebDAV slots`() {
        val first = Folder(path = "Comics/HD", name = "HD", isLocal = false, sourceSlot = 1)
        val second = first.copy(sourceSlot = 2)

        assertNotEquals(
            MixedWaterfallIdentity.folderKey(first),
            MixedWaterfallIdentity.folderKey(second)
        )
    }

    @Test
    fun `folder and media identities have separate namespaces`() {
        val folderKey = MixedWaterfallIdentity.folderKey(Folder(path = "123", name = "123"))
        val mediaKey = MixedWaterfallIdentity.mediaKey("123")

        assertTrue(folderKey.startsWith("folder:"))
        assertTrue(mediaKey.startsWith("media:"))
        assertNotEquals(folderKey, mediaKey)
    }
}
