package erl.webdavtoon

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalVideoOpenerTest {

    @Test
    fun buildRemoteLaunchUrl_encodes_special_characters_and_preserves_auth() {
        val rawUrl =
            "https://example.com/Videos/PMV/b\u7ad9\u540c\u8863\u670d91v/XXR \u2764\u8981\u548c\u59d0\u59d0\u4e00\u8d77\u556a\u556a\u5417.mp4"

        val launchUrl = ExternalVideoOpener.buildRemoteLaunchUrl(
            mediaUri = rawUrl,
            username = "alice",
            password = "p%40ss word"
        )

        assertTrue(launchUrl.startsWith("https://alice:p%40ss%20word@example.com/"))
        assertTrue(launchUrl.contains("/b%E7%AB%99%E5%90%8C%E8%A1%A3%E6%9C%8D91v/"))
        assertTrue(
            launchUrl.contains(
                "/XXR%20%E2%9D%A4%E8%A6%81%E5%92%8C%E5%A7%90%E5%A7%90%E4%B8%80%E8%B5%B7%E5%95%AA%E5%95%AA%E5%90%97.mp4"
            )
        )
        assertFalse(launchUrl.contains(" "))
        assertFalse(launchUrl.contains("\u2764"))
    }

    @Test
    fun buildRemoteLaunchUrl_without_credentials_still_returns_encoded_url() {
        val rawUrl =
            "https://example.com/Videos/PMV/b\u7ad9\u540c\u8863\u670d91v/XXR \u2764\u8981\u548c\u59d0\u59d0\u4e00\u8d77\u556a\u556a\u5417.mp4"

        val launchUrl = ExternalVideoOpener.buildRemoteLaunchUrl(
            mediaUri = rawUrl,
            username = "",
            password = ""
        )

        assertTrue(launchUrl.startsWith("https://example.com/"))
        assertTrue(launchUrl.contains("%E2%9D%A4"))
        assertFalse(launchUrl.contains(" "))
    }
}
