// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterfallThumbnailSizeResolverTest {

    @Test
    fun `default percent resolves to displayed card size`() {
        val target = WaterfallThumbnailSizeResolver.resolve(
            displayWidth = 528,
            displayHeight = 698,
            qualityMode = SettingsManager.WATERFALL_MODE_PERCENT,
            percent = 70,
            maxWidth = 600,
            maxTargetPixels = 900_000
        )

        assertEquals(WaterfallThumbnailTargetSize(528, 698), target)
    }

    @Test
    fun `higher percent can request larger than displayed card size`() {
        val target = WaterfallThumbnailSizeResolver.resolve(
            displayWidth = 528,
            displayHeight = 698,
            qualityMode = SettingsManager.WATERFALL_MODE_PERCENT,
            percent = 100,
            maxWidth = 600,
            maxTargetPixels = 900_000
        )

        assertEquals(WaterfallThumbnailTargetSize(754, 997), target)
    }

    @Test
    fun `pixel cap never reduces below displayed card size`() {
        val target = WaterfallThumbnailSizeResolver.resolve(
            displayWidth = 1080,
            displayHeight = 1440,
            qualityMode = SettingsManager.WATERFALL_MODE_PERCENT,
            percent = 100,
            maxWidth = 600,
            maxTargetPixels = 900_000
        )

        assertEquals(WaterfallThumbnailTargetSize(1080, 1440), target)
    }

    @Test
    fun `max width mode keeps smaller displayed card size unchanged`() {
        val target = WaterfallThumbnailSizeResolver.resolve(
            displayWidth = 528,
            displayHeight = 698,
            qualityMode = SettingsManager.WATERFALL_MODE_MAX_WIDTH,
            percent = 70,
            maxWidth = 600,
            maxTargetPixels = 900_000
        )

        assertEquals(WaterfallThumbnailTargetSize(528, 698), target)
    }

    @Test
    fun `max width mode scales down wide displayed cards`() {
        val target = WaterfallThumbnailSizeResolver.resolve(
            displayWidth = 1080,
            displayHeight = 1440,
            qualityMode = SettingsManager.WATERFALL_MODE_MAX_WIDTH,
            percent = 70,
            maxWidth = 600,
            maxTargetPixels = 900_000
        )

        assertEquals(WaterfallThumbnailTargetSize(1080, 1440), target)
    }

    @Test
    fun `bucket width rounds requests from one pinch onto shared sizes`() {
        val rawWidths = listOf(171, 177, 194, 209, 137, 228, 360, 504)

        val bucketed = rawWidths.map { WaterfallThumbnailSizeResolver.bucketWidth(it) }

        assertEquals(listOf(192, 192, 256, 256, 192, 256, 384, 512), bucketed)
        assertEquals(4, bucketed.toSet().size)
    }

    @Test
    fun `bucket width keeps exact multiples and never returns less than requested`() {
        assertEquals(64, WaterfallThumbnailSizeResolver.bucketWidth(64))
        assertEquals(64, WaterfallThumbnailSizeResolver.bucketWidth(1))
        assertEquals(128, WaterfallThumbnailSizeResolver.bucketWidth(65))
        assertEquals(512, WaterfallThumbnailSizeResolver.bucketWidth(512))
        assertEquals(576, WaterfallThumbnailSizeResolver.bucketWidth(513))

        val rawWidths = listOf(1, 7, 63, 64, 65, 171, 194, 360, 504)
        rawWidths.forEach { raw ->
            val bucketed = WaterfallThumbnailSizeResolver.bucketWidth(raw)
            assertTrue("$raw -> $bucketed", bucketed >= raw)
            assertEquals(0, bucketed % WaterfallThumbnailSizeResolver.TARGET_WIDTH_BUCKET)
        }
    }
}
