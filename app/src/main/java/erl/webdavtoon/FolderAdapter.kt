package erl.webdavtoon

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import erl.webdavtoon.databinding.ItemFolderBinding

class FolderAdapter(
    private val onFolderClick: (Folder) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val onRemotePreviewNeeded: ((Folder, Boolean) -> Unit)? = null,
    private val remotePreviewGeneration: () -> String = { "" }
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    private data class RemotePreviewKey(
        val generation: String,
        val path: String
    )

    private var folders: List<Folder> = emptyList()
    private val selectedFolderPaths = linkedSetOf<String>()
    private val requestedPreviewKeys = hashSetOf<RemotePreviewKey>()
    private val remotePreviewCache = mutableMapOf<RemotePreviewKey, List<Uri>>()
    private val forcedPreviewRefreshPaths = hashSetOf<String>()
    private var activePreviewGeneration: String? = null
    var isSelectionMode = false
        private set

    init {
        setHasStableIds(true)
    }

    fun setFolders(newFolders: List<Folder>) {
        val generation = remotePreviewGeneration()
        val generationChanged = activePreviewGeneration != null && activePreviewGeneration != generation
        val previousByPath = folders.associateBy { it.path }
        val mergedFolders = newFolders.map { incoming ->
            val key = RemotePreviewKey(generation, incoming.path)
            if (!incoming.isLocal && incoming.previewUris.isNotEmpty()) {
                remotePreviewCache[key] = incoming.previewUris
                return@map incoming
            }
            remotePreviewCache[key]?.let { cachedPreviews ->
                return@map incoming.copy(previewUris = cachedPreviews)
            }
            val previous = if (generationChanged) null else previousByPath[incoming.path]
            if (previous != null && incoming.previewUris.isEmpty() && previous.previewUris.isNotEmpty()) {
                incoming.copy(previewUris = previous.previewUris)
            } else {
                incoming
            }
        }

        val previewlessPaths = mergedFolders
            .asSequence()
            .filter { !it.isLocal && it.previewUris.isEmpty() }
            .map { it.path }
            .toSet()
        val currentPaths = mergedFolders.map { it.path }.toSet()
        val previousSelectionCount = selectedFolderPaths.size
        val previousSelectionMode = isSelectionMode

        if (folders == mergedFolders) {
            folders = mergedFolders
            activePreviewGeneration = generation
            selectedFolderPaths.retainAll(currentPaths)
            requestedPreviewKeys.retainAll { it.path in currentPaths }
            remotePreviewCache.keys.retainAll { it.path in currentPaths }
            forcedPreviewRefreshPaths.retainAll(currentPaths)
            requestedPreviewKeys.removeAll { it.generation == generation && it.path in previewlessPaths }
            if (folders.isNotEmpty()) {
                notifyItemRangeChanged(0, folders.size)
            }
            notifySelectionChangedIfNeeded(previousSelectionCount, previousSelectionMode)
            return
        }

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = folders.size

            override fun getNewListSize(): Int = mergedFolders.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return folders[oldItemPosition].path == mergedFolders[newItemPosition].path
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return folders[oldItemPosition] == mergedFolders[newItemPosition]
            }
        })

        folders = mergedFolders
        activePreviewGeneration = generation
        selectedFolderPaths.retainAll(currentPaths)
        requestedPreviewKeys.retainAll { it.path in currentPaths }
        remotePreviewCache.keys.retainAll { it.path in currentPaths }
        forcedPreviewRefreshPaths.retainAll(currentPaths)
        requestedPreviewKeys.removeAll { it.generation == generation && it.path in previewlessPaths }
        if (selectedFolderPaths.isEmpty() && isSelectionMode) {
            isSelectionMode = false
        }
        diffResult.dispatchUpdatesTo(this)
        notifySelectionChangedIfNeeded(previousSelectionCount, previousSelectionMode)
    }

    private fun notifySelectionChangedIfNeeded(previousCount: Int, previousMode: Boolean) {
        if (previousCount != selectedFolderPaths.size || previousMode != isSelectionMode) {
            onSelectionChanged(selectedFolderPaths.size)
        }
    }

    fun enterSelectionMode(folder: Folder) {
        if (!isSelectionMode) {
            isSelectionMode = true
        }
        if (selectedFolderPaths.add(folder.path)) {
            notifyFolderChanged(folder.path)
        }
        onSelectionChanged(selectedFolderPaths.size)
    }

    fun exitSelectionMode() {
        if (!isSelectionMode && selectedFolderPaths.isEmpty()) return

        val changedPaths = selectedFolderPaths.toList()
        isSelectionMode = false
        selectedFolderPaths.clear()
        changedPaths.forEach(::notifyFolderChanged)
        onSelectionChanged(0)
    }

    fun getSelectedFolders(): List<Folder> = folders.filter { selectedFolderPaths.contains(it.path) }

    private fun toggleSelection(folder: Folder) {
        if (selectedFolderPaths.contains(folder.path)) {
            selectedFolderPaths.remove(folder.path)
        } else {
            selectedFolderPaths.add(folder.path)
        }

        notifyFolderChanged(folder.path)

        if (selectedFolderPaths.isEmpty()) {
            isSelectionMode = false
            onSelectionChanged(0)
        } else {
            isSelectionMode = true
            onSelectionChanged(selectedFolderPaths.size)
        }
    }

    private fun notifyFolderChanged(path: String) {
        val index = folders.indexOfFirst { it.path == path }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    fun updateFolderPreview(path: String, previewUris: List<Uri>, hasSubFolders: Boolean) {
        val index = folders.indexOfFirst { it.path == path }
        if (index == -1) return

        val generation = remotePreviewGeneration()
        val key = RemotePreviewKey(generation, path)
        forcedPreviewRefreshPaths.remove(path)
        remotePreviewCache[key] = previewUris

        val current = folders[index]
        val updated = current.copy(
            previewUris = previewUris,
            hasSubFolders = current.hasSubFolders || hasSubFolders
        )
        if (updated == current) return

        folders = folders.toMutableList().also { it[index] = updated }
        notifyItemChanged(index)
    }

    fun refreshVisibleRemotePreviews() {
        val generation = remotePreviewGeneration()
        val remotePaths = folders.asSequence()
            .filter { !it.isLocal && !it.path.startsWith("virtual://") }
            .map { it.path }
            .toSet()
        if (remotePaths.isEmpty()) return

        forcedPreviewRefreshPaths.clear()
        forcedPreviewRefreshPaths.addAll(remotePaths)
        requestedPreviewKeys.removeAll { it.generation == generation && it.path in remotePaths }
        notifyItemRangeChanged(0, folders.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding).apply {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos >= folders.size) return@setOnClickListener
                val folder = folders[pos]
                if (isSelectionMode) {
                    toggleSelection(folder)
                } else {
                    onFolderClick(folder)
                }
            }

            itemView.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos >= folders.size) {
                    return@setOnLongClickListener false
                }

                if (!isSelectionMode) {
                    enterSelectionMode(folders[pos])
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.bind(folder, selectedFolderPaths.contains(folder.path))
        maybeRequestRemotePreview(position)
    }

    override fun onViewAttachedToWindow(holder: FolderViewHolder) {
        super.onViewAttachedToWindow(holder)
        maybeRequestRemotePreview(holder.bindingAdapterPosition)
    }

    override fun onViewRecycled(holder: FolderViewHolder) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = folders.size

    override fun getItemId(position: Int): Long = folders[position].path.hashCode().toLong()

    private fun maybeRequestRemotePreview(position: Int) {
        if (position == RecyclerView.NO_POSITION || position >= folders.size) return
        val folder = folders[position]
        val forceRefresh = folder.path in forcedPreviewRefreshPaths
        if (!forceRefresh && !needsRemotePreviewRefresh(folder)) return

        val key = RemotePreviewKey(remotePreviewGeneration(), folder.path)
        if (!forceRefresh && remotePreviewCache.containsKey(key)) return
        if (requestedPreviewKeys.add(key)) {
            onRemotePreviewNeeded?.invoke(folder, forceRefresh)
        }
    }

    private fun needsRemotePreviewRefresh(folder: Folder): Boolean {
        return !folder.isLocal && !folder.path.startsWith("virtual://") && folder.previewUris.isEmpty()
    }

    inner class FolderViewHolder(private val binding: ItemFolderBinding) : RecyclerView.ViewHolder(binding.root) {

        private val previewBinder = FolderPreviewBinder(
            previewImageViews = listOf(binding.preview1, binding.preview2, binding.preview3, binding.preview4),
            folderIcon = binding.folderIcon,
            logTag = "FolderAdapter"
        )

        fun bind(folder: Folder, isSelected: Boolean) {
            binding.folderName.text = folder.name.trimEnd('/')
            binding.folderInfo.text = when {
                folder.isLocal -> binding.root.context.getString(R.string.photos_local_suffix, folder.photoCount)
                else -> binding.root.context.getString(R.string.webdav_folder)
            }

            binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            (binding.root as? com.google.android.material.card.MaterialCardView)?.strokeWidth = if (isSelected) 4 else 1
            previewBinder.bind(folder)
        }

        fun clear() {
            previewBinder.clear()
        }
    }
}
