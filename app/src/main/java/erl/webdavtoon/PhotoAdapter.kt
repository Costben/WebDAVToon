package erl.webdavtoon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import erl.webdavtoon.databinding.ItemPhotoBinding

/**
 * 瀑布流图片适配�?
 */
class PhotoAdapter(
    private val onPhotoClick: (List<Photo>, Int) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    private var photos: List<Photo> = emptyList()
    private var isImmersiveMode = false
    private var isSingleColumn = false
    
    // 多选状态管�?
    private var isSelectionMode = false
    private val selectedPositions = mutableSetOf<Int>()

    fun setPhotos(newPhotos: List<Photo>) {
        if (photos == newPhotos) return

        val oldSize = photos.size
        if (newPhotos.size > oldSize && newPhotos.take(oldSize) == photos) {
            photos = newPhotos
            notifyItemRangeInserted(oldSize, newPhotos.size - oldSize)
        } else {
            photos = newPhotos
            notifyDataSetChanged()
        }
    }
    fun setImmersiveMode(immersive: Boolean, singleColumn: Boolean) {
        isImmersiveMode = immersive
        isSingleColumn = singleColumn
        notifyDataSetChanged()
    }

    // 多选相关方�?
    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) {
                selectedPositions.clear()
            }
            notifyDataSetChanged()
        }
    }

    fun isSelectionMode() = isSelectionMode

    fun getSelectedCount() = selectedPositions.size

    fun getSelectedPhotos(): List<Photo> = selectedPositions.map { photos[it] }

    fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        notifyItemChanged(position)
        onSelectionChanged(selectedPositions.size)
    }

    fun clearSelection() {
        selectedPositions.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        val isSelected = selectedPositions.contains(position)
        
        holder.bind(photo, isImmersiveMode, isSingleColumn, isSelectionMode, isSelected)
        
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(position)
            } else {
                onPhotoClick(photos, position)
            }
        }
        
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                // 标记为长按触�?
                (holder.itemView.context as? MainActivity)?.setLongPressSelection(true)
                setSelectionMode(true)
                toggleSelection(position)
                true
            } else {
                false
            }
        }
        
        // 根据沉浸模式和选中状态调整布局
        val cardView = holder.itemView as com.google.android.material.card.MaterialCardView
        
        // 选中状态的边框
        if (isSelectionMode && isSelected) {
            cardView.strokeWidth = 4 // 使用 2dp 的像素值或动态获�?
        } else {
            cardView.strokeWidth = 0
        }

        if (isImmersiveMode && isSingleColumn) {
            cardView.radius = 0f
            cardView.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            cardView.elevation = 0f
            val layoutParams = cardView.layoutParams as RecyclerView.LayoutParams
            layoutParams.setMargins(0, 0, 0, 0)
            cardView.layoutParams = layoutParams
        } else {
            cardView.radius = 12f
            cardView.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            cardView.elevation = 0f
            val layoutParams = cardView.layoutParams as RecyclerView.LayoutParams
            layoutParams.setMargins(4, 4, 4, 4)
            cardView.layoutParams = layoutParams
        }
    }

    override fun getItemCount(): Int = photos.size

    class PhotoViewHolder(private val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: Photo, isImmersiveMode: Boolean, isSingleColumn: Boolean, isSelectionMode: Boolean, isSelected: Boolean) {
            binding.titleTextView.text = photo.title
            
            if (isImmersiveMode) {
                binding.titleTextView.visibility = View.GONE
            } else {
                binding.titleTextView.visibility = View.VISIBLE
            }
            
            // 多选状态反�?
            if (isSelectionMode) {
                binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
                binding.checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            } else {
                binding.selectionOverlay.visibility = View.GONE
                binding.checkIcon.visibility = View.GONE
            }

            val isVideo = photo.mediaType == MediaType.VIDEO
            val durationText = formatVideoDuration(photo.durationMs)
            binding.videoIndicator.visibility = if (isVideo) View.VISIBLE else View.GONE
            binding.videoDurationTextView.visibility = if (isVideo && durationText.isNotEmpty()) View.VISIBLE else View.GONE
            binding.videoDurationTextView.text = durationText

            if (isVideo) {
                binding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                binding.imageView.adjustViewBounds = true
                if (photo.isLocal) {
                    WebDavImageLoader.loadLocalVideoThumbnail(
                        binding.imageView.context,
                        photo.imageUri,
                        binding.imageView,
                        isFolderPreview = false
                    )
                } else {
                    WebDavImageLoader.loadWebDavVideoThumbnail(
                        binding.imageView.context,
                        photo.imageUri,
                        binding.imageView,
                        isFolderPreview = false
                    )
                }
            } else {
                if (photo.isLocal) {
                    WebDavImageLoader.loadLocalImage(
                        binding.imageView.context,
                        photo.imageUri,
                        binding.imageView,
                        limitSize = false,
                        isWaterfall = true
                    )
                } else {
                    WebDavImageLoader.loadWebDavImage(
                        binding.imageView.context,
                        photo.imageUri,
                        binding.imageView,
                        limitSize = false,
                        isWaterfall = true
                    )
                }
            }
            
            if (isImmersiveMode && isSingleColumn) {
                binding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_XY
                binding.imageView.adjustViewBounds = false
            } else if (isVideo) {
                binding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                binding.imageView.adjustViewBounds = true
            } else {
                binding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                binding.imageView.adjustViewBounds = true
            }
        }
    }
}
