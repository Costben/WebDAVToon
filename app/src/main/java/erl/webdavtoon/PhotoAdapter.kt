package erl.webdavtoon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import erl.webdavtoon.databinding.ItemPhotoBinding

/**
 * 瀑布流图片适配器
 */
class PhotoAdapter(
    private val onPhotoClick: (List<Photo>, Int) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    private var photos: List<Photo> = emptyList()
    private var isImmersiveMode = false
    private var isSingleColumn = false

    fun setPhotos(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    fun setImmersiveMode(immersive: Boolean, singleColumn: Boolean) {
        isImmersiveMode = immersive
        isSingleColumn = singleColumn
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        holder.bind(photo, isImmersiveMode, isSingleColumn)
        holder.itemView.setOnClickListener {
            onPhotoClick(photos, position)
        }
        
        // 根据沉浸模式调整布局
        val cardView = holder.itemView as com.google.android.material.card.MaterialCardView
        if (isImmersiveMode && isSingleColumn) {
            // 1列沉浸模式：移除圆角和边界
            cardView.radius = 0f
            cardView.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            cardView.strokeWidth = 0
            cardView.elevation = 0f
            // 移除间距
            val layoutParams = cardView.layoutParams as RecyclerView.LayoutParams
            layoutParams.setMargins(0, 0, 0, 0)
            cardView.layoutParams = layoutParams
        } else {
            // 普通模式：恢复圆角和边界
            cardView.radius = 12f
            cardView.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            cardView.strokeWidth = 0
            cardView.elevation = 0f
            // 恢复间距
            val layoutParams = cardView.layoutParams as RecyclerView.LayoutParams
            layoutParams.setMargins(4, 4, 4, 4)
            cardView.layoutParams = layoutParams
        }
    }

    override fun getItemCount(): Int = photos.size

    class PhotoViewHolder(private val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: Photo, isImmersiveMode: Boolean, isSingleColumn: Boolean) {
            binding.titleTextView.text = photo.title
            
            // 根据沉浸模式控制文件名和阴影的显示
            if (isImmersiveMode) {
                binding.titleTextView.visibility = View.GONE
            } else {
                binding.titleTextView.visibility = View.VISIBLE
            }
            
            if (photo.isLocal) {
                WebDavImageLoader.loadLocalImage(
                    binding.imageView.context,
                    photo.imageUri,
                    binding.imageView
                )
            } else {
                WebDavImageLoader.loadWebDavImage(
                    binding.imageView.context,
                    photo.imageUri,
                    binding.imageView
                )
            }
            
            // 1列沉浸模式下调整图片样式
            if (isImmersiveMode && isSingleColumn) {
                binding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_XY
            } else {
                binding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                binding.imageView.adjustViewBounds = true
            }
        }
    }
}
