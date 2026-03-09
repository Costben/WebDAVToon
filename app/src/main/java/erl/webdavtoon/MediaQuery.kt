package erl.webdavtoon

data class MediaQuery(
    val keyword: String = "",
    val extensions: Set<String> = emptySet(),
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null
)

data class MediaPageResult(
    val items: List<Photo>,
    val hasMore: Boolean,
    val nextOffset: Int
)
