package erl.webdavtoon

import android.app.Application
import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.module.AppGlideModule
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

class WebDAVToonApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        appContext = applicationContext
        AppSettingsStore.prime(this)
        LogManager.initialize(this)
        AppDatabase.getInstance(this)
        FavoritePhotoStore.getInstance(this)
        runBlocking(Dispatchers.IO) {
            ConfigMigration.migrateIfNeeded(this@WebDAVToonApplication)
            WebDavCredentialMigration.migrateIfNeeded(this@WebDAVToonApplication)
        }

        try {
            try {
                System.loadLibrary("rust_core")
                LogManager.log("System.loadLibrary(\"rust_core\") succeeded", Log.INFO)
            } catch (t: Throwable) {
                LogManager.log("System.loadLibrary(\"rust_core\") failed: ${t.message}", Log.WARN)
            }

            try {
                val libraryPath = applicationInfo.nativeLibraryDir
                System.setProperty("jna.library.path", libraryPath)
                LogManager.log("Set jna.library.path to: $libraryPath", Log.INFO)
            } catch (t: Throwable) {
                LogManager.log("Failed to set jna.library.path: ${t.message}", Log.WARN)
            }

            uniffi.rust_core.initLogger()
            val greeting = uniffi.rust_core.helloFromRust("Android")
            LogManager.log("Rust Core initialized: $greeting", Log.INFO)

            val dbPath = getDatabasePath("rust_core.db").absolutePath
            rustRepository = uniffi.rust_core.RustRepository(dbPath)
            rustInitError = null
            LogManager.log("RustRepository initialized with db: $dbPath", Log.INFO)
            Log.i("WebDAVToon", "RustRepository initialized with db: $dbPath")
        } catch (t: Throwable) {
            rustInitError = t.message ?: t.javaClass.simpleName
            LogManager.log("Failed to initialize Rust Core: ${t.message}", Log.ERROR)
            Log.e("WebDAVToon", "Rust Core Init Failed", t)
        }

        val settingsManager = SettingsManager(this)
        val logLevel = settingsManager.getLogLevel()
        LogManager.setMinLogLevel(logLevel)

        LogManager.log("Application started", Log.INFO)
        LogManager.log("Log level set to: $logLevel (${getLogLevelName(logLevel)})", Log.INFO)
    }

    private fun getLogLevelName(level: Int): String {
        return when (level) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            else -> "UNKNOWN"
        }
    }

    override fun onTerminate() {
        LogManager.shutdown()
        super.onTerminate()
    }

    companion object {
        var rustRepository: uniffi.rust_core.RustRepository? = null
        var rustInitError: String? = null
        lateinit var appContext: Context
    }
}

@com.bumptech.glide.annotation.GlideModule
class MyGlideModule : AppGlideModule() {

    private fun responseCount(response: Response): Int {
        var result = 1
        var current = response.priorResponse
        while (current != null) {
            result++
            current = current.priorResponse
        }
        return result
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val diskCacheSizeBytes = 2L * 1024 * 1024 * 1024L
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeBytes))

        val sourceExecutor = com.bumptech.glide.load.engine.executor.GlideExecutor.newSourceBuilder()
            .setThreadCount(20)
            .setName("source-executor")
            .setUncaughtThrowableStrategy(com.bumptech.glide.load.engine.executor.GlideExecutor.UncaughtThrowableStrategy.LOG)
            .build()
        builder.setSourceExecutor(sourceExecutor)
    }

    override fun registerComponents(context: Context, glide: Glide, registry: com.bumptech.glide.Registry) {
        Log.d("MyGlideModule", "registerComponents called")

        val settingsManager = SettingsManager(context)
        Log.d("MyGlideModule", "Registering dynamic OkHttp integration for WebDAV")

        val connectionPool = ConnectionPool(20, 5, TimeUnit.MINUTES)
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 20
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .dispatcher(dispatcher)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()

                if (settingsManager.isWebDavEnabled()) {
                    val username = settingsManager.getWebDavUsername()
                    val password = settingsManager.getWebDavPassword()

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
            .authenticator { _, response ->
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

        registry.replace(
            com.bumptech.glide.load.model.GlideUrl::class.java,
            java.io.InputStream::class.java,
            com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader.Factory(okHttpClient)
        )

        Log.d("MyGlideModule", "Dynamic OkHttp integration registered successfully")
    }

    override fun isManifestParsingEnabled(): Boolean = false
}
