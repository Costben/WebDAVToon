package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Test

class WaterfallLayoutModeTest {

    @Test
    fun `default waterfall layout uses follow zoom mode`() {
        assertEquals(
            SettingsManager.WATERFALL_LAYOUT_FOLLOW_ZOOM,
            SettingsManager.normalizeWaterfallLayoutMode(null)
        )
    }

    @Test
    fun `legacy waterfall layout mode is accepted`() {
        assertEquals(
            SettingsManager.WATERFALL_LAYOUT_LEGACY,
            SettingsManager.normalizeWaterfallLayoutMode(SettingsManager.WATERFALL_LAYOUT_LEGACY)
        )
    }

    @Test
    fun `unknown waterfall layout mode falls back to follow zoom`() {
        assertEquals(
            SettingsManager.WATERFALL_LAYOUT_FOLLOW_ZOOM,
            SettingsManager.normalizeWaterfallLayoutMode("unknown")
        )
    }
}
