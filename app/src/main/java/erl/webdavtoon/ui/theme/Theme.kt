// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import erl.webdavtoon.ThemeHelper

val LocalDarkTheme = compositionLocalOf { false }

/**
 * WebDAVToon 顶层主题入口（COUI only）
 *
 * @param darkTheme 是否深色模式，默认跟随系统
 * @param dynamicColor 是否启用动态取色（Monet 取色，默认 true）
 * @param seedColor 自定义种子颜色（用于不支持或未启用动态取色时的调色板生成）
 * @param themeId 设置中选中的主题 ID（THEME_FOLLOW_DEVICE=-1 为跟随系统动态取色，>=0 为内置主题色）
 * @param useCouiDefaultColors 为 true 时不应用任何取色（Monet 壁纸取色与内置主题调色板
 *   全部忽略），直接使用 COUI 自带的默认配色体系（`ColorSchemeMode.Light/Dark`）
 * @param content 内容
 */
@Composable
fun WebDAVToonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    themeId: Int = ThemeHelper.THEME_FOLLOW_DEVICE,
    useCouiDefaultColors: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // "Use COUI default colors" short-circuits the whole color pipeline: no Monet wallpaper
    // extraction and no built-in theme palette. With dynamicColor=false and seedColor=null,
    // CouiTheme falls through to ColorSchemeMode.Light/Dark - COUI's own scheme.
    val isDynamic = !useCouiDefaultColors &&
        themeId == ThemeHelper.THEME_FOLLOW_DEVICE &&
        dynamicColor &&
        ThemeHelper.isMonetAvailable

    val effectiveSeed = if (useCouiDefaultColors) {
        null
    } else {
        seedColor ?: if (themeId != ThemeHelper.THEME_FOLLOW_DEVICE) {
            remember(context, themeId) {
                Color(ThemeHelper.themeSeedColor(context, themeId))
            }
        } else {
            DefaultThemeSeedColor
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        CouiTheme(
            darkTheme = darkTheme,
            dynamicColor = isDynamic,
            seedColor = if (isDynamic) null else effectiveSeed,
            content = content,
        )
    }
}
