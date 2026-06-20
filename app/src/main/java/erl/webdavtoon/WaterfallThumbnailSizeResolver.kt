package erl.webdavtoon

import kotlin.math.roundToInt
import kotlin.math.sqrt

data class WaterfallThumbnailTargetSize(
    val width: Int,
    val height: Int
)

object WaterfallThumbnailSizeResolver {
    private const val DEFAULT_PERCENT_BASELINE = 70

    fun resolve(
        displayWidth: Int,
        displayHeight: Int,
        qualityMode: String,
        percent: Int,
        maxWidth: Int,
        maxTargetPixels: Int
    ): WaterfallThumbnailTargetSize {
        val safeDisplayWidth = displayWidth.coerceAtLeast(1)
        val safeDisplayHeight = displayHeight.coerceAtLeast(1)
        var targetWidth = safeDisplayWidth
        var targetHeight = safeDisplayHeight

        when (qualityMode) {
            SettingsManager.WATERFALL_MODE_MAX_WIDTH -> {
                val safeMaxWidth = maxWidth.coerceAtLeast(1)
                if (targetWidth > safeMaxWidth) {
                    val scale = safeMaxWidth.toFloat() / targetWidth.toFloat()
                    targetWidth = maxOf(1, (targetWidth * scale).roundToInt())
                    targetHeight = maxOf(1, (targetHeight * scale).roundToInt())
                }
            }

            else -> {
                val scale = percent.coerceIn(10, 100).toFloat() / DEFAULT_PERCENT_BASELINE.toFloat()
                targetWidth = maxOf(1, (safeDisplayWidth * scale).roundToInt())
                targetHeight = maxOf(1, (safeDisplayHeight * scale).roundToInt())
            }
        }

        return capByPixels(
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            minimumWidth = safeDisplayWidth,
            minimumHeight = safeDisplayHeight,
            maxTargetPixels = maxTargetPixels
        )
    }

    private fun capByPixels(
        targetWidth: Int,
        targetHeight: Int,
        minimumWidth: Int,
        minimumHeight: Int,
        maxTargetPixels: Int
    ): WaterfallThumbnailTargetSize {
        var width = targetWidth
        var height = targetHeight
        val effectiveMaxPixels = maxOf(
            maxTargetPixels.coerceAtLeast(1).toLong(),
            minimumWidth.toLong() * minimumHeight.toLong()
        )
        val pixelCount = width.toLong() * height.toLong()
        if (pixelCount > effectiveMaxPixels) {
            val scale = sqrt(effectiveMaxPixels.toDouble() / pixelCount.toDouble()).toFloat()
            width = maxOf(1, (width * scale).roundToInt())
            height = maxOf(1, (height * scale).roundToInt())
        }

        return WaterfallThumbnailTargetSize(
            width = maxOf(width, minimumWidth),
            height = maxOf(height, minimumHeight)
        )
    }
}
