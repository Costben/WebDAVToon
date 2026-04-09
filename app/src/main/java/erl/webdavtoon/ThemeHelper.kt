package erl.webdavtoon

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import java.util.Locale

object ThemeHelper {
    const val THEME_FOLLOW_DEVICE = -1

    private val themeMap = mapOf(
        0 to R.style.Theme_WebDAVToon,          // Lavender (Default)
        1 to R.style.Theme_WebDAVToon_Blue,     // Blue
        2 to R.style.Theme_WebDAVToon_Green,    // Green
        3 to R.style.Theme_WebDAVToon_Red,      // Red
        4 to R.style.Theme_WebDAVToon_Orange,   // Orange
        5 to R.style.Theme_WebDAVToon_Teal,     // Teal
        6 to R.style.Theme_WebDAVToon_Purple,   // Purple
        7 to R.style.Theme_WebDAVToon_Pink,     // Pink
        8 to R.style.Theme_WebDAVToon_Brown,    // Brown
        9 to R.style.Theme_WebDAVToon_Grey      // Grey
    )

    private val themeNames = listOf(
        "Lavender (Default)",
        "Midnight Blue",
        "Forest Green",
        "Crimson Red",
        "Sunset Orange",
        "Ocean Teal",
        "Deep Purple",
        "Rose Pink",
        "Coffee Brown",
        "Neutral Grey"
    )

    fun applyTheme(activity: Activity) {
        val settingsManager = SettingsManager(activity)
        
        // Apply Language
        val lang = settingsManager.getLanguage()
        if (lang != "default") {
            val locale = if (lang == "zh") Locale.CHINESE else Locale.ENGLISH
            Locale.setDefault(locale)
            
            val resources = activity.resources
            val configuration = resources.configuration
            val displayMetrics = resources.displayMetrics
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                configuration.setLocale(locale)
            } else {
                configuration.locale = locale
            }
            
            resources.updateConfiguration(configuration, displayMetrics)
        }

        val themeId = settingsManager.getThemeId()
        
        if (themeId == THEME_FOLLOW_DEVICE && DynamicColors.isDynamicColorAvailable()) {
            DynamicColors.applyToActivityIfAvailable(activity)
        } else {
            val themeRes = themeMap[themeId] ?: R.style.Theme_WebDAVToon
            activity.setTheme(themeRes)
        }
    }

    fun getThemeNames(): Array<String> = themeNames.toTypedArray()

    fun getThemeName(context: Context, id: Int): String {
        if (id == THEME_FOLLOW_DEVICE) return context.getString(R.string.theme_follow_device)
        
        val resId = when(id) {
            0 -> R.string.theme_midnight_blue // Default
            1 -> R.string.theme_midnight_blue
            2 -> R.string.theme_forest_green
            3 -> R.string.theme_crimson_red
            4 -> R.string.theme_sunset_orange
            5 -> R.string.theme_ocean_teal
            6 -> R.string.theme_deep_purple
            7 -> R.string.theme_rose_pink
            8 -> R.string.theme_coffee_brown
            9 -> R.string.theme_neutral_grey
            else -> R.string.theme_unknown
        }
        return context.getString(resId)
    }

    data class ThemeColors(
        val primary: Int,
        val secondary: Int,
        val tertiary: Int,
        val surface: Int
    )

    fun getThemeColors(context: Context, themeId: Int): ThemeColors {
        if (themeId == THEME_FOLLOW_DEVICE && DynamicColors.isDynamicColorAvailable()) {
            return resolveThemeColors(context)
        }

        val themeRes = themeMap[themeId] ?: R.style.Theme_WebDAVToon
        return resolveThemeColors(ContextThemeWrapper(context, themeRes))
    }

    private fun resolveThemeColors(context: Context): ThemeColors {
        return ThemeColors(
            primary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0),
            secondary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondary, 0),
            tertiary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiary, 0),
            surface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, 0)
        )
    }
}
