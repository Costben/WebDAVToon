package erl.webdavtoon

import android.util.Log
import com.bumptech.glide.load.model.GlideUrl
import java.net.URLEncoder

/**
 * Maps stable virtual media URIs onto fetchable HTTP URLs.
 *
 * - `http(s)://…` (WebDAV): passes through percent-encoding unchanged — the
 *   existing direct-HTTP path with Basic auth.
 * - `smb://…`, `ftp://…`: rewritten to the Rust loopback media proxy
 *   (`http://127.0.0.1:{port}/{token}/{encoded-rel-path}`). The proxy token
 *   is the auth; no Authorization header must be attached.
 *
 * Stored URIs (favorites, preview caches, Glide cache keys) always stay in
 * virtual form; conversion happens at the moment a fetch URL is needed.
 */
object RemoteMediaUrlResolver {

    private const val TAG = "RemoteMediaUrlResolver"

    @Volatile
    private var cachedProxy: uniffi.rust_core.MediaProxyInfo? = null

    fun isProxyScheme(uri: String): Boolean =
        uri.startsWith("smb://", ignoreCase = true) || uri.startsWith("ftp://", ignoreCase = true)

    /** Replacement for the historical `startsWith("http")` remote checks. */
    fun isRemoteMediaUri(uri: String): Boolean =
        uri.startsWith("http", ignoreCase = true) || isProxyScheme(uri)

    /** Only direct-HTTP (WebDAV) requests carry Basic auth headers. */
    fun needsBasicAuth(uri: String): Boolean = uri.startsWith("http", ignoreCase = true)

    /**
     * Resolves a virtual/remote URI to a fetchable, percent-encoded HTTP URL.
     * Returns null when a proxy-scheme URI cannot be served: proxy startup
     * failure, or a URI minted by a different slot (prefix mismatch).
     */
    fun resolveForHttp(settingsManager: SettingsManager, uriString: String): String? {
        if (!isProxyScheme(uriString)) {
            return FileUtils.encodeWebDavUrl(uriString)
        }

        val base = settingsManager.getFullWebDavUrl()
        val relPath = computeRelPath(base, uriString)
        if (relPath == null) {
            Log.w(TAG, "URI is not servable from the current slot: $uriString (base=$base)")
            return null
        }
        val proxy = proxyInfo() ?: return null
        return "http://127.0.0.1:${proxy.port}/${proxy.token}/${encodeRelPath(relPath)}"
    }

    /**
     * Strips the slot's canonical endpoint prefix off a virtual URI. Null when
     * the URI does not belong to the given endpoint (e.g. favorite from
     * another slot) or degenerates to an empty path. Pure function (no
     * android.util.Log) so it stays plain-JVM-testable.
     */
    internal fun computeRelPath(fullEndpointUrl: String, uriString: String): String? {
        val base = fullEndpointUrl.trimEnd('/')
        if (base.isEmpty() || !uriString.startsWith(base, ignoreCase = true)) {
            return null
        }
        return uriString.substring(base.length).trimStart('/').ifEmpty { null }
    }

    private fun proxyInfo(): uniffi.rust_core.MediaProxyInfo? {
        cachedProxy?.let { return it }
        return try {
            uniffi.rust_core.ensureMediaProxy().also { cachedProxy = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media proxy", e)
            null
        }
    }

    /** Percent-encodes each path segment, keeping '/' separators. */
    internal fun encodeRelPath(relPath: String): String {
        return relPath.split('/').joinToString("/") { segment ->
            if (segment.isEmpty()) "" else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
    }
}

/**
 * GlideUrl whose disk-cache key is the stable virtual URI rather than the
 * proxy URL. The proxy port and token change every process start; without
 * this, every smb/ftp image would re-download after each app restart.
 */
class StableKeyGlideUrl(
    url: String,
    private val stableKey: String
) : GlideUrl(url) {
    override fun getCacheKey(): String = stableKey
}
