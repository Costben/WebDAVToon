package erl.webdavtoon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.DynamicColors
import erl.webdavtoon.ThemeHelper
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
 * @param themeId 设置中选中的主题 ID（THEME_FOLLOW_DEVICE=-1 为跟随系统动态取色，>=0 为内置主题色）
 * @param content 内容
 */
@Composable
fun WebDAVToonTheme(
    uiMode: UiMode? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    themeId: Int = ThemeHelper.THEME_FOLLOW_DEVICE,
    content: @Composable () -> Unit
) {
    val currentUiMode = uiMode ?: LocalUiMode.current
    val context = LocalContext.current

    val isDynamic = if (themeId == ThemeHelper.THEME_FOLLOW_DEVICE) {
        dynamicColor && DynamicColors.isDynamicColorAvailable()
    } else {
        false
    }

    val effectiveSeed = seedColor ?: if (themeId != ThemeHelper.THEME_FOLLOW_DEVICE) {
        remember(context, themeId) {
            Color(ThemeHelper.getThemeColors(context, themeId).primary)
        }
    } else {
        DefaultThemeSeedColor
    }

    CompositionLocalProvider(
        LocalUiMode provides currentUiMode,
        LocalDarkTheme provides darkTheme
    ) {
        when (currentUiMode) {
            UiMode.Miuix -> {
                MiuixTheme(
                    darkTheme = darkTheme,
                    dynamicColor = isDynamic,
                    seedColor = if (isDynamic) null else effectiveSeed,
                ) {
                    MaterialTheme(
                        darkTheme = darkTheme,
                        dynamicColor = isDynamic,
                        seedColor = effectiveSeed,
                        content = content
                    )
                }
            }
            UiMode.Material -> {
                MaterialTheme(
                    darkTheme = darkTheme,
                    dynamicColor = isDynamic,
                    seedColor = effectiveSeed,
                    content = content
                )
            }
        }
    }
}
