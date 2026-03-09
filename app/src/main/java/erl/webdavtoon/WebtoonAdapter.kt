package erl.webdavtoon

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import erl.webdavtoon.databinding.ItemPhotoViewBinding

/**
 * Webtoon 模式适配器
 */
class WebtoonAdapter(
    private val tapListener: OnWebtoonTapListener? = null,
    private val onLongPress: (Int) -> Unit = {},
    private val onClick: (Int) -> Unit = {}
) : RecyclerView.Adapter<WebtoonAdapter.WebtoonViewHolder>() {

    interface OnWebtoonTapListener {
        fun onDoubleTap()
    }

    private var photos: List<Photo> = emptyList()
    private var selectionMode = false
    private val selectedIds = mutableSetOf<String>()

    fun setPhotos(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyDataSetChanged()
    }

    fun isSelectionMode() = selectionMode

    fun toggleSelection(position: Int) {
        if (position !in photos.indices) return
        val id = photos[position].id
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        notifyItemChanged(position)
    }

    fun getSelectedPhotos(): List<Photo> {
        return photos.filter { selectedIds.contains(it.id) }
    }

    fun getSelectedCount() = selectedIds.size

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WebtoonViewHolder {
        val binding = ItemPhotoViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WebtoonViewHolder(binding, tapListener, onLongPress, onClick)
    }

    override fun onBindViewHolder(holder: WebtoonViewHolder, position: Int) {
        val photo = photos[position]
        holder.bind(photo, selectionMode, selectedIds.contains(photo.id))
    }

    override fun getItemCount(): Int = photos.size

    class WebtoonViewHolder(
        private val binding: ItemPhotoViewBinding,
        private val tapListener: OnWebtoonTapListener?,
        private val onLongPress: (Int) -> Unit,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private val gestureDetector = android.view.GestureDetector(binding.root.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                tapListener?.onDoubleTap()
                return true
            }

            override fun onLongPress(e: android.view.MotionEvent) {
                onLongPress(bindingAdapterPosition)
            }

            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                onClick(bindingAdapterPosition)
                return true
            }
        })

        init {
            binding.root.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true 
            }
        }

        fun bind(photo: Photo, isSelectionMode: Boolean, isSelected: Boolean) {
            val context = binding.root.context
            
            binding.progressBar.visibility = android.view.View.VISIBLE

            if (photo.isLocal) {
                WebDavImageLoader.loadLocalImage(
                    context,
                    photo.imageUri,
                    binding.imageView,
                    binding.progressBar,
                    limitSize = false // Webtoon模式下不限制图片大小，确保显示全图
                )
            } else {
                WebDavImageLoader.loadWebDavImage(
                    context,
                    photo.imageUri,
                    binding.imageView,
                    binding.progressBar,
                    limitSize = false // Webtoon模式下不限制图片大小，确保显示全图
                )
            }

            // 更新多选 UI
            if (isSelectionMode) {
                binding.selectionOverlay.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
                binding.checkIcon.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
            } else {
                binding.selectionOverlay.visibility = android.view.View.GONE
                binding.checkIcon.visibility = android.view.View.GONE
            }
        }
    }
}
