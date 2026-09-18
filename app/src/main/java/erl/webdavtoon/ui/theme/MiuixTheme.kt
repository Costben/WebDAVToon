package erl.webdavtoon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * Miuix 主题实现
 *
 * @param darkTheme 是否深色模式，null 则跟随系统
 * @param dynamicColor 是否开启 Monet 动态取色
 * @param seedColor 自定义种子颜色
 * @param squircleEnabled 是否启用 Squircle 平滑圆角（默认开启）
 * @param content 内容
 */
@Composable
fun MiuixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    squircleEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorSchemeMode = when {
        dynamicColor -> if (darkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight
        seedColor != null -> if (darkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight
        else -> if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light
    }

    val controller = remember(darkTheme, dynamicColor, seedColor) {
        ThemeController(
            colorSchemeMode = colorSchemeMode,
            keyColor = seedColor,
            colorSpec = ThemeColorSpec.Spec2021,
            paletteStyle = ThemePaletteStyle.TonalSpot,
            isDark = darkTheme
        )
    }

    CompositionLocalProvider(
        LocalSquircleEnabled provides squircleEnabled
    ) {
        MiuixTheme(
            controller = controller,
            content = content
        )
    }
}

