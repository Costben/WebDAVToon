package erl.webdavtoon

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import java.util.Locale

object FolderSearchMatcher {
    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }
    private val aliasCache = linkedMapOf<String, FolderSearchAliases>()

    fun matches(folderName: String, rawKeyword: String): Boolean {
        val keyword = rawKeyword.trim().lowercase(Locale.ROOT)
        if (keyword.isEmpty()) return true
        if (folderName.lowercase(Locale.ROOT).contains(keyword)) return true

        val compactKeyword = keyword.filterNot(Char::isWhitespace)
        val aliases = aliasesFor(folderName)
        return aliases.joined.contains(compactKeyword) ||
            aliases.initials.contains(compactKeyword) ||
            aliases.tokens.any { it.contains(keyword) }
    }

    private fun aliasesFor(folderName: String): FolderSearchAliases {
        aliasCache[folderName]?.let { return it }

        val tokens = folderName.map { char -> pinyinToken(char) }
        val aliases = FolderSearchAliases(
            joined = tokens.joinToString(separator = "").lowercase(Locale.ROOT),
            tokens = listOf(tokens.joinToString(separator = " ").lowercase(Locale.ROOT)),
            initials = tokens.mapNotNull { it.firstOrNull() }.joinToString(separator = "").lowercase(Locale.ROOT)
        )
        aliasCache[folderName] = aliases
        if (aliasCache.size > 512) {
            aliasCache.remove(aliasCache.keys.first())
        }
        return aliases
    }

    private fun pinyinToken(char: Char): String {
        val pinyin = PinyinHelper.toHanyuPinyinStringArray(char, pinyinFormat)
        return pinyin?.firstOrNull() ?: char.toString().lowercase(Locale.ROOT)
    }
}

private data class FolderSearchAliases(
    val joined: String,
    val tokens: List<String>,
    val initials: String
)
