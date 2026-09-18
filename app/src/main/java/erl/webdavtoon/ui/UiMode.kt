package erl.webdavtoon.ui

import androidx.compose.runtime.compositionLocalOf

enum class UiMode(val code: Int, val value: String) {
    Miuix(0, "miuix"),
    Material(1, "material");

    companion object {
        fun fromCode(code: Int?): UiMode {
            return entries.firstOrNull { it.code == code } ?: Miuix
        }

        fun fromValue(value: String?): UiMode {
            if (value.isNullOrBlank()) return Miuix
            return entries.firstOrNull { it.value.equals(value.trim(), ignoreCase = true) } ?: Miuix
        }
    }
}

val LocalUiMode = compositionLocalOf<UiMode> { UiMode.Miuix }
