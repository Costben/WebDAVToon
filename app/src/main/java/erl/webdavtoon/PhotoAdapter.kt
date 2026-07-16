package erl.webdavtoon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import erl.webdavtoon.databinding.ItemPhotoBinding

class PhotoAdapter(
    private val onPhotoClick: (List<Photo>, Int) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val onPhotoDimensionsResolved: (photoId: String, width: Int, height: Int) -> Unit = { _, _, _ -> }
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    private var photos: List<Photo> = emptyList()
    private var isImmersiveMode = false
    private var isSingleColumn = false
    private var showFilenames = true
    private var isSelectionMode = false
    private val selectedPositions = mutableSetOf<Int>()
    private val resolvedDimensions = mutableMapOf<String, Pair<Int, Int>>()

    init {
        setHasStableIds(true)
    }

    fun setPhotos(newPhotos: List<Photo>): Boolean {
        if (photos == newPhotos) return false

        val oldSize = photos.size
        if (newPhotos.size > oldSize && newPhotos.take(oldSize) == photos) {
            photos = newPhotos
            notifyItemRangeInserted(oldSize, newPhotos.size - oldSize)
        } else {
            photos = newPhotos
            notifyDataSetChanged()
        }
        return true
    }

    fun setImmersiveMode(immersive: Boolean, singleColumn: Boolean) {
        isImmersiveMode = immersive
        isSingleColumn = singleColumn
        notifyDataSetChanged()
    }

    fun setShowFilenames(show: Boolean) {
        if (showFilenames == show) return
        showFilenames = show
        notifyDataSetChanged()
    }

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

    fun getSelectedPhotos(): List<Photo> = selectedPositions.mapNotNull { photos.getOrNull(it) }

    fun getPhotoAspectRatio(position: Int): Float {
        val photo = photos.getOrNull(position) ?: return DEFAULT_ASPECT_RATIO
        return PhotoAspectRatioResolver.resolve(photo, resolvedDimensions[photo.id])
    }

    fun updateResolvedDimensions(photoId: String, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val current = resolvedDimensions[photoId]
        if (current?.first == width && current.second == height) return false
        resolvedDimensions[photoId] = width to height
        return true
    }

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
        return PhotoViewHolder(binding, onPhotoDimensionsResolved)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        val isSelected = selectedPositions.contains(position)

        holder.bind(
            photo = photo,
            isImmersiveMode = isImmersiveMode,
            isSingleColumn = isSingleColumn,
            showFilenames = showFilenames,
            isSelectionMode = isSelectionMode,
            isSelected = isSelected
        )

        holder.itemView.setOnClickListener {
            if ((holder.itemView.context as? MainActivity)?.shouldSuppressPhotoInteraction() == true) {
                return@setOnClickListener
            }
            if (isSelectionMode) {
                toggleSelection(position)
            } else {
                onPhotoClick(photos, position)
            }
        }

        holder.itemView.setOnLongClickListener {
            if ((holder.itemView.context as? MainActivity)?.shouldSuppressPhotoInteraction() == true) {
                return@setOnLongClickListener true
            }
            if (!isSelectionMode) {
                (holder.itemView.context as? MainActivity)?.setLongPressSelection(true)
                setSelectionMode(true)
                toggleSelection(position)
                true
            } else {
                false
            }
        }

        val cardView = holder.itemView as MaterialCardView
        cardView.strokeWidth = if (isSelectionMode && isSelected) 4 else 0

        if (isImmersiveMode && isSingleColumn) {
            cardView.radius = 0f
            cardView.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            cardView.elevation = 0f
            (cardView.layoutParams as RecyclerView.LayoutParams).setMargins(0, 0, 0, 0)
        } else {
            cardView.radius = 12f
            cardView.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            cardView.elevation = 0f
            (cardView.layoutParams as RecyclerView.LayoutParams).setMargins(0, 0, 0, 0)
        }
    }

    override fun getItemCount(): Int = photos.size

    override fun getItemId(position: Int): Long {
        return photos.getOrNull(position)?.id?.stableLongId() ?: RecyclerView.NO_ID
    }

    class PhotoViewHolder(
        private val binding: ItemPhotoBinding,
        private val onPhotoDimensionsResolved: (photoId: String, width: Int, height: Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            photo: Photo,
            isImmersiveMode: Boolean,
            isSingleColumn: Boolean,
            showFilenames: Boolean,
            isSelectionMode: Boolean,
            isSelected: Boolean
        ) {
            binding.titleTextView.text = photo.title
            binding.titleTextView.visibility = if (!showFilenames || isImmersiveMode) View.GONE else View.VISIBLE
            binding.imageView.layoutParams = binding.imageView.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }

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
            val imageKey = buildImageBindKey(photo)
            val shouldLoadImage = binding.imageView.getTag(R.id.tag_media_bind_key) != imageKey

            if (isVideo) {
                binding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                binding.imageView.adjustViewBounds = false
                if (shouldLoadImage) {
                    binding.imageView.setTag(R.id.tag_media_bind_key, imageKey)
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
                }
            } else {
                binding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                binding.imageView.adjustViewBounds = false
                if (shouldLoadImage) {
                    binding.imageView.setTag(R.id.tag_media_bind_key, imageKey)
                    loadWaterfallImageWhenMeasured(photo, imageKey)
                }
            }

            if (isImmersiveMode && isSingleColumn) {
                binding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_XY
            }
        }

        private fun buildImageBindKey(photo: Photo): String {
            return buildString {
                append(photo.mediaType.name)
                append('|')
                append(photo.id)
                append('|')
                append(photo.imageUri)
            }
        }

        private fun loadWaterfallImageWhenMeasured(photo: Photo, imageKey: String) {
            val imageView = binding.imageView
            val width = imageView.width.takeIf { it > 0 } ?: imageView.layoutParams?.width?.takeIf { it > 0 }
            val height = imageView.height.takeIf { it > 0 } ?: imageView.layoutParams?.height?.takeIf { it > 0 }
            if (width != null && height != null) {
                loadWaterfallImage(photo, imageKey, width, height)
                return
            }

            imageView.post {
                val postedWidth = imageView.width.takeIf { it > 0 } ?: imageView.layoutParams?.width?.takeIf { it > 0 }
                val postedHeight = imageView.height.takeIf { it > 0 } ?: imageView.layoutParams?.height?.takeIf { it > 0 }
                if (postedWidth == null || postedHeight == null) return@post
                loadWaterfallImage(photo, imageKey, postedWidth, postedHeight)
            }
        }

        private fun loadWaterfallImage(photo: Photo, imageKey: String, width: Int, height: Int) {
            val imageView = binding.imageView
            if (imageView.getTag(R.id.tag_media_bind_key) != imageKey) return
            if (photo.isLocal) {
                WebDavImageLoader.loadLocalImage(
                    imageView.context,
                    photo.imageUri,
                    imageView,
                    limitSize = false,
                    isWaterfall = true,
                    width = width,
                    height = height,
                    onDimensionsReady = { resolvedWidth, resolvedHeight ->
                        onPhotoDimensionsResolved(photo.id, resolvedWidth, resolvedHeight)
                    }
                )
            } else {
                WebDavImageLoader.loadWebDavImage(
                    imageView.context,
                    photo.imageUri,
                    imageView,
                    limitSize = false,
                    isWaterfall = true,
                    width = width,
                    height = height,
                    onDimensionsReady = { resolvedWidth, resolvedHeight ->
                        onPhotoDimensionsResolved(photo.id, resolvedWidth, resolvedHeight)
                    }
                )
            }
        }
    }

    private fun String.stableLongId(): Long {
        var hash = 1125899906842597L
        forEach { char ->
            hash = 31L * hash + char.code
        }
        return hash
    }

    companion object {
        private const val DEFAULT_ASPECT_RATIO = 1f
    }
}
