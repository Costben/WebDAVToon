package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaTypeUtilsTest {

    @Test
    fun detectMediaTypeByName_supports_images_and_videos() {
        assertEquals(MediaType.IMAGE, detectMediaTypeByName("cover.JPG"))
        assertEquals(MediaType.VIDEO, detectMediaTypeByName("clip.Mp4"))
        assertEquals(MediaType.VIDEO, detectMediaTypeByName("movie.mkv"))
        assertEquals("video/x-msvideo", detectVideoMimeType("legacy.AVI"))
        assertNull(detectMediaTypeByName("notes.txt"))
    }

    @Test
    fun formatVideoDuration_formats_expected_text() {
        assertEquals("", formatVideoDuration(null))
        assertEquals("", formatVideoDuration(0))
        assertEquals("01:05", formatVideoDuration(65_000))
        assertEquals("1:01:05", formatVideoDuration(3_665_000))
    }
}
