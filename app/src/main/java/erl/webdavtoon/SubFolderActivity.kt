package erl.webdavtoon

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import erl.webdavtoon.databinding.ActivityFolderViewBinding
import kotlinx.coroutines.launch
import java.io.File

/**
 * 下一级文件夹 Activity
 */
class SubFolderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderViewBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var adapter: FolderAdapter
    private var folderPath: String = ""
    private var isWebDav: Boolean = false

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadFolders()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用沉浸式
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityFolderViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        
        // 处理系统栏间距
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            binding.recyclerView.setPadding(
                binding.recyclerView.paddingLeft,
                binding.recyclerView.paddingTop,
                binding.recyclerView.paddingRight,
                systemBars.bottom
            )
            insets
        }
        folderPath = intent.getStringExtra("EXTRA_FOLDER_PATH") ?: ""
        isWebDav = intent.getBooleanExtra("EXTRA_IS_WEBDAV", false)

        setupUI()
        loadFolders()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        val displayTitle = when {
            folderPath.isEmpty() && !isWebDav -> "本地照片"
            folderPath.isEmpty() && isWebDav -> "WebDAV"
            else -> {
                val lastSegment = folderPath.trimEnd('/').split('/').lastOrNull { it.isNotEmpty() } ?: folderPath
                android.net.Uri.decode(lastSegment)
            }
        }
        supportActionBar?.title = displayTitle
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter = FolderAdapter { folder ->
            if (folder.hasSubFolders) {
                // 如果还有下一级文件夹，导航到 SubFolderActivity
                val intent = Intent(this, SubFolderActivity::class.java).apply {
                    putExtra("EXTRA_FOLDER_PATH", folder.path)
                    putExtra("EXTRA_IS_WEBDAV", !folder.isLocal)
                }
                startActivity(intent)
            } else {
                // 如果没有下一级文件夹，直接进入瀑布流预览
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("EXTRA_FOLDER_PATH", folder.path)
                    putExtra("EXTRA_IS_WEBDAV", !folder.isLocal)
                }
                startActivity(intent)
            }
        }
        
        // 长按进入瀑布流预览
        // 或者可以添加一个按钮，这里为了演示，先改成点击进入 SubFolder，如果需要进入瀑布流，可以考虑在 Item 增加判断
        // 用户要求：首页 -> 下一级 -> 瀑布流
        // 所以在 SubFolderActivity 中，我们需要决定是去下一个 SubFolder 还是去 Waterfall
        
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadFolders(forceRefresh = true)
        }

        binding.settingsFab.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", folderPath)
                putExtra("EXTRA_IS_WEBDAV", isWebDav)
                putExtra("EXTRA_RECURSIVE", true)
            }
            startActivity(intent)
        }
        // 更换 FAB 图标为 "播放/查看" 类似的图标表示进入预览
        binding.settingsFab.setImageResource(android.R.drawable.ic_menu_gallery)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadFolders(forceRefresh: Boolean = false) {
        if (!binding.swipeRefreshLayout.isRefreshing) {
            binding.progressBar.visibility = View.VISIBLE
        }
        
        lifecycleScope.launch {
            val allFolders = mutableListOf<Folder>()
            
            try {
                val repository: PhotoRepository = if (isWebDav) {
                    WebDavPhotoRepository(this@SubFolderActivity, WebDavClient(settingsManager), settingsManager)
                } else {
                    LocalPhotoRepository(this@SubFolderActivity)
                }
                
                val folders = repository.getFolders(folderPath, forceRefresh)
                android.util.Log.d("SubFolderActivity", "Loaded ${folders.size} folders for $folderPath")
                
                if (folders.isEmpty() && !forceRefresh) {
                    // 如果没有下一级文件夹且不是强制刷新（可能是刚点进来），直接进入瀑布流预览
                    val intent = Intent(this@SubFolderActivity, MainActivity::class.java).apply {
                        putExtra("EXTRA_FOLDER_PATH", folderPath)
                        putExtra("EXTRA_IS_WEBDAV", isWebDav)
                    }
                    startActivity(intent)
                    finish()
                    return@launch
                }
                
                allFolders.addAll(folders)
            } catch (e: Exception) {
                android.util.Log.e("SubFolderActivity", "Folders load failed", e)
            }

            adapter.setFolders(allFolders)
            binding.progressBar.visibility = View.GONE
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }
}
