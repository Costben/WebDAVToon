# P1-2：网络请求改造

> 涉及文件：RustWebDavPhotoRepository.kt, WebDAVToonApplication.kt

---

## 现状问题

- `deletePhoto` 和 `deleteFolder` 每次创建新的 `OkHttpClient()`，没有连接复用
- 没有重试机制，网络抖动直接失败
- WebDAV 文件夹删除是逐个 DELETE 请求，没有批量操作

---

## 改造步骤

### 第 1 步：复用 OkHttpClient

```
当前：val client = okhttp3.OkHttpClient()  // 每次新建
改造：
  object WebDavClientProvider {
      private var client: OkHttpClient? = null

      fun getClient(settingsManager: SettingsManager): OkHttpClient {
          if (client == null) {
              client = OkHttpClient.Builder()
                  .connectTimeout(30, TimeUnit.SECONDS)
                  .readTimeout(30, TimeUnit.SECONDS)
                  .writeTimeout(30, TimeUnit.SECONDS)
                  .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                  .addInterceptor { chain ->
                      // Basic Auth 拦截器
                      val username = settingsManager.getWebDavUsername()
                      val password = settingsManager.getWebDavPassword()
                      if (username.isNotEmpty() && password.isNotEmpty()) {
                          val credentials = okhttp3.Credentials.basic(username, password)
                          chain.proceed(chain.request().newBuilder()
                              .header("Authorization", credentials).build())
                      } else {
                          chain.proceed(chain.request())
                      }
                  }
                  .build()
          }
          return client!!
      }

      fun reset() { client = null }
  }
```

- `RustWebDavPhotoRepository.deletePhoto/deleteFolder` 改用共享客户端
- WebDAV 配置变更时调用 `reset()` 重建客户端

### 第 2 步：指数退避重试

```kotlin
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 500,
    maxDelay: Long = 5000,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (attempt == maxRetries - 1) throw e
            delay(currentDelay)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelay)
        }
    }
    throw IllegalStateException("Unreachable")
}
```

- `deletePhoto` 和 `deleteFolder` 内部调用包裹在 `retryWithBackoff` 中
- 只对网络异常重试（IOException），不重试 4xx 业务错误
- 首次失败等 500ms，第二次等 1000ms，第三次等 2000ms

### 第 3 步：文件夹批量删除

```
当前：WebDAV DELETE 方法逐个删除文件，文件夹用 DELETE + Depth: infinity
改造：
  1. 对于 WebDAV 文件夹删除，直接用 DELETE 方法 + Depth: infinity 头
  2. 大多数 WebDAV 服务器支持此特性，一次请求递归删除整个文件夹
  3. 如果服务器不支持，回退到逐个删除
```

```kotlin
val request = Request.Builder()
    .url(encodedUrl)
    .delete()
    .header("Depth", "infinity")
    .build()
```

### 第 4 步：统一认证入口

当前 `WebDAVToonApplication.MyGlideModule` 和 `RustWebDavPhotoRepository` 各自构建认证头：

```
改造：
  1. 所有 WebDAV 请求统一走 WebDavClientProvider
  2. Glide 的 OkHttp 集成也使用同一个 OkHttpClient
  3. 认证逻辑只在一处维护
```

---

## 验证要点

- 网络抖动时删除操作能自动重试
- 删除大量文件夹时请求次数明显减少
- OkHttp 连接池复用正常，无连接泄漏
- WebDAV 配置变更后认证仍然正确
