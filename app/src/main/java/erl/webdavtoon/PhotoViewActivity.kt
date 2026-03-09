package erl.webdavtoon

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.MotionEvent
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

    private var isSelectionMode = false
    private var isFavorites = false
    private var deleteMenuItem: android.view.MenuItem? = null
    private var isDraggingFastScroll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
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
        setupBottomBar()
        loadData()
        
        // 处理系统返回键
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { 
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                onBackPressedDispatcher.onBackPressed() 
            }
        }

        adapter = PhotoDetailAdapter(
            onLongPress = { position -> enterSelectionMode(position) },
            onClick = { position -> handleItemClick(position) }
        )
        webtoonAdapter = WebtoonAdapter(
            tapListener = object : WebtoonAdapter.OnWebtoonTapListener {
                override fun onDoubleTap() {
                    if (!isSelectionMode) {
                        toggleImmersiveMode(!isImmersiveMode)
                    }
                }
            },
            onLongPress = { position -> enterSelectionMode(position) },
            onClick = { position -> handleItemClick(position) }
        )
        
        // 初始设置为Webtoon模式
        setupWebtoonMode()
        
        // 默认开启沉浸模式
        toggleImmersiveMode(true)
        
        setupFastScroll()
        
        // 监听滑动事件，更新当前图片索引和标题
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!isDraggingFastScroll) {
                    updateFastScrollThumbPosition()
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateCurrentPosition()
                }
            }
        })
    }

    private fun setupFastScroll() {
        binding.fastScrollContainer.setOnTouchListener { _, event ->
            val totalHeight = binding.fastScrollContainer.height
            val thumbHeight = binding.fastScrollThumb.height
            val paddingTop = binding.fastScrollContainer.paddingTop
            val paddingBottom = binding.fastScrollContainer.paddingBottom
            
            // 可滚动的有效高度
            val effectiveHeight = totalHeight - paddingTop - paddingBottom
            val maxScrollY = effectiveHeight - thumbHeight
            
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    isDraggingFastScroll = true
                    // 计算 Y 坐标在容器内的百分比（相对于有效滚动区域）
                    var y = event.y - paddingTop - thumbHeight / 2f
                    if (y < 0) y = 0f
                    if (y > maxScrollY) y = maxScrollY.toFloat()
                    
                    // 设置滑块位置（需要加上 paddingTop）
                    binding.fastScrollThumb.y = y + paddingTop
                    
                    // 计算滚动到的位置
                    val percentage = y / maxScrollY
                    val totalItems = photos.size
                    if (totalItems > 0) {
                        val targetPos = (percentage * (totalItems - 1)).toInt()
                        (binding.recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(targetPos, 0)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDraggingFastScroll = false
                    updateCurrentPosition()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateFastScrollThumbPosition() {
        if (isCardMode || photos.isEmpty()) return
        
        val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePos == RecyclerView.NO_POSITION) return
        
        val totalItems = photos.size
        val totalHeight = binding.fastScrollContainer.height
        val thumbHeight = binding.fastScrollThumb.height
        val paddingTop = binding.fastScrollContainer.paddingTop
        val paddingBottom = binding.fastScrollContainer.paddingBottom
        
        val effectiveHeight = totalHeight - paddingTop - paddingBottom
        val maxScrollY = effectiveHeight - thumbHeight
        
        if (totalItems > 1) {
            val percentage = firstVisiblePos.toFloat() / (totalItems - 1)
            binding.fastScrollThumb.y = paddingTop + (percentage * maxScrollY)
        } else {
            binding.fastScrollThumb.y = paddingTop.toFloat()
        }
    }

    private fun enterSelectionMode(position: Int) {
        if (isSelectionMode) return
        // 只有在非沉浸模式（工具栏可见）下才允许进入多选模式
        if (isImmersiveMode) return
        
        isSelectionMode = true
        
        adapter.setSelectionMode(true)
        webtoonAdapter?.setSelectionMode(true)
        
        adapter.toggleSelection(position)
        webtoonAdapter?.toggleSelection(position)
        
        updateSelectionUI()
    }

    private fun handleItemClick(position: Int) {
        if (isSelectionMode) {
            adapter.toggleSelection(position)
            webtoonAdapter?.toggleSelection(position)
            
            val count = if (isCardMode) adapter.getSelectedCount() else webtoonAdapter?.getSelectedCount() ?: 0
            if (count == 0) {
                exitSelectionMode()
            } else {
                updateSelectionUI()
            }
        } else {
            toggleImmersiveMode(!isImmersiveMode)
        }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        adapter.setSelectionMode(false)
        webtoonAdapter?.setSelectionMode(false)
        adapter.clearSelection()
        webtoonAdapter?.clearSelection()
        updateSelectionUI()
        updateCurrentPosition() // 恢复标题
    }

    private fun updateSelectionUI() {
        val count = if (isCardMode) adapter.getSelectedCount() else webtoonAdapter?.getSelectedCount() ?: 0
        val onSurfaceColor = ContextCompat.getColor(this, R.color.onSurface)
        val onSurfaceTint = android.content.res.ColorStateList.valueOf(onSurfaceColor)
        val primaryTint = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
        val redTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)

        if (isSelectionMode) {
            binding.toolbar.title = getString(R.string.selected_count, count)
            deleteMenuItem?.isVisible = true
            // 确保 Toolbar 删除图标为红色
            deleteMenuItem?.icon?.let { icon ->
                androidx.core.graphics.drawable.DrawableCompat.setTint(icon, android.graphics.Color.RED)
            }
            // 确保底栏删除图标为红色
            binding.deleteButton.imageTintList = redTint
            // 更新多选按钮图标颜色为主题色
            binding.selectButton.imageTintList = primaryTint
        } else {
            deleteMenuItem?.isVisible = false
            // 恢复底栏按钮颜色为统一的 onSurface
            binding.deleteButton.imageTintList = onSurfaceTint
            binding.selectButton.imageTintList = onSurfaceTint
            updateCurrentPosition()
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        // 隐藏不需要的菜单项
        menu.findItem(R.id.action_search)?.isVisible = false
        menu.findItem(R.id.action_select)?.isVisible = false
        menu.findItem(R.id.action_settings)?.isVisible = false
        menu.findItem(R.id.action_sort_order)?.isVisible = false
        menu.findItem(R.id.action_grid_columns)?.isVisible = false
        
        deleteMenuItem = menu.findItem(R.id.action_delete)
        deleteMenuItem?.isVisible = isSelectionMode
        if (isSelectionMode) {
            deleteMenuItem?.icon?.let { icon ->
                androidx.core.graphics.drawable.DrawableCompat.setTint(icon, android.graphics.Color.RED)
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                deleteSelectedPhotos()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun deleteSelectedPhotos() {
        val selectedPhotos = if (isCardMode) adapter.getSelectedPhotos() else webtoonAdapter?.getSelectedPhotos() ?: emptyList()
        if (selectedPhotos.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.delete_photos)
            .setMessage(getString(R.string.delete_photos_message, selectedPhotos.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                val settingsManager = SettingsManager(this)
                lifecycleScope.launch {
                    var allSuccess = true
                    selectedPhotos.forEach { photo ->
                        val success = FileUtils.deleteImage(this@PhotoViewActivity, photo, settingsManager)
                        if (success) {
                            WebDavImageLoader.clearCache(this@PhotoViewActivity, photo)
                        } else {
                            allSuccess = false
                        }
                    }

                    if (allSuccess) {
                        Toast.makeText(this@PhotoViewActivity, getString(R.string.delete_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@PhotoViewActivity, getString(R.string.delete_partial_failed), Toast.LENGTH_SHORT).show()
                    }

                    // 更新列表并退出多选模式
                    val newPhotos = photos.toMutableList()
                    newPhotos.removeAll(selectedPhotos)
                    photos = newPhotos
                    
                    // 更新缓存和状态，以便 MainActivity 同步更新
                    PhotoCache.setPhotos(newPhotos)
                    MediaState.removePhotos(selectedPhotos)
                    
                    adapter.setPhotos(newPhotos)
                    webtoonAdapter?.setPhotos(newPhotos)
                    
                    exitSelectionMode()

                    if (newPhotos.isEmpty()) {
                        finish()
                    } else {
                        if (currentIndex >= newPhotos.size) {
                            currentIndex = newPhotos.size - 1
                        }
                        if (isCardMode) {
                            binding.recyclerView.scrollToPosition(currentIndex)
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
        // 设置线性 (Outlined) 风格图标
        binding.detailButton.setImageResource(R.drawable.ic_info_outlined)
        binding.favoriteButton.setImageResource(R.drawable.ic_star_outlined)
        binding.downloadButton.setImageResource(R.drawable.ic_download_outlined)
        binding.deleteButton.setImageResource(R.drawable.ic_delete_outlined)
        binding.selectButton.setImageResource(R.drawable.ic_check_circle_outlined)
        
        // 统一设置底栏按钮颜色为 onSurface (深色/黑色)
        val tintColor = ContextCompat.getColor(this, R.color.onSurface)
        val tintList = android.content.res.ColorStateList.valueOf(tintColor)
        
        binding.detailButton.imageTintList = tintList
        binding.favoriteButton.imageTintList = tintList
        binding.downloadButton.imageTintList = tintList
        binding.modeSwitchButton.imageTintList = tintList
        binding.immersiveButton.imageTintList = tintList
        binding.selectButton.imageTintList = tintList
        binding.deleteButton.imageTintList = tintList

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

        // 多选按钮点击事件
        binding.selectButton.setOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                enterSelectionMode(currentIndex)
            }
        }
        
        // 根据当前模式设置初始图标
        updateBottomBarIcons()
        
        // 删除按钮点击事件
        binding.deleteButton.setOnClickListener {
            if (isSelectionMode) {
                deleteSelectedPhotos()
            } else {
                deletePhoto()
            }
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
            binding.fastScrollContainer.visibility = View.GONE
            // 更新卡片模式适配器数据
            adapter.setPhotos(photos)
            binding.recyclerView.adapter = adapter
        } else {
            // 切换到webtoon模式
            setupWebtoonMode()
            if (!isImmersiveMode) {
                binding.fastScrollContainer.visibility = View.VISIBLE
            }
            // 更新webtoon模式适配器数据
            webtoonAdapter?.setPhotos(photos)
            binding.recyclerView.adapter = webtoonAdapter
        }
        
        // 更新模式切换按钮图标
        updateBottomBarIcons()
        
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
            binding.fastScrollContainer.visibility = View.GONE
            
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
            if (!isCardMode) {
                binding.fastScrollContainer.visibility = View.VISIBLE
                binding.recyclerView.post { updateFastScrollThumbPosition() }
            }
            
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
        
        // 更新底栏图标状态
        updateBottomBarIcons()
    }
    


    private fun loadData() {
        photos = PhotoCache.getPhotos()
        currentIndex = intent.getIntExtra("EXTRA_CURRENT_INDEX", 0)
        isFavorites = intent.getBooleanExtra("EXTRA_IS_FAVORITES", false)
        
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
            updateFavoriteButtonState()
        }
        
        // 更新下载按钮可见性
        binding.downloadButton.visibility = if (isWebtoonFolder) View.VISIBLE else View.GONE
    }

    private fun updateFavoriteButtonState() {
        if (photos.isEmpty() || currentIndex !in photos.indices) return
        
        val photo = photos[currentIndex]
        val settingsManager = SettingsManager(this)
        if (settingsManager.isPhotoFavorite(photo.id)) {
            binding.favoriteButton.setImageResource(R.drawable.ic_star_filled_md3)
        } else {
            binding.favoriteButton.setImageResource(R.drawable.ic_star_outlined)
        }
    }

    private fun updateCurrentPosition() {
        val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
        if (layoutManager != null) {
            val pos = layoutManager.findFirstVisibleItemPosition()
            if (pos != RecyclerView.NO_POSITION) {
                if (currentIndex != pos) {
                    currentIndex = pos
                    if (!isSelectionMode && photos.isNotEmpty() && currentIndex in photos.indices) {
                        binding.toolbar.title = photos[currentIndex].title
                        updateFavoriteButtonState()
                    }
                }
            }
        }
    }

    private fun showPhotoDetails() {
        if (photos.isEmpty() || currentIndex !in photos.indices) return
        
        val photo = photos[currentIndex]
        AlertDialog.Builder(this)
            .setTitle(R.string.photo_details)
            .setMessage(getString(R.string.file_name_prefix, photo.title) + "\n" +
                    getString(R.string.file_size_prefix, android.text.format.Formatter.formatFileSize(this, photo.size)) + "\n" +
                    getString(R.string.file_dimension_prefix, photo.width, photo.height) + "\n" +
                    getString(R.string.local_prefix, photo.isLocal))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun toggleFavorite() {
        if (photos.isEmpty() || currentIndex !in photos.indices) return
        
        val photo = photos[currentIndex]
        val settingsManager = SettingsManager(this)
        
        if (settingsManager.isPhotoFavorite(photo.id)) {
            // 取消收藏
            settingsManager.removeFavoritePhoto(photo.id)
            binding.favoriteButton.setImageResource(R.drawable.ic_star_outlined)
            Toast.makeText(this, getString(R.string.favorite_removed), Toast.LENGTH_SHORT).show()
            
            // 如果是从收藏夹进入的，取消收藏时从当前列表中移除
            if (isFavorites) {
                val newPhotos = photos.toMutableList()
                newPhotos.removeAt(currentIndex)
                
                // 更新全局缓存和状态
                PhotoCache.setPhotos(newPhotos)
                MediaState.removePhotos(listOf(photo))
                
                photos = newPhotos
                
                // 更新适配器
                adapter.setPhotos(photos)
                webtoonAdapter?.setPhotos(photos)
                
                if (photos.isEmpty()) {
                    finish()
                } else {
                    // 调整当前索引
                    if (currentIndex >= photos.size) {
                        currentIndex = photos.size - 1
                    }
                    // 重新加载当前页面的标题和收藏状态
                    val currentPhoto = photos[currentIndex]
                    binding.toolbar.title = currentPhoto.title
                    updateFavoriteButtonState()
                    // 滚动到当前位置（对于卡片模式比较重要）
                    if (isCardMode) {
                        binding.recyclerView.scrollToPosition(currentIndex)
                    }
                }
            }
        } else {
            // 添加收藏
            settingsManager.addFavoritePhoto(photo)
            binding.favoriteButton.setImageResource(R.drawable.ic_star_filled_md3)
            Toast.makeText(this, getString(R.string.favorite_added), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBottomBarIcons() {
        if (isCardMode) {
            binding.modeSwitchButton.setImageResource(R.drawable.ic_webtoon_mode_outlined)
        } else {
            binding.modeSwitchButton.setImageResource(R.drawable.ic_card_mode_outlined)
        }
        
        if (isImmersiveMode) {
            binding.immersiveButton.setImageResource(R.drawable.ic_fullscreen_exit_md3)
        } else {
            binding.immersiveButton.setImageResource(R.drawable.ic_fullscreen_md3)
        }
        
        // 重新应用颜色，因为setImageResource可能会重置tint
        val onSurfaceColor = ContextCompat.getColor(this, R.color.onSurface)
        val onSurfaceTint = android.content.res.ColorStateList.valueOf(onSurfaceColor)
        binding.modeSwitchButton.imageTintList = onSurfaceTint
        binding.immersiveButton.imageTintList = onSurfaceTint
    }

    private fun downloadPhoto() {
        if (photos.isEmpty() || currentIndex !in photos.indices) return
        
        val photo = photos[currentIndex]
        
        lifecycleScope.launch {
            val success = FileUtils.downloadImage(this@PhotoViewActivity, photo)
            if (success) {
                Toast.makeText(this@PhotoViewActivity, getString(R.string.download_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@PhotoViewActivity, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deletePhoto() {
        if (photos.isEmpty() || currentIndex !in photos.indices) return
        
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_photos)
            .setMessage(R.string.delete_photo_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                val photo = photos[currentIndex]
                val settingsManager = SettingsManager(this)
                
                lifecycleScope.launch {
                    val success = FileUtils.deleteImage(this@PhotoViewActivity, photo, settingsManager)
                    
                    if (success) {
                        WebDavImageLoader.clearCache(this@PhotoViewActivity, photo)
                        Toast.makeText(this@PhotoViewActivity, getString(R.string.delete_success), Toast.LENGTH_SHORT).show()
                        // 从列表中移除图片并更新UI
                        val newPhotos = photos.toMutableList()
                        val deletedPhoto = photos[currentIndex]
                        newPhotos.removeAt(currentIndex)
                        photos = newPhotos
                        
                        // 更新缓存和状态，以便 MainActivity 同步更新
                        PhotoCache.setPhotos(newPhotos)
                        MediaState.removePhotos(listOf(deletedPhoto))
                        
                        // 更新适配器数据
                        adapter.setPhotos(newPhotos)
                        webtoonAdapter?.setPhotos(newPhotos)
                        
                        if (newPhotos.isEmpty()) {
                            // 没有图片了，返回上一页
                            finish()
                        } else {
                            // 保持当前索引在有效范围内
                            if (currentIndex >= newPhotos.size) {
                                currentIndex = newPhotos.size - 1
                            }
                            // 如果是卡片模式，RecyclerView可能需要更新当前显示的图片
                            if (isCardMode) {
                                binding.recyclerView.scrollToPosition(currentIndex)
                            }
                            // 更新标题
                            binding.toolbar.title = newPhotos[currentIndex].title
                        }
                    } else {
                        Toast.makeText(this@PhotoViewActivity, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 不要在这里清除 PhotoCache，因为旋转屏幕可能会重新创建 Activity
    }
}
