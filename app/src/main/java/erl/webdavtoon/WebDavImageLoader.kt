package erl.webdavtoon

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder

object WebDavImageLoader {

    fun loadWebDavImage(
        context: Context,
        imageUri: Uri,
        imageView: ImageView,
        progressBar: ProgressBar? = null,
        limitSize: Boolean = true,
        isWaterfall: Boolean = false
    ) {
        val requestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)
            .priority(Priority.HIGH)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)

        if (isWaterfall) {
            // 瀑布流模式：原图分辨率的 70%
            requestOptions.override(Target.SIZE_ORIGINAL)
            requestOptions.sizeMultiplier(0.7f)
            requestOptions.downsample(DownsampleStrategy.AT_MOST)
        } else if (limitSize) {
            // 普通缩略图模式（如文件夹预览）：固定 320x320
            requestOptions.override(320, 320)
            requestOptions.downsample(DownsampleStrategy.AT_MOST)
        } else {
            // 全图模式（如详情页）：原分辨率
            requestOptions.override(Target.SIZE_ORIGINAL)
            requestOptions.downsample(DownsampleStrategy.NONE)
        }

        val settings = SettingsManager(context)
        val username = settings.getWebDavUsername()
        val password = settings.getWebDavPassword()

        val model: Any = if (username.isNotEmpty() && password.isNotEmpty()) {
            val auth = "Basic " + android.util.Base64.encodeToString(
                "$username:$password".toByteArray(),
                android.util.Base64.NO_WRAP
            )
            GlideUrl(
                encodeWebDavUrl(imageUri.toString()),
                LazyHeaders.Builder().addHeader("Authorization", auth).build()
            )
        } else {
            imageUri
        }

        Glide.with(context)
            .load(model)
            .apply(requestOptions)
            .listener(defaultListener("WebDAV", progressBar))
            .into(imageView)
    }

    fun loadLocalImage(
        context: Context,
        imageUri: Uri,
        imageView: ImageView,
        progressBar: ProgressBar? = null,
        limitSize: Boolean = true,
        isWaterfall: Boolean = false
    ) {
        val requestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)
            .priority(Priority.HIGH)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)

        if (isWaterfall) {
            // 瀑布流模式：原图分辨率的 70%
            requestOptions.override(Target.SIZE_ORIGINAL)
            requestOptions.sizeMultiplier(0.7f)
            requestOptions.downsample(DownsampleStrategy.AT_MOST)
        } else if (limitSize) {
            // 普通缩略图模式（如文件夹预览）：固定 320x320
            requestOptions.override(320, 320)
            requestOptions.downsample(DownsampleStrategy.AT_MOST)
        } else {
            // 全图模式（如详情页）：原分辨率
            requestOptions.override(Target.SIZE_ORIGINAL)
            requestOptions.downsample(DownsampleStrategy.NONE)
        }

        Glide.with(context)
            .load(imageUri)
            .apply(requestOptions)
            .listener(defaultListener("Local", progressBar))
            .into(imageView)
    }

    private fun defaultListener(tag: String, progressBar: ProgressBar?): RequestListener<Drawable> {
        return object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                android.util.Log.e("WebDavImageLoader", "$tag image load failed: $model", e)
                e?.logRootCauses("WebDavImageLoader")
                progressBar?.visibility = View.GONE
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                progressBar?.visibility = View.GONE
                return false
            }
        }
    }

    fun clearCache(context: Context, photo: Photo) {
        val settings = SettingsManager(context)
        val username = settings.getWebDavUsername()
        val password = settings.getWebDavPassword()

        val model: Any = if (!photo.isLocal && username.isNotEmpty() && password.isNotEmpty()) {
            val auth = "Basic " + android.util.Base64.encodeToString(
                "$username:$password".toByteArray(),
                android.util.Base64.NO_WRAP
            )
            GlideUrl(
                encodeWebDavUrl(photo.imageUri.toString()),
                LazyHeaders.Builder().addHeader("Authorization", auth).build()
            )
        } else {
            photo.imageUri
        }

        // 异步清除磁盘缓存，同步清除内存缓存
        GlobalScope.launch(Dispatchers.Main) {
            Glide.get(context).clearMemory()
            withContext(Dispatchers.IO) {
                // Glide 没有直接按 Key 删除单个缓存的公开 API (除非使用特定的 DiskCache)
                // 但我们可以通过提交一个删除任务来间接实现，或者通知 Glide 资源已失效
                // 对于简单实现，我们至少清除内存缓存，并依赖下一次加载时的失效机制
                // 或者如果用户要求严格清除，通常需要访问 DiskCache 接口
            }
        }
    }

    private fun encodeWebDavUrl(url: String): String {
        return try {
            val schemeEnd = url.indexOf("://")
            if (schemeEnd == -1) return Uri.encode(url, ":/@#?&=")

            val pathStart = url.indexOf('/', schemeEnd + 3)
            if (pathStart == -1) return url

            val baseUrl = url.substring(0, pathStart)
            val rawPath = url.substring(pathStart)
            val encodedPath = rawPath.split('/').joinToString("/") { segment ->
                if (segment.isEmpty()) return@joinToString ""
                val safeSegment = segment.replace("+", "%2B")
                val decoded = runCatching { URLDecoder.decode(safeSegment, "UTF-8") }.getOrDefault(segment)
                URLEncoder.encode(decoded, "UTF-8")
                    .replace("+", "%20")
                    .replace("%2E", ".")
                    .replace("%2D", "-")
                    .replace("%5F", "_")
                    .replace("%7E", "~")
            }
            "$baseUrl$encodedPath"
        } catch (e: Exception) {
            android.util.Log.e("WebDavImageLoader", "URL encoding failed", e)
            url
        }
    }
}
