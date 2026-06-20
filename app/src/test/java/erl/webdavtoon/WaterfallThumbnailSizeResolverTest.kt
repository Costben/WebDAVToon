package erl.webdavtoon

import org.junit.Assert.assertEquals
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
}
