package erl.webdavtoon

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.sqrt

object VideoThumbnailHeuristics {

    data class FolderPreviewCandidate<T>(
        val value: T,
        val mediaType: MediaType,
        val isBlankLike: Boolean,
        val sourceOrder: Int
    )

    private const val DARK_LUMA_THRESHOLD = 24
    private const val BRIGHT_LUMA_THRESHOLD = 232
    private const val LOW_SATURATION_THRESHOLD = 0.12f
    private const val EDGE_DARK_RATIO_THRESHOLD = 0.83f
    private val defaultFrameTimesUs = listOf(
        1_000_000L,
        3_000_000L,
        5_000_000L,
        8_000_000L,
        12_000_000L
    )

    private val lateFallbackFrameTimesUs = listOf(
        20_000_000L,
        30_000_000L,
        45_000_000L,
        60_000_000L,
        90_000_000L,
        120_000_000L,
        180_000_000L,
        240_000_000L
    )

    fun candidateFrameTimesUs(
        requestedTimeUs: Long,
        durationMs: Long? = null,
        sourceExtension: String? = null,
        isFolderPreview: Boolean = false
    ): List<Long> {
        val durationUs = durationMs?.takeIf { it > 0L }?.times(1_000L)
        val candidates = linkedSetOf<Long>()
        val shouldUseFirstFrameOnly = sourceExtension.equals("avi", ignoreCase = true)

        fun addCandidate(rawTimeUs: Long) {
            val bounded = when {
                durationUs == null -> rawTimeUs.coerceAtLeast(0L)
                durationUs <= 1_000L -> 0L
                else -> rawTimeUs.coerceIn(0L, durationUs - 1_000L)
            }
            candidates.add(bounded)
        }

        if (shouldUseFirstFrameOnly) {
            addCandidate(0L)
        } else {
            if (isFolderPreview) {
                if (requestedTimeUs > 0L) {
                    addCandidate(requestedTimeUs)
                }
                defaultFrameTimesUs.forEach(::addCandidate)
            } else {
                addCandidate(requestedTimeUs)
                defaultFrameTimesUs.forEach(::addCandidate)
            }
            if (durationUs != null) {
                lateFallbackFrameTimesUs.forEach(::addCandidate)
            }
        }

        addCandidate(0L)
        return candidates.toList()
    }

    fun <T> selectFolderPreviewCandidates(
        candidates: List<FolderPreviewCandidate<T>>,
        limit: Int = 4
    ): List<T> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()

        return candidates
            .sortedWith(
                compareBy<FolderPreviewCandidate<T>>(
                    { it.isBlankLike },
                    { it.mediaType == MediaType.VIDEO },
                    { it.sourceOrder }
                )
            )
            .take(limit)
            .map { it.value }
    }

    fun shouldPreferSyncFrameSearch(
        sourceExtension: String?,
        isFolderPreview: Boolean
    ): Boolean {
        return sourceExtension.equals("avi", ignoreCase = true)
    }

    fun isLikelyBlankFrame(argbSamples: IntArray): Boolean {
        if (argbSamples.isEmpty()) return true

        var darkCount = 0
        var brightCount = 0
        var lowSaturationCount = 0
        var minLuma = 255
        var maxLuma = 0
        val lumaSamples = IntArray(argbSamples.size)

        for ((index, argb) in argbSamples.withIndex()) {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF

            val luma = ((r * 299) + (g * 587) + (b * 114)) / 1000
            lumaSamples[index] = luma
            minLuma = minOf(minLuma, luma)
            maxLuma = maxOf(maxLuma, luma)

            if (luma <= DARK_LUMA_THRESHOLD) darkCount++
            if (luma >= BRIGHT_LUMA_THRESHOLD) brightCount++

            val channelMax = max(r, max(g, b))
            val channelMin = minOf(r, g, b)
            val saturation = if (channelMax == 0) 0f else (channelMax - channelMin).toFloat() / channelMax
            if (saturation <= LOW_SATURATION_THRESHOLD) {
                lowSaturationCount++
            }
        }

        val sampleCount = argbSamples.size.toFloat()
        val darkRatio = darkCount / sampleCount
        val brightRatio = brightCount / sampleCount
        val lowSaturationRatio = lowSaturationCount / sampleCount
        val lumaRange = maxLuma - minLuma
        val darkEdgeSpan = largestDarkEdgeSpan(lumaSamples)
        val inferredGridSize = sqrt(argbSamples.size.toDouble()).toInt()
        val hasLargeDarkEdgeRegion = inferredGridSize >= 2 && darkEdgeSpan >= max(2, inferredGridSize / 3)

        return when {
            darkRatio >= 0.82f -> true
            brightRatio >= 0.82f -> true
            lowSaturationRatio >= 0.85f && (darkRatio >= 0.60f || brightRatio >= 0.60f) -> true
            lowSaturationRatio >= 0.92f && lumaRange <= 28 -> true
            lowSaturationRatio >= 0.75f && darkRatio >= 0.45f && hasLargeDarkEdgeRegion -> true
            else -> false
        }
    }

    private fun largestDarkEdgeSpan(lumaSamples: IntArray): Int {
        val gridSize = sqrt(lumaSamples.size.toDouble()).toInt()
        if (gridSize < 2 || gridSize * gridSize != lumaSamples.size) return 0

        val darkColumns = BooleanArray(gridSize) { column ->
            val darkInColumn = (0 until gridSize).count { row ->
                lumaSamples[row * gridSize + column] <= DARK_LUMA_THRESHOLD
            }
            darkInColumn / gridSize.toFloat() >= EDGE_DARK_RATIO_THRESHOLD
        }
        val darkRows = BooleanArray(gridSize) { row ->
            val darkInRow = (0 until gridSize).count { column ->
                lumaSamples[row * gridSize + column] <= DARK_LUMA_THRESHOLD
            }
            darkInRow / gridSize.toFloat() >= EDGE_DARK_RATIO_THRESHOLD
        }

        return maxOf(
            leadingTrueCount(darkColumns),
            trailingTrueCount(darkColumns),
            leadingTrueCount(darkRows),
            trailingTrueCount(darkRows)
        )
    }

    private fun leadingTrueCount(flags: BooleanArray): Int {
        var count = 0
        while (count < flags.size && flags[count]) {
            count++
        }
        return count
    }

    private fun trailingTrueCount(flags: BooleanArray): Int {
        var count = 0
        var index = flags.lastIndex
        while (index >= 0 && flags[index]) {
            count++
            index--
        }
        return count
    }
}

fun Bitmap.isLikelyBlankPreviewBitmap(): Boolean {
    if (width <= 0 || height <= 0) return true

    val sampleColumns = minOf(6, width)
    val sampleRows = minOf(6, height)
    val samples = IntArray(sampleColumns * sampleRows)
    var index = 0

    for (row in 0 until sampleRows) {
        val y = ((row + 0.5f) * height / sampleRows).toInt().coerceIn(0, height - 1)
        for (column in 0 until sampleColumns) {
            val x = ((column + 0.5f) * width / sampleColumns).toInt().coerceIn(0, width - 1)
            samples[index++] = getPixel(x, y)
        }
    }

    return VideoThumbnailHeuristics.isLikelyBlankFrame(samples)
}

fun Bitmap.isLikelyBlankVideoThumbnail(): Boolean = isLikelyBlankPreviewBitmap()
