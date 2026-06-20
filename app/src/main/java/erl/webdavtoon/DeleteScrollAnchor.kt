package erl.webdavtoon

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

data class DeleteScrollAnchor(
    val position: Int,
    val offset: Int = 0
)

object DeleteScrollAnchorHelper {
    fun forDeletedPhotos(
        deletedPhotos: List<Photo>,
        sourcePhotos: List<Photo>,
        fallbackPosition: Int = RecyclerView.NO_POSITION,
        recyclerView: RecyclerView? = null
    ): DeleteScrollAnchor? {
        if (deletedPhotos.isEmpty()) return null

        val deletedIds = deletedPhotos.mapTo(mutableSetOf()) { it.id }
        val firstDeletedPosition = sourcePhotos.indexOfFirst { it.id in deletedIds }
        val targetPosition = when {
            firstDeletedPosition != -1 -> firstDeletedPosition
            fallbackPosition != RecyclerView.NO_POSITION -> fallbackPosition
            else -> 0
        }

        val safePosition = targetPosition.coerceAtLeast(0)
        val offset = recyclerView
            ?.layoutManager
            ?.findViewByPosition(safePosition)
            ?.let { view -> view.top - recyclerView.paddingTop }
            ?: 0

        return DeleteScrollAnchor(position = safePosition, offset = offset)
    }

    fun restore(recyclerView: RecyclerView, anchor: DeleteScrollAnchor, itemCount: Int) {
        if (itemCount <= 0) return

        val targetPosition = anchor.position.coerceIn(0, itemCount - 1)
        recyclerView.post {
            when (val layoutManager = recyclerView.layoutManager) {
                is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(targetPosition, anchor.offset)
                is StaggeredGridLayoutManager -> layoutManager.scrollToPositionWithOffset(targetPosition, anchor.offset)
                is FollowZoomWaterfallLayoutManager -> layoutManager.scrollToPositionWithOffset(targetPosition, anchor.offset)
                else -> recyclerView.scrollToPosition(targetPosition)
            }
        }
    }
}
