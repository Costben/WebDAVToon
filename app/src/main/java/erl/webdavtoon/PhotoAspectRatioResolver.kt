package erl.webdavtoon

internal object PhotoAspectRatioResolver {
    private const val DEFAULT_ASPECT_RATIO = 1f
    private const val MIN_ASPECT_RATIO = 0.2f
    private const val MAX_ASPECT_RATIO = 5f

    fun resolve(photo: Photo, resolvedDimensions: Pair<Int, Int>? = null): Float {
        val widthHeight = resolvedDimensions ?: (photo.width to photo.height)
        return resolve(widthHeight.first, widthHeight.second)
    }

    fun resolve(width: Int, height: Int): Float {
        return resolve(width to height)
    }

    fun resolve(widthHeight: Pair<Int, Int>): Float {
        val width = widthHeight.first
        val height = widthHeight.second
        if (width <= 0 || height <= 0) {
            return DEFAULT_ASPECT_RATIO
        }
        return (width.toFloat() / height.toFloat()).coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
    }
}
