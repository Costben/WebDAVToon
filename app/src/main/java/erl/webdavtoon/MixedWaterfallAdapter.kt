package erl.webdavtoon

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import erl.webdavtoon.databinding.ItemMixedFolderBinding
import erl.webdavtoon.databinding.ItemPhotoBinding

class MixedWaterfallAdapter(
    private val onFolderClick: (Folder) -> Unit,
    private val onMediaClick: (Photo) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val shouldSuppressItemInteraction: () -> Boolean = { false },
    private val onPhotoDimensionsResolved: (photoId: String, width: Int, height: Int) -> Unit,
    private val onRemotePreviewNeeded: (Folder, Boolean) -> Unit,
    private val remotePreviewGeneration: () -> String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private data class RemotePreviewKey(
        val generation: String,
        val itemKey: String
    )

    private var items: List<MixedWaterfallItem> = emptyList()
    private var showFilenames = true
    private val resolvedDimensions = mutableMapOf<String, Pair<Int, Int>>()
    private val requestedPreviewKeys = hashSetOf<RemotePreviewKey>()
    private val remotePreviewCache = mutableMapOf<RemotePreviewKey, List<Uri>>()
    private val forcedPreviewRefreshPaths = hashSetOf<String>()
    private val selectedItemKeys = linkedSetOf<String>()
    var isSelectionMode = false
        private set

    init {
        setHasStableIds(true)
    }

    fun setItems(newItems: List<MixedWaterfallItem>) {
        val previousSelectionCount = selectedItemKeys.size
        val previousSelectionMode = isSelectionMode
        val generation = remotePreviewGeneration()
        val currentFolderKeys = newItems.mapNotNull {
            (it as? MixedWaterfallItem.FolderTile)?.let(MixedWaterfallIdentity::key)
        }.toSet()
        requestedPreviewKeys.retainAll { it.itemKey in currentFolderKeys }
        remotePreviewCache.keys.retainAll { it.itemKey in currentFolderKeys }
        forcedPreviewRefreshPaths.retainAll(currentFolderKeys)

        items = newItems.map { item ->
            if (item !is MixedWaterfallItem.FolderTile) {
                return@map item
            }
            val key = RemotePreviewKey(generation, MixedWaterfallIdentity.key(item))
            if (!item.folder.isLocal && item.folder.previewUris.isNotEmpty()) {
                remotePreviewCache[key] = item.folder.previewUris
                return@map item
            }
            remotePreviewCache[key]?.let { cachedPreviews ->
                return@map item.copy(folder = item.folder.copy(previewUris = cachedPreviews))
            }
            item
        }
        val currentKeys = items.mapTo(mutableSetOf(), MixedWaterfallIdentity::key)
        selectedItemKeys.retainAll(currentKeys)
        if (selectedItemKeys.isEmpty()) {
            isSelectionMode = false
        }
        notifyDataSetChanged()
        if (previousSelectionCount != selectedItemKeys.size || previousSelectionMode != isSelectionMode) {
            onSelectionChanged(selectedItemKeys.size)
        }
    }

    fun setShowFilenames(show: Boolean) {
        if (showFilenames == show) return
        showFilenames = show
        notifyDataSetChanged()
    }

    fun updateFolderPreview(folder: Folder, previewUris: List<Uri>, hasSubFolders: Boolean) {
        val itemKey = MixedWaterfallIdentity.folderKey(folder)
        val index = items.indexOfFirst {
            it is MixedWaterfallItem.FolderTile && MixedWaterfallIdentity.key(it) == itemKey
        }
        if (index == -1) return

        val generation = remotePreviewGeneration()
        val key = RemotePreviewKey(generation, itemKey)
        forcedPreviewRefreshPaths.remove(itemKey)
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
        val remoteKeys = items.asSequence()
            .mapNotNull { it as? MixedWaterfallItem.FolderTile }
            .filter { !it.folder.isLocal && !it.folder.path.startsWith("virtual://") }
            .map(MixedWaterfallIdentity::key)
            .toSet()
        if (remoteKeys.isEmpty()) return

        forcedPreviewRefreshPaths.clear()
        forcedPreviewRefreshPaths.addAll(remoteKeys)
        requestedPreviewKeys.removeAll { it.generation == generation && it.itemKey in remoteKeys }
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

    fun getSelectedFolders(): List<Folder> {
        return items.mapNotNull { item ->
            (item as? MixedWaterfallItem.FolderTile)
                ?.takeIf { MixedWaterfallIdentity.key(it) in selectedItemKeys }
                ?.folder
        }
    }

    fun getSelectedPhotos(): List<Photo> {
        return items.mapNotNull { item ->
            (item as? MixedWaterfallItem.MediaTile)
                ?.takeIf { MixedWaterfallIdentity.key(it) in selectedItemKeys }
                ?.photo
        }
    }

    fun exitSelectionMode() {
        if (!isSelectionMode && selectedItemKeys.isEmpty()) return
        val changedKeys = selectedItemKeys.toList()
        selectedItemKeys.clear()
        isSelectionMode = false
        changedKeys.forEach(::notifyItemChangedByKey)
        onSelectionChanged(0)
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
                        val item = items.getOrNull(position) as? MixedWaterfallItem.FolderTile
                            ?: return@setOnClickListener
                        if (isSelectionMode) {
                            toggleSelection(item)
                        } else {
                            onFolderClick(item.folder)
                        }
                    }
                    itemView.setOnLongClickListener {
                        if (shouldSuppressItemInteraction()) return@setOnLongClickListener true
                        val position = bindingAdapterPosition
                        val item = items.getOrNull(position) as? MixedWaterfallItem.FolderTile
                            ?: return@setOnLongClickListener false
                        enterSelectionMode(item)
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
                        val item = items.getOrNull(position) as? MixedWaterfallItem.MediaTile
                            ?: return@setOnClickListener
                        if (isSelectionMode) {
                            toggleSelection(item)
                        } else {
                            onMediaClick(item.photo)
                        }
                    }
                    itemView.setOnLongClickListener {
                        if (shouldSuppressItemInteraction()) return@setOnLongClickListener true
                        val position = bindingAdapterPosition
                        val item = items.getOrNull(position) as? MixedWaterfallItem.MediaTile
                            ?: return@setOnLongClickListener false
                        enterSelectionMode(item)
                    }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MixedWaterfallItem.FolderTile -> {
                (holder as FolderViewHolder).bind(
                    folder = item.folder,
                    isSelected = MixedWaterfallIdentity.key(item) in selectedItemKeys
                )
                maybeRequestRemotePreview(position)
            }
            is MixedWaterfallItem.MediaTile -> {
                (holder as MediaViewHolder).bind(
                    photo = item.photo,
                    showFilenames = showFilenames,
                    isSelectionMode = isSelectionMode,
                    isSelected = MixedWaterfallIdentity.key(item) in selectedItemKeys
                )
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
        val item = items.getOrNull(position) ?: return RecyclerView.NO_ID
        return MixedWaterfallIdentity.key(item).stableLongId()
    }

    private fun enterSelectionMode(item: MixedWaterfallItem): Boolean {
        if (isSelectionMode) return false
        isSelectionMode = true
        val key = MixedWaterfallIdentity.key(item)
        selectedItemKeys.add(key)
        notifyItemChangedByKey(key)
        onSelectionChanged(selectedItemKeys.size)
        return true
    }

    private fun toggleSelection(item: MixedWaterfallItem) {
        val key = MixedWaterfallIdentity.key(item)
        if (!selectedItemKeys.add(key)) {
            selectedItemKeys.remove(key)
        }
        notifyItemChangedByKey(key)
        isSelectionMode = selectedItemKeys.isNotEmpty()
        onSelectionChanged(selectedItemKeys.size)
    }

    private fun notifyItemChangedByKey(key: String) {
        val index = items.indexOfFirst { MixedWaterfallIdentity.key(it) == key }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    private fun maybeRequestRemotePreview(position: Int) {
        if (position == RecyclerView.NO_POSITION || position >= items.size) return
        val item = items[position] as? MixedWaterfallItem.FolderTile ?: return
        val folder = item.folder
        val itemKey = MixedWaterfallIdentity.key(item)
        val forceRefresh = itemKey in forcedPreviewRefreshPaths
        if (!forceRefresh && !needsRemotePreviewRefresh(folder)) return

        val key = RemotePreviewKey(remotePreviewGeneration(), itemKey)
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

        fun bind(
            photo: Photo,
            showFilenames: Boolean,
            isSelectionMode: Boolean,
            isSelected: Boolean
        ) {
            delegate.bind(
                photo = photo,
                isImmersiveMode = false,
                isSingleColumn = false,
                showFilenames = showFilenames,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected
            )
            (itemView as? MaterialCardView)?.apply {
                radius = resources.getDimension(R.dimen.waterfall_card_corner_radius)
                setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                elevation = 0f
                strokeColor = MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorPrimary
                )
                strokeWidth = if (isSelected) 4 else 0
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

        fun bind(folder: Folder, isSelected: Boolean) {
            binding.folderName.text = folder.name.trimEnd('/')
            binding.root.radius = itemView.resources.getDimension(R.dimen.waterfall_card_corner_radius)
            binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.root.strokeColor = MaterialColors.getColor(
                binding.root,
                if (isSelected) {
                    com.google.android.material.R.attr.colorPrimary
                } else {
                    com.google.android.material.R.attr.colorOutline
                }
            )
            binding.root.strokeWidth = if (isSelected) 4 else 1
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
