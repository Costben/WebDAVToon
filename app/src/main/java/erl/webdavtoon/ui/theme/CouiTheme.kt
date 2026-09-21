package erl.webdavtoon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.squircle.LocalSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeColorSpec
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.ThemePaletteStyle

/** Fallback seed used when the user has not picked a built-in theme and dynamic color is off. */
val DefaultThemeSeedColor = Color(0xFF6750A4)

/**
 * COUI 主题实现
 *
 * @param darkTheme 是否深色模式，null 则跟随系统
 * @param dynamicColor 是否开启 Monet 动态取色
 * @param seedColor 自定义种子颜色
 * @param squircleEnabled 是否启用 Squircle 平滑圆角（默认开启）
 * @param content 内容
 */
@Composable
fun CouiTheme(
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
        COUITheme(
            controller = controller,
            content = content
        )
    }
}

