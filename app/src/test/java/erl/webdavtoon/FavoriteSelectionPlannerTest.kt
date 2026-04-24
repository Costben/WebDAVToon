package erl.webdavtoon

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteSelectionPlannerTest {

    @Test
    fun `buildPlan adds only non favorited photos in normal view`() {
        val photo1 = "1"
        val photo2 = "2"
        val photo3 = "3"

        val plan = FavoriteSelectionPlanner.buildPlan(
            selectedItems = listOf(photo1, photo2, photo3),
            favoriteIds = setOf("2"),
            isFavoritesView = false,
            idSelector = { it }
        )

        assertEquals(listOf(photo1, photo3), plan.toAdd)
        assertEquals(emptyList<String>(), plan.toRemove)
    }

    @Test
    fun `buildPlan removes all selected photos in favorites view`() {
        val photo1 = "1"
        val photo2 = "2"

        val plan = FavoriteSelectionPlanner.buildPlan(
            selectedItems = listOf(photo1, photo2),
            favoriteIds = setOf("1", "2"),
            isFavoritesView = true,
            idSelector = { it }
        )

        assertEquals(emptyList<String>(), plan.toAdd)
        assertEquals(listOf(photo1, photo2), plan.toRemove)
    }

    @Test
    fun `buildPlan removes selected photos in normal view when all are already favorited`() {
        val photo1 = "1"
        val photo2 = "2"

        val plan = FavoriteSelectionPlanner.buildPlan(
            selectedItems = listOf(photo1, photo2),
            favoriteIds = setOf("1", "2"),
            isFavoritesView = false,
            idSelector = { it }
        )

        assertEquals(emptyList<String>(), plan.toAdd)
        assertEquals(listOf(photo1, photo2), plan.toRemove)
    }

    @Test
    fun `buildPlan returns empty when nothing selected`() {
        val plan = FavoriteSelectionPlanner.buildPlan(
            selectedItems = emptyList<String>(),
            favoriteIds = setOf("1"),
            isFavoritesView = false,
            idSelector = { it }
        )

        assertEquals(emptyList<String>(), plan.toAdd)
        assertEquals(emptyList<String>(), plan.toRemove)
    }
}
