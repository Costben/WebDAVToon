package erl.webdavtoon

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import erl.webdavtoon.databinding.ActivityPhotoViewBinding
import kotlinx.coroutines.launch

/**
 * Webtoon 浏览模式 Activity
 */
class PhotoViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewBinding
    private lateinit var adapter: PhotoDetailAdapter
    private var webtoonAdapter: WebtoonAdapter? = null
    private var photos: List<Photo> = emptyList()
    private var currentIndex = 0
    private var isWebtoonFolder = false
    private var isCardMode = false // true: 卡片模式, false: webtoon模式
    private var isImmersiveMode = false // 沉浸模式状态
    private var pagerSnapHelper: androidx.recyclerview.widget.PagerSnapHelper? = null // 用于卡片模式的吸附效果

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用沉浸式，让导航栏自然悬浮在app之上
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 设置导航栏背景透明
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // 设置导航栏内容浅色/深色模式
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        
        // 允许内容延伸到刘海区域 (Short Edges)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }
        
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置系统栏间距监听器
        setupWindowInsetsListener()

        setupUI()
        loadData()
        setupBottomBar()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter = PhotoDetailAdapter {
            toggleImmersiveMode(!isImmersiveMode)
        }
        webtoonAdapter = WebtoonAdapter(object : WebtoonAdapter.OnWebtoonTapListener {
            override fun onDoubleTap() {
                toggleImmersiveMode(!isImmersiveMode)
            }
        })
        
        // 初始设置为Webtoon模式
        setupWebtoonMode()
        
        // 默认开启沉浸模式
        toggleImmersiveMode(true)
        
        // 监听滑动事件，更新当前图片索引和标题
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateCurrentPosition()
                }
            }
        })
        
        // 移除双击触发沉浸模式，改为使用底栏图标按钮控制
    }

    private fun setupCardMode() {
        // 显示沉浸模式按钮
        binding.immersiveButton.visibility = View.VISIBLE
        
        // 设置为水平布局，支持左右滑动，一页显示一张图片
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
        // 确保图片居中显示，没有左右间距
        // 保留系统栏的内边距处理
        val currentTopPadding = binding.recyclerView.paddingTop
        val currentBottomPadding = binding.recyclerView.paddingBottom
        binding.recyclerView.setPadding(0, currentTopPadding, 0, currentBottomPadding)
        
        // 添加PagerSnapHelper，实现一次翻一页的效果
        pagerSnapHelper = androidx.recyclerview.widget.PagerSnapHelper()
        pagerSnapHelper?.attachToRecyclerView(binding.recyclerView)
    }

    private fun setupWebtoonMode() {
        // 隐藏沉浸模式按钮，并退出沉浸模式
        binding.immersiveButton.visibility = View.GONE
        if (isImmersiveMode) {
            toggleImmersiveMode(false)
        }
        
        // 移除PagerSnapHelper的吸附效果
        pagerSnapHelper?.attachToRecyclerView(null)
        pagerSnapHelper = null
        
        // 设置为垂直布局，支持上下滑动，连续滚动
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = webtoonAdapter
        binding.recyclerView.setHasFixedSize(true)
        // 保留系统栏的内边距处理
        val currentTopPadding = binding.recyclerView.paddingTop
        val currentBottomPadding = binding.recyclerView.paddingBottom
        binding.recyclerView.setPadding(0, currentTopPadding, 0, currentBottomPadding)
    }

    private fun setupBottomBar() {
        // 设置现代化MD3风格图标
        binding.detailButton.setImageResource(R.drawable.ic_info_md3)
        binding.favoriteButton.setImageResource(R.drawable.ic_star_md3)
        binding.downloadButton.setImageResource(R.drawable.ic_download_md3)
        binding.modeSwitchButton.setImageResource(R.drawable.ic_mode_switch_md3)
        binding.deleteButton.setImageResource(R.drawable.ic_delete_md3)
        binding.immersiveButton.setImageResource(R.drawable.ic_fullscreen_md3)
        
        // 文件详情按钮点击事件
        binding.detailButton.setOnClickListener {
            showPhotoDetails()
        }
        
        // 收藏按钮点击事件
        binding.favoriteButton.setOnClickListener {
            toggleFavorite()
        }
        
        // 下载按钮点击事件
        binding.downloadButton.setOnClickListener {
            downloadPhoto()
        }
        
        // 模式切换按钮点击事件
        binding.modeSwitchButton.setOnClickListener {
            toggleViewMode()
        }
        
        // 沉浸模式按钮点击事件
        binding.immersiveButton.setOnClickListener {
            toggleImmersiveMode(!isImmersiveMode)
        }
        
        // 根据当前模式设置初始图标
        updateModeSwitchIcon()
        
        // 删除按钮点击事件
        binding.deleteButton.setOnClickListener {
            deletePhoto()
        }
        
        // 初始隐藏下载按钮，仅在webtoon文件夹显示
        binding.downloadButton.visibility = if (isWebtoonFolder) View.VISIBLE else View.GONE
    }
    
    private fun toggleViewMode() {
        // 先保存当前滚动位置
        val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
        val currentScrollPosition = layoutManager?.findFirstVisibleItemPosition() ?: currentIndex
        
        isCardMode = !isCardMode
        
        if (isCardMode) {
            // 切换到卡片模式
            setupCardMode()
            // 更新卡片模式适配器数据
            adapter.setPhotos(photos)
            binding.recyclerView.adapter = adapter
        } else {
            // 切换到webtoon模式
            setupWebtoonMode()
            // 更新webtoon模式适配器数据
            webtoonAdapter?.setPhotos(photos)
            binding.recyclerView.adapter = webtoonAdapter
        }
        
        // 更新模式切换按钮图标
        updateModeSwitchIcon()
        
        // 确保RecyclerView完全准备好后再滚动
        binding.recyclerView.post {
            binding.recyclerView.scrollToPosition(currentScrollPosition)
        }
    }
    
    /**
     * 设置系统栏间距监听器
     */
    private fun setupWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 应用栏设置顶部内边距，使其在状态栏下方显示
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            // 底栏设置底部内边距，使其在导航栏上方显示
            binding.bottomAppBar.setPadding(0, 0, 0, systemBars.bottom)
            // 内容区域设置相应内边距
            binding.recyclerView.setPadding(
                binding.recyclerView.paddingLeft,
                binding.recyclerView.paddingTop,
                binding.recyclerView.paddingRight,
                systemBars.bottom
            )
            insets
        }
    }
    
    private fun toggleImmersiveMode(enabled: Boolean) {
        isImmersiveMode = enabled
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        
        if (enabled) {
            // 进入沉浸模式：隐藏状态栏、导航栏和标题栏
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            binding.appBarLayout.visibility = View.GONE
            binding.bottomAppBar.visibility = View.GONE
            
            // 确保导航栏和状态栏背景透明，让应用使用整个屏幕空间
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            
            // 确保应用可以绘制到状态栏和导航栏区域
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            
            // 完全忽略系统栏的存在
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // 移除OnApplyWindowInsetsListener，避免系统栏影响布局
            ViewCompat.setOnApplyWindowInsetsListener(binding.root, null)
            ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView, null)
            ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, null)
            ViewCompat.setOnApplyWindowInsetsListener(binding.bottomAppBar, null)
            
            // 移除所有内边距，让内容占据整个屏幕
            binding.recyclerView.setPadding(0, 0, 0, 0)
            binding.appBarLayout.setPadding(0, 0, 0, 0)
            binding.bottomAppBar.setPadding(0, 0, 0, 0)
            binding.root.setPadding(0, 0, 0, 0)
            
            // 确保RecyclerView布局参数填满整个屏幕
            binding.recyclerView.layoutParams.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            binding.recyclerView.layoutParams.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            
            // 强制重新布局
            binding.recyclerView.requestLayout()
            binding.appBarLayout.requestLayout()
            binding.bottomAppBar.requestLayout()
            binding.root.requestLayout()
        } else {
            // 退出沉浸模式：显示状态栏、导航栏和标题栏
            controller.show(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.visibility = View.VISIBLE
            binding.bottomAppBar.visibility = View.VISIBLE
            
            // 保留系统栏布局标志，只隐藏系统栏
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            
            // 保持系统栏适配设置一致
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // 重新设置系统栏间距监听器
            setupWindowInsetsListener()
            
            // 强制重新布局以确保所有组件位置正确
            binding.recyclerView.requestLayout()
            binding.appBarLayout.requestLayout()
            binding.bottomAppBar.requestLayout()
            binding.root.requestLayout()
        }
        
        // 更新沉浸模式按钮图标
        updateImmersiveButtonIcon()
    }
    
    /**
     * 更新沉浸模式按钮图标
     */
    private fun updateImmersiveButtonIcon() {
        if (isImmersiveMode) {
            // 在沉浸模式下显示退出图标
            binding.immersiveButton.setImageResource(R.drawable.ic_fullscreen_exit_md3)
        } else {
            // 在普通模式下显示沉浸模式图标
            binding.immersiveButton.setImageResource(R.drawable.ic_fullscreen_md3)
        }
    }

    private fun loadData() {
        photos = PhotoCache.getPhotos()
        currentIndex = intent.getIntExtra("EXTRA_CURRENT_INDEX", 0)
        
        // 检查是否为webtoon文件夹
        isWebtoonFolder = photos.isNotEmpty() && photos[currentIndex].title.contains("webtoon", ignoreCase = true)
        
        // 更新两个适配器的数据
        adapter.setPhotos(photos)
        webtoonAdapter?.setPhotos(photos)
        binding.recyclerView.scrollToPosition(currentIndex)
        
        if (photos.isNotEmpty() && currentIndex in photos.indices) {
            val photo = photos[currentIndex]
            binding.toolbar.title = photo.title
            
            // 初始化收藏按钮状态
            val settingsManager = SettingsManager(this)
            if (settingsManager.isPhotoFavorite(photo.id)) {
                binding.favoriteButton.setImageResource(R.drawable.ic_star_filled_md3)
            } else {
                binding.favoriteButton.setImageResource(R.drawable.ic_star_md3)
            }
        }
        
        // 更新下载按钮可见性
        binding.downloadButton.visibility = if (isWebtoonFolder) View.VISIBLE else View.GONE
    }

    private fun updateCurrentPosition() {
        val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
        if (layoutManager != null) {
            currentIndex = layoutManager.findFirstVisibleItemPosition()
            if (photos.isNotEmpty() && currentIndex in photos.indices) {
                binding.toolbar.title = photos[currentIndex].title
            }
        }
    }

    private fun showPhotoDetails() {
        if (photos.isEmpty() || currentIndex !in photos.indices) return
        
        val photo = photos[currentIndex]
        AlertDialog.Builder(this)
            .setTitle("图片详情")
            .setMessage("文件名: ${photo.title}\n" +
                    "大小: ${android.text.format.Formatter.formatFileSize(this, photo.size)}\n" +
                    "尺寸: ${photo.width}x${photo.height}\n" +
                    "本地: ${photo.isLocal}")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun toggleFavorite() {
        if (photos.isEmpty() || currentIndex !in photos.indices) return
        
        val photo = photos[currentIndex]
        val settingsManager = SettingsManager(this)
        
        if (settingsManager.isPhotoFavorite(photo.id)) {
            // 取消收藏
            settingsManager.removeFavoritePhoto(photo.id)
            binding.favoriteButton.setImageResource(R.drawable.ic_star_md3)
            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show()
        } else {
            // 添加收藏
            settingsManager.addFavoritePhoto(photo.id)
            binding.favoriteButton.setImageResource(R.drawable.ic_star_filled_md3)
            Toast.makeText(this, "已收藏", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateModeSwitchIcon() {
        if (isCardMode) {
            binding.modeSwitchButton.setImageResource(R.drawable.ic_card_mode_md3)
        } else {
            binding.modeSwitchButton.setImageResource(R.drawable.ic_webtoon_mode_md3)
        }
    }

    private fun downloadPhoto() {
        if (photos.isEmpty() || currentIndex !in photos.indices) return
        
        val photo = photos[currentIndex]
        
        lifecycleScope.launch {
            val success = FileUtils.downloadImage(this@PhotoViewActivity, photo)
            if (success) {
                Toast.makeText(this@PhotoViewActivity, "下载成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@PhotoViewActivity, "下载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deletePhoto() {
        if (photos.isEmpty() || currentIndex !in photos.indices) return
        
        AlertDialog.Builder(this)
            .setTitle("删除图片")
            .setMessage("确定要删除这张图片吗？")
            .setPositiveButton("删除", DialogInterface.OnClickListener { dialog, which ->
                val photo = photos[currentIndex]
                val success = FileUtils.deleteImage(this@PhotoViewActivity, photo)
                
                if (success) {
                    Toast.makeText(this@PhotoViewActivity, "删除成功", Toast.LENGTH_SHORT).show()
                    // 从列表中移除图片并更新UI
                    val newPhotos = photos.toMutableList()
                    newPhotos.removeAt(currentIndex)
                    photos = newPhotos
                    adapter.setPhotos(newPhotos)
                    
                    if (newPhotos.isEmpty()) {
                        // 没有图片了，返回上一页
                        finish()
                    }
                } else {
                    Toast.makeText(this@PhotoViewActivity, "删除失败", Toast.LENGTH_SHORT).show()
                }
            })
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 不要在这里清除 PhotoCache，因为旋转屏幕可能会重新创建 Activity
    }
}
