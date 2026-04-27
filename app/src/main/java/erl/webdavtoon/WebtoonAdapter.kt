package erl.webdavtoon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import erl.webdavtoon.databinding.ItemPhotoViewBinding

/**
 * 漫画模式适配器。
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

    fun appendPhotos(newPhotos: List<Photo>) {
        if (newPhotos.isEmpty()) return
        val start = photos.size
        photos = photos + newPhotos
        notifyItemRangeInserted(start, newPhotos.size)
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

        init {
            binding.root.setOnLongClickListener {
                onLongPress(bindingAdapterPosition)
                true
            }
        }

        fun bind(photo: Photo, isSelectionMode: Boolean, isSelected: Boolean) {
            val context = binding.root.context

            if (!isSelectionMode) {
                binding.root.setOnClickListener(null)
            } else {
                binding.root.setOnClickListener {
                    onClick(bindingAdapterPosition)
                }
            }

            if (photo.isLocal) {
                WebDavImageLoader.loadLocalImage(
                    context = context,
                    imageUri = photo.imageUri,
                    imageView = binding.imageView,
                    progressBar = binding.progressBar,
                    limitSize = false,
                    isWebtoonReader = true
                )
            } else {
                WebDavImageLoader.loadWebDavImage(
                    context = context,
                    imageUri = photo.imageUri,
                    imageView = binding.imageView,
                    progressBar = binding.progressBar,
                    limitSize = false,
                    isWebtoonReader = true
                )
            }

            if (isSelectionMode) {
                binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
                binding.checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            } else {
                binding.selectionOverlay.visibility = View.GONE
                binding.checkIcon.visibility = View.GONE
            }
        }
    }
}
