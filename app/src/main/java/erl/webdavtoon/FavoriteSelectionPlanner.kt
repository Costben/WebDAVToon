package erl.webdavtoon

data class FavoriteSelectionPlan<T>(
    val toAdd: List<T>,
    val toRemove: List<T>
)

object FavoriteSelectionPlanner {
    fun <T> buildPlan(
        selectedItems: List<T>,
        favoriteIds: Set<String>,
        isFavoritesView: Boolean,
        idSelector: (T) -> String
    ): FavoriteSelectionPlan<T> {
        if (selectedItems.isEmpty()) {
            return FavoriteSelectionPlan(emptyList(), emptyList())
        }

        return if (isFavoritesView) {
            FavoriteSelectionPlan(
                toAdd = emptyList(),
                toRemove = selectedItems
            )
        } else {
            FavoriteSelectionPlan(
                toAdd = selectedItems.filterNot { idSelector(it) in favoriteIds },
                toRemove = emptyList()
            )
        }
    }
}
