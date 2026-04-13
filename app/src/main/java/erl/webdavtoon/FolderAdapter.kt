package erl.webdavtoon

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import erl.webdavtoon.databinding.ItemFolderBinding

class FolderAdapter(
    private val onFolderClick: (Folder) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val onRemotePreviewNeeded: ((Folder) -> Unit)? = null
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    private var folders: List<Folder> = emptyList()
    private val selectedFolderPaths = linkedSetOf<String>()
    private val requestedPreviewPaths = hashSetOf<String>()
    var isSelectionMode = false
        private set

    init {
        setHasStableIds(true)
    }

    fun setFolders(newFolders: List<Folder>) {
        val previousByPath = folders.associateBy { it.path }
        val mergedFolders = newFolders.map { incoming ->
            val previous = previousByPath[incoming.path]
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

        if (folders == mergedFolders) {
            folders = mergedFolders
            selectedFolderPaths.retainAll(folders.map { it.path }.toSet())
            requestedPreviewPaths.retainAll(folders.map { it.path }.toSet())
            requestedPreviewPaths.removeAll(previewlessPaths)
            if (folders.isNotEmpty()) {
                notifyItemRangeChanged(0, folders.size)
            }
            onSelectionChanged(selectedFolderPaths.size)
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
        selectedFolderPaths.retainAll(folders.map { it.path }.toSet())
        requestedPreviewPaths.retainAll(folders.map { it.path }.toSet())
        requestedPreviewPaths.removeAll(previewlessPaths)
        if (selectedFolderPaths.isEmpty() && isSelectionMode) {
            isSelectionMode = false
        }
        diffResult.dispatchUpdatesTo(this)
        onSelectionChanged(selectedFolderPaths.size)
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

        val current = folders[index]
        val updated = current.copy(
            previewUris = if (previewUris.isNotEmpty()) previewUris else current.previewUris,
            hasSubFolders = hasSubFolders
        )
        if (updated == current) return

        folders = folders.toMutableList().also { it[index] = updated }
        notifyItemChanged(index)
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
        if (folder.isLocal || folder.previewUris.isNotEmpty()) return
        if (requestedPreviewPaths.add(folder.path)) {
            onRemotePreviewNeeded?.invoke(folder)
        }
    }

    inner class FolderViewHolder(private val binding: ItemFolderBinding) : RecyclerView.ViewHolder(binding.root) {

        private val previewImageViews = listOf(binding.preview1, binding.preview2, binding.preview3, binding.preview4)

        fun bind(folder: Folder, isSelected: Boolean) {
            binding.folderName.text = folder.name.trimEnd('/')
            binding.folderInfo.text = when {
                folder.isLocal -> binding.root.context.getString(R.string.photos_local_suffix, folder.photoCount)
                else -> binding.root.context.getString(R.string.webdav_folder)
            }

            binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            (binding.root as? com.google.android.material.card.MaterialCardView)?.strokeWidth = if (isSelected) 4 else 0

            clearPreviews()

            if (folder.previewUris.isNotEmpty()) {
                showPreviews(folder.previewUris)
            } else {
                binding.folderIcon.visibility = View.VISIBLE
            }
        }

        fun clear() {
            clearPreviews()
            binding.folderIcon.visibility = View.VISIBLE
        }

        private fun clearPreviews() {
            previewImageViews.forEach { imageView ->
                WebDavImageLoader.clear(imageView)
                imageView.setImageDrawable(null)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.visibility = View.INVISIBLE
            }
        }

        private fun showPreviews(uris: List<Uri>) {
            binding.folderIcon.visibility = View.GONE
            uris.take(previewImageViews.size).forEachIndexed { index, uri ->
                val imageView = previewImageViews[index]
                imageView.visibility = View.VISIBLE

                val uriString = uri.toString()
                val mediaType = detectMediaTypeByUri(uri) ?: MediaType.IMAGE
                val isRemote = uriString.startsWith("http", ignoreCase = true)
                android.util.Log.d(
                    "FolderAdapter",
                    "preview bind uri=$uriString mediaType=$mediaType isRemote=$isRemote"
                )

                if (mediaType == MediaType.VIDEO) {
                    if (isRemote) {
                        showRemoteVideoPlaceholder(imageView)
                        WebDavImageLoader.loadWebDavVideoThumbnail(
                            imageView.context,
                            uri,
                            imageView,
                            progressBar = null,
                            isFolderPreview = true
                        )
                    } else {
                        WebDavImageLoader.loadLocalVideoThumbnail(
                            imageView.context,
                            uri,
                            imageView,
                            progressBar = null,
                            isFolderPreview = true
                        )
                    }
                } else {
                    if (isRemote) {
                        WebDavImageLoader.loadWebDavImage(
                            imageView.context,
                            uri,
                            imageView,
                            progressBar = null,
                            limitSize = true,
                            isWaterfall = false,
                            isFolderPreview = true
                        )
                    } else {
                        WebDavImageLoader.loadLocalImage(
                            imageView.context,
                            uri,
                            imageView,
                            progressBar = null,
                            limitSize = true,
                            isWaterfall = false,
                            isFolderPreview = true
                        )
                    }
                }
            }
        }

        private fun showRemoteVideoPlaceholder(imageView: ImageView) {
            imageView.scaleType = ImageView.ScaleType.CENTER
            imageView.setImageResource(R.drawable.ic_ior_play)
        }
    }
}
