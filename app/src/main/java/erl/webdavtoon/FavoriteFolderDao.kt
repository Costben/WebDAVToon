package erl.webdavtoon

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoriteFolderDao {
    @Query("SELECT * FROM favorite_folders ORDER BY addedAt DESC")
    suspend fun getAll(): List<FavoriteFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FavoriteFolderEntity)

    @Query(
        """
        DELETE FROM favorite_folders
        WHERE path = :path
          AND isLocal = :isLocal
          AND sourceSlot = :sourceSlot
          AND isPrivate = :isPrivate
        """
    )
    suspend fun deleteByIdentity(
        path: String,
        isLocal: Boolean,
        sourceSlot: Int,
        isPrivate: Boolean
    )
}
