# P0-1：分页加载算法改造

> 涉及文件：MediaManager.kt, LocalPhotoRepository.kt, RustWebDavPhotoRepository.kt, MediaState.kt, PhotoRepository.kt

---

## 现状问题

当前数据流：`getPhotos()` 全量加载 → `filter` 过滤 → `sort` 全量排序 → `drop(offset)` 跳过 → `take(limit)` 取页

三个数据源全部存在这个问题：
- **LocalPhotoRepository**：MediaStore 查询无 LIMIT/OFFSET，全量遍历
- **RustWebDavPhotoRepository**：`getPhotos()` 返回全量列表，再在 Kotlin 端排序+过滤+分页
- **收藏夹**：`getFavoritePhotos()` 返回全量 JSON 解析，再 drop

---

## 改造步骤

### 第 1 步：改造 LocalPhotoRepository.queryMediaPage

把 MediaStore 查询直接加 `LIMIT/OFFSET`，不再全量加载：

```
当前：contentResolver.query(...) 无限制 → while 遍历全部 → drop(offset).take(limit)
改造：contentResolver.query(...) + "LIMIT ? OFFSET ?" 参数 → 只遍历当页数据
```

具体改动：
- 新增 `getPhotosCount(folderPath, recursive, query)` 方法返回总数，用于判断 `hasMore`
- keyword 过滤仍需全量（MediaStore 不支持 LIKE 模糊搜索标题），但 size/extension 过滤可以推到 SQL
- 排序由 `sortOrder` 参数直接推到 MediaStore，不再 Kotlin 端排序
- `queryMediaPage` 内部逻辑改为：先 count 获取总数，再 query 加 LIMIT OFFSET 获取当前页

### 第 2 步：改造 RustWebDavPhotoRepository.queryMediaPage

当前 Rust 核心的 `repo.getPhotos()` 只支持全量拉取。两种方案：

**方案 A（推荐，不改 Rust）：**
- 保持 Rust 端全量拉取，但在 Kotlin 端用 `Sequence` 做惰性计算
- 排序用 `sortedWith()` 一次完成，避免多次 `sortedBy/sortedByDescending`
- filter → drop → take 全部在 Sequence 上操作，不产生中间 List
- 好处：不涉及 Rust FFI 重新生成，改动小

**方案 B（改 Rust FFI，效果最好）：**
- Rust 核心新增 `getPhotosPage(path, sort, offset, limit, forceRefresh)` 方法
- 分页逻辑推到 Rust 端，只返回当前页数据
- Kotlin 端 `queryMediaPage` 直接调用新方法
- 缺点：需要修改 Rust 代码 + 重新生成 UniFFI 绑定

### 第 3 步：改造收藏夹分页

与 P0-2 第 4 步联动（Room 数据库）。

```
当前：getFavoritePhotos() → 全量 JSON 解析为 List<Photo> → filter → drop → take
改造：
  1. FavoritePhotoDao 新增 getPage(keyword, offset, limit): List<FavoritePhotoEntity>
  2. FavoritePhotoDao 新增 getCount(keyword): Int
  3. MediaManager 中收藏分页逻辑改为调用 Room DAO
  4. 不再维护全量列表，直接 SQL 分页
```

### 第 4 步：优化 MediaManager.sortPhotos

当前对递归模式做 `groupBy` → 每组排序 → 拼接，时间复杂度 O(n log n × folderCount)：

```
改造：
  1. 如果已预排序（LocalPhotoRepository 的 SQL 已排好），跳过重排序
  2. 递归模式改为：先按 folderPath 分组，每组已是有序的，直接归并拼接
  3. 新增 SortStrategy 枚举：PRE_SORTED / CLIENT_SORT，由 Repository 告知
```

具体改动：
- `PhotoRepository.queryMediaPage` 返回值新增 `isPreSorted: Boolean` 字段
- `MediaPageResult` 扩展为 `(items, hasMore, nextOffset, isPreSorted)`
- `MediaManager.sortPhotos` 根据 `isPreSorted` 决定是否跳过排序

### 第 5 步：更新 PhotoCache

当前 PhotoCache 每次 append 都 `toMutableList()` + `addAll()`，产生新 List：

```
改造：
  1. PhotoCache 内部改用 CopyOnWriteArrayList 或 SnapshotStateList
  2. append 操作改为 addAll 直接在原列表上操作
  3. 或改为只存当前页 + totalCount，不维护全量列表（需要配合 PhotoViewActivity 改造）
```

---

## 验证要点

- 本地 1000+ 图片时，滑动加载不应出现明显卡顿
- 内存占用不应随页数增加线性增长
- 排序切换后，分页结果正确
- 递归模式下跨文件夹排序正确
