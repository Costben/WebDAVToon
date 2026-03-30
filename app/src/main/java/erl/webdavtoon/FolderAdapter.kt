package erl.webdavtoon

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import erl.webdavtoon.databinding.ItemFolderBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderAdapter(
    private val onFolderClick: (Folder) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    private var folders: MutableList<Folder> = mutableListOf()
    private val selectedFolders = mutableSetOf<Folder>()
    var isSelectionMode = false
        private set

    fun setFolders(newFolders: List<Folder>) {
        folders = newFolders.toMutableList()
        notifyDataSetChanged()
    }

    fun enterSelectionMode(folder: Folder) {
        isSelectionMode = true
        selectedFolders.add(folder)
        notifyDataSetChanged()
        onSelectionChanged(selectedFolders.size)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedFolders.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedFolders(): List<Folder> = selectedFolders.toList()

    private fun toggleSelection(folder: Folder) {
        if (selectedFolders.contains(folder)) {
            selectedFolders.remove(folder)
        } else {
            selectedFolders.add(folder)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedFolders.size)

        if (selectedFolders.isEmpty()) {
            exitSelectionMode()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.bind(folder, selectedFolders.contains(folder))

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < folders.size) {
                if (isSelectionMode) {
                    toggleSelection(folders[pos])
                } else {
                    onFolderClick(folders[pos])
                }
            }
        }

        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < folders.size) {
                if (!isSelectionMode) {
                    enterSelectionMode(folders[pos])
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    override fun getItemCount(): Int = folders.size

    inner class FolderViewHolder(private val binding: ItemFolderBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(folder: Folder, isSelected: Boolean) {
            binding.folderName.text = folder.name.trimEnd('/')
            binding.folderInfo.text = when {
                folder.isLocal -> binding.root.context.getString(R.string.photos_local_suffix, folder.photoCount)
                else -> binding.root.context.getString(R.string.webdav_folder)
            }

            // 更新选中状态 UI
            binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            (binding.root as? com.google.android.material.card.MaterialCardView)?.apply {
                strokeWidth = if (isSelected) 4 else 0
            }

            val previewImageViews = listOf(binding.preview1, binding.preview2, binding.preview3, binding.preview4)
            previewImageViews.forEach { imageView ->
                imageView.setImageDrawable(null)
                imageView.visibility = View.INVISIBLE
            }

            if (folder.previewUris.isNotEmpty()) {
                showPreviews(folder.previewUris, previewImageViews)
                return
            }

            if (!folder.isLocal) {
                binding.folderIcon.visibility = View.VISIBLE

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repo = WebDAVToonApplication.rustRepository
                        if (repo != null) {
                            val inspection = repo.inspectFolder(folder.path)
                            val previewUris = inspection.previewUris.map { Uri.parse(it) }

                            if (previewUris.isNotEmpty() || !inspection.hasSubFolders) {
                                withContext(Dispatchers.Main) {
                                    if (binding.folderName.text == folder.name) {
                                        if (previewUris.isNotEmpty()) {
                                            showPreviews(previewUris, previewImageViews)
                                        }
                                        val pos = bindingAdapterPosition
                                        if (pos != RecyclerView.NO_POSITION && pos < folders.size) {
                                            val current = folders[pos]
                                            if (current.path == folder.path) {
                                                folders[pos] = current.copy(
                                                    hasSubFolders = inspection.hasSubFolders,
                                                    previewUris = previewUris
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } else {
                binding.folderIcon.visibility = View.VISIBLE
            }
        }

        private fun showPreviews(uris: List<Uri>, views: List<ImageView>) {
            binding.folderIcon.visibility = View.GONE
            uris.take(4).forEachIndexed { index, uri ->
                val imageView = views[index]
                imageView.visibility = View.VISIBLE

                val uriString = uri.toString()
                if (uriString.startsWith("http", ignoreCase = true)) {
                    WebDavImageLoader.loadWebDavImage(
                        imageView.context,
                        uri,
                        imageView,
                        null,
                        true
                    )
                } else {
                    WebDavImageLoader.loadLocalImage(
                        imageView.context,
                        uri,
                        imageView,
                        null,
                        true
                    )
                }
            }
        }
    }
}
