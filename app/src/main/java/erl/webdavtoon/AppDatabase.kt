package erl.webdavtoon

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FavoritePhotoEntity::class, FavoriteFolderEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritePhotoDao(): FavoritePhotoDao
    abstract fun favoriteFolderDao(): FavoriteFolderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorite_photos ADD COLUMN isPrivate INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_folders (
                        path TEXT NOT NULL,
                        name TEXT NOT NULL,
                        isLocal INTEGER NOT NULL,
                        previewUrisJson TEXT NOT NULL,
                        hasSubFolders INTEGER NOT NULL,
                        dateModified INTEGER NOT NULL,
                        sourceSlot INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL,
                        isPrivate INTEGER NOT NULL,
                        PRIMARY KEY(path, isLocal, sourceSlot, isPrivate)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "webdavtoon.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
