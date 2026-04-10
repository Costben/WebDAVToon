package erl.webdavtoon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import wseemann.media.FFmpegMediaMetadataRetriever
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object WebDavImageLoader {

    private const val FOLDER_PREVIEW_SIZE_PX = 160
    private const val NORMAL_VIDEO_PREVIEW_SIZE_PX = 320
    private const val REMOTE_AVI_CACHE_BYTES = 24L * 1024L * 1024L
    private val remoteVideoThumbCache = object : LruCache<String, Bitmap>(24) {}
    private val localVideoThumbCache = object : LruCache<String, Bitmap>(24) {}

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
        val requestOptions = buildRequestOptions(context, limitSize, isWaterfall, isFolderPreview)
        val model = buildWebDavModel(context, imageUri)

        Glide.with(context)
            .load(model)
            .apply(requestOptions)
            .listener(defaultDrawableListener("WebDAV", progressBar))
            .into(imageView)
    }

    fun loadWebDavVideoThumbnail(
        context: Context,
        videoUri: Uri,
        imageView: ImageView,
        progressBar: ProgressBar? = null,
        isFolderPreview: Boolean = false
    ) {
        val encodedUrl = FileUtils.encodeWebDavUrl(videoUri.toString())
        val cacheKey = buildString {
            append(encodedUrl)
            append("#")
            append(if (isFolderPreview) "folder" else "normal")
        }
        imageView.tag = cacheKey
        progressBar?.visibility = View.VISIBLE

        remoteVideoThumbCache.get(cacheKey)?.let { cached ->
            android.util.Log.d("WebDavImageLoader", "WebDAV-Video cache hit: $encodedUrl")
            imageView.setImageBitmap(cached)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            progressBar?.visibility = View.GONE
            return
        }

        val settings = getSettingsManager(context)
        val headers = mutableMapOf<String, String>()
        val username = settings.getWebDavUsername()
        val password = settings.getWebDavPassword()
        if (username.isNotEmpty() && password.isNotEmpty()) {
            headers["Authorization"] = getCachedAuthHeader(username, password)
        }

        android.util.Log.i(
            "WebDavImageLoader",
            "WebDAV-Video thumbnail start uri=$encodedUrl isFolderPreview=$isFolderPreview"
        )

        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            val diskCached = loadCachedVideoBitmap(context, cacheKey)
            val bitmap = diskCached ?: extractRemoteVideoThumbnail(context, encodedUrl, headers, isFolderPreview)

            withContext(Dispatchers.Main) {
                if (imageView.tag != cacheKey) {
                    android.util.Log.d("WebDavImageLoader", "WebDAV-Video thumbnail ignored(stale): $encodedUrl")
                    progressBar?.visibility = View.GONE
                    return@withContext
                }

                if (bitmap != null) {
                    remoteVideoThumbCache.put(cacheKey, bitmap)
                    if (diskCached == null) {
                        saveCachedVideoBitmap(context, cacheKey, bitmap)
                    }
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    imageView.setImageBitmap(bitmap)
                    android.util.Log.i("WebDavImageLoader", "WebDAV-Video thumbnail success: $encodedUrl")
                } else {
                    android.util.Log.w("WebDavImageLoader", "WebDAV-Video thumbnail fallback placeholder: $encodedUrl")
                }
                progressBar?.visibility = View.GONE
            }
        }
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
            .listener(defaultDrawableListener("Local", progressBar))
            .into(imageView)
    }

    fun loadLocalVideoThumbnail(
        context: Context,
        videoUri: Uri,
        imageView: ImageView,
        progressBar: ProgressBar? = null,
        isFolderPreview: Boolean = false
    ) {
        val cacheKey = buildString {
            append(videoUri)
            append("#")
            append(if (isFolderPreview) "folder" else "normal")
        }
        imageView.tag = cacheKey
        progressBar?.visibility = View.VISIBLE

        localVideoThumbCache.get(cacheKey)?.let { cached ->
            imageView.setImageBitmap(cached)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            progressBar?.visibility = View.GONE
            return
        }

        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            val diskCached = loadCachedVideoBitmap(context, cacheKey)
            val bitmap = diskCached ?: extractLocalVideoThumbnail(context, videoUri, isFolderPreview)
            withContext(Dispatchers.Main) {
                if (imageView.tag != cacheKey) {
                    progressBar?.visibility = View.GONE
                    return@withContext
                }

                if (bitmap != null) {
                    localVideoThumbCache.put(cacheKey, bitmap)
                    if (diskCached == null) {
                        saveCachedVideoBitmap(context, cacheKey, bitmap)
                    }
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    imageView.setImageBitmap(bitmap)
                } else {
                    android.util.Log.w("WebDavImageLoader", "Local-Video thumbnail fallback placeholder: $videoUri")
                }
                progressBar?.visibility = View.GONE
            }
        }
    }

    private fun extractRemoteVideoThumbnail(
        context: Context,
        encodedUrl: String,
        headers: Map<String, String>,
        isFolderPreview: Boolean
    ): Bitmap? {
        val frameTimeUs = if (isFolderPreview) 0L else 1_000_000L
        val isAvi = isAviVideo(encodedUrl)

        val primary = if (isAvi) {
            tryExtractWithFfmpegRetriever(
                dataSourceLabel = encodedUrl,
                frameTimeUs = frameTimeUs
            ) { retriever ->
                retriever.setDataSource(encodedUrl, headers)
            }
        } else {
            tryExtractWithPlatformRetriever(
                dataSourceLabel = encodedUrl,
                frameTimeUs = frameTimeUs
            ) { retriever ->
                retriever.setDataSource(encodedUrl, headers)
            }
        }

        val fallback = if (primary != null) {
            null
        } else if (isAvi) {
            tryExtractFromCachedRemoteAvi(
                context = context,
                dataSourceLabel = encodedUrl,
                headers = headers,
                frameTimeUs = frameTimeUs
            ) ?: tryExtractWithPlatformRetriever(
                dataSourceLabel = encodedUrl,
                frameTimeUs = frameTimeUs
            ) { retriever ->
                retriever.setDataSource(encodedUrl, headers)
            }
        } else {
            tryExtractWithFfmpegRetriever(
                dataSourceLabel = encodedUrl,
                frameTimeUs = frameTimeUs
            ) { retriever ->
                retriever.setDataSource(encodedUrl, headers)
            }
        }

        return (primary ?: fallback)?.let { frame ->
            normalizeVideoThumbnail(frame, isFolderPreview)
        }
    }

    private fun extractLocalVideoThumbnail(
        context: Context,
        videoUri: Uri,
        isFolderPreview: Boolean
    ): Bitmap? {
        val dataSourceLabel = videoUri.toString()
        val frameTimeUs = if (isFolderPreview) 0L else 1_000_000L
        val isAvi = isAviVideo(dataSourceLabel)

        val primary = if (isAvi) {
            tryExtractWithFfmpegRetriever(
                dataSourceLabel = dataSourceLabel,
                frameTimeUs = frameTimeUs
            ) { retriever ->
                configureLocalFfmpegDataSource(context, videoUri, retriever)
            }
        } else {
            tryExtractWithPlatformRetriever(
                dataSourceLabel = dataSourceLabel,
                frameTimeUs = frameTimeUs
            ) { retriever ->
                retriever.setDataSource(context, videoUri)
            }
        }

        val fallback = if (primary != null) {
            null
        } else if (isAvi) {
            tryExtractFromCachedLocalAvi(
                context = context,
                videoUri = videoUri,
                dataSourceLabel = dataSourceLabel,
                frameTimeUs = frameTimeUs
            ) ?: tryExtractWithPlatformRetriever(
                dataSourceLabel = dataSourceLabel,
                frameTimeUs = frameTimeUs
            ) { retriever ->
                retriever.setDataSource(context, videoUri)
            }
        } else {
            tryExtractWithFfmpegRetriever(
                dataSourceLabel = dataSourceLabel,
                frameTimeUs = frameTimeUs
            ) { retriever ->
                configureLocalFfmpegDataSource(context, videoUri, retriever)
            }
        }

        return (primary ?: fallback)?.let { frame ->
            normalizeVideoThumbnail(frame, isFolderPreview)
        }
    }


    private fun configureLocalFfmpegDataSource(
        context: Context,
        videoUri: Uri,
        retriever: FFmpegMediaMetadataRetriever
    ) {
        if (videoUri.scheme.equals("content", ignoreCase = true)) {
            context.contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                return
            }
        }
        retriever.setDataSource(context, videoUri)
    }

    private fun tryExtractFromCachedRemoteAvi(
        context: Context,
        dataSourceLabel: String,
        headers: Map<String, String>,
        frameTimeUs: Long
    ): Bitmap? {
        val cachedFile = cacheRemoteVideoLocally(context, dataSourceLabel, headers) ?: return null
        return tryExtractWithFfmpegRetriever(
            dataSourceLabel = "${dataSourceLabel}#cached",
            frameTimeUs = frameTimeUs
        ) { retriever ->
            retriever.setDataSource(cachedFile.absolutePath)
        }
    }

    private fun tryExtractFromCachedLocalAvi(
        context: Context,
        videoUri: Uri,
        dataSourceLabel: String,
        frameTimeUs: Long
    ): Bitmap? {
        val cachedFile = cacheLocalVideoLocally(context, videoUri) ?: return null
        return tryExtractWithFfmpegRetriever(
            dataSourceLabel = "${dataSourceLabel}#cached",
            frameTimeUs = frameTimeUs
        ) { retriever ->
            retriever.setDataSource(cachedFile.absolutePath)
        }
    }

    private fun cacheRemoteVideoLocally(
        context: Context,
        encodedUrl: String,
        headers: Map<String, String>
    ): File? {
        return try {
            val cacheFile = prepareVideoCacheFile(context, encodedUrl, "avi")
            if (cacheFile.exists() && cacheFile.length() in 1..REMOTE_AVI_CACHE_BYTES) {
                android.util.Log.i("WebDavImageLoader", "Reusing cached remote AVI: ${cacheFile.absolutePath}")
                return cacheFile
            }
            if (cacheFile.exists()) {
                runCatching { cacheFile.delete() }
            }

            val requestBuilder = okhttp3.Request.Builder().url(encodedUrl)
                .header("Range", "bytes=0-${REMOTE_AVI_CACHE_BYTES - 1}")
            headers.forEach { (key, value) -> requestBuilder.header(key, value) }
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("WebDavImageLoader", "Remote AVI cache download failed: code=${response.code} url=$encodedUrl")
                    return null
                }
                val body = response.body ?: return null
                cacheFile.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        copyStreamWithLimit(input, output, REMOTE_AVI_CACHE_BYTES)
                    }
                }
            }
            android.util.Log.i("WebDavImageLoader", "Cached remote AVI for thumbnail: ${cacheFile.absolutePath} size=${cacheFile.length()}")
            cacheFile
        } catch (t: Throwable) {
            android.util.Log.e("WebDavImageLoader", "Failed caching remote AVI locally: $encodedUrl", t)
            null
        }
    }

    private fun cacheLocalVideoLocally(context: Context, videoUri: Uri): File? {
        return try {
            if (videoUri.scheme.equals("file", ignoreCase = true)) {
                val path = videoUri.path
                if (!path.isNullOrBlank()) {
                    return File(path)
                }
            }

            val cacheFile = prepareVideoCacheFile(context, videoUri.toString(), "avi")
            if (cacheFile.exists() && cacheFile.length() > 0L) {
                android.util.Log.i("WebDavImageLoader", "Reusing cached local AVI: ${cacheFile.absolutePath}")
                return cacheFile
            }

            context.contentResolver.openInputStream(videoUri)?.use { input ->
                cacheFile.parentFile?.mkdirs()
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            android.util.Log.i("WebDavImageLoader", "Cached local AVI for thumbnail: ${cacheFile.absolutePath} size=${cacheFile.length()}")
            cacheFile
        } catch (t: Throwable) {
            android.util.Log.e("WebDavImageLoader", "Failed caching local AVI locally: $videoUri", t)
            null
        }
    }

    private fun prepareVideoCacheFile(context: Context, key: String, extension: String): File {
        val safeExtension = extension.trimStart('.').ifBlank { "bin" }
        val hash = md5(key)
        val dir = File(context.cacheDir, "video_thumb_sources")
        return File(dir, "$hash.$safeExtension")
    }

    private fun prepareBitmapCacheFile(context: Context, key: String): File {
        val dir = File(context.cacheDir, "video_thumb_bitmaps")
        return File(dir, "${md5(key)}.jpg")
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun loadCachedVideoBitmap(context: Context, cacheKey: String): Bitmap? {
        return try {
            val cacheFile = prepareBitmapCacheFile(context, cacheKey)
            if (!cacheFile.exists() || cacheFile.length() <= 0L) return null
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.also {
                android.util.Log.d("WebDavImageLoader", "Video bitmap disk cache hit: $cacheKey")
            }
        } catch (t: Throwable) {
            android.util.Log.w("WebDavImageLoader", "Failed reading video bitmap cache: $cacheKey", t)
            null
        }
    }

    private fun saveCachedVideoBitmap(context: Context, cacheKey: String, bitmap: Bitmap) {
        runCatching {
            val cacheFile = prepareBitmapCacheFile(context, cacheKey)
            cacheFile.parentFile?.mkdirs()
            FileOutputStream(cacheFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                output.flush()
            }
            android.util.Log.d("WebDavImageLoader", "Video bitmap disk cache saved: $cacheKey size=${cacheFile.length()}")
        }.onFailure { t ->
            android.util.Log.w("WebDavImageLoader", "Failed saving video bitmap cache: $cacheKey", t)
        }
    }

    private fun copyStreamWithLimit(
        input: java.io.InputStream,
        output: FileOutputStream,
        maxBytes: Long
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = maxBytes
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        output.flush()
    }

    private inline fun tryExtractWithPlatformRetriever(
        dataSourceLabel: String,
        frameTimeUs: Long,
        configure: (MediaMetadataRetriever) -> Unit
    ): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                configure(retriever)
                val frame = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                frame?.let { normalizeVideoThumbnail(it, isFolderPreview = frameTimeUs == 0L) }
            } finally {
                runCatching { retriever.release() }
            }
        } catch (t: Throwable) {
            android.util.Log.w("WebDavImageLoader", "Platform retriever failed: $dataSourceLabel", t)
            null
        }
    }

    private inline fun tryExtractWithFfmpegRetriever(
        dataSourceLabel: String,
        frameTimeUs: Long,
        configure: (FFmpegMediaMetadataRetriever) -> Unit
    ): Bitmap? {
        return try {
            val retriever = FFmpegMediaMetadataRetriever()
            try {
                configure(retriever)
                retriever.getFrameAtTime(frameTimeUs, FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                runCatching { retriever.release() }
            }
        } catch (t: Throwable) {
            android.util.Log.e("WebDavImageLoader", "FFmpeg retriever failed: $dataSourceLabel", t)
            null
        }
    }

    private fun normalizeVideoThumbnail(source: Bitmap, isFolderPreview: Boolean): Bitmap {
        val targetSize = if (isFolderPreview) FOLDER_PREVIEW_SIZE_PX else NORMAL_VIDEO_PREVIEW_SIZE_PX
        return cropCenterSquare(source, targetSize)
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
                .priority(Priority.NORMAL)
                .timeout(3_000)
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

    private fun buildWebDavModel(context: Context, mediaUri: Uri): Any {
        val settings = getSettingsManager(context)
        val username = settings.getWebDavUsername()
        val password = settings.getWebDavPassword()

        return if (username.isNotEmpty() && password.isNotEmpty()) {
            val auth = getCachedAuthHeader(username, password)
            GlideUrl(
                FileUtils.encodeWebDavUrl(mediaUri.toString()),
                LazyHeaders.Builder().addHeader("Authorization", auth).build()
            )
        } else {
            mediaUri
        }
    }

    private fun cropCenterSquare(source: Bitmap, targetSize: Int): Bitmap {
        val cropSize = minOf(source.width, source.height)
        val left = ((source.width - cropSize) / 2).coerceAtLeast(0)
        val top = ((source.height - cropSize) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(source, left, top, cropSize, cropSize)
        return if (cropped.width == targetSize && cropped.height == targetSize) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        }
    }

    private fun defaultDrawableListener(tag: String, progressBar: ProgressBar?): RequestListener<Drawable> {
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

    private fun defaultBitmapListener(tag: String, progressBar: ProgressBar?): RequestListener<Bitmap> {
        return object : RequestListener<Bitmap> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Bitmap>,
                isFirstResource: Boolean
            ): Boolean {
                android.util.Log.e("WebDavImageLoader", "$tag image load failed: $model", e)
                e?.logRootCauses("WebDavImageLoader")
                progressBar?.visibility = View.GONE
                return false
            }

            override fun onResourceReady(
                resource: Bitmap,
                model: Any,
                target: Target<Bitmap>,
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
            remoteVideoThumbCache.evictAll()
            localVideoThumbCache.evictAll()
            withContext(Dispatchers.IO) {
                Glide.get(context).clearDiskCache()
                runCatching {
                    File(context.cacheDir, "video_thumb_sources").deleteRecursively()
                    File(context.cacheDir, "video_thumb_bitmaps").deleteRecursively()
                }
            }
        }
    }

    fun clear(imageView: ImageView) {
        Glide.with(imageView).clear(imageView)
        imageView.setImageDrawable(null)
    }
}
