package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoThumbnailHeuristicsTest {

    @Test
    fun candidateFrameTimes_prioritizeLaterFramesWithoutDuplicates() {
        assertEquals(
            listOf(0L, 1_000_000L, 3_000_000L, 5_000_000L, 8_000_000L, 12_000_000L),
            VideoThumbnailHeuristics.candidateFrameTimesUs(0L)
        )

        assertEquals(
            listOf(1_000_000L, 3_000_000L, 5_000_000L, 8_000_000L, 12_000_000L, 0L),
            VideoThumbnailHeuristics.candidateFrameTimesUs(1_000_000L)
        )
    }

    @Test
    fun candidateFrameTimes_withLongDurationIncludeLateFallbackChecks() {
        assertEquals(
            listOf(
                0L,
                1_000_000L,
                3_000_000L,
                5_000_000L,
                8_000_000L,
                12_000_000L,
                20_000_000L,
                30_000_000L,
                45_000_000L,
                60_000_000L,
                90_000_000L,
                120_000_000L,
                129_999_000L
            ),
            VideoThumbnailHeuristics.candidateFrameTimesUs(
                requestedTimeUs = 0L,
                durationMs = 130_000L
            )
        )
    }

    @Test
    fun candidateFrameTimes_aviAlwaysUsesFirstFrameOnly() {
        assertEquals(
            listOf(0L),
            VideoThumbnailHeuristics.candidateFrameTimesUs(
                requestedTimeUs = 1_000_000L,
                durationMs = 200_000L,
                sourceExtension = "avi",
                isFolderPreview = false
            )
        )
        assertEquals(
            listOf(0L),
            VideoThumbnailHeuristics.candidateFrameTimesUs(
                requestedTimeUs = 0L,
                durationMs = 200_000L,
                sourceExtension = "avi",
                isFolderPreview = true
            )
        )
    }

    @Test
    fun shouldPreferSyncFrameSearch_onlyForNormalAviFrames() {
        assertTrue(
            VideoThumbnailHeuristics.shouldPreferSyncFrameSearch(
                sourceExtension = "avi",
                isFolderPreview = false
            )
        )
        assertTrue(
            VideoThumbnailHeuristics.shouldPreferSyncFrameSearch(
                sourceExtension = "avi",
                isFolderPreview = true
            )
        )
        assertFalse(
            VideoThumbnailHeuristics.shouldPreferSyncFrameSearch(
                sourceExtension = "mp4",
                isFolderPreview = false
            )
        )
    }

    @Test
    fun mostlyBlackSamples_areTreatedAsBlank() {
        val blackSamples = IntArray(36) { 0xFF000000.toInt() }

        assertTrue(VideoThumbnailHeuristics.isLikelyBlankFrame(blackSamples))
    }

    @Test
    fun mostlyMonochromeWarningSamples_areTreatedAsBlank() {
        val warningLikeSamples = IntArray(36) { index ->
            when {
                index < 24 -> 0xFFF8F8F8.toInt()
                index < 34 -> 0xFF101010.toInt()
                else -> 0xFFB00000.toInt()
            }
        }

        assertTrue(VideoThumbnailHeuristics.isLikelyBlankFrame(warningLikeSamples))
    }

    @Test
    fun edgeDominatedDarkSplitSamples_areTreatedAsBlank() {
        val splitSamples = IntArray(36) { index ->
            val column = index % 6
            if (column >= 3) {
                0xFF000000.toInt()
            } else {
                0xFF908A84.toInt()
            }
        }

        assertTrue(VideoThumbnailHeuristics.isLikelyBlankFrame(splitSamples))
    }

    @Test
    fun variedColorSamples_areNotTreatedAsBlank() {
        val colorfulSamples = intArrayOf(
            0xFF102030.toInt(), 0xFFAA3300.toInt(), 0xFF11AA55.toInt(), 0xFF2244CC.toInt(),
            0xFFEECC44.toInt(), 0xFF8822AA.toInt(), 0xFF447799.toInt(), 0xFFCC6677.toInt(),
            0xFF223344.toInt(), 0xFFAA8844.toInt(), 0xFF2288AA.toInt(), 0xFF44CC88.toInt()
        )

        assertFalse(VideoThumbnailHeuristics.isLikelyBlankFrame(colorfulSamples))
    }
}
