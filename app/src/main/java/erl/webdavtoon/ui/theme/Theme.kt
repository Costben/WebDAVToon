package erl.webdavtoon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import erl.webdavtoon.ui.LocalUiMode
import erl.webdavtoon.ui.UiMode

val LocalDarkTheme = compositionLocalOf { false }

/**
 * WebDAVToon 统一顶层双主题入口
 *
 * @param uiMode 显式指定的 UI 风格模式；为 null 时优先使用 [LocalUiMode.current]
 * @param darkTheme 是否深色模式，默认跟随系统
 * @param dynamicColor 是否启用动态取色（Monet 取色，默认 true）
 * @param seedColor 自定义种子颜色（用于不支持或未启用动态取色时的调色板生成）
 * @param content 内容
 */
@Composable
fun WebDAVToonTheme(
    uiMode: UiMode? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val currentUiMode = uiMode ?: LocalUiMode.current
    val effectiveSeed = seedColor ?: DefaultThemeSeedColor

    CompositionLocalProvider(
        LocalUiMode provides currentUiMode,
        LocalDarkTheme provides darkTheme
    ) {
        when (currentUiMode) {
            UiMode.Miuix -> {
                MiuixTheme(
                    darkTheme = darkTheme,
                    dynamicColor = dynamicColor,
                    seedColor = seedColor,
                    content = content
                )
            }
            UiMode.Material -> {
                MaterialTheme(
                    darkTheme = darkTheme,
                    dynamicColor = dynamicColor,
                    seedColor = effectiveSeed,
                    content = content
                )
            }
        }
    }
}
