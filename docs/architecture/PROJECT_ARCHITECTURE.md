# 项目架构与逻辑链总结

## 一、项目概述

**项目名称**: WebDAVToon  
**包名**: `erl.webdavtoon`  
**主要功能**: 图片浏览器，支持本地图片和WebDAV远程图片浏览，提供瀑布流和Webtoon（条漫）两种浏览模式

---

## 二、核心逻辑链

### 2.1 应用启动流程

```
WebDAVToonApplication (Application)
    ↓
    ├─> LogManager.initialize() - 初始化日志系统
    ├─> SettingsManager - 读取设置（日志级别等）
    └─> 设置全局日志级别
```

### 2.2 主界面流程（MainActivity）

```
MainActivity.onCreate()
    ↓
    ├─> 初始化 LocalPhotoRepository (本地图片仓库)
    ├─> 初始化 SettingsManager (设置管理器)
    ├─> 初始化 PhotoAdapter (瀑布流适配器)
    ├─> 设置 RecyclerView (StaggeredGridLayoutManager)
    └─> 加载图片
        ├─> 本地模式: LocalPhotoRepository.getPhotos()
        └─> WebDAV模式: WebDavPhotoRepository.getPhotos()
            ↓
        PhotoAdapter.bind() - 显示缩略图
            ↓
        用户点击图片
            ↓
        PhotoCache.setPhotos() - 缓存图片列表
            ↓
        Intent -> PhotoViewActivity
            └─> EXTRA_CURRENT_INDEX (当前图片索引)
```

### 2.3 Webtoon模式流程（PhotoViewActivity）

```
PhotoViewActivity.onCreate()
    ↓
    ├─> 从 PhotoCache 获取图片列表
    ├─> 获取 currentIndex (目标位置)
    ├─> 创建 WebtoonAdapter (enableImageLoading = false)
    ├─> 设置 RecyclerView (LinearLayoutManager, VERTICAL)
    ├─> 延迟100ms后启用图片加载
    ├─> 滚动到目标位置 (scrollToPositionWithOffset)
    └─> 预加载图片 (preloadImages)
        ↓
    WebtoonAdapter.bind()
        ↓
    ├─> 判断可见范围 (firstVisible - 5 到 lastVisible + 5)
    ├─> 设置优先级 (IMMEDIATE/HIGH/NORMAL/LOW)
    └─> 三阶段图片加载:
        1. 预览图 (screenWidth * 0.8, IMMEDIATE)
        2. 压缩版本 (screenWidth, IMMEDIATE/HIGH)
        3. 完整大图 (screenWidth * 1.2, HIGH/NORMAL/LOW)
            ↓
        Glide.with(context)
            .load(photo.imageUri)
            .priority(priority)
            .thumbnail(compressedRequest)
            .override(targetWidth)
            .into(CustomViewTarget)
```

### 2.4 图片加载优化策略

```
可见性判断:
    - 立即可见 (isImmediateVisible): IMMEDIATE 优先级
    - 可见范围 (isVisible): HIGH/NORMAL 优先级
    - 非可见: LOW 优先级或取消加载

三阶段加载:
    阶段1: 预览图 (0.8x 屏幕宽度) - 最快显示
    阶段2: 压缩版本 (1.0x 屏幕宽度) - 无锯齿
    阶段3: 完整大图 (1.2x 屏幕宽度) - 高质量

请求管理:
    - 滚动时取消不可见区域的请求
    - onViewRecycled() 时自动取消请求
    - 使用 Glide.clear(target) 释放资源
```

---

## 三、核心组件

### 3.1 Activity组件

| 组件 | 功能 | 关键参数 |
|------|------|----------|
| **MainActivity** | 主界面，瀑布流浏览 | `StaggeredGridLayoutManager`, `PhotoAdapter` |
| **PhotoViewActivity** | Webtoon模式，条漫浏览 | `LinearLayoutManager`, `WebtoonAdapter` |
| **FolderViewActivity** | 文件夹浏览 | `RecyclerView`, `FolderAdapter` |
| **SettingsActivity** | 设置页面 | `SettingsManager` |

### 3.2 Adapter组件

| 组件 | 功能 | 关键配置 |
|------|------|----------|
| **PhotoAdapter** | 瀑布流图片适配器 | `StaggeredGridLayoutManager`, 缩略图加载 |
| **WebtoonAdapter** | 条漫模式适配器 | `LinearLayoutManager`, 三阶段加载 |
| **FolderAdapter** | 文件夹列表适配器 | `LinearLayoutManager` |
| **WebDavFileAdapter** | WebDAV文件列表适配器 | `RecyclerView` |

### 3.3 Repository组件

| 组件 | 功能 | 数据源 |
|------|------|--------|
| **LocalPhotoRepository** | 本地图片仓库 | `MediaStore`, `ContentResolver` |
| **WebDavPhotoRepository** | WebDAV图片仓库 | `WebDavClient`, HTTP请求 |
| **PhotoRepository** | 统一图片仓库接口 | 组合上述仓库 |

### 3.4 Manager组件

| 组件 | 功能 | 关键方法 |
|------|------|----------|
| **LogManager** | 日志管理 | `log()`, `setMinLogLevel()`, 写入文件 |
| **SettingsManager** | 设置管理 | `SharedPreferences`, 保存/读取设置 |
| **PhotoCache** | 图片列表缓存 | `setPhotos()`, `getPhotos()` |

---

## 四、使用的工具和库

### 4.1 核心库

#### Glide (图片加载)
```kotlin
版本: 4.16.0
集成: com.github.bumptech.glide:compiler (kapt)

关键配置:
- DiskCacheStrategy.ALL - 磁盘缓存策略
- skipMemoryCache(false) - 启用内存缓存
- priority(Priority.IMMEDIATE/HIGH/NORMAL/LOW) - 加载优先级
- override(width, height) - 图片尺寸限制
- thumbnail() - 缩略图机制
- fitCenter() / centerCrop() - 图片缩放模式
```

#### OkHttp (网络请求)
```kotlin
版本: 通过 libs.versions.toml 管理
集成: Glide OkHttp集成

关键配置:
- OkHttpClient.Builder()
- addInterceptor(HttpLoggingInterceptor) - 日志拦截器
- connectTimeout, readTimeout - 超时设置
```

#### Retrofit (REST API)
```kotlin
用途: Unsplash API (可选功能)
配置:
- Retrofit.Builder()
- baseUrl()
- addConverterFactory(GsonConverterFactory)
```

#### RecyclerView (列表显示)
```kotlin
布局管理器:
- StaggeredGridLayoutManager (瀑布流)
  - spanCount: 2-4 (可配置)
  - orientation: VERTICAL
  
- LinearLayoutManager (条漫模式)
  - orientation: VERTICAL
  - reverseLayout: false

缓存配置:
- setItemViewCacheSize(20) - ViewHolder缓存大小
```

### 4.2 Kotlin协程
```kotlin
依赖:
- kotlinx-coroutines-core
- kotlinx-coroutines-android

使用场景:
- 图片加载 (Dispatchers.Main)
- 预加载 (Dispatchers.IO)
- 进度更新 (CoroutineScope)
```

### 4.3 AndroidX组件
```kotlin
- androidx.appcompat
- androidx.core.ktx
- androidx.recyclerview
- androidx.viewpager2
- androidx.navigation
- androidx.lifecycle
- material (Material Design)
```

---

## 五、关键参数配置

### 5.1 图片加载参数

#### 瀑布流模式 (PhotoAdapter)
```kotlin
缩略图尺寸:
- thumbnailWidth = screenWidth / spanCount
- maxThumbnailSize = thumbnailWidth * 2

缓存策略:
- DiskCacheStrategy.ALL
- skipMemoryCache(false)
```

#### Webtoon模式 (WebtoonAdapter)
```kotlin
三阶段尺寸:
- previewWidth = screenWidth * 0.8
- compressedWidth = screenWidth * 1.0
- targetWidth = screenWidth * 1.2

可见范围:
- visibleRange = 5 (前后各5个位置)
- initialTargetRange = 10 (初始目标位置前后各10个)

优先级策略:
- 立即可见: IMMEDIATE
- 可见范围: HIGH (预览/压缩), NORMAL (完整大图)
- 非可见: LOW 或取消
```

### 5.2 RecyclerView配置

#### 瀑布流 (MainActivity)
```kotlin
StaggeredGridLayoutManager:
- spanCount: 2-4 (SettingsManager配置)
- orientation: VERTICAL
- gapStrategy: GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
```

#### 条漫模式 (PhotoViewActivity)
```kotlin
LinearLayoutManager:
- orientation: VERTICAL
- reverseLayout: false

ViewHolder缓存:
- setItemViewCacheSize(20)
```

### 5.3 日志系统配置

#### LogManager
```kotlin
日志级别:
- VERBOSE (最低)
- DEBUG
- INFO (默认)
- WARN
- ERROR (最高)

日志文件:
- 路径: /storage/emulated/0/Android/data/erl.webdavtoon/files/logs/
- 格式: yyyy-MM-dd_HH-mm-ss.log
- 最大文件数: 10
- 单个文件最大大小: 5MB

日志拉取:
- Gradle任务: pullLogs
- 路径: 项目根目录/logs/
- 方法: adb pull /storage/emulated/0/Android/data/erl.webdavtoon/files/logs/
```

### 5.4 设置参数

#### SettingsManager
```kotlin
SharedPreferences键值:
- KEY_GRID_COLUMNS: "grid_columns" (默认: 2)
- KEY_SORT_ORDER: "sort_order" (默认: DATE_DESC)
- KEY_CROP_ENABLED: "crop_enabled" (默认: false)
- KEY_CROP_ASPECT_RATIO: "crop_aspect_ratio" (默认: 1.0)
- KEY_LOG_LEVEL: "log_level" (默认: Log.INFO)
```

### 5.5 WebDAV配置

#### WebDavClient
```kotlin
连接参数:
- baseUrl: String (WebDAV服务器地址)
- username: String
- password: String

HTTP配置:
- connectTimeout: 30秒
- readTimeout: 30秒
- writeTimeout: 30秒

请求方法:
- PROPFIND (列出文件)
- GET (下载文件)
- HEAD (获取文件信息)
```

---

## 六、性能优化策略

### 6.1 图片加载优化
1. **三阶段加载**: 预览图 → 压缩版本 → 完整大图
2. **优先级管理**: 可见区域优先，非可见区域延迟或取消
3. **请求取消**: 滚动时取消不可见区域的请求
4. **缓存策略**: 磁盘缓存 + 内存缓存

### 6.2 UI优化
1. **延迟加载**: UI先显示，100ms后启用图片加载
2. **ViewHolder缓存**: 缓存20个ViewHolder
3. **预加载**: 预加载当前图片前后50张（Webtoon模式）

### 6.3 内存管理
1. **图片尺寸限制**: 根据屏幕尺寸限制图片大小
2. **请求取消**: ViewHolder回收时取消请求
3. **内存清理**: Activity销毁时清理Glide缓存

---

## 七、数据流

### 7.1 图片数据流
```
数据源
    ↓
Repository (LocalPhotoRepository / WebDavPhotoRepository)
    ↓
Photo对象列表
    ↓
PhotoCache (临时缓存，避免Intent数据过大)
    ↓
Adapter (PhotoAdapter / WebtoonAdapter)
    ↓
Glide加载
    ↓
ImageView显示
```

### 7.2 设置数据流
```
SettingsActivity
    ↓
用户操作 (选择列数、排序方式等)
    ↓
SettingsManager.setXXX()
    ↓
SharedPreferences保存
    ↓
MainActivity.applyGridSettings()
    ↓
RecyclerView更新
```

### 7.3 日志数据流
```
代码调用 LogManager.log()
    ↓
日志级别过滤 (minLogLevel)
    ↓
Android Log输出
    ↓
文件写入 (/storage/emulated/0/Android/data/.../logs/)
    ↓
Gradle任务 pullLogs
    ↓
项目根目录/logs/
```

---

## 八、关键文件路径

### 8.1 源代码
```
app/src/main/java/erl/webdavtoon/
├── MainActivity.kt (主界面)
├── PhotoViewActivity.kt (Webtoon模式)
├── SettingsActivity.kt (设置页面)
├── PhotoAdapter.kt (瀑布流适配器)
├── WebtoonAdapter.kt (条漫适配器)
├── LogManager.kt (日志管理)
├── SettingsManager.kt (设置管理)
├── PhotoCache.kt (图片缓存)
├── Photo.kt (数据模型)
├── local/ (本地图片仓库)
├── remote/ (WebDAV仓库)
└── api/ (API相关)
```

### 8.2 布局文件
```
app/src/main/res/layout/
├── activity_main.xml
├── activity_photo_view.xml
├── activity_settings.xml
├── item_photo.xml (瀑布流项)
└── item_photo_view.xml (条漫项)
```

### 8.3 配置文件
```
app/build.gradle.kts (依赖配置)
app/src/main/AndroidManifest.xml (权限、Activity声明)
gradle/pullLogs任务 (日志拉取)
```

---

## 九、构建配置

### 9.1 Gradle配置
```kotlin
compileSdk: 36
minSdk: 24
targetSdk: 36
versionCode: 1
versionName: "1.0"

构建特性:
- viewBinding: true
- buildConfig: true
- kotlin-kapt: true (Glide注解处理)
```

### 9.2 自定义任务
```kotlin
pullLogs任务:
- 功能: 从设备拉取日志到项目根目录
- 路径: /storage/emulated/0/Android/data/erl.webdavtoon/files/logs/
- 输出: 项目根目录/logs/
- 触发: preBuild任务依赖
```

---

## 十、总结

这是一个功能完整的图片浏览器应用，主要特点：

1. **双模式浏览**: 瀑布流 + Webtoon条漫模式
2. **多数据源**: 本地图片 + WebDAV远程图片
3. **性能优化**: 三阶段加载、优先级管理、请求取消
4. **日志系统**: 完整的日志记录和拉取机制
5. **设置管理**: 可配置的列数、排序、裁剪等

核心工具链：
- **Glide**: 图片加载和缓存
- **OkHttp**: 网络请求
- **RecyclerView**: 列表显示
- **Kotlin协程**: 异步处理
- **SharedPreferences**: 设置持久化

