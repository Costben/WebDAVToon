package erl.webdavtoon.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

val DefaultThemeSeedColor = Color(0xFF6750A4)

/**
 * Material Expressive / Material 3 主题实现
 *
 * @param darkTheme 是否深色模式
 * @param dynamicColor 是否开启系统动态取色（Monet，Android 12+）
 * @param seedColor 自定义种子颜色（dynamicColor 关闭或不可用时使用）
 * @param content 内容
 */
@Composable
fun MaterialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color = DefaultThemeSeedColor,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    if (dynamicColor && supportsDynamic) {
        val colorScheme = if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    } else {
        DynamicMaterialTheme(
            seedColor = seedColor,
            isDark = darkTheme,
            // Match Miuix's palette: Expressive rotates the seed hue and makes the drawer disagree.
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2021,
            content = content
        )
    }
}
