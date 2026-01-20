package erl.webdavtoon

import android.app.Application
import android.util.Base64
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

/**
 * Application 类，用于全局配置
 */
class WebDAVToonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 初始化日志管理器
        LogManager.initialize(this)
        
        // 读取并设置日志级别
        val settingsManager = SettingsManager(this)
        val logLevel = settingsManager.getLogLevel()
        LogManager.setMinLogLevel(logLevel)
        
        // 记录应用启动
        LogManager.log("Application started", android.util.Log.INFO)
        LogManager.log("Log level set to: ${logLevel} (${getLogLevelName(logLevel)})", android.util.Log.INFO)
    }
    
    /**
     * 获取日志级别名称
     */
    private fun getLogLevelName(level: Int): String {
        return when (level) {
            android.util.Log.VERBOSE -> "VERBOSE"
            android.util.Log.DEBUG -> "DEBUG"
            android.util.Log.INFO -> "INFO"
            android.util.Log.WARN -> "WARN"
            android.util.Log.ERROR -> "ERROR"
            else -> "UNKNOWN"
        }
    }
    
    override fun onTerminate() {
        // 应用终止时关闭日志管理器
        LogManager.shutdown()
        super.onTerminate()
    }
}

/**
 * Glide 模块，配置 WebDAV 认证和缓存策略
 */
@com.bumptech.glide.annotation.GlideModule
class MyGlideModule : AppGlideModule() {
    
    /**
     * 计算响应链中的响应数量（用于限制重试次数）
     */
    private fun responseCount(response: Response): Int {
        var result = 1
        var current = response.priorResponse
        while (current != null) {
            result++
            current = current.priorResponse
        }
        return result
    }
    override fun applyOptions(context: android.content.Context, builder: GlideBuilder) {
        // 配置内存缓存大小（增加到200MB，支持更多webtoon图片缓存，充分利用内网带宽）
        val memoryCacheSizeBytes = 200 * 1024 * 1024L
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes))
        
        // 配置磁盘缓存大小（增加到2GB，充分利用内网带宽缓存更多图片和缩略图）
        val diskCacheSizeBytes = 2L * 1024 * 1024 * 1024L
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeBytes))
        
        // 注意：线程池配置已移除，因为 GlideExecutor 的 API 在不同版本中可能不同
        // 并发加载限制将通过 OkHttpClient 的连接池和 Dispatcher 来实现
    }
    
    override fun registerComponents(context: android.content.Context, glide: Glide, registry: com.bumptech.glide.Registry) {
        android.util.Log.d("MyGlideModule", "registerComponents called")
        
        // 获取 WebDAV 认证信息
        val settingsManager = SettingsManager(context)
        
        // 始终注册 OkHttp 集成，以便支持动态切换和认证
        android.util.Log.d("MyGlideModule", "Registering dynamic OkHttp integration for WebDAV")
        
        // WebDAV 专用配置：低并发，避免服务器过载
        val connectionPool = okhttp3.ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES
        )
        
        val dispatcher = okhttp3.Dispatcher()
        dispatcher.maxRequests = 5
        dispatcher.maxRequestsPerHost = 3
        
        val okHttpClient = OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .dispatcher(dispatcher)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                
                // 动态检查是否启用 WebDAV 并获取当前槽位的凭据
                if (settingsManager.isWebDavEnabled()) {
                    val username = settingsManager.getWebDavUsername()
                    val password = if (settingsManager.isWebDavRememberPassword()) {
                        settingsManager.getWebDavPassword()
                    } else {
                        ""
                    }
                    
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        val credentials = "$username:$password"
                        val base64 = Base64.encode(credentials.toByteArray(), Base64.NO_WRAP)
                        val credential = "Basic ${String(base64)}"
                        
                        val authenticatedRequest = originalRequest.newBuilder()
                            .header("Authorization", credential)
                            .build()
                        return@addInterceptor chain.proceed(authenticatedRequest)
                    }
                }
                
                chain.proceed(originalRequest)
            }
            .authenticator { route, response ->
                // 如果收到 401 错误，且启用了 WebDAV，重试认证
                if (response.code == 401 && responseCount(response) < 3 && settingsManager.isWebDavEnabled()) {
                    val username = settingsManager.getWebDavUsername()
                    val password = settingsManager.getWebDavPassword()
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        val credentials = "$username:$password"
                        val base64 = Base64.encode(credentials.toByteArray(), Base64.NO_WRAP)
                        val credential = "Basic ${String(base64)}"
                        return@authenticator response.request.newBuilder()
                            .header("Authorization", credential)
                            .build()
                    }
                }
                null
            }
            .build()
        
        // 注册 OkHttp 集成
        registry.replace(
            com.bumptech.glide.load.model.GlideUrl::class.java,
            java.io.InputStream::class.java,
            com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader.Factory(okHttpClient)
        )
        
        android.util.Log.d("MyGlideModule", "Dynamic OkHttp integration registered successfully")
    }
    
    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}

