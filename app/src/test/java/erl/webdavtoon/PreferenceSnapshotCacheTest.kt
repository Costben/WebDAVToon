package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PreferenceSnapshotCacheTest {

    @Test
    fun read_returnsDefaultForMissingKey() {
        val cache = PreferenceSnapshotCache()

        assertEquals(-1, cache.read("missing", -1))
    }

    @Test
    fun write_andRemove_keepOtherEntries() {
        val cache = PreferenceSnapshotCache()
        cache.replace(mapOf("a" to 1, "b" to 2))

        cache.write("c", 3)
        assertEquals(1, cache.read("a", -1))
        assertEquals(2, cache.read("b", -1))
        assertEquals(3, cache.read("c", -1))

        cache.remove("b")
        assertEquals(1, cache.read("a", -1))
        assertEquals(-1, cache.read("b", -1))
        assertEquals(3, cache.read("c", -1))
    }

    /**
     * The regression this guards: an in-place `clear()` + repopulate lets a reader observe an
     * empty/partial map and fall back to defaults. With a snapshot swap every read must observe
     * either the previous or the next complete snapshot.
     */
    @Test
    fun reads_neverObserveAPartialSnapshotDuringReplace() {
        val cache = PreferenceSnapshotCache()
        val keys = (0 until 64).map { "key_$it" }
        fun completeSnapshot(): Map<String, Any> = keys.associateWith { 1 }

        cache.replace(completeSnapshot())

        val stop = AtomicBoolean(false)
        val failures = AtomicInteger(0)
        val started = CountDownLatch(1)

        val reader = Thread {
            started.countDown()
            while (!stop.get()) {
                for (key in keys) {
                    if (cache.read(key, -1) != 1) {
                        failures.incrementAndGet()
                    }
                }
            }
        }
        reader.start()
        started.await()

        repeat(200_000) {
            cache.replace(completeSnapshot())
        }

        stop.set(true)
        reader.join()

        assertEquals(0, failures.get())
    }
}
