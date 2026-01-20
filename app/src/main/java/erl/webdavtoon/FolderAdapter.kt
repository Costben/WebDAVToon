package erl.webdavtoon

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import erl.webdavtoon.databinding.ItemFolderBinding

/**
 * 文件夹列表适配器
 */
class FolderAdapter(
    private val onFolderClick: (Folder) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    private var folders: List<Folder> = emptyList()

    fun setFolders(newFolders: List<Folder>) {
        folders = newFolders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.bind(folder)
        holder.itemView.setOnClickListener { onFolderClick(folder) }
    }

    override fun getItemCount(): Int = folders.size

    class FolderViewHolder(private val binding: ItemFolderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(folder: Folder) {
            binding.folderName.text = folder.name
            binding.folderInfo.text = if (folder.isLocal) {
                "${folder.photoCount} photos • Local"
            } else {
                "WebDAV Folder"
            }

            val previewImageViews = listOf(
                binding.preview1,
                binding.preview2,
                binding.preview3,
                binding.preview4
            )

            // 清除旧图片并设置可见性
            previewImageViews.forEach { iv ->
                iv.setImageDrawable(null)
                iv.visibility = android.view.View.INVISIBLE
            }

            if (folder.previewUris.isEmpty()) {
                binding.folderIcon.visibility = android.view.View.VISIBLE
            } else {
                binding.folderIcon.visibility = android.view.View.GONE
                folder.previewUris.take(4).forEachIndexed { index, uri ->
                    val iv = previewImageViews[index]
                    iv.visibility = android.view.View.VISIBLE
                    com.bumptech.glide.Glide.with(iv.context)
                        .load(uri)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .centerCrop()
                        .into(iv)
                }
            }
        }
    }
}
