package erl.webdavtoon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
object WebDavImageLoader {

    private const val FOLDER_PREVIEW_SIZE_PX = 160
    private const val FOLDER_PREVIEW_DECODE_MAX_PX = 240
    private const val NORMAL_VIDEO_PREVIEW_SIZE_PX = 320
    private const val DEFAULT_REMOTE_VIDEO_SOURCE_CACHE_BYTES = 64L * 1024L * 1024L
    private const val AVI_REMOTE_VIDEO_SOURCE_CACHE_BYTES = 8L * 1024L * 1024L
    private const val LOCAL_IMAGE_PREVIEW_SAMPLE_MAX_PX = 96
    private const val VIDEO_BITMAP_CACHE_VERSION = 6
    private val remoteVideoThumbCache = object : LruCache<String, Bitmap>(24) {}
    private val localVideoThumbCache = object : LruCache<String, Bitmap>(24) {}
    private val blankLocalImagePreviewCache = object : LruCache<String, Boolean>(96) {}

    @Volatile
    private var cachedAuthKey: String? = null

    @Volatile
    private var cachedAuthHeader: String? = null

    private val settingsManagers = ConcurrentHashMap<String, SettingsManager>()
    private val videoThumbnailDispatcher = Dispatchers.IO.limitedParallelism(2)

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
        val cacheKey = buildVideoBitmapCacheKey(encodedUrl, isFolderPreview)
        imageView.tag = cacheKey
        progressBar?.visibility = View.VISIBLE

        remoteVideoThumbCache.get(cacheKey)?.let { cached ->
            if (cached.isLikelyBlankVideoThumbnail()) {
                android.util.Log.i("WebDavImageLoader", "Discarded blank cached WebDAV thumbnail: $encodedUrl")
                remoteVideoThumbCache.remove(cacheKey)
            } else {
                android.util.Log.d("WebDavImageLoader", "WebDAV-Video cache hit: $encodedUrl")
                applyVideoThumbnailDisplayMode(imageView, isFolderPreview)
                imageView.setImageBitmap(cached)
                progressBar?.visibility = View.GONE
                return
            }
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
        GlobalScope.launch(videoThumbnailDispatcher) {
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
                    applyVideoThumbnailDisplayMode(imageView, isFolderPreview)
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
        val cacheKey = buildVideoBitmapCacheKey(videoUri.toString(), isFolderPreview)
        imageView.tag = cacheKey
        progressBar?.visibility = View.VISIBLE

        localVideoThumbCache.get(cacheKey)?.let { cached ->
            if (cached.isLikelyBlankVideoThumbnail()) {
                android.util.Log.i("WebDavImageLoader", "Discarded blank cached local thumbnail: $videoUri")
                localVideoThumbCache.remove(cacheKey)
            } else {
                applyVideoThumbnailDisplayMode(imageView, isFolderPreview)
                imageView.setImageBitmap(cached)
                progressBar?.visibility = View.GONE
                return
            }
        }

        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        GlobalScope.launch(videoThumbnailDispatcher) {
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
                    applyVideoThumbnailDisplayMode(imageView, isFolderPreview)
                    imageView.setImageBitmap(bitmap)
                } else {
                    android.util.Log.w("WebDavImageLoader", "Local-Video thumbnail fallback placeholder: $videoUri")
                }
                progressBar?.visibility = View.GONE
            }
        }
    }

    fun isLikelyBlankLocalImagePreview(context: Context, imageUri: Uri): Boolean {
        val cacheKey = imageUri.toString()
        blankLocalImagePreviewCache.get(cacheKey)?.let { return it }

        val result = runCatching {
            decodeLocalPreviewSample(context, imageUri)?.let { bitmap ->
                try {
                    bitmap.isLikelyBlankPreviewBitmap()
                } finally {
                    runCatching { bitmap.recycle() }
                }
            } ?: false
        }.getOrElse { t ->
            android.util.Log.w("WebDavImageLoader", "Failed to inspect local image preview: $imageUri", t)
            false
        }

        blankLocalImagePreviewCache.put(cacheKey, result)
        return result
    }

    private fun extractRemoteVideoThumbnail(
        context: Context,
        encodedUrl: String,
        headers: Map<String, String>,
        isFolderPreview: Boolean
    ): Bitmap? {
        val frameTimeUs = if (isFolderPreview) 0L else 1_000_000L
        val sourceExtension = normalizedVideoSourceExtension(encodedUrl)
        val requiresCachedFallback = requiresCachedVideoThumbnailFallback(encodedUrl)

        val primary = if (requiresCachedFallback) {
            tryExtractWithFfmpegRetriever(
                dataSourceLabel = encodedUrl,
                frameTimeUs = frameTimeUs,
                sourceExtension = sourceExtension,
                isFolderPreview = isFolderPreview
            ) { retriever ->
                retriever.setDataSource(encodedUrl, headers)
            }
        } else {
            tryExtractWithPlatformRetriever(
                dataSourceLabel = encodedUrl,
                frameTimeUs = frameTimeUs,
                sourceExtension = sourceExtension,
                isFolderPreview = isFolderPreview
            ) { retriever ->
                retriever.setDataSource(encodedUrl, headers)
            }
        }

        val fallback = if (primary != null) {
            null
        } else if (requiresCachedFallback) {
            tryExtractFromCachedRemoteVideo(
                context = context,
                dataSourceLabel = encodedUrl,
                headers = headers,
                frameTimeUs = frameTimeUs,
                sourceExtension = sourceExtension,
                isFolderPreview = isFolderPreview
            ) ?: tryExtractWithPlatformRetriever(
                dataSourceLabel = encodedUrl,
                frameTimeUs = frameTimeUs,
                sourceExtension = sourceExtension,
                isFolderPreview = isFolderPreview
            ) { retriever ->
                retriever.setDataSource(encodedUrl, headers)
            }
        } else {
            tryExtractWithFfmpegRetriever(
                dataSourceLabel = encodedUrl,
                frameTimeUs = frameTimeUs,
                sourceExtension = sourceExtension,
                isFolderPreview = isFolderPreview
            ) { retriever ->
                retriever.setDataSource(encodedUrl, headers)
            }
        }

        return primary ?: fallback
    }

    private fun extractLocalVideoThumbnail(
        context: Context,
        videoUri: Uri,
        isFolderPreview: Boolean
    ): Bitmap? {
        val dataSourceLabel = videoUri.toString()
        val frameTimeUs = if (isFolderPreview) 0L else 1_000_000L
        val sourceExtension = normalizedVideoSourceExtension(dataSourceLabel)
        val requiresCachedFallback = requiresCachedVideoThumbnailFallback(dataSourceLabel)

        val primary = if (requiresCachedFallback) {
            tryExtractWithFfmpegRetriever(
                dataSourceLabel = dataSourceLabel,
                frameTimeUs = frameTimeUs,
                sourceExtension = sourceExtension,
                isFolderPreview = isFolderPreview
            ) { retriever ->
                configureLocalFfmpegDataSource(context, videoUri, retriever)
            }
        } else {
            tryExtractWithPlatformRetriever(
                dataSourceLabel = dataSourceLabel,
                frameTimeUs = frameTimeUs,
                sourceExtension = sourceExtension,
                isFolderPreview = isFolderPreview
            ) { retriever ->
                retriever.setDataSource(context, videoUri)
            }
        }

        val fallback = if (primary != null) {
            null
        } else if (requiresCachedFallback) {
            tryExtractFromCachedLocalVideo(
                context = context,
                videoUri = videoUri,
                dataSourceLabel = dataSourceLabel,
                frameTimeUs = frameTimeUs,
                sourceExtension = sourceExtension,
                isFolderPreview = isFolderPreview
            ) ?: tryExtractWithPlatformRetriever(
                dataSourceLabel = dataSourceLabel,
                frameTimeUs = frameTimeUs,
                sourceExtension = sourceExtension,
                isFolderPreview = isFolderPreview
            ) { retriever ->
                retriever.setDataSource(context, videoUri)
            }
        } else {
            tryExtractWithFfmpegRetriever(
                dataSourceLabel = dataSourceLabel,
                frameTimeUs = frameTimeUs,
                sourceExtension = sourceExtension,
                isFolderPreview = isFolderPreview
            ) { retriever ->
                configureLocalFfmpegDataSource(context, videoUri, retriever)
            }
        }

        return primary ?: fallback
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

    private fun tryExtractFromCachedRemoteVideo(
        context: Context,
        dataSourceLabel: String,
        headers: Map<String, String>,
        frameTimeUs: Long,
        sourceExtension: String,
        isFolderPreview: Boolean
    ): Bitmap? {
        val cachedFile = cacheRemoteVideoLocally(context, dataSourceLabel, headers, sourceExtension) ?: return null
        return tryExtractWithFfmpegRetriever(
            dataSourceLabel = "${dataSourceLabel}#cached",
            frameTimeUs = frameTimeUs,
            sourceExtension = sourceExtension,
            isFolderPreview = isFolderPreview
        ) { retriever ->
            retriever.setDataSource(cachedFile.absolutePath)
        }
    }

    private fun tryExtractFromCachedLocalVideo(
        context: Context,
        videoUri: Uri,
        dataSourceLabel: String,
        frameTimeUs: Long,
        sourceExtension: String,
        isFolderPreview: Boolean
    ): Bitmap? {
        val cachedFile = cacheLocalVideoLocally(context, videoUri, sourceExtension) ?: return null
        return tryExtractWithFfmpegRetriever(
            dataSourceLabel = "${dataSourceLabel}#cached",
            frameTimeUs = frameTimeUs,
            sourceExtension = sourceExtension,
            isFolderPreview = isFolderPreview
        ) { retriever ->
            retriever.setDataSource(cachedFile.absolutePath)
        }
    }

    private fun cacheRemoteVideoLocally(
        context: Context,
        encodedUrl: String,
        headers: Map<String, String>,
        sourceExtension: String
    ): File? {
        return try {
            val cacheFile = prepareVideoCacheFile(context, encodedUrl, sourceExtension)
            val maxBytes = videoSourceCacheBytesForExtension(sourceExtension)
            if (cacheFile.exists() && cacheFile.length() in 1..maxBytes) {
                android.util.Log.i("WebDavImageLoader", "Reusing cached remote video source: ${cacheFile.absolutePath}")
                return cacheFile
            }
            if (cacheFile.exists()) {
                runCatching { cacheFile.delete() }
            }

            val requestBuilder = okhttp3.Request.Builder().url(encodedUrl)
                .header("Range", "bytes=0-${maxBytes - 1}")
            headers.forEach { (key, value) -> requestBuilder.header(key, value) }
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("WebDavImageLoader", "Remote video source cache download failed: code=${response.code} url=$encodedUrl")
                    return null
                }
                val body = response.body ?: return null
                cacheFile.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        copyStreamWithLimit(input, output, maxBytes)
                    }
                }
            }
            android.util.Log.i("WebDavImageLoader", "Cached remote video source for thumbnail: ${cacheFile.absolutePath} size=${cacheFile.length()}")
            cacheFile
        } catch (t: Throwable) {
            android.util.Log.e("WebDavImageLoader", "Failed caching remote video source locally: $encodedUrl", t)
            null
        }
    }

    private fun cacheLocalVideoLocally(context: Context, videoUri: Uri, sourceExtension: String): File? {
        return try {
            if (videoUri.scheme.equals("file", ignoreCase = true)) {
                val path = videoUri.path
                if (!path.isNullOrBlank()) {
                    return File(path)
                }
            }

            val cacheFile = prepareVideoCacheFile(context, videoUri.toString(), sourceExtension)
            val maxBytes = videoSourceCacheBytesForExtension(sourceExtension)
            if (cacheFile.exists() && cacheFile.length() > 0L) {
                android.util.Log.i("WebDavImageLoader", "Reusing cached local video source: ${cacheFile.absolutePath}")
                return cacheFile
            }

            context.contentResolver.openInputStream(videoUri)?.use { input ->
                cacheFile.parentFile?.mkdirs()
                FileOutputStream(cacheFile).use { output ->
                    copyStreamWithLimit(input, output, maxBytes)
                }
            } ?: return null

            android.util.Log.i("WebDavImageLoader", "Cached local video source for thumbnail: ${cacheFile.absolutePath} size=${cacheFile.length()}")
            cacheFile
        } catch (t: Throwable) {
            android.util.Log.e("WebDavImageLoader", "Failed caching local video source locally: $videoUri", t)
            null
        }
    }

    private fun normalizedVideoSourceExtension(nameOrUri: String): String {
        return videoExtensionOf(nameOrUri)?.takeIf { it.all(Char::isLetterOrDigit) } ?: "video"
    }

    private fun videoSourceCacheBytesForExtension(sourceExtension: String): Long {
        return if (sourceExtension.equals("avi", ignoreCase = true)) {
            AVI_REMOTE_VIDEO_SOURCE_CACHE_BYTES
        } else {
            DEFAULT_REMOTE_VIDEO_SOURCE_CACHE_BYTES
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

    private fun buildVideoBitmapCacheKey(
        source: String,
        isFolderPreview: Boolean
    ): String {
        return buildString {
            append(source)
            append("#")
            append(if (isFolderPreview) "folder" else "normal")
            append("#v")
            append(VIDEO_BITMAP_CACHE_VERSION)
        }
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun loadCachedVideoBitmap(context: Context, cacheKey: String): Bitmap? {
        return try {
            val cacheFile = prepareBitmapCacheFile(context, cacheKey)
            if (!cacheFile.exists() || cacheFile.length() <= 0L) return null
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.takeIf { bitmap ->
                if (!bitmap.isLikelyBlankVideoThumbnail()) {
                    true
                } else {
                    android.util.Log.i("WebDavImageLoader", "Discarded blank video bitmap disk cache: $cacheKey")
                    runCatching { cacheFile.delete() }
                    runCatching { bitmap.recycle() }
                    false
                }
            }?.also {
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
        sourceExtension: String,
        isFolderPreview: Boolean,
        configure: (MediaMetadataRetriever) -> Unit
    ): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                configure(retriever)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                val scaledFrameSize = resolvePlatformScaledFrameSize(retriever, isFolderPreview)
                val candidateTimes = VideoThumbnailHeuristics.candidateFrameTimesUs(
                    requestedTimeUs = frameTimeUs,
                    durationMs = durationMs,
                    sourceExtension = sourceExtension,
                    isFolderPreview = isFolderPreview
                )
                val preferSyncOnly = VideoThumbnailHeuristics.shouldPreferSyncFrameSearch(
                    sourceExtension = sourceExtension,
                    isFolderPreview = isFolderPreview
                )

                for (candidateTimeUs in candidateTimes) {
                    retriever.extractFrameAtTime(
                        timeUs = candidateTimeUs,
                        option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        scaledFrameSize = scaledFrameSize
                    )
                        ?.takeIfUsableVideoFrame(dataSourceLabel, candidateTimeUs, sourceExtension, isFolderPreview, "platform-sync")
                        ?.let { return it }
                }

                if (!preferSyncOnly) {
                    for (candidateTimeUs in candidateTimes) {
                        retriever.extractFrameAtTime(
                            timeUs = candidateTimeUs,
                            option = MediaMetadataRetriever.OPTION_CLOSEST,
                            scaledFrameSize = scaledFrameSize
                        )
                            ?.takeIfUsableVideoFrame(dataSourceLabel, candidateTimeUs, sourceExtension, isFolderPreview, "platform-closest")
                            ?.let { return it }
                    }
                }
                null
            } finally {
                runCatching { retriever.release() }
            }
        } catch (t: Throwable) {
            android.util.Log.w("WebDavImageLoader", "Platform retriever failed: $dataSourceLabel", t)
            null
        }
    }

    private fun resolvePlatformScaledFrameSize(
        retriever: MediaMetadataRetriever,
        isFolderPreview: Boolean
    ): Pair<Int, Int>? {
        if (!isFolderPreview || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return null
        }

        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
        if (width == null || height == null || width <= 0 || height <= 0) {
            return null
        }

        val longestEdge = maxOf(width, height)
        if (longestEdge <= FOLDER_PREVIEW_DECODE_MAX_PX) {
            return width to height
        }

        val scale = FOLDER_PREVIEW_DECODE_MAX_PX.toFloat() / longestEdge.toFloat()
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    private fun MediaMetadataRetriever.extractFrameAtTime(
        timeUs: Long,
        option: Int,
        scaledFrameSize: Pair<Int, Int>?
    ): Bitmap? {
        if (scaledFrameSize != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching {
                return getScaledFrameAtTime(timeUs, option, scaledFrameSize.first, scaledFrameSize.second)
            }
        }

        return getFrameAtTime(timeUs, option)
    }

    private inline fun tryExtractWithFfmpegRetriever(
        dataSourceLabel: String,
        frameTimeUs: Long,
        sourceExtension: String,
        isFolderPreview: Boolean,
        configure: (FFmpegMediaMetadataRetriever) -> Unit
    ): Bitmap? {
        return try {
            val retriever = FFmpegMediaMetadataRetriever()
            try {
                configure(retriever)
                val durationMs = retriever.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                val candidateTimes = VideoThumbnailHeuristics.candidateFrameTimesUs(
                    requestedTimeUs = frameTimeUs,
                    durationMs = durationMs,
                    sourceExtension = sourceExtension,
                    isFolderPreview = isFolderPreview
                )
                val preferSyncOnly = VideoThumbnailHeuristics.shouldPreferSyncFrameSearch(
                    sourceExtension = sourceExtension,
                    isFolderPreview = isFolderPreview
                )

                for (candidateTimeUs in candidateTimes) {
                    retriever.getFrameAtTime(candidateTimeUs, FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.takeIfUsableVideoFrame(dataSourceLabel, candidateTimeUs, sourceExtension, isFolderPreview, "ffmpeg-sync")
                        ?.let { return it }
                }

                if (!preferSyncOnly) {
                    for (candidateTimeUs in candidateTimes) {
                        retriever.getFrameAtTime(candidateTimeUs, FFmpegMediaMetadataRetriever.OPTION_CLOSEST)
                            ?.takeIfUsableVideoFrame(dataSourceLabel, candidateTimeUs, sourceExtension, isFolderPreview, "ffmpeg-closest")
                            ?.let { return it }
                    }
                }
                null
            } finally {
                runCatching { retriever.release() }
            }
        } catch (t: Throwable) {
            android.util.Log.e("WebDavImageLoader", "FFmpeg retriever failed: $dataSourceLabel", t)
            null
        }
    }

    private fun Bitmap.takeIfUsableVideoFrame(
        dataSourceLabel: String,
        candidateTimeUs: Long,
        sourceExtension: String,
        isFolderPreview: Boolean,
        extractionMode: String
    ): Bitmap? {
        val normalized = normalizeVideoThumbnail(this, isFolderPreview)
        if (normalized !== this) {
            runCatching { recycle() }
        }

        val acceptAviFirstFrameDirectly =
            sourceExtension.equals("avi", ignoreCase = true) && candidateTimeUs == 0L

        if (acceptAviFirstFrameDirectly) {
            android.util.Log.i(
                "WebDavImageLoader",
                "Accepted AVI first frame source=$dataSourceLabel timeUs=$candidateTimeUs mode=$extractionMode"
            )
            return normalized
        }

        if (!normalized.isLikelyBlankVideoThumbnail()) {
            android.util.Log.i(
                "WebDavImageLoader",
                "Accepted video frame source=$dataSourceLabel timeUs=$candidateTimeUs mode=$extractionMode"
            )
            return normalized
        }

        android.util.Log.i(
            "WebDavImageLoader",
            "Rejected blank-like video frame source=$dataSourceLabel timeUs=$candidateTimeUs"
        )
        runCatching { normalized.recycle() }
        return null
    }

    private fun normalizeVideoThumbnail(source: Bitmap, isFolderPreview: Boolean): Bitmap {
        return if (isFolderPreview) {
            cropCenterSquare(source, FOLDER_PREVIEW_SIZE_PX)
        } else {
            scaleBitmapPreservingAspectRatio(source, NORMAL_VIDEO_PREVIEW_SIZE_PX)
        }
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
            .placeholder(R.drawable.ic_ior_media_image)
            .error(R.drawable.ic_ior_warning_circle)

        if (isFolderPreview) {
            requestOptions = requestOptions
                .override(FOLDER_PREVIEW_SIZE_PX, FOLDER_PREVIEW_SIZE_PX)
                .downsample(DownsampleStrategy.AT_MOST)
                .format(DecodeFormat.PREFER_RGB_565)
                .priority(Priority.HIGH)
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

    private fun scaleBitmapPreservingAspectRatio(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source

        val longestEdge = maxOf(width, height)
        if (longestEdge <= maxDimension) return source

        val scale = maxDimension.toFloat() / longestEdge.toFloat()
        val targetWidth = maxOf(1, (width * scale).toInt())
        val targetHeight = maxOf(1, (height * scale).toInt())
        if (targetWidth == width && targetHeight == height) return source

        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun applyVideoThumbnailDisplayMode(imageView: ImageView, isFolderPreview: Boolean) {
        imageView.scaleType = if (isFolderPreview) {
            ImageView.ScaleType.CENTER_CROP
        } else {
            ImageView.ScaleType.FIT_CENTER
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

    private fun decodeLocalPreviewSample(context: Context, imageUri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                srcWidth = bounds.outWidth,
                srcHeight = bounds.outHeight,
                targetMaxDimension = LOCAL_IMAGE_PREVIEW_SAMPLE_MAX_PX
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return context.contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        }
    }

    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        targetMaxDimension: Int
    ): Int {
        if (srcWidth <= 0 || srcHeight <= 0 || targetMaxDimension <= 0) return 1

        var sampleSize = 1
        var currentWidth = srcWidth
        var currentHeight = srcHeight
        while (maxOf(currentWidth, currentHeight) > targetMaxDimension) {
            sampleSize *= 2
            currentWidth = srcWidth / sampleSize
            currentHeight = srcHeight / sampleSize
        }
        return sampleSize.coerceAtLeast(1)
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
            blankLocalImagePreviewCache.evictAll()
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
