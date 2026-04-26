package erl.webdavtoon

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import erl.webdavtoon.databinding.ItemPhotoDetailBinding

/**
 * 图片详情适配器：使用 PhotoView 库实现流畅的缩放体验
 */
class PhotoDetailAdapter(
    private val onLongPress: (Int) -> Unit,
    private val onClick: (Int) -> Unit,
    private val onGesture: (GestureType, Int, Float, Float) -> Boolean = { _, _, _, _ -> false }
) : RecyclerView.Adapter<PhotoDetailAdapter.PhotoDetailViewHolder>() {

    private var photos: List<Photo> = emptyList()
    private var selectionMode = false
    private val selectedIds = mutableSetOf<String>()
    private var maxZoomScale = 3f

    fun setPhotos(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyDataSetChanged()
    }

    fun setMaxZoomPercent(percent: Int) {
        maxZoomScale = (percent.coerceIn(100, 500) / 100f)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoDetailViewHolder {
        val binding = ItemPhotoDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoDetailViewHolder(binding, onLongPress, onClick, onGesture)
    }

    override fun onBindViewHolder(holder: PhotoDetailViewHolder, position: Int) {
        val photo = photos[position]
        holder.bind(photo, selectionMode, selectedIds.contains(photo.id), maxZoomScale)
    }

    override fun getItemCount(): Int = photos.size

    class PhotoDetailViewHolder(
        private val binding: ItemPhotoDetailBinding,
        private val onLongPress: (Int) -> Unit,
        private val onClick: (Int) -> Unit,
        private val onGesture: (GestureType, Int, Float, Float) -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        private var lastTouchX = 0.5f
        private var lastTouchY = 0.5f

        init {
            binding.imageView.apply {
                maximumScale = 10f
                mediumScale = 3f

                setOnDoubleTapListener(object : android.view.GestureDetector.OnDoubleTapListener {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        val position = bindingAdapterPosition
                        if (position == RecyclerView.NO_POSITION) return false
                        val consumed = onGesture(GestureType.SINGLE_TAP, position, normalizedX(e), normalizedY(e))
                        if (!consumed) {
                            onClick(position)
                        }
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val position = bindingAdapterPosition
                        if (position == RecyclerView.NO_POSITION) return false
                        val consumed = onGesture(GestureType.DOUBLE_TAP, position, normalizedX(e), normalizedY(e))
                        if (consumed) {
                            return true
                        }
                        val currentScale = scale
                        if (currentScale > 1.0f) {
                            setScale(1.0f, e.x, e.y, true)
                        } else {
                            setScale(mediumScale, e.x, e.y, true)
                        }
                        return true
                    }

                    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false
                })

                setOnScaleChangeListener { _, _, _ ->
                    if (scale > 1.0f) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                }

                setOnTouchListener { v, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                        lastTouchX = normalizedX(event)
                        lastTouchY = normalizedY(event)
                    }
                    val disallowParent = event.pointerCount > 1 || scale > 1.0f
                    v.parent?.requestDisallowInterceptTouchEvent(disallowParent)
                    false
                }

                setOnLongClickListener {
                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                    val consumed = onGesture(GestureType.LONG_PRESS, position, lastTouchX, lastTouchY)
                    if (!consumed) {
                        onLongPress(position)
                    }
                    true
                }
            }
        }

        private fun normalizedX(event: MotionEvent): Float {
            return (event.x / binding.imageView.width.toFloat()).coerceIn(0f, 0.9999f)
        }

        private fun normalizedY(event: MotionEvent): Float {
            return (event.y / binding.imageView.height.toFloat()).coerceIn(0f, 0.9999f)
        }

        fun bind(photo: Photo, isSelectionMode: Boolean, isSelected: Boolean, maxZoomScale: Float) {
            binding.imageView.minimumScale = 1.0f
            binding.imageView.mediumScale = ((1.0f + maxZoomScale) / 2f).coerceAtLeast(1.5f)
            binding.imageView.maximumScale = maxZoomScale
            binding.imageView.setScale(1.0f, false)


            if (photo.isLocal) {
                WebDavImageLoader.loadLocalImage(
                    binding.imageView.context,
                    photo.imageUri,
                    binding.imageView,
                    limitSize = false
                )
            } else {
                WebDavImageLoader.loadWebDavImage(
                    binding.imageView.context,
                    photo.imageUri,
                    binding.imageView,
                    limitSize = false
                )
            }

            if (isSelectionMode) {
                binding.selectionOverlay.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
                binding.checkIcon.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
            } else {
                binding.selectionOverlay.visibility = android.view.View.GONE
                binding.checkIcon.visibility = android.view.View.GONE
            }
        }

        fun showPreparedDrawable(drawable: Drawable, maxZoomScale: Float) {
            binding.imageView.minimumScale = 1.0f
            binding.imageView.mediumScale = ((1.0f + maxZoomScale) / 2f).coerceAtLeast(1.5f)
            binding.imageView.maximumScale = maxZoomScale
            binding.imageView.setScale(1.0f, false)
            binding.imageView.setImageDrawable(drawable)
        }
    }
}
