package erl.webdavtoon

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import erl.webdavtoon.databinding.ItemMixedFolderBinding
import erl.webdavtoon.databinding.ItemPhotoBinding

class MixedWaterfallAdapter(
    private val onFolderClick: (Folder) -> Unit,
    private val onMediaClick: (Photo) -> Unit,
    private val shouldSuppressItemInteraction: () -> Boolean = { false },
    private val onPhotoDimensionsResolved: (photoId: String, width: Int, height: Int) -> Unit,
    private val onRemotePreviewNeeded: (Folder, Boolean) -> Unit,
    private val remotePreviewGeneration: () -> String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private data class RemotePreviewKey(
        val generation: String,
        val path: String
    )

    private var items: List<MixedWaterfallItem> = emptyList()
    private var showFilenames = true
    private val resolvedDimensions = mutableMapOf<String, Pair<Int, Int>>()
    private val requestedPreviewKeys = hashSetOf<RemotePreviewKey>()
    private val remotePreviewCache = mutableMapOf<RemotePreviewKey, List<Uri>>()
    private val forcedPreviewRefreshPaths = hashSetOf<String>()

    init {
        setHasStableIds(true)
    }

    fun setItems(newItems: List<MixedWaterfallItem>) {
        val generation = remotePreviewGeneration()
        val currentPaths = newItems.mapNotNull {
            (it as? MixedWaterfallItem.FolderTile)?.folder?.path
        }.toSet()
        requestedPreviewKeys.retainAll { it.path in currentPaths }
        remotePreviewCache.keys.retainAll { it.path in currentPaths }
        forcedPreviewRefreshPaths.retainAll(currentPaths)

        items = newItems.map { item ->
            if (item !is MixedWaterfallItem.FolderTile) {
                return@map item
            }
            val key = RemotePreviewKey(generation, item.folder.path)
            if (!item.folder.isLocal && item.folder.previewUris.isNotEmpty()) {
                remotePreviewCache[key] = item.folder.previewUris
                return@map item
            }
            remotePreviewCache[key]?.let { cachedPreviews ->
                return@map item.copy(folder = item.folder.copy(previewUris = cachedPreviews))
            }
            item
        }
        notifyDataSetChanged()
    }

    fun setShowFilenames(show: Boolean) {
        if (showFilenames == show) return
        showFilenames = show
        notifyDataSetChanged()
    }

    fun updateFolderPreview(path: String, previewUris: List<Uri>, hasSubFolders: Boolean) {
        val index = items.indexOfFirst {
            it is MixedWaterfallItem.FolderTile && it.folder.path == path
        }
        if (index == -1) return

        val generation = remotePreviewGeneration()
        val key = RemotePreviewKey(generation, path)
        forcedPreviewRefreshPaths.remove(path)
        remotePreviewCache[key] = previewUris

        val current = items[index] as? MixedWaterfallItem.FolderTile ?: return
        val updatedFolder = current.folder.copy(
            previewUris = previewUris,
            hasSubFolders = current.folder.hasSubFolders || hasSubFolders
        )
        if (updatedFolder == current.folder) return

        items = items.toMutableList().also { mutable ->
            mutable[index] = MixedWaterfallItem.FolderTile(updatedFolder)
        }
        notifyItemChanged(index)
    }

    fun refreshVisibleRemotePreviews() {
        val generation = remotePreviewGeneration()
        val remotePaths = items.asSequence()
            .mapNotNull { (it as? MixedWaterfallItem.FolderTile)?.folder }
            .filter { !it.isLocal && !it.path.startsWith("virtual://") }
            .map { it.path }
            .toSet()
        if (remotePaths.isEmpty()) return

        forcedPreviewRefreshPaths.clear()
        forcedPreviewRefreshPaths.addAll(remotePaths)
        requestedPreviewKeys.removeAll { it.generation == generation && it.path in remotePaths }
        notifyItemRangeChanged(0, items.size)
    }

    fun getItemAspectRatio(position: Int): Float {
        return when (val item = items.getOrNull(position)) {
            is MixedWaterfallItem.FolderTile -> FOLDER_TILE_ASPECT_RATIO
            is MixedWaterfallItem.MediaTile -> PhotoAspectRatioResolver.resolve(
                item.photo,
                resolvedDimensions[item.photo.id]
            )
            null -> DEFAULT_ASPECT_RATIO
        }
    }

    fun updateResolvedDimensions(photoId: String, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val current = resolvedDimensions[photoId]
        if (current?.first == width && current.second == height) return false
        resolvedDimensions[photoId] = width to height
        return true
    }

    fun mediaPhotos(): List<Photo> {
        return items.mapNotNull { (it as? MixedWaterfallItem.MediaTile)?.photo }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is MixedWaterfallItem.FolderTile -> VIEW_TYPE_FOLDER
            is MixedWaterfallItem.MediaTile -> VIEW_TYPE_MEDIA
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_FOLDER -> {
                val binding = ItemMixedFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                FolderViewHolder(binding).apply {
                    itemView.setOnClickListener {
                        if (shouldSuppressItemInteraction()) return@setOnClickListener
                        val position = bindingAdapterPosition
                        val folder = (items.getOrNull(position) as? MixedWaterfallItem.FolderTile)?.folder
                            ?: return@setOnClickListener
                        onFolderClick(folder)
                    }
                }
            }
            else -> {
                val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                MediaViewHolder(
                    binding = binding,
                    delegate = PhotoAdapter.PhotoViewHolder(binding, onPhotoDimensionsResolved)
                ).apply {
                    itemView.setOnClickListener {
                        if (shouldSuppressItemInteraction()) return@setOnClickListener
                        val position = bindingAdapterPosition
                        val photo = (items.getOrNull(position) as? MixedWaterfallItem.MediaTile)?.photo
                            ?: return@setOnClickListener
                        onMediaClick(photo)
                    }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MixedWaterfallItem.FolderTile -> {
                (holder as FolderViewHolder).bind(item.folder)
                maybeRequestRemotePreview(position)
            }
            is MixedWaterfallItem.MediaTile -> {
                (holder as MediaViewHolder).bind(item.photo, showFilenames)
            }
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        maybeRequestRemotePreview(holder.bindingAdapterPosition)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is FolderViewHolder) {
            holder.clear()
        }
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long {
        return when (val item = items.getOrNull(position)) {
            is MixedWaterfallItem.FolderTile -> "folder:${item.folder.path}".stableLongId()
            is MixedWaterfallItem.MediaTile -> "media:${item.photo.id}".stableLongId()
            null -> RecyclerView.NO_ID
        }
    }

    private fun maybeRequestRemotePreview(position: Int) {
        if (position == RecyclerView.NO_POSITION || position >= items.size) return
        val folder = (items[position] as? MixedWaterfallItem.FolderTile)?.folder ?: return
        val forceRefresh = folder.path in forcedPreviewRefreshPaths
        if (!forceRefresh && !needsRemotePreviewRefresh(folder)) return

        val key = RemotePreviewKey(remotePreviewGeneration(), folder.path)
        if (!forceRefresh && remotePreviewCache.containsKey(key)) return
        if (requestedPreviewKeys.add(key)) {
            onRemotePreviewNeeded(folder, forceRefresh)
        }
    }

    private fun needsRemotePreviewRefresh(folder: Folder): Boolean {
        return !folder.isLocal && !folder.path.startsWith("virtual://") && folder.previewUris.isEmpty()
    }

    private class MediaViewHolder(
        binding: ItemPhotoBinding,
        private val delegate: PhotoAdapter.PhotoViewHolder
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: Photo, showFilenames: Boolean) {
            delegate.bind(
                photo = photo,
                isImmersiveMode = false,
                isSingleColumn = false,
                showFilenames = showFilenames,
                isSelectionMode = false,
                isSelected = false,
                displaySize = null
            )
            (itemView as? MaterialCardView)?.apply {
                radius = resources.getDimension(R.dimen.waterfall_card_corner_radius)
                setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                elevation = 0f
                strokeWidth = 0
            }
        }
    }

    private class FolderViewHolder(
        private val binding: ItemMixedFolderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val previewBinder = FolderPreviewBinder(
            previewImageViews = listOf(binding.preview1, binding.preview2, binding.preview3, binding.preview4),
            folderIcon = binding.folderIcon,
            logTag = "MixedWaterfallAdapter"
        )

        fun bind(folder: Folder) {
            binding.folderName.text = folder.name.trimEnd('/')
            binding.root.radius = itemView.resources.getDimension(R.dimen.waterfall_card_corner_radius)
            previewBinder.bind(folder)
        }

        fun clear() {
            previewBinder.clear()
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
        private const val VIEW_TYPE_FOLDER = 1
        private const val VIEW_TYPE_MEDIA = 2
        private const val DEFAULT_ASPECT_RATIO = 1f
        private const val FOLDER_TILE_ASPECT_RATIO = 0.78f
    }
}
