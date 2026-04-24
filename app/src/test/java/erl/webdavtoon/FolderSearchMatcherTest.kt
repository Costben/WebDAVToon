package erl.webdavtoon

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderSearchMatcherTest {

    @Test
    fun matches_existing_case_insensitive_name_search() {
        assertTrue(FolderSearchMatcher.matches("ComfyOUT", "out"))
        assertTrue(FolderSearchMatcher.matches("ComfyOUT", "COMFY"))
        assertFalse(FolderSearchMatcher.matches("ComfyOUT", "video"))
    }

    @Test
    fun matches_chinese_keyword_directly() {
        assertTrue(FolderSearchMatcher.matches("汉字文件夹", "汉字"))
        assertTrue(FolderSearchMatcher.matches("漫画", "画"))
    }

    @Test
    fun matches_chinese_name_by_full_pinyin() {
        assertTrue(FolderSearchMatcher.matches("漫画", "manhua"))
        assertTrue(FolderSearchMatcher.matches("汉字文件夹", "hanzi"))
        assertTrue(FolderSearchMatcher.matches("汉字文件夹", "wenjian"))
    }

    @Test
    fun matches_chinese_name_by_initials() {
        assertTrue(FolderSearchMatcher.matches("漫画", "mh"))
        assertTrue(FolderSearchMatcher.matches("汉字文件夹", "hzwjj"))
    }

    @Test
    fun matches_mixed_chinese_ascii_and_digits() {
        assertTrue(FolderSearchMatcher.matches("漫画2024", "manhua2024"))
        assertTrue(FolderSearchMatcher.matches("测试ABC", "ceshiabc"))
        assertTrue(FolderSearchMatcher.matches("测试ABC", "csabc"))
    }

    @Test
    fun blank_keyword_matches_all() {
        assertTrue(FolderSearchMatcher.matches("漫画", ""))
        assertTrue(FolderSearchMatcher.matches("漫画", "   "))
    }

    @Test
    fun unrelated_pinyin_does_not_match() {
        assertFalse(FolderSearchMatcher.matches("漫画", "xiaoshuo"))
        assertFalse(FolderSearchMatcher.matches("汉字文件夹", "mh"))
    }
}
