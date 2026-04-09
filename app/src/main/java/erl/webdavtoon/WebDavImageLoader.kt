package erl.webdavtoon

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DecodeFormat
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object WebDavImageLoader {

    private const val FOLDER_PREVIEW_SIZE_PX = 224

    @Volatile
    private var cachedAuthKey: String? = null

    @Volatile
    private var cachedAuthHeader: String? = null

    private val settingsManagers = ConcurrentHashMap<String, SettingsManager>()

    fun loadWebDavImage(
        context: Context,
        imageUri: Uri,
        imageView: ImageView,
        progressBar: ProgressBar? = null,
        limitSize: Boolean = true,
        isWaterfall: Boolean = false,
        isFolderPreview: Boolean = false
    ) {
        val settings = getSettingsManager(context)
        val requestOptions = buildRequestOptions(context, limitSize, isWaterfall, isFolderPreview)

        val username = settings.getWebDavUsername()
        val password = settings.getWebDavPassword()

        val model: Any = if (username.isNotEmpty() && password.isNotEmpty()) {
            val auth = getCachedAuthHeader(username, password)
            GlideUrl(
                FileUtils.encodeWebDavUrl(imageUri.toString()),
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
        isWaterfall: Boolean = false,
        isFolderPreview: Boolean = false
    ) {
        val requestOptions = buildRequestOptions(context, limitSize, isWaterfall, isFolderPreview)

        Glide.with(context)
            .load(imageUri)
            .apply(requestOptions)
            .listener(defaultListener("Local", progressBar))
            .into(imageView)
    }

    private fun buildRequestOptions(
        context: Context,
        limitSize: Boolean,
        isWaterfall: Boolean,
        isFolderPreview: Boolean
    ): RequestOptions {
        var requestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)
            .priority(Priority.HIGH)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)

        if (isFolderPreview) {
            requestOptions = requestOptions
                .override(FOLDER_PREVIEW_SIZE_PX, FOLDER_PREVIEW_SIZE_PX)
                .downsample(DownsampleStrategy.AT_MOST)
                .format(DecodeFormat.PREFER_RGB_565)
                .priority(Priority.LOW)
                .dontAnimate()
        } else if (isWaterfall) {
            val settings = SettingsManager(context)
            requestOptions = when (settings.getWaterfallQualityMode()) {
                SettingsManager.WATERFALL_MODE_MAX_WIDTH -> {
                    val maxWidth = settings.getWaterfallMaxWidth()
                    requestOptions
                        .override(maxWidth, Target.SIZE_ORIGINAL)
                        .downsample(DownsampleStrategy.AT_MOST)
                }

                else -> {
                    val percent = settings.getWaterfallPercent().coerceIn(10, 100)
                    requestOptions
                        .override(Target.SIZE_ORIGINAL)
                        .sizeMultiplier(percent / 100f)
                        .downsample(DownsampleStrategy.AT_MOST)
                }
            }
        } else if (limitSize) {
            requestOptions = requestOptions
                .override(320, 320)
                .downsample(DownsampleStrategy.AT_MOST)
        } else {
            requestOptions = requestOptions.downsample(DownsampleStrategy.AT_MOST)
        }

        return requestOptions
    }

    private fun getCachedAuthHeader(username: String, password: String): String {
        val key = "$username:$password"
        val currentKey = cachedAuthKey
        val currentHeader = cachedAuthHeader
        if (currentKey == key && currentHeader != null) {
            return currentHeader
        }

        val header = "Basic " + android.util.Base64.encodeToString(
            key.toByteArray(),
            android.util.Base64.NO_WRAP
        )
        cachedAuthKey = key
        cachedAuthHeader = header
        return header
    }

    private fun getSettingsManager(context: Context): SettingsManager {
        val appContext = context.applicationContext
        val key = appContext.packageName
        return settingsManagers.getOrPut(key) { SettingsManager(appContext) }
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

    /**
     * 清除图片的 Glide 缓存
     */
    fun clearCache(context: Context) {
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            Glide.get(context).clearMemory()
            withContext(Dispatchers.IO) {
                Glide.get(context).clearDiskCache()
            }
        }
    }

    fun clear(imageView: ImageView) {
        Glide.with(imageView).clear(imageView)
        imageView.setImageDrawable(null)
    }
}
