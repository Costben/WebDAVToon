# P3-2：日志系统改造

> 涉及文件：LogManager.kt, 全项目调用点

---

## 现状问题

- 使用 `android.util.Log` 原始 API，没有结构化日志
- 没有日志轮转，文件会无限增长
- 日志级别控制分散，没有统一入口
- 文件日志写入可能阻塞主线程

---

## 改造步骤

### 第 1 步：引入 Timber

在 `build.gradle.kts` 添加依赖：

```kotlin
implementation("com.jakewharton.timber:timber:5.0.1")
```

### 第 2 步：配置 Timber Tree

```kotlin
class AppTimberTree(
    private val minLogLevel: Int = Log.VERBOSE
) : Timber.DebugTree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= minLogLevel
    }

    override fun createStackElementTag(element: StackTraceElement): String {
        return "(${element.fileName}:${element.lineNumber})"
    }
}

class FileTimberTree(
    private val logDir: File,
    private val maxFileSize: Long = 5 * 1024 * 1024, // 5MB
    private val maxFiles: Int = 3
) : Timber.Tree() {

    private var currentFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "?"
        }
        val timestamp = dateFormat.format(Date())
        val logLine = "$timestamp $level/${tag ?: "?"}: $message${if (t != null) "\n${t.stackTraceToString()}" else ""}\n"

        synchronized(this) {
            rotateIfNeeded()
            currentFile?.appendText(logLine)
        }
    }

    private fun rotateIfNeeded() {
        if (currentFile == null) {
            currentFile = File(logDir, "app.log")
        }
        if (currentFile!!.length() > maxFileSize) {
            // 轮转：app.log.2 → app.log.3, app.log.1 → app.log.2, app.log → app.log.1
            for (i in maxFiles downTo 2) {
                val old = File(logDir, "app.log.${i - 1}")
                val new = File(logDir, "app.log.$i")
                if (old.exists()) old.renameTo(new)
            }
            currentFile!!.renameTo(File(logDir, "app.log.1"))
            currentFile = File(logDir, "app.log")
        }
    }
}
```

### 第 3 步：初始化 Timber

在 `WebDAVToonApplication.onCreate()` 中：

```kotlin
override fun onCreate() {
    super.onCreate()
    // ...
    Timber.plant(AppTimberTree(settingsManager.getLogLevel()))
    val logDir = File(filesDir, "logs")
    logDir.mkdirs()
    Timber.plant(FileTimberTree(logDir))
}
```

### 第 4 步：迁移现有调用

```
当前：android.util.Log.d("Tag", "message")
改造：Timber.d("message")

当前：android.util.Log.e("Tag", "message", e)
改造：Timber.e(e, "message")
```

- 全局替换 `android.util.Log.` 为 `Timber.`
- tag 由 Timber 自动从调用栈获取（文件名+行号）
- 移除手动传入的 tag 字符串

### 第 5 步：删除旧 LogManager

```
当前：LogManager.kt (82 行) 手动管理日志级别和文件写入
改造：
  1. 删除 LogManager 类
  2. 所有 LogManager.log() 调用替换为 Timber.d/i/w/e
  3. 设置页面的日志级别设置改为控制 Timber 的 minLogLevel
```

### 第 6 步：设置页面增加日志导出

```kotlin
fun exportLogs(context: Context): File? {
    val logDir = File(context.filesDir, "logs")
    val allLogs = logDir.listFiles()?.sortedByDescending { it.lastModified() }
    val exportFile = File(context.cacheDir, "webdavtoon_logs_${System.currentTimeMillis()}.txt")
    allLogs?.forEach { file ->
        exportFile.appendText("=== ${file.name} ===\n")
        exportFile.appendText(file.readText())
        exportFile.appendText("\n")
    }
    return exportFile
}
```

- 设置页面增加"导出日志"按钮
- 日志文件按大小轮转，最多保留 3 个文件（当前 + 2 个归档）

---

## 验证要点

- 日志输出格式统一，包含时间戳和文件位置
- 日志文件不超过 5MB，自动轮转
- 最多保留 3 个日志文件
- 设置页面切换日志级别后实时生效
- 日志导出功能正常
