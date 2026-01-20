package erl.webdavtoon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import erl.webdavtoon.databinding.ActivityFolderViewBinding
import kotlinx.coroutines.launch

/**
 * 文件夹浏览 Activity (启动页)
 */
class FolderViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderViewBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var adapter: FolderAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadFolders()
        } else {
            Toast.makeText(this, "需要存储权限以查看本地相册", Toast.LENGTH_LONG).show()
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 设置已更改，刷新文件夹列表
            loadFolders()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用沉浸式，让导航栏自然悬浮在app之上
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 设置导航栏背景透明
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // 设置导航栏内容浅色/深色模式
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        
        binding = ActivityFolderViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        LogManager.initialize(this)

        // 处理系统栏间距
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            binding.recyclerView.setPadding(
                binding.recyclerView.paddingLeft,
                binding.recyclerView.paddingTop,
                binding.recyclerView.paddingRight,
                systemBars.bottom + 16 // 增加额外的padding，确保不被导航栏遮挡
            )
            insets
        }

        setupUI()
        checkPermissionsAndLoad()
    }

    override fun onResume() {
        super.onResume()
        // Removed redundant loadFolders() call here, 
        // because onCreate() already calls checkPermissionsAndLoad()
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermissionsAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            hasStoragePermission() -> {
                loadFolders()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)

        adapter = FolderAdapter { folder ->
            if (folder.path == "virtual://local_all" || folder.hasSubFolders) {
                // 如果是虚拟文件夹或者有子文件夹，进入子文件夹浏览界面
                val intent = if (folder.path == "virtual://local_all") {
                    Intent(this, SubFolderActivity::class.java).apply {
                        putExtra("EXTRA_FOLDER_PATH", "")
                        putExtra("EXTRA_IS_WEBDAV", false)
                    }
                } else {
                    Intent(this, SubFolderActivity::class.java).apply {
                        putExtra("EXTRA_FOLDER_PATH", folder.path)
                        putExtra("EXTRA_IS_WEBDAV", !folder.isLocal)
                    }
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
        
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadFolders(forceRefresh = true)
        }

        binding.settingsFab.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", "")
                putExtra("EXTRA_IS_WEBDAV", settingsManager.isWebDavEnabled())
                putExtra("EXTRA_RECURSIVE", true)
            }
            startActivity(intent)
        }
        binding.settingsFab.setImageResource(android.R.drawable.ic_menu_gallery)
        binding.settingsFab.visibility = View.VISIBLE
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
            val isWebDavEnabled = settingsManager.isWebDavEnabled()
            
            // 1. 首先尝试加载 WebDAV 文件夹，并立即更新 UI
            if (isWebDavEnabled) {
                try {
                    val webDavRepo = WebDavPhotoRepository(this@FolderViewActivity, WebDavClient(settingsManager), settingsManager)
                    val webDavFolders = webDavRepo.getFolders("", forceRefresh)
                    android.util.Log.d("FolderViewActivity", "Loaded ${webDavFolders.size} WebDAV folders")
                    allFolders.addAll(webDavFolders)
                    
                    // 立即显示 WebDAV 文件夹
                    adapter.setFolders(allFolders.toList())
                } catch (e: Exception) {
                    android.util.Log.e("FolderViewActivity", "WebDAV folders load failed: ${e.message}", e)
                }
            }

            // 2. 然后在后台加载本地照片信息
            try {
                val localRepo = LocalPhotoRepository(this@FolderViewActivity)
                if (isWebDavEnabled) {
                    // 如果启用了 WebDAV，将本地照片收纳到一个虚拟文件夹
                    // 异步获取本地文件夹信息以更新计数和缩略图
                    val localFolders = localRepo.getFolders("", forceRefresh)
                    if (localFolders.isNotEmpty()) {
                        val totalPhotos = localFolders.sumOf { it.photoCount }
                        val allPreviews = localFolders.flatMap { it.previewUris }.take(4)
                        val localVirtualFolder = Folder(
                            path = "virtual://local_all",
                            name = "本地照片",
                            isLocal = true,
                            photoCount = totalPhotos,
                            previewUris = allPreviews,
                            hasSubFolders = true
                        )
                        allFolders.add(localVirtualFolder)
                    }
                } else {
                    // 未启用 WebDAV 时，显示所有本地文件夹
                    val localFolders = localRepo.getFolders("", forceRefresh)
                    allFolders.addAll(localFolders)
                }
            } catch (e: Exception) {
                android.util.Log.e("FolderViewActivity", "Local folders load failed: ${e.message}", e)
            }

            // 3. 最终更新 UI
            android.util.Log.d("FolderViewActivity", "Final folders to display: ${allFolders.size}")
            adapter.setFolders(allFolders.toList())
            binding.progressBar.visibility = View.GONE
            binding.swipeRefreshLayout.isRefreshing = false
            
            // 检查 RecyclerView 状态
            binding.recyclerView.post {
                android.util.Log.d("FolderViewActivity", "RecyclerView has ${binding.recyclerView.childCount} children visible")
                android.util.Log.d("FolderViewActivity", "Adapter item count: ${adapter.itemCount}")
            }
        }
    }
}
