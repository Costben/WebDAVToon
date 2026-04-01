# P1-3：权限处理改造

> 涉及文件：FileUtils.kt, AndroidManifest.xml, FolderViewActivity.kt, MainActivity.kt

---

## 现状问题

- 只检查了 `READ_MEDIA_IMAGES`，Android 14+ 需要 `READ_MEDIA_VISUAL_USER_SELECTED`
- 没有处理权限被拒绝后的引导逻辑
- 权限检查分散在多处，没有统一管理

---

## 改造步骤

### 第 1 步：AndroidManifest 增加新权限

```xml
<!-- Android 14+ 精细化媒体权限 -->
<uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
```

- `READ_MEDIA_IMAGES` 在 Android 13+ 仍然需要
- `READ_MEDIA_VISUAL_USER_SELECTED` 是 Android 14+ 新增，用户可选择部分照片

### 第 2 步：新建 PermissionManager

```kotlin
object PermissionManager {

    enum class PermissionState {
        GRANTED,           // 已授权
        DENIED,            // 被拒绝（可再请求）
        PERMANENTLY_DENIED, // 永久拒绝（需引导去设置）
        PARTIAL            // 部分授权（Android 14+ 用户选择了部分照片）
    }

    fun checkStoragePermission(context: Context): PermissionState {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                // Android 14+
                val hasFull = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
                val hasPartial = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED
                when {
                    hasFull -> PermissionState.GRANTED
                    hasPartial -> PermissionState.PARTIAL
                    else -> PermissionState.DENIED
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13
                if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED) {
                    PermissionState.GRANTED
                } else {
                    PermissionState.DENIED
                }
            }
            else -> {
                // Android 12 及以下
                if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED) {
                    PermissionState.GRANTED
                } else {
                    PermissionState.DENIED
                }
            }
        }
    }

    fun shouldShowRationale(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.READ_MEDIA_IMAGES
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
}
```

### 第 3 步：权限状态机实现

```kotlin
// 在 Activity 中
fun handleStoragePermission() {
    when (PermissionManager.checkStoragePermission(this)) {
        PermissionState.GRANTED -> loadData()
        PermissionState.PARTIAL -> {
            // Android 14+ 部分授权，提示用户可去设置选择更多
            showSnackbar("部分照片已授权，可在设置中选择更多") {
                action("去设置") { openAppSettings() }
            }
            loadData() // 部分授权也可以先加载
        }
        PermissionState.DENIED -> {
            if (PermissionManager.shouldShowRationale(this)) {
                showRationaleDialog()
            } else {
                requestPermissionLauncher.launch(getRequiredPermissions())
            }
        }
        PermissionState.PERMANENTLY_DENIED -> {
            showGoToSettingsDialog()
        }
    }
}
```

### 第 4 步：更新 FileUtils

```
当前：fun hasStoragePermission(context: Context): Boolean
改造：委托给 PermissionManager.checkStoragePermission，返回 PermissionState
```

- 删除 `FileUtils.hasStoragePermission`，改用 `PermissionManager`
- 其他需要检查权限的地方统一调用 PermissionManager

### 第 5 步：更新调用方

- `FolderViewActivity` — 启动时权限检查流程改造
- `MainActivity` — 加载图片前权限检查改造
- `SettingsActivity` — 存储相关设置增加权限状态显示

---

## 验证要点

- Android 14 设备上请求权限时弹出"选择照片"对话框
- 用户选择部分照片后，应用能正常加载所选照片
- 权限被拒绝后，再次进入时弹出说明对话框
- 永久拒绝后，引导用户去系统设置页面
- Android 13/12 及以下设备行为不变
