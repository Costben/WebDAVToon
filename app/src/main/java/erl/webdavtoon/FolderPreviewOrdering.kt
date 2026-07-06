package erl.webdavtoon

import java.util.Locale

object FolderPreviewOrdering {

    data class Candidate<T>(
        val value: T,
        val title: String,
        val dateModified: Long,
        val mediaType: MediaType = MediaType.IMAGE,
        val isBlankLike: Boolean = false,
        val sourceOrder: Int = 0
    )

    fun sortPhotos(photos: List<Photo>, sortOrder: Int): List<Photo> {
        return sortCandidates(
            photos.mapIndexed { index, photo ->
                Candidate(
                    value = photo,
                    title = photo.title,
                    dateModified = photo.dateModified,
                    mediaType = photo.mediaType,
                    sourceOrder = index
                )
            },
            sortOrder
        ).map { it.value }
    }

    fun <T> selectPreviewValues(
        candidates: List<Candidate<T>>,
        sortOrder: Int,
        limit: Int = 4,
        preferUsableMedia: Boolean = false
    ): List<T> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()

        val sorted = sortCandidates(candidates, sortOrder)
        if (!preferUsableMedia) {
            return sorted.take(limit).map { it.value }
        }

        return VideoThumbnailHeuristics.selectFolderPreviewCandidates(
            sorted.mapIndexed { index, candidate ->
                VideoThumbnailHeuristics.FolderPreviewCandidate(
                    value = candidate.value,
                    mediaType = candidate.mediaType,
                    isBlankLike = candidate.isBlankLike,
                    sourceOrder = index
                )
            },
            limit
        )
    }

    fun <T> sortCandidates(
        candidates: List<Candidate<T>>,
        sortOrder: Int
    ): List<Candidate<T>> {
        data class IndexedCandidate<T>(
            val originalIndex: Int,
            val candidate: Candidate<T>
        )

        val indexed = candidates.mapIndexed { index, candidate ->
            IndexedCandidate(index, candidate)
        }

        val comparator = when (sortOrder) {
            SettingsManager.SORT_NAME_ASC -> compareBy<IndexedCandidate<T>> {
                it.candidate.title.lowercase(Locale.ROOT)
            }.thenBy { it.candidate.sourceOrder }.thenBy { it.originalIndex }

            SettingsManager.SORT_NAME_DESC -> compareByDescending<IndexedCandidate<T>> {
                it.candidate.title.lowercase(Locale.ROOT)
            }.thenBy { it.candidate.sourceOrder }.thenBy { it.originalIndex }

            SettingsManager.SORT_DATE_ASC -> compareBy<IndexedCandidate<T>> {
                it.candidate.dateModified
            }.thenBy { it.candidate.title.lowercase(Locale.ROOT) }
                .thenBy { it.candidate.sourceOrder }
                .thenBy { it.originalIndex }

            SettingsManager.SORT_DATE_DESC,
            SettingsManager.SORT_RANDOM_FOLDERS -> compareByDescending<IndexedCandidate<T>> {
                it.candidate.dateModified
            }.thenBy { it.candidate.title.lowercase(Locale.ROOT) }
                .thenBy { it.candidate.sourceOrder }
                .thenBy { it.originalIndex }

            else -> compareByDescending<IndexedCandidate<T>> {
                it.candidate.dateModified
            }.thenBy { it.candidate.title.lowercase(Locale.ROOT) }
                .thenBy { it.candidate.sourceOrder }
                .thenBy { it.originalIndex }
        }

        return indexed.sortedWith(comparator).map { it.candidate }
    }
}
