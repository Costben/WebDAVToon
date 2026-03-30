# WebDAVToon 项目结构文档

本文档记录了 WebDAVToon Android 项目的代码结构和主要组件职责。

## 1. 项目概览
WebDAVToon 是一个支持本地和 WebDAV 协议的漫画/图片浏览器。它支持瀑布流预览、沉浸式阅读、Webtoon（长条图）模式以及文件夹嵌套浏览。

## 2. 目录结构 (App Module)

主要代码位于 `app/src/main/java/erl/webdavtoon/` 下。

### A. 核心组件 (Activities)
| 文件名 | 描述 |
|---|---|
| `FolderViewActivity.kt` | **应用入口**。显示顶层文件夹（本地存储、WebDAV 入口）。 |
| `SubFolderActivity.kt` | **子文件夹浏览**。处理递归的文件夹导航，支持无限层级嵌套。 |
| `MainActivity.kt` | **图片列表预览**。以瀑布流（StaggeredGrid）形式展示文件夹内的所有图片。支持双指缩放改变列数。 |
| `PhotoViewActivity.kt` | **图片详情/阅读器**。核心阅读界面，支持两种模式切换：<br>1. **卡片模式** (Card Mode)：左右滑动翻页。<br>2. **Webtoon 模式**：垂直连续滚动，适合长条漫。 |
| `SettingsActivity.kt` | **设置界面**。配置 WebDAV 服务器信息、网格列数等。 |

### B. 列表适配器 (Adapters)
| 文件名 | 描述 |
|---|---|
| `FolderAdapter.kt` | 用于 `FolderViewActivity` 和 `SubFolderActivity`，展示文件夹网格及缩略图。 |
| `PhotoAdapter.kt` | 用于 `MainActivity`，展示瀑布流图片列表。 |
| `PhotoDetailAdapter.kt` | 用于 `PhotoViewActivity` 的**卡片模式**，处理单张图片的缩放和展示。 |
| `WebtoonAdapter.kt` | 用于 `PhotoViewActivity` 的**Webtoon 模式**，处理长条图的垂直拼接显示。 |

### C. 数据与网络 (Data & Network)
| 文件名 | 描述 |
|---|---|
| `PhotoRepository.kt` | 数据仓库接口，定义获取图片和文件夹的方法。 |
| `LocalPhotoRepository.kt` | `PhotoRepository` 实现，负责读取本地存储的媒体文件。 |
| `WebDavPhotoRepository.kt` | `PhotoRepository` 实现，负责通过 WebDAV 协议获取文件列表。 |
| `WebDavClient.kt` | WebDAV 客户端核心逻辑。处理 XML 解析、PROPFIND 请求等。 |
| `WebDavImageLoader.kt` | 图片加载封装 (基于 Glide)。支持 WebDAV 流式加载和本地加载，包含**尺寸限制控制**逻辑。 |
| `Models.kt` | 数据模型定义 (`Photo`, `Folder` 等)。 |
| `FolderCache.kt` | 简单的内存缓存，用于存储文件夹结构，减少网络请求。 |

### D. 工具类 (Utils)
| 文件名 | 描述 |
|---|---|
| `SettingsManager.kt` | 管理 SharedPreferences，存取 WebDAV 配置、UI 偏好设置等。 |
| `PinchZoomHelper.kt` | 处理手势缩放逻辑，用于 `MainActivity` 的动态列数调整。 |
| `FileUtils.kt` | 文件操作工具类。 |
| `LogManager.kt` | 日志管理工具。 |
| `WebDAVToonApplication.kt` | 全局 Application 类，负责 Glide 配置（内存/磁盘缓存策略）等初始化工作。 |

## 3. 关键业务流程

### 3.1 导航流程
1.  **启动** -> `FolderViewActivity` (显示根目录)
2.  **点击文件夹** ->
    *   如果有子文件夹 -> 跳转 `SubFolderActivity` (递归)
    *   如果是图片文件夹 -> 跳转 `MainActivity` (瀑布流预览)
3.  **点击图片** -> 跳转 `PhotoViewActivity` (阅读器)

### 3.2 Webtoon 模式优化
在 `PhotoViewActivity` 中切换到 Webtoon 模式时：
*   使用 `WebtoonAdapter` 替换 `PhotoDetailAdapter`。
*   布局管理器切换为垂直 `LinearLayoutManager`。
*   **关键优化**：调用 `WebDavImageLoader` 时禁用 `DownsampleStrategy.AT_MOST`，强制加载原图宽度，防止长条图被缩放到屏幕高度导致宽度过窄。

### 3.3 沉浸模式
应用实现了全屏沉浸模式：
*   通过 `WindowCompat.setDecorFitsSystemWindows(window, false)` 实现内容延伸到状态栏/导航栏。
*   Activity 根据状态动态调整 Padding 以避免 UI 被系统栏遮挡。
*   在阅读模式下，支持隐藏所有系统栏和控制按钮，提供纯净阅读体验。

## 4. 资源文件
*   布局文件位于 `app/src/main/res/layout/`。
*   图标资源主要使用 MD3 风格 (`ic_*_md3.xml`)。
