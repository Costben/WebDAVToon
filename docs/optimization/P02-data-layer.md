# P0-2：数据层架构改造

> 涉及文件：SettingsManager.kt, MediaState.kt, WebDAVToonApplication.kt, 所有 Activity

---

## 现状问题

SettingsManager（247 行）直接操作 SharedPreferences：
- 所有配置都是 `prefs.getInt/getString/getBoolean` + 硬编码 key 字符串
- 收藏数据用 `Map<String, String>` 存 JSON，每次读写都做 Gson 序列化/反序列化
- WebDAV 多槽位配置用 `slot${n}_$key` 拼接，没有类型安全
- `getAllSlots()` 扫描 0-9 十个槽位，效率低

MediaState 是全局 `object` 单例：
- 多个 Activity 共享同一个 MutableStateFlow
- Activity 旋转/重建时状态可能丢失或冲突

---

## 改造步骤

### 第 1 步：引入 Jetpack DataStore

在 `build.gradle.kts` 添加依赖：

```kotlin
implementation("androidx.datastore:datastore-preferences:1.1.1")
```

新建 `AppSettings.kt`，用 DataStore 定义类型安全的配置：

```kotlin
object PreferenceKeys {
    val GRID_COLUMNS = intPreferencesKey("grid_columns")
    val SORT_ORDER = intPreferencesKey("sort_order")
    val THEME_ID = intPreferencesKey("theme_id")
    val LANGUAGE = stringPreferencesKey("language")
    // ... 所有 key 集中定义
}
```

### 第 2 步：迁移简单配置

把 SettingsManager 中的 getter/setter 逐步迁移到 DataStore：

```
当前：fun getGridColumns(): Int = prefs.getInt(KEY_GRID_COLUMNS, 2)
改造：fun getGridColumns(): Flow<Int> = dataStore.data.map { it[GRID_COLUMNS] ?: 2 }

当前：fun setGridColumns(count: Int) = prefs.edit().putInt(KEY_GRID_COLUMNS, count).apply()
改造：suspend fun setGridColumns(count: Int) = dataStore.edit { it[GRID_COLUMNS] = count }
```

- 简单配置（grid_columns, sort_order, theme_id, language 等）直接迁移
- 设置方法变为 suspend，调用方需要在协程中执行
- 读取返回 Flow，UI 层用 collect 自动响应变化

### 第 3 步：WebDAV 槽位配置迁移

当前用 `slot${n}_$key` 拼接字符串，改为数据类：

```kotlin
data class WebDavSlotConfig(
    val enabled: Boolean = false,
    val protocol: String = "https",
    val url: String = "",
    val port: Int = 443,
    val username: String = "",
    val password: String = "",
    val rememberPassword: Boolean = true,
    val alias: String = ""
)
```

- 用 DataStore 存储 `Map<Int, WebDavSlotConfig>` 的 JSON
- 或为每个槽位定义独立的 key 前缀，保持 DataStore 的类型安全
- `getAllSlots()` 不再扫描 0-9，直接从 Map 的 keys 获取

### 第 4 步：收藏数据迁移到 Room

添加 Room 依赖：

```kotlin
implementation("androidx.room:room-runtime:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
```

新建表结构：

```kotlin
@Entity(tableName = "favorite_photos")
data class FavoritePhotoEntity(
    @PrimaryKey val id: String,
    val imageUri: String,
    val title: String,
    val width: Int,
    val height: Int,
    val isLocal: Boolean,
    val dateModified: Long,
    val size: Long,
    val folderPath: String,
    val addedAt: Long = System.currentTimeMillis()
)
```

DAO 接口：

```kotlin
@Dao
interface FavoritePhotoDao {
    @Query("SELECT * FROM favorite_photos WHERE title LIKE '%' || :keyword || '%' ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(keyword: String, offset: Int, limit: Int): List<FavoritePhotoEntity>

    @Query("SELECT COUNT(*) FROM favorite_photos WHERE title LIKE '%' || :keyword || '%'")
    suspend fun getCount(keyword: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: FavoritePhotoEntity)

    @Delete
    suspend fun delete(photo: FavoritePhotoEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_photos WHERE id = :id)")
    suspend fun isFavorite(id: String): Boolean
}
```

- 与 P0-1 第 3 步联动：收藏分页直接用 Room 的 SQL LIMIT/OFFSET
- 首次启动时需要从 SharedPreferences 迁移旧数据到 Room

### 第 5 步：改造 MediaState 为 ViewModel

当前 MediaState 是全局 object，改为每个 Activity 持有独立 ViewModel：

```kotlin
class MediaViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private val sessionKey: String
        get() = savedStateHandle["sessionKey"] ?: ""

    fun start(...) { ... }
    fun loadNextPage(...) { ... }
}
```

- MainActivity 和 PhotoViewActivity 各自持有独立的 MediaViewModel
- 共享状态通过 PhotoCache（也改为单例 Repository）传递，不再依赖全局 MediaState
- 旋转重建时 SavedStateHandle 自动恢复 sessionKey 和当前位置

### 第 6 步：更新所有调用方

需要同步修改的文件：
- `FolderViewActivity.kt` — SettingsManager 调用改为协程/DataStore
- `MainActivity.kt` — MediaState 改为 MediaViewModel
- `PhotoViewActivity.kt` — MediaState 改为 MediaViewModel，SettingsManager 改为协程
- `SettingsActivity.kt` — 所有 getter/setter 改为 Flow/suspend
- `SubFolderActivity.kt` — 同上
- `WebDavImageLoader.kt` — SettingsManager 调用调整
- `WebDAVToonApplication.kt` — 初始化 Room 数据库
- `ThemeHelper.kt` — 配置读取改为 Flow

### 第 7 步：旧 SharedPreferences 数据迁移

```kotlin
class MigrationHelper(context: Context) {
    suspend fun migrateIfNeeded() {
        // 1. 检查是否已迁移
        // 2. 从 SharedPreferences 读取所有配置
        // 3. 写入 DataStore
        // 4. 迁移收藏数据到 Room
        // 5. 标记迁移完成
        // 6. 可选：清除旧 SharedPreferences
    }
}
```

在 `WebDAVToonApplication.onCreate()` 中调用，只执行一次。

---

## 验证要点

- 首次升级后，旧配置不丢失
- WebDAV 槽位配置读写正确
- 收藏数据增删改查正常，分页性能提升
- Activity 旋转后状态正确恢复
- 设置页面修改配置后，其他页面实时响应
