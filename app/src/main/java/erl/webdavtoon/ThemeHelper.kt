package erl.webdavtoon

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.google.android.material.color.DynamicColors
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
            // Get current dynamic colors
            val typedArray = context.obtainStyledAttributes(intArrayOf(
                com.google.android.material.R.attr.colorPrimary,
                com.google.android.material.R.attr.colorSecondary,
                com.google.android.material.R.attr.colorTertiary,
                com.google.android.material.R.attr.colorSurface
            ))
            val colors = ThemeColors(
                typedArray.getColor(0, 0),
                typedArray.getColor(1, 0),
                typedArray.getColor(2, 0),
                typedArray.getColor(3, 0)
            )
            typedArray.recycle()
            return colors
        }

        val themeRes = themeMap[themeId] ?: R.style.Theme_WebDAVToon
        val typedArray = context.obtainStyledAttributes(themeRes, intArrayOf(
            com.google.android.material.R.attr.colorPrimary,
            com.google.android.material.R.attr.colorSecondary,
            com.google.android.material.R.attr.colorTertiary,
            com.google.android.material.R.attr.colorSurface
        ))
        val colors = ThemeColors(
            typedArray.getColor(0, 0),
            typedArray.getColor(1, 0),
            typedArray.getColor(2, 0),
            typedArray.getColor(3, 0)
        )
        typedArray.recycle()
        return colors
    }
}
