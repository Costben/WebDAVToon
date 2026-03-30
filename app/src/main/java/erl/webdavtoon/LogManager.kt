package erl.webdavtoon

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日志管理类
 */
object LogManager {
    private var minLogLevel = Log.INFO
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    fun initialize(context: Context) {
        val logDir = File(context.getExternalFilesDir(null), "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        
        // 清理旧日志（保持最多10个）
        val files = logDir.listFiles()?.sortedByDescending { it.lastModified() }
        if (files != null && files.size > 10) {
            files.subList(10, files.size).forEach { it.delete() }
        }

        val fileName = "${fileDateFormat.format(Date())}.log"
        logFile = File(logDir, fileName)
    }

    fun setMinLogLevel(level: Int) {
        minLogLevel = level
    }

    fun log(message: String, level: Int = Log.DEBUG, tag: String = "WebDAVToon") {
        // 在 Release 版本中，跳过 VERBOSE 和 DEBUG 级别的日志处理
        if (!BuildConfig.DEBUG && (level == Log.VERBOSE || level == Log.DEBUG)) {
            return
        }

        if (level >= minLogLevel) {
            when (level) {
                Log.VERBOSE -> Log.v(tag, message)
                Log.DEBUG -> Log.d(tag, message)
                Log.INFO -> Log.i(tag, message)
                Log.WARN -> Log.w(tag, message)
                Log.ERROR -> Log.e(tag, message)
            }
            writeToFile("[$tag] ${getLogLevelName(level)}: $message")
        }
    }

    private fun writeToFile(text: String) {
        logFile?.let {
            try {
                val timestamp = dateFormat.format(Date())
                FileWriter(it, true).use { writer ->
                    writer.append("$timestamp $text\n")
                }
            } catch (e: Exception) {
                Log.e("LogManager", "Failed to write log to file", e)
            }
        }
    }

    private fun getLogLevelName(level: Int): String = when (level) {
        Log.VERBOSE -> "VERBOSE"
        Log.DEBUG -> "DEBUG"
        Log.INFO -> "INFO"
        Log.WARN -> "WARN"
        Log.ERROR -> "ERROR"
        else -> "UNKNOWN"
    }

    fun shutdown() {
        logFile = null
    }
}
