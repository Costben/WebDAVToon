package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAspectRatioResolverTest {

    @Test
    fun falls_back_to_default_ratio_when_dimensions_are_unknown() {
        assertEquals(1f, PhotoAspectRatioResolver.resolve(width = 0, height = 0), 0.0001f)
    }

    @Test
    fun computes_real_aspect_ratio_from_resolved_dimensions() {
        assertEquals(1.5f, PhotoAspectRatioResolver.resolve(width = 1200, height = 800), 0.0001f)
    }

    @Test
    fun clamps_extreme_aspect_ratios() {
        assertEquals(5f, PhotoAspectRatioResolver.resolve(width = 10000, height = 100), 0.0001f)
    }
}
