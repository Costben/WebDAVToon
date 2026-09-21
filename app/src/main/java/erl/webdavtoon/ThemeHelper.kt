package erl.webdavtoon

import android.app.Activity
import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * View-layer bootstrap: locale application plus the built-in theme seed colors.
 *
 * There is no Material Design here any more. Colors are read straight from
 * resources (no `MaterialColors` attribute lookup) and the Compose layer gets
 * Monet from COUI, not from `com.google.android.material.color.DynamicColors`.
 */
object ThemeHelper {
    const val THEME_FOLLOW_DEVICE = -1

    /** Seed color resource per built-in theme id. */
    private val themePrimaryRes = mapOf(
        0 to R.color.primary,           // Lavender (Default)
        1 to R.color.primary_blue,      // Blue
        2 to R.color.primary_green,     // Green
        3 to R.color.primary_red,       // Red
        4 to R.color.primary_orange,    // Orange
        5 to R.color.primary_teal,      // Teal
        6 to R.color.primary_purple,    // Purple
        7 to R.color.primary_pink,      // Pink
        8 to R.color.primary_brown,     // Brown
        9 to R.color.primary_grey,      // Grey
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

    /** True when the platform can supply a Monet wallpaper palette (Android 12+). */
    val isMonetAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Applies the process-wide locale. The window background comes from the single
     * non-Material theme declared in `values/themes.xml`; per-theme colors are applied by
     * the Compose layer, so there is no `setTheme` per theme id any more.
     */
    fun applyTheme(activity: Activity) {
        val lang = SettingsManager(activity).getLanguage()
        if (lang == "default") return

        val locale = if (lang == "zh") Locale.CHINESE else Locale.ENGLISH
        Locale.setDefault(locale)

        val resources = activity.resources
        val configuration = resources.configuration
        val displayMetrics = resources.displayMetrics
        configuration.setLocale(locale)
        resources.updateConfiguration(configuration, displayMetrics)
    }

    fun getThemeNames(): Array<String> = themeNames.toTypedArray()

    fun getThemeName(context: Context, id: Int): String {
        if (id == THEME_FOLLOW_DEVICE) return context.getString(R.string.theme_follow_device)

        val resId = when (id) {
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

    /** Seed color for a built-in theme, resolved directly from resources. */
    fun themeSeedColor(context: Context, themeId: Int): Int =
        context.getColor(themePrimaryRes[themeId] ?: R.color.primary)
}
