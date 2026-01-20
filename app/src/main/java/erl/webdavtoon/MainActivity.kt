package erl.webdavtoon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.transition.TransitionSet
import androidx.transition.ChangeBounds
import androidx.transition.ChangeTransform
import androidx.transition.Fade
import androidx.activity.result.contract.ActivityResultContracts
import android.view.animation.PathInterpolator
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import android.graphics.Rect
import erl.webdavtoon.databinding.ActivityMainBinding

/**
 * 主界面：瀑布流浏览
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var photoAdapter: PhotoAdapter
    private var folderPath: String = ""
    private var isWebDav: Boolean = false
    private var isRecursive: Boolean = false
    private var pinchZoomHelper: PinchZoomHelper? = null
    

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 设置已更改，刷新列表
            loadPhotos()
            // 更新列数
            val columns = settingsManager.getGridColumns()
            (binding.recyclerView.layoutManager as? StaggeredGridLayoutManager)?.spanCount = columns
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
        
        binding = ActivityMainBinding.inflate(layoutInflater)
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
        isRecursive = intent.getBooleanExtra("EXTRA_RECURSIVE", false)

        setupUI()
        
        if (isWebDav || hasStoragePermission()) {
            loadPhotos()
        }
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(folderPath.isNotEmpty())
        val displayTitle = when {
            folderPath.isEmpty() && !isWebDav -> "本地照片"
            folderPath.isEmpty() && isWebDav -> "WebDAV"
            else -> {
                val lastSegment = folderPath.trimEnd('/').split('/').lastOrNull { it.isNotEmpty() } ?: folderPath
                android.net.Uri.decode(lastSegment)
            }
        }
        supportActionBar?.title = if (isRecursive) "$displayTitle (全部)" else displayTitle
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val columns = settingsManager.getGridColumns()
        binding.recyclerView.layoutManager = StaggeredGridLayoutManager(columns, StaggeredGridLayoutManager.VERTICAL)
        binding.recyclerView.setItemViewCacheSize(30) // 增加缓存，减少白屏
        binding.recyclerView.setHasFixedSize(true) // 提高性能
        
        // 初始化新版 Google Photos 风格缩放 Helper
        pinchZoomHelper = PinchZoomHelper(binding.recyclerView, binding.zoomContainer) { newSpanCount ->
            settingsManager.setGridColumns(newSpanCount)
        }

        photoAdapter = PhotoAdapter { photos, position ->
            PhotoCache.setPhotos(photos)
            val intent = Intent(this, PhotoViewActivity::class.java).apply {
                putExtra("EXTRA_CURRENT_INDEX", position)
            }
            startActivity(intent)
        }
        binding.recyclerView.adapter = photoAdapter
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.pointerCount > 1) {
            pinchZoomHelper?.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun loadPhotos() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE

        android.util.Log.d("MainActivity", "Loading photos for folder: $folderPath, isWebDav: $isWebDav")

        lifecycleScope.launch {
            try {
                val repository: PhotoRepository = if (isWebDav) {
                    WebDavPhotoRepository(this@MainActivity, WebDavClient(settingsManager), settingsManager)
                } else {
                    LocalPhotoRepository(this@MainActivity)
                }

                val photos = repository.getPhotos(folderPath, isRecursive)
                android.util.Log.d("MainActivity", "Loaded ${photos.size} photos")
                
                if (photos.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                } else {
                    photoAdapter.setPhotos(photos)
                    
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to load photos", e)
                binding.emptyView.text = "Error: ${e.message}"
                binding.emptyView.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    
}

/**
 * 图片缓存类，用于跨 Activity 传递大量数据
 */
object PhotoCache {
    private var photos: List<Photo> = emptyList()

    fun setPhotos(list: List<Photo>) {
        photos = list
    }

    fun getPhotos(): List<Photo> = photos

    fun clear() {
        photos = emptyList()
    }
}
