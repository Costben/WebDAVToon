package erl.webdavtoon

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import erl.webdavtoon.databinding.ItemPhotoDetailBinding

/**
 * 图片详情适配器：使用 PhotoView 库实现流畅的缩放体验
 */
class PhotoDetailAdapter(private val onLongPress: () -> Unit) : RecyclerView.Adapter<PhotoDetailAdapter.PhotoDetailViewHolder>() {

    private var photos: List<Photo> = emptyList()

    fun setPhotos(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoDetailViewHolder {
        val binding = ItemPhotoDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoDetailViewHolder(binding, onLongPress)
    }

    override fun onBindViewHolder(holder: PhotoDetailViewHolder, position: Int) {
        val photo = photos[position]
        holder.bind(photo)
    }

    override fun getItemCount(): Int = photos.size

    class PhotoDetailViewHolder(private val binding: ItemPhotoDetailBinding, private val onLongPress: () -> Unit) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            // 配置 PhotoView
            binding.imageView.apply {
                // 设置最大缩放倍数
                maximumScale = 10f
                mediumScale = 3f
                
                // 1. 优化双击缩放逻辑：双击 1x -> 3x, 再次双击 -> 1x
                // 我们通过设置 OnDoubleTapListener 来拦截默认行为
                setOnDoubleTapListener(object : android.view.GestureDetector.OnDoubleTapListener {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        // 单击仍然交给 PhotoView 的其他监听器或外部处理
                        return false
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val currentScale = scale
                        if (currentScale > 1.0f) {
                            // 如果当前已经放大，双击还原到 1x
                            setScale(1.0f, e.x, e.y, true)
                        } else {
                            // 如果是 1x，双击放大到 3x (mediumScale)
                            setScale(mediumScale, e.x, e.y, true)
                        }
                        return true
                    }

                    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false
                })

                // 2. 提高缩放优先级，防止双指操作触发 ViewPager2/RecyclerView 翻页
                // PhotoView 内部在检测到多指时会自动调用 requestDisallowInterceptTouchEvent(true)
                // 但为了更保险，我们可以在缩放开始时显式调用
                setOnScaleChangeListener { scaleFactor, focusX, focusY ->
                    if (scale > 1.0f) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
                
                // 设置长按监听
                setOnLongClickListener {
                    onLongPress()
                    true
                }
            }
        }

        fun bind(photo: Photo) {
            // 3. 每次重新绑定数据时重置缩放比例，确保页面切换后恢复 1x
            binding.imageView.setScale(1.0f, false)
            
            if (photo.isLocal) {
                WebDavImageLoader.loadLocalImage(
                    binding.imageView.context,
                    photo.imageUri,
                    binding.imageView,
                    limitSize = false // 详情模式下加载原图
                )
            } else {
                WebDavImageLoader.loadWebDavImage(
                    binding.imageView.context,
                    photo.imageUri,
                    binding.imageView,
                    limitSize = false
                )
            }
        }
    }
}
