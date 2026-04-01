# P2-3：Adapter 重构

> 涉及文件：PhotoDetailAdapter.kt, WebtoonAdapter.kt, PhotoViewActivity.kt

---

## 现状问题

- PhotoDetailAdapter（168 行）和 WebtoonAdapter（129 行）有大量重复逻辑
- 选择模式管理、长按回调、点击回调在两处实现
- PhotoDetailAdapter 用 PhotoView 做缩放，WebtoonAdapter 用 ZoomableRecyclerView 的 Matrix，两套缩放逻辑
- PhotoViewActivity 中大量代码根据 `isCardMode` 在两个 Adapter 之间切换

---

## 改造步骤

### 第 1 步：抽取 BasePhotoAdapter

```kotlin
abstract class BasePhotoAdapter<VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {
    protected var photos: List<Photo> = emptyList()
    protected var isSelectionMode = false
    protected val selectedPositions = mutableSetOf<Int>()

    var onPhotoClick: ((Int) -> Unit)? = null
    var onPhotoLongPress: ((Int) -> Unit)? = null

    fun setPhotos(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) selectedPositions.clear()
        notifyDataSetChanged()
    }

    fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        notifyItemChanged(position)
    }

    fun clearSelection() {
        selectedPositions.clear()
        notifyDataSetChanged()
    }

    fun getSelectedCount(): Int = selectedPositions.size

    fun getSelectedPhotos(): List<Photo> = selectedPositions.mapNotNull { photos.getOrNull(it) }
}
```

- 所有选择逻辑集中在基类
- PhotoDetailAdapter 和 WebtoonAdapter 只需实现 ViewHolder 和 bind 逻辑

### 第 2 步：改造 PhotoDetailAdapter

```
当前：PhotoDetailAdapter extends RecyclerView.Adapter
改造：PhotoDetailAdapter extends BasePhotoAdapter
```

- 删除重复的选择模式代码
- 只保留卡片模式特有的 PhotoView 缩放逻辑
- 长按/点击回调委托给基类

### 第 3 步：改造 WebtoonAdapter

```
当前：WebtoonAdapter extends RecyclerView.Adapter
改造：WebtoonAdapter extends BasePhotoAdapter
```

- 删除重复的选择模式代码
- 只保留 Webtoon 模式特有的长图加载逻辑
- 双击缩放由 ZoomableRecyclerView 处理，Adapter 不再管理

### 第 4 步：简化 PhotoViewActivity

```
当前：大量 if (isCardMode) adapter.xxx() else webtoonAdapter?.xxx() 代码
改造：
  1. 用一个变量 currentAdapter: BasePhotoAdapter<*> 统一引用
  2. 切换模式时只改变 currentAdapter 的指向
  3. 所有操作统一调用 currentAdapter.xxx()
```

```kotlin
private lateinit var currentAdapter: BasePhotoAdapter<*>

private fun toggleViewMode() {
    isCardMode = !isCardMode
    currentAdapter = if (isCardMode) adapter else webtoonAdapter!!
    binding.recyclerView.adapter = currentAdapter
}
```

- 删除重复的 `if (isCardMode)` 分支
- 选择计数、删除等操作统一走 `currentAdapter`

### 第 5 步：统一缩放方案（可选）

```
当前：PhotoDetailAdapter 内部用 PhotoView 做缩放，WebtoonAdapter 用 ZoomableRecyclerView
改造方案：
  - 卡片模式下禁用 ZoomableRecyclerView 的缩放（保持现状，因为 PhotoView 处理单图缩放更好）
  - Webtoon 模式继续用 ZoomableRecyclerView 处理缩放
  - 不强行统一，两者各有优势
```

---

## 验证要点

- 卡片模式下选择/缩放/翻页正常
- Webtoon 模式下选择/缩放/滚动正常
- 模式切换后选择状态保持
- 代码量减少，重复逻辑消除
