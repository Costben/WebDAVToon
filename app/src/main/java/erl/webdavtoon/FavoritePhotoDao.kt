package erl.webdavtoon

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoritePhotoDao {
    @Query("SELECT * FROM favorite_photos WHERE title LIKE '%' || :keyword || '%' ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(keyword: String, offset: Int, limit: Int): List<FavoritePhotoEntity>

    @Query("SELECT COUNT(*) FROM favorite_photos WHERE title LIKE '%' || :keyword || '%'")
    suspend fun getCount(keyword: String): Int

    @Query("SELECT * FROM favorite_photos")
    suspend fun getAll(): List<FavoritePhotoEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_photos WHERE id = :id)")
    suspend fun isFavorite(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: FavoritePhotoEntity)

    @Delete
    suspend fun delete(photo: FavoritePhotoEntity)

    @Query("DELETE FROM favorite_photos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM favorite_photos")
    suspend fun clearAll()
}
