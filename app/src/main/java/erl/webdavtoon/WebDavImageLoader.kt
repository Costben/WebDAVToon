package erl.webdavtoon

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target

/**
 * WebDAV 专用图片加载器
 */
object WebDavImageLoader {
    
    fun loadWebDavImage(
        context: Context,
        imageUri: Uri,
        imageView: ImageView,
        progressBar: ProgressBar? = null,
        limitSize: Boolean = true
    ) {
        val requestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)
            .priority(com.bumptech.glide.Priority.HIGH)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
        
        if (limitSize) {
            requestOptions.downsample(DownsampleStrategy.AT_MOST)
        } else {
            requestOptions.override(Target.SIZE_ORIGINAL)
            requestOptions.downsample(DownsampleStrategy.NONE)
        }
        
        Glide.with(context)
            .load(imageUri)
            .apply(requestOptions)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
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
            })
            .into(imageView)
    }
    
    fun loadLocalImage(
        context: Context,
        imageUri: Uri,
        imageView: ImageView,
        progressBar: ProgressBar? = null,
        limitSize: Boolean = true
    ) {
        val requestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)
            .priority(com.bumptech.glide.Priority.HIGH)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)

        if (limitSize) {
            requestOptions.downsample(DownsampleStrategy.AT_MOST)
        } else {
            requestOptions.override(Target.SIZE_ORIGINAL)
            requestOptions.downsample(DownsampleStrategy.NONE)
        }

        Glide.with(context)
            .load(imageUri)
            .apply(requestOptions)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
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
            })
            .into(imageView)
    }
}
