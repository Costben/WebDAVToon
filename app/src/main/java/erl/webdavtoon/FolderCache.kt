package erl.webdavtoon

import androidx.room.*
import android.content.Context

@Entity(tableName = "folder_cache")
data class FolderCacheEntity(
    @PrimaryKey val path: String,
    val name: String,
    val previewUrisJson: String, // Store as JSON string
    val hasSubFolders: Boolean,
    val lastUpdated: Long,
    val sortOrder: Int // Cache is valid for a specific sort order
)

@Dao
interface FolderCacheDao {
    @Query("SELECT * FROM folder_cache WHERE path = :path AND sortOrder = :sortOrder")
    suspend fun getFolderCache(path: String, sortOrder: Int): FolderCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderCache(cache: FolderCacheEntity)

    @Query("DELETE FROM folder_cache")
    suspend fun clearAll()
}

@Database(entities = [FolderCacheEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderCacheDao(): FolderCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "webdavtoon_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
