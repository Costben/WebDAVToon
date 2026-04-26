package erl.webdavtoon

import com.google.gson.annotations.SerializedName

data class ReaderGestureControlConfig(
    @SerializedName("version") val version: Int = CURRENT_VERSION,
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("zones") val zones: List<GestureZoneConfig> = defaultZones()
) {
    companion object {
        const val CURRENT_VERSION = 1

        fun defaultConfig(): ReaderGestureControlConfig = ReaderGestureControlConfig()

        fun defaultZones(): List<GestureZoneConfig> = GestureZone.entries.map { zone ->
            GestureZoneConfig(zone = zone.code)
        }
    }
}

data class GestureZoneConfig(
    @SerializedName("zone") val zone: String,
    @SerializedName("singleTapAction") val singleTapAction: String = GestureAction.NONE.code,
    @SerializedName("doubleTapAction") val doubleTapAction: String = GestureAction.NONE.code,
    @SerializedName("longPressAction") val longPressAction: String = GestureAction.NONE.code
)

enum class GestureType(val code: String) {
    SINGLE_TAP("single_tap"),
    DOUBLE_TAP("double_tap"),
    LONG_PRESS("long_press")
}

enum class GestureAction(val code: String) {
    NONE("none"),
    PHOTO_INFO("photo_info"),
    START_SLIDESHOW("start_slideshow"),
    PREVIOUS_PAGE("previous_page"),
    NEXT_PAGE("next_page");

    companion object {
        fun fromCode(code: String): GestureAction = entries.firstOrNull { it.code == code } ?: NONE
    }
}

enum class GestureZone(val code: String) {
    TOP_LEFT("top_left"),
    TOP_CENTER("top_center"),
    TOP_RIGHT("top_right"),
    CENTER_LEFT("center_left"),
    CENTER("center"),
    CENTER_RIGHT("center_right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_CENTER("bottom_center"),
    BOTTOM_RIGHT("bottom_right");

    companion object {
        fun fromCode(code: String): GestureZone? = entries.firstOrNull { it.code == code }

        fun fromGridPosition(row: Int, column: Int): GestureZone {
            return when (row to column) {
                0 to 0 -> TOP_LEFT
                0 to 1 -> TOP_CENTER
                0 to 2 -> TOP_RIGHT
                1 to 0 -> CENTER_LEFT
                1 to 1 -> CENTER
                1 to 2 -> CENTER_RIGHT
                2 to 0 -> BOTTOM_LEFT
                2 to 1 -> BOTTOM_CENTER
                else -> BOTTOM_RIGHT
            }
        }
    }
}

fun ReaderGestureControlConfig.normalize(): ReaderGestureControlConfig {
    val normalizedZones = GestureZone.entries.map { gestureZone ->
        val existing = zones.firstOrNull { it.zone == gestureZone.code }
        GestureZoneConfig(
            zone = gestureZone.code,
            singleTapAction = GestureAction.fromCode(existing?.singleTapAction ?: GestureAction.NONE.code).code,
            doubleTapAction = GestureAction.fromCode(existing?.doubleTapAction ?: GestureAction.NONE.code).code,
            longPressAction = GestureAction.fromCode(existing?.longPressAction ?: GestureAction.NONE.code).code
        )
    }
    return copy(version = ReaderGestureControlConfig.CURRENT_VERSION, zones = normalizedZones)
}
