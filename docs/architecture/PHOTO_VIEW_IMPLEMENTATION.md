# Photo View Implementation Analysis

本文档详细描述了 WebDAVToon 应用中图片浏览功能（Photo View）的实现细节，包括**卡片模式**和**Webtoon 模式**的架构、画布处理以及沉浸式全屏的实现方案。

## 1. 核心架构 (PhotoViewActivity)

`PhotoViewActivity` 是图片浏览的宿主 Activity，负责：
1.  管理界面状态（沉浸模式、工具栏、底栏）。
2.  处理图片数据加载。
3.  切换浏览模式（Card Mode vs Webtoon Mode）。

### 关键组件
*   **RecyclerView**: 作为主要的视图容器，根据模式不同动态切换 `LayoutManager` 和 `Adapter`。
*   **WindowCompat**: 用于处理系统栏（状态栏/导航栏）的沉浸式显示。

## 2. 浏览模式实现

### 2.1 卡片模式 (Card Mode)
**适用场景**: 普通图片浏览，翻页体验。

*   **LayoutManager**: `LinearLayoutManager` (HORIZONTAL)。
*   **Adapter**: `PhotoDetailAdapter`。
*   **交互**:
    *   **翻页**: 使用 `PagerSnapHelper` 实现类似 ViewPager 的整页吸附效果。
    *   **缩放**: 在 `PhotoDetailAdapter` 中实现了自定义的 `ScaleGestureDetector` 和 `GestureDetector`。
        *   支持双指缩放 (Pinch-to-zoom)。
        *   支持单指拖动 (Panning)。
        *   双击切换 1x / 3x 缩放。
        *   放大状态下请求父容器不拦截触摸事件，解决滑动冲突。
*   **画布实现**:
    *   布局文件: `item_photo_detail.xml`。
    *   使用 `ShapeableImageView`，宽度高度设为 `match_parent` (配合 `ConstraintLayout` 的 `match_parent`)。
    *   `scaleType` 设为 `fitCenter`，确保图片完整显示在屏幕内。

### 2.2 Webtoon 模式 (Webtoon Mode)
**适用场景**: 长条漫阅读，垂直连续滚动。

*   **LayoutManager**: `LinearLayoutManager` (VERTICAL)。
*   **Adapter**: `WebtoonAdapter`。
*   **交互**: 标准的垂直列表滚动。
*   **画布实现**:
    *   布局文件: `item_photo_view.xml`。
    *   宽度: `match_parent`。
    *   高度: `wrap_content`。
    *   `scaleType` 设为 `fitCenter` (结合 `adjustViewBounds=true`)。
    *   **关键点**: 图片加载时禁用了 `DownsampleStrategy.AT_MOST`，强制加载原图宽度。这确保了在 Webtoon 模式下，图片宽度能够撑满屏幕宽度，高度根据比例自适应，从而实现无缝拼接效果。

## 3. 沉浸式全屏实现 (Immersive Mode)

目前通过 `toggleImmersiveMode` 方法控制。

### 3.1 开启沉浸模式
1.  **隐藏系统栏**:
    *   调用 `WindowInsetsController.hide(WindowInsetsCompat.Type.systemBars())`。
    *   设置行为为 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` (滑动呼出)。
2.  **隐藏 UI 组件**:
    *   `appBarLayout.visibility = GONE`
    *   `bottomAppBar.visibility = GONE`
3.  **扩展布局**:
    *   设置 `window.navigationBarColor` 和 `statusBarColor` 为透明。
    *   移除 `RecyclerView` 的 Padding，使其填充整个屏幕。
    *   **注意**: 为完美适配刘海屏 (Display Cutout)，需要在 `styles.xml` 或代码中设置 `windowLayoutInDisplayCutoutMode`。

### 3.2 退出沉浸模式
1.  显示系统栏。
2.  显示 UI 组件。
3.  恢复 `RecyclerView` 的 Padding (以避开系统栏区域)。

## 4. 待修复问题与方案

**问题**: 用户反馈开启沉浸模式后，内容仍然没有真正占满全屏（可能存在黑边或无法延伸到刘海区域）。

**解决方案**:
在 `PhotoViewActivity.onCreate` 或 `toggleImmersiveMode` 中，必须显式设置 `layoutInDisplayCutoutMode` 为 `SHORT_EDGES`。

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    val params = window.attributes
    params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    window.attributes = params
}
```

这将允许应用内容延伸到屏幕缺口（刘海）区域，实现真正的全屏阅读体验。
