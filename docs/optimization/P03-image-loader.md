# P1-1：图片加载策略改造

> 涉及文件：WebDavImageLoader.kt, WebDAVToonApplication.kt (MyGlideModule)

---

## 现状问题

- 缩略图模式硬编码 `320x320`，不考虑屏幕 DPI 和列数
- 瀑布流质量控制只有两种模式（百分比/最大宽度），没有自适应
- 使用 `GlobalScope.launch` 清除缓存，有生命周期泄漏风险
- 缓存只有 Glide 的 L2 磁盘缓存，没有 L1 内存优化策略

---

## 改造步骤

### 第 1 步：动态计算缩略图尺寸

```
当前：requestOptions.override(320, 320)
改造：
  fun calculateThumbnailSize(context: Context, columns: Int): Int {
      val displayMetrics = context.resources.displayMetrics
      val screenWidth = displayMetrics.widthPixels
      val cellWidth = screenWidth / columns
      return min(cellWidth * 2, 1024) // 2 倍采样，最大 1024px
  }
```

- 文件夹预览图也改为动态计算，不再硬编码
- 根据列数自动调整，1 列时加载更大图，4 列时加载更小图

### 第 2 步：瀑布流自适应模式

在 SettingsManager 中新增第三种模式：

```kotlin
const val WATERFALL_MODE_AUTO = "auto"
```

自适应逻辑：

```kotlin
fun getAutoQuality(context: Context): Int {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val network = cm.activeNetwork
    val caps = cm.getNetworkCapabilities(network)
    val bandwidth = caps?.linkDownstreamBandwidthKbps ?: 0

    return when {
        bandwidth > 10_000 -> 100 // WiFi/5G，全质量
        bandwidth > 5_000 -> 70   // 4G，70%
        bandwidth > 1_000 -> 50   // 3G，50%
        else -> 30                 // 弱网，30%
    }
}
```

- 设置页面增加"自适应"选项
- `WebDavImageLoader` 在 auto 模式下根据网络状态选择加载质量

### 第 3 步：分层缓存策略

当前只有 Glide 的磁盘缓存，增加内存缓存感知：

```kotlin
// 在 MyGlideModule.applyOptions 中
val memoryCacheSize = (Runtime.getRuntime().maxMemory() / 8).toInt() // 1/8 最大内存
builder.setMemoryCache(LruResourceCache(memoryCacheSize.toLong()))
```

- 增大 Glide 内存缓存到可用内存的 1/8（当前默认 1/10）
- 对于 WebDAV 远程图片，增加"预加载下一屏"逻辑
- 磁盘缓存当前 2GB，保持不变

### 第 4 步：消除 GlobalScope

```
当前：WebDavImageLoader.clearCache 中使用 GlobalScope.launch
改造：
  fun clearCache(scope: CoroutineScope, context: Context) {
      scope.launch(Dispatchers.Main) {
          Glide.get(context).clearMemory()
          withContext(Dispatchers.IO) {
              Glide.get(context).clearDiskCache()
          }
      }
  }
```

- `clearCache` 方法签名增加 `scope: CoroutineScope` 参数
- 调用方传入 `lifecycleScope`，确保生命周期安全

### 第 5 步：WebDavImageLoader 去重

当前 `loadWebDavImage` 和 `loadLocalImage` 有大量重复代码（RequestOptions 构建逻辑完全一样）：

```
改造：
  1. 抽取公共方法 buildRequestOptions(isWaterfall, limitSize, settings)
  2. loadWebDavImage 和 loadLocalImage 只负责构建 model（GlideUrl vs Uri）
  3. 公共部分调用 buildRequestOptions
```

---

## 验证要点

- 不同列数下缩略图清晰度合理
- 弱网环境下载流量明显减少
- 大量图片浏览后内存占用稳定
- 清除缓存不会导致内存泄漏
