# P2-2：文件夹分组算法改造

> 涉及文件：LocalPhotoRepository.kt

---

## 现状问题

- 用硬编码的 `commonRoots = listOf("Pictures", "DCIM", "Download", "Movies")` 做路径分组
- 文件夹名称翻译散落在多个 `when` 分支里（3 处重复代码）
- 路径解析逻辑复杂，有两套分支（rootPath 为空 / 非空），容易出错

---

## 改造步骤

### 第 1 步：利用 MediaStore BUCKET 分组

```
当前：用 DATA 列做字符串路径解析 → 手动分组
改造：用 BUCKET_ID + BUCKET_DISPLAY_NAME 列，由系统提供标准分组
```

```kotlin
val projection = arrayOf(
    MediaStore.Images.Media._ID,
    MediaStore.Images.Media.DATA,
    MediaStore.Images.Media.DATE_MODIFIED,
    MediaStore.Images.Media.BUCKET_ID,
    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
)
```

- `BUCKET_DISPLAY_NAME` 是系统提供的文件夹显示名，不需要手动解析路径
- `BUCKET_ID` 是文件夹的唯一标识，用它做 key 比路径字符串更可靠

### 第 2 步：统一文件夹名翻译

```
当前：3 个 when 分支都做 "Pictures" -> R.string.folder_pictures 映射
改造：
  private fun translateFolderName(context: Context, name: String): String {
      return when (name) {
          "Pictures" -> context.getString(R.string.folder_pictures)
          "DCIM" -> context.getString(R.string.folder_dcim)
          "Download" -> context.getString(R.string.folder_download)
          "Movies" -> context.getString(R.string.folder_movies)
          else -> name
      }
  }
```

- 所有翻译逻辑集中在一个函数
- 3 个 when 分支全部替换为 `translateFolderName(context, bucketName)`

### 第 3 步：简化 getFolders 逻辑

```
当前：rootPath 为空时有复杂路径解析 + commonRoots 查找
改造：
  1. rootPath 为空时，按 BUCKET_ID 分组
  2. 每个 BUCKET 对应一个 Folder
  3. folderKey 直接用 BUCKET_ID（Int），不再用路径字符串
  4. folderName 直接用 BUCKET_DISPLAY_NAME 翻译
```

```kotlin
cursor.use {
    val bucketIdCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
    val bucketNameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
    
    while (it.moveToNext()) {
        val bucketId = it.getLong(bucketIdCol)
        val bucketName = translateFolderName(context, it.getString(bucketNameCol) ?: "Unknown")
        // 按 bucketId 分组聚合
    }
}
```

- 删除 `commonRoots` 硬编码列表
- 删除路径解析的复杂分支逻辑
- 代码行数预计从 150 行减少到 60 行

### 第 4 步：处理 rootPath 非空的情况

```
当前：用 startsWith 做路径匹配
改造：
  1. 如果 rootPath 对应某个 BUCKET_ID，直接按 BUCKET 查询
  2. 如果 rootPath 是更深层路径，仍用 DATA LIKE 过滤，但分组仍用 BUCKET
```

- rootPath 非空时，先查出该路径下所有图片的 BUCKET_ID
- 再按这些 BUCKET_ID 分组生成 Folder 列表

---

## 验证要点

- 文件夹列表与系统相册一致
- 中文文件夹名正确显示
- 有子文件夹的目录正确标记 hasSubFolders
- rootPath 非空时子文件夹浏览正确
