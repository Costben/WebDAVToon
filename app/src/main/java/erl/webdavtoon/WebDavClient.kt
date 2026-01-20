package erl.webdavtoon

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * WebDAV 资源模型
 */
data class WebDavResource(
    val href: String,
    val displayName: String,
    val isCollection: Boolean,
    val contentLength: Long = 0,
    val lastModified: String = ""
)

/**
 * WebDAV 客户端
 */
class WebDavClient(private val settingsManager: SettingsManager) {
    companion object {
        private val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 20
            maxRequestsPerHost = 10
        }
        
        private val client = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .authenticator { _, response ->
                // 如果收到 401 错误，重试认证
                if (response.code == 401 && response.priorResponse == null) {
                    val settings = response.request.tag(SettingsManager::class.java)
                    val username = settings?.getWebDavUsername() ?: ""
                    val password = settings?.getWebDavPassword() ?: ""
                        
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        val credentials = "$username:$password"
                        val base64 = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                        return@authenticator response.request.newBuilder()
                            .header("Authorization", "Basic $base64")
                            .build()
                    }
                }
                null
            }
            .build()
    }

    private fun getAuthHeader(): String {
        val username = settingsManager.getWebDavUsername()
        val password = settingsManager.getWebDavPassword()
        if (username.isEmpty()) return ""
        val credentials = "$username:$password"
        return "Basic ${Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)}"
    }

    suspend fun listFiles(path: String, depth: String = "1"): List<WebDavResource> = withContext(Dispatchers.IO) {
        val baseUrl = settingsManager.getFullWebDavUrl().trimEnd('/')
        val baseUri = android.net.Uri.parse(baseUrl)
        val hostWithScheme = "${baseUri.scheme}://${baseUri.authority}"
        val basePath = baseUri.path ?: ""
        
        var fullUrl = when {
            path.startsWith("http") -> path
            path.startsWith("/") -> {
                // 如果 path 是 "/"，则表示请求 WebDAV 根目录（baseUrl）
                if (path == "/") {
                    baseUrl
                }
                // 如果 path 已经包含了 basePath (例如 /dav/folder 且 basePath 是 /dav)
                else if (basePath.isNotEmpty() && path.startsWith(basePath)) {
                    "$hostWithScheme${path}"
                }
                // 其他绝对路径情况
                else {
                    "$hostWithScheme${path}"
                }
            }
            path.isEmpty() -> baseUrl
            else -> "$baseUrl/${path}"
        }


        // WebDAV 规范建议对集合（文件夹）请求使用以 / 结尾的 URL
        if (!fullUrl.endsWith("/")) {
            fullUrl += "/"
        }

        android.util.Log.d("WebDavClient", "Requesting PROPFIND: $fullUrl (original path: $path, baseUrl: $baseUrl)")
        
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <propfind xmlns="DAV:">
              <prop>
                <displayname/>
                <resourcetype/>
                <getcontentlength/>
                <getlastmodified/>
              </prop>
            </propfind>
        """.trimIndent()

        val request = Request.Builder()
            .url(fullUrl)
            .tag(SettingsManager::class.java, settingsManager)
            .addHeader("Authorization", getAuthHeader())
            .addHeader("Depth", depth)
            .addHeader("Accept", "text/xml, application/xml")
            .addHeader("User-Agent", "WebDAVToon/1.0")
            .method("PROPFIND", propfindBody.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                android.util.Log.d("WebDavClient", "Response received: ${response.code}")
                if (!response.isSuccessful) {
                    android.util.Log.e("WebDavClient", "WebDAV Error: ${response.code} for $fullUrl")
                    throw Exception("WebDAV request failed: ${response.code}")
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                parseWebDavResponse(body)
            }
        } catch (e: Exception) {
            android.util.Log.e("WebDavClient", "Request failed for $fullUrl", e)
            throw e
        }
    }

    private fun parseWebDavResponse(xml: String): List<WebDavResource> {
        val resources = mutableListOf<WebDavResource>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentHref = ""
            var currentDisplayName = ""
            var isCollection = false
            var contentLength = 0L
            var lastModified = ""
            val textBuilder = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name?.lowercase()
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        textBuilder.setLength(0)
                        if (name == "collection") {
                            isCollection = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        textBuilder.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        val currentText = textBuilder.toString().trim()
                        when (name) {
                            "href" -> currentHref = currentText
                            "displayname" -> currentDisplayName = currentText
                            "getcontentlength" -> contentLength = currentText.toLongOrNull() ?: 0L
                            "getlastmodified" -> lastModified = currentText
                            "response" -> {
                                if (currentHref.isNotEmpty()) {
                                    val decodedHref = Uri.decode(currentHref)
                                    if (currentDisplayName.isEmpty()) {
                                        currentDisplayName = decodedHref.trimEnd('/').split('/').last()
                                    }
                                    resources.add(WebDavResource(currentHref, currentDisplayName, isCollection, contentLength, lastModified))
                                }
                                // Reset for next response
                                currentHref = ""
                                currentDisplayName = ""
                                isCollection = false
                                contentLength = 0L
                                lastModified = ""
                            }
                        }
                        textBuilder.setLength(0)
                    }
                }
                eventType = parser.next()
            }
            android.util.Log.d("WebDavClient", "Parsed ${resources.size} resources")
        } catch (e: Exception) {
            android.util.Log.e("WebDavClient", "Error parsing WebDAV XML", e)
        }
        return resources
    }
}
