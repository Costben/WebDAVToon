---
name: waterfall-thumbnail-quality-setting
overview: 在设置页面新增瀑布流缩略图分辨率选项，支持缩放比例（百分比）和最大宽度（px）两种模式，保存到 SettingsManager，WebDavImageLoader 中读取并应用。
todos:
  - id: add-settings-keys
    content: 在 SettingsManager.kt 新增瀑布流质量模式的3个KEY常量及对应getter/setter
    status: completed
  - id: update-image-loader
    content: 修改 WebDavImageLoader.kt 的 isWaterfall 分支，读取 SettingsManager 动态应用缩放比例或最大宽度
    status: completed
    dependencies:
      - add-settings-keys
  - id: update-ui-resources
    content: 在 activity_settings_md3.xml 中新增 setting_thumbnail_quality 条目，并在 strings.xml 和 strings-zh.xml 中补充所有相关字符串
    status: completed
  - id: update-settings-activity
    content: 在 SettingsActivity.kt 新增 showThumbnailQualityDialog() 对话框及 setupItems/refreshUi 绑定逻辑
    status: completed
    dependencies:
      - add-settings-keys
      - update-ui-resources
  - id: build-and-deploy
    content: 编译 assembleDebug，通过 adb 安装到手机，使用 [mcp:telegram] 发送完成通知
    status: completed
    dependencies:
      - update-image-loader
      - update-settings-activity
---

## 用户需求

在设置页面新增一个"瀑布流缩略图质量"选项，支持两种分辨率控制模式：

### 核心功能

1. **缩放比例模式**：以百分比（%）输入，瀑布流缩略图按原图的指定百分比加载，范围 10%~100%，默认 70%

2. **最大宽度模式**：以像素（px）输入最大宽度，瀑布流缩略图加载时限制最大宽度，默认 600px

### 交互说明

- 设置页"显示"分组中新增"缩略图质量"条目，点击弹出配置对话框
- 对话框内先选择模式（缩放比例 / 最大宽度），再输入对应数值
- 摘要文本实时反映当前配置，如"缩放比例：70%"或"最大宽度：600px"
- 设置生效后，重新加载瀑布流图片时应用新参数（Glide 缓存不自动清除，用户可手动清除缓存）

## 技术栈

- **语言**：Kotlin（沿用项目现有栈）
- **图片加载**：Glide（现有，`WebDavImageLoader.kt`）
- **持久化**：SharedPreferences（现有，`SettingsManager.kt`）
- **UI**：Material3 对话框（`MaterialAlertDialogBuilder`，现有模式）

---

## 实现思路

### 整体策略

在现有三层结构（SettingsManager 存储 → WebDavImageLoader 读取 → PhotoAdapter 调用）上做最小侵入改动：

1. **SettingsManager** 新增 2 个 KEY 和对应 getter/setter
2. **WebDavImageLoader** 的 `isWaterfall` 分支读取设置动态计算参数，替换硬编码的 `sizeMultiplier(0.7f)`
3. **SettingsActivity** 新增一个设置条目和对话框
4. **布局 & 字符串** 补充对应资源

### 关键决策

- **两种模式用同一个 Key 控制**：`KEY_WATERFALL_QUALITY_MODE`（值 `"percent"` 或 `"max_width"`），避免逻辑分支混乱
- **WebDavImageLoader 中读取 Context**：两个 load 方法已有 `context` 参数，直接构造 `SettingsManager(context)` 即可，与现有 `loadWebDavImage` 读取用户名密码的方式一致
- **缩放比例模式**：使用 `sizeMultiplier(percent / 100f)`，配合 `override(Target.SIZE_ORIGINAL)` + `DownsampleStrategy.AT_MOST`，与现有逻辑一致
- **最大宽度模式**：使用 `override(maxWidth, Target.SIZE_ORIGINAL)`，Glide 会按宽度限制缩放高度自适应
- **不自动清缓存**：Glide 磁盘缓存是基于 URL key 的，改变分辨率后只影响新加载的图片；用户可通过现有"清除缓存"手动刷新，这与 Android 主流图片应用惯例一致

### 性能说明

- `SettingsManager(context)` 在每次 `loadWebDavImage` 调用时构造，但由于 SharedPreferences 自身有内存缓存，实际 IO 开销可忽略。与现有用户名密码读取完全相同的模式

---

## 目录结构

```
app/src/main/
├── java/erl/webdavtoon/
│   ├── SettingsManager.kt          [MODIFY] 新增 KEY_WATERFALL_QUALITY_MODE / KEY_WATERFALL_PERCENT / KEY_WATERFALL_MAX_WIDTH 三个常量及对应 getter/setter
│   ├── WebDavImageLoader.kt        [MODIFY] isWaterfall 分支改为读取 SettingsManager，根据模式动态应用 sizeMultiplier 或 override(maxWidth)；loadLocalImage 同步修改
│   └── SettingsActivity.kt         [MODIFY] 新增 binding.settingThumbnailQuality 的点击绑定和 showThumbnailQualityDialog() 方法；refreshUi() 中补充摘要更新
├── res/
│   ├── layout/
│   │   └── activity_settings_md3.xml  [MODIFY] Display 分组内 setting_sort_order 之后插入一行 include，id=setting_thumbnail_quality
│   └── values/
│       └── strings.xml             [MODIFY] 新增 thumbnail_quality / thumbnail_quality_percent_mode / thumbnail_quality_max_width_mode / thumbnail_quality_summary_percent / thumbnail_quality_summary_max_width / thumbnail_quality_enter_percent / thumbnail_quality_enter_max_width 字符串
│   └── values-zh/
│       └── strings.xml             [MODIFY] 同步新增上述字符串的中文翻译
```

## Agent Extensions

### MCP

- **telegram**
- Purpose：每完成一个关键步骤（编译、安装）后通过 `send_telegram_notification` 向用户发送进度通知
- Expected outcome：用户在手机上实时收到"编译成功 / 安装到设备完成"的通知通知