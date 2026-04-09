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
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    private var folders: List<Folder> = emptyList()
    private val selectedFolderPaths = linkedSetOf<String>()
    var isSelectionMode = false
        private set

    init {
        setHasStableIds(true)
    }

    fun setFolders(newFolders: List<Folder>) {
        if (folders == newFolders) return

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = folders.size

            override fun getNewListSize(): Int = newFolders.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return folders[oldItemPosition].path == newFolders[newItemPosition].path
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return folders[oldItemPosition] == newFolders[newItemPosition]
            }
        })

        folders = newFolders
        selectedFolderPaths.retainAll(folders.map { it.path }.toSet())
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
    }

    override fun onViewRecycled(holder: FolderViewHolder) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = folders.size

    override fun getItemId(position: Int): Long = folders[position].path.hashCode().toLong()

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
                imageView.visibility = View.INVISIBLE
            }
        }

        private fun showPreviews(uris: List<Uri>) {
            binding.folderIcon.visibility = View.GONE
            uris.take(previewImageViews.size).forEachIndexed { index, uri ->
                val imageView = previewImageViews[index]
                imageView.visibility = View.VISIBLE

                val uriString = uri.toString()
                if (uriString.startsWith("http", ignoreCase = true)) {
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
}
