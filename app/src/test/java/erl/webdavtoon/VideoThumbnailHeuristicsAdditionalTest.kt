package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoThumbnailHeuristicsAdditionalTest {

    @Test
    fun selectFolderPreviewCandidates_videoOnlyFoldersStillReturnVideoCandidates() {
        val selected = VideoThumbnailHeuristics.selectFolderPreviewCandidates(
            listOf(
                VideoThumbnailHeuristics.FolderPreviewCandidate("video-1", MediaType.VIDEO, false, 0),
                VideoThumbnailHeuristics.FolderPreviewCandidate("video-2", MediaType.VIDEO, false, 1),
                VideoThumbnailHeuristics.FolderPreviewCandidate("video-3", MediaType.VIDEO, false, 2)
            )
        )

        assertEquals(listOf("video-1", "video-2", "video-3"), selected)
    }

    @Test
    fun selectFolderPreviewCandidates_whenOnlyBlankLikeCandidatesExist_keepsSourceOrder() {
        val selected = VideoThumbnailHeuristics.selectFolderPreviewCandidates(
            listOf(
                VideoThumbnailHeuristics.FolderPreviewCandidate("blank-video", MediaType.VIDEO, true, 0),
                VideoThumbnailHeuristics.FolderPreviewCandidate("blank-image", MediaType.IMAGE, true, 1)
            )
        )

        assertEquals(listOf("blank-image", "blank-video"), selected)
    }
}
