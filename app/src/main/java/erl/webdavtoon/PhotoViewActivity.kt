package erl.webdavtoon

import android.content.ClipData
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import erl.webdavtoon.databinding.ActivityPhotoViewBinding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Webtoon 浏览模式 Activity
 */
class PhotoViewActivity : AppCompatActivity() {

    companion object {
        private const val FAST_SCROLL_HEIGHT_RATIO = 0.67f
    }

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
    private var shareMenuItem: android.view.MenuItem? = null
    private var isDraggingFastScroll = false
    private var isInitialLoad = true

    private val settingsManager by lazy { SettingsManager(this) }
    private var maxReaderZoomScale = 3f
    private val mediaViewModel by lazy { androidx.lifecycle.ViewModelProvider(this)[MediaViewModel::class.java] }
    // 移除旧的宽度缩放变量


    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        LogManager.initialize(this)
        super.onCreate(savedInstanceState)
        MediaManager.mediaViewModel = mediaViewModel
        // 应用旋转锁定设置
        applyRotationLock()
        
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

        loadReaderZoomSettings()
        initScaleGestureDetector()

        setupUI()

        setupBottomBar()
        loadData(savedInstanceState)
        
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
            onLongPress = { position -> 
                if (isImmersiveMode) {
                    toggleImmersiveMode(false)
                } else {
                    enterSelectionMode(position)
                }
            },
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
            onLongPress = { position -> 
                if (isImmersiveMode) {
                    toggleImmersiveMode(false)
                } else {
                    enterSelectionMode(position)
                }
            },
            onClick = { position -> handleItemClick(position) }
        )
        adapter.setMaxZoomPercent((maxReaderZoomScale * 100).toInt())
        
        setupFastScroll()
        
        // 监听滑动事件，更新当前图片索引和标题
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!isDraggingFastScroll) {
                    updateFastScrollThumbPosition()
                }
                updateCurrentPosition()
                
                // 检查是否需要加载下一页
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                if (layoutManager != null && photos.isNotEmpty()) {
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    if (lastVisible >= photos.size - 10) { // 提前 10 张开始预加载
                        MediaManager.loadNextPage(this@PhotoViewActivity, lifecycleScope)
                    }
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateCurrentPosition()
                }
            }
        })

        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateFastScrollContainerLayout()
        }
    }

    private fun loadReaderZoomSettings() {
        val percent = settingsManager.getReaderMaxZoomPercent().coerceIn(100, 500)
        maxReaderZoomScale = percent / 100f
        if (::adapter.isInitialized) {
            adapter.setMaxZoomPercent(percent)
        }
        binding.recyclerView.setMaxScale(maxReaderZoomScale)
        
        // 设置单击监听，用于显示/隐藏工具栏
        binding.recyclerView.onTapListener = object : ZoomableRecyclerView.OnTapListener {
            override fun onSingleTap() {
                // 如果当前工具栏可见，单击可以隐藏它（沉浸模式）
                if (!isSelectionMode && !isImmersiveMode) {
                    toggleImmersiveMode(true)
                }
            }

            override fun onLongPress() {
                // 只有在沉浸模式（全屏）下，长按才呼出工具栏
                // 如果已经在非沉浸模式下，长按是为了进入多选模式，这里不处理，由 Adapter 处理
                if (!isSelectionMode && isImmersiveMode) {
                    toggleImmersiveMode(false)
                }
            }
        }
    }


    private fun initScaleGestureDetector() {
        // 缩放逻辑已迁移至 ZoomableRecyclerView
    }

    private fun applyWebtoonZoom(scrollX: Int = -1) {
        // 宽度缩放逻辑已移除，现在使用 Matrix 缩放
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
            if (maxScrollY <= 0) {
                return@setOnTouchListener false
            }
            
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
        if (maxScrollY <= 0) {
            binding.fastScrollThumb.y = paddingTop.toFloat()
            return
        }
        
        if (totalItems > 1) {
            val percentage = firstVisiblePos.toFloat() / (totalItems - 1)
            binding.fastScrollThumb.y = paddingTop + (percentage * maxScrollY)
        } else {
            binding.fastScrollThumb.y = paddingTop.toFloat()
        }
    }

    private fun updateFastScrollContainerLayout() {
        val rootHeight = binding.root.height
        if (rootHeight <= 0) return

        val layoutParams = binding.fastScrollContainer.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        val desiredHeight = (rootHeight * FAST_SCROLL_HEIGHT_RATIO).roundToInt()
            .coerceAtLeast(binding.fastScrollThumb.height + binding.fastScrollContainer.paddingTop + binding.fastScrollContainer.paddingBottom)

        if (layoutParams.height != desiredHeight || layoutParams.gravity != (Gravity.END or Gravity.CENTER_VERTICAL)) {
            layoutParams.height = desiredHeight
            layoutParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            binding.fastScrollContainer.layoutParams = layoutParams
        }

        if (!isCardMode && !isImmersiveMode && binding.fastScrollContainer.visibility == View.VISIBLE) {
            binding.fastScrollContainer.post { updateFastScrollThumbPosition() }
        }
    }

    private var lastImmersiveToggleTime = 0L

    private fun enterSelectionMode(position: Int) {
        if (isSelectionMode) return
        // 只有在非沉浸模式（工具栏可见）下才允许进入多选模式
        if (isImmersiveMode) return
        
        // 如果刚刚才通过长按呼出了工具栏，则本次长按不再进入多选模式，防止一次操作触发两个功能
        if (System.currentTimeMillis() - lastImmersiveToggleTime < 500) return
        
        isSelectionMode = true
        binding.recyclerView.isSelectionMode = true
        
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
            // 在卡片模式下，PhotoView 会拦截单击事件，我们需要在这里处理 UI 隐藏逻辑
            // 如果当前工具栏可见，单击可以隐藏它（沉浸模式）
            if (!isImmersiveMode) {
                toggleImmersiveMode(true)
            }
        }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        binding.recyclerView.isSelectionMode = false
        adapter.setSelectionMode(false)
        webtoonAdapter?.setSelectionMode(false)
        adapter.clearSelection()
        webtoonAdapter?.clearSelection()
        updateSelectionUI()
        updateCurrentPosition() // 恢复标题
    }

    private fun updateSelectionUI() {
        val count = if (isCardMode) adapter.getSelectedCount() else webtoonAdapter?.getSelectedCount() ?: 0
        val defaultTint = getBottomBarIconTint()
        val primaryTint = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))


        if (isSelectionMode) {
            binding.toolbar.title = getString(R.string.selected_count, count)
            shareMenuItem?.isVisible = true
            deleteMenuItem?.isVisible = true
            deleteMenuItem?.icon?.let { icon ->
                androidx.core.graphics.drawable.DrawableCompat.setTint(icon, android.graphics.Color.RED)
            }
            // 确保底栏删除图标为红色
            binding.deleteButton.visibility = View.GONE
            // 更新多选按钮图标颜色为主题色
            binding.selectButton.imageTintList = primaryTint
            binding.favoriteButton.imageTintList = primaryTint
            invalidateOptionsMenu()
        } else {
            deleteMenuItem?.isVisible = false
            shareMenuItem?.isVisible = false
            // 恢复底栏按钮颜色
            binding.deleteButton.imageTintList = defaultTint
            binding.selectButton.imageTintList = defaultTint
            shareMenuItem?.isVisible = false
            updateFavoriteButtonState()
            updateCurrentPosition()
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        OverflowMenuHelper.enableOptionalIcons(menu)
        // 隐藏不需要的菜单项
        menu.findItem(R.id.action_search)?.isVisible = false
        menu.findItem(R.id.action_select)?.isVisible = false
        menu.findItem(R.id.action_settings)?.isVisible = false
        menu.findItem(R.id.action_sort_order)?.isVisible = false
        menu.findItem(R.id.action_grid_columns)?.isVisible = false
        shareMenuItem = menu.findItem(R.id.action_share)
        shareMenuItem?.isVisible = isSelectionMode
        shareMenuItem?.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)

        deleteMenuItem = menu.findItem(R.id.action_delete)
        deleteMenuItem?.isVisible = isSelectionMode
        if (isSelectionMode) {
            deleteMenuItem?.icon?.let { icon ->
                androidx.core.graphics.drawable.DrawableCompat.setTint(icon, android.graphics.Color.RED)
            }
        }

        // 初始化旋转锁定项状态
        val rotationLockItem = menu.findItem(R.id.action_rotation_lock)
        rotationLockItem?.isChecked = settingsManager.isRotationLocked()

        tintOverflowMenuIcons(menu)

        return true
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        OverflowMenuHelper.enableOptionalIcons(menu)
        shareMenuItem?.isVisible = isSelectionMode
        menu.findItem(R.id.action_share)?.isVisible = isSelectionMode
        tintOverflowMenuIcons(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: android.view.Menu): Boolean {
        OverflowMenuHelper.enableOptionalIcons(menu)
        tintOverflowMenuIcons(menu)
        return super.onMenuOpened(featureId, menu)
    }

    private fun tintOverflowMenuIcons(menu: android.view.Menu) {
        val normalColor = ContextCompat.getColor(this, R.color.onSurface)
        val deleteColor = ContextCompat.getColor(this, R.color.primary_red)
        menu.findItem(R.id.action_share)?.icon?.mutate()?.let { icon ->
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, normalColor)
        }
        menu.findItem(R.id.action_rotation_lock)?.icon?.mutate()?.let { icon ->
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, normalColor)
        }
        menu.findItem(R.id.action_delete)?.icon?.mutate()?.let { icon ->
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, deleteColor)
        }
        val deleteTitle = android.text.SpannableString(getString(R.string.delete)).apply {
            setSpan(android.text.style.ForegroundColorSpan(deleteColor), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        menu.findItem(R.id.action_delete)?.title = deleteTitle
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                shareSelectedPhotos()
                true
            }
            R.id.action_delete -> {
                deleteSelectedPhotos()
                true
            }
            R.id.action_rotation_lock -> {
                val newLockedState = !item.isChecked
                item.isChecked = newLockedState
                settingsManager.setRotationLocked(newLockedState)
                applyRotationLock()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareSelectedPhotos() {
        val selectedPhotos = if (isCardMode) adapter.getSelectedPhotos() else webtoonAdapter?.getSelectedPhotos() ?: emptyList()
        if (selectedPhotos.isEmpty()) return

        lifecycleScope.launch {
            runCatching {
                val shareFiles = withContext(Dispatchers.IO) {
                    selectedPhotos.map { photo ->
                        if (photo.isLocal) photo.imageUri else downloadRemotePhotoForShare(photo)
                    }
                }
                val shareIntent = buildFileShareIntent(selectedPhotos, shareFiles)
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            }.onFailure { error ->
                android.util.Log.e("PhotoViewActivity", "Share failed", error)
                Toast.makeText(this@PhotoViewActivity, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildFileShareIntent(selectedPhotos: List<Photo>, uris: List<Uri>): Intent {
        return if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = shareMimeType(selectedPhotos.first())
                putExtra(Intent.EXTRA_STREAM, uris.first())
                clipData = ClipData.newUri(contentResolver, selectedPhotos.first().title, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            val shareUris = ArrayList(uris)
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = if (selectedPhotos.all { it.mediaType == MediaType.IMAGE }) "image/*" else "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
                clipData = ClipData.newUri(contentResolver, selectedPhotos.first().title, shareUris.first()).apply {
                    shareUris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun downloadRemotePhotoForShare(photo: Photo): Uri {
        val shareDir = File(cacheDir, "shared_media").apply { mkdirs() }
        val safeName = photo.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "shared_media" }
        val targetFile = File(shareDir, safeName)
        val credentials = okhttp3.Credentials.basic(settingsManager.getWebDavUsername(), settingsManager.getWebDavPassword())
        val request = okhttp3.Request.Builder()
            .url(FileUtils.encodeWebDavUrl(photo.imageUri.toString()))
            .addHeader("Authorization", credentials)
            .build()
        okhttp3.OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body ?: throw java.io.IOException("Empty response body")
            FileOutputStream(targetFile).use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
        return FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", targetFile)
    }

    private fun shareMimeType(photo: Photo): String {
        return when (photo.mediaType) {
            MediaType.IMAGE -> "image/*"
            MediaType.VIDEO -> detectVideoMimeType(photo.title)
                ?: detectVideoMimeType(photo.imageUri.toString())
                ?: "video/*"
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
                    val deletedPhotos = mutableListOf<Photo>()
                    selectedPhotos.forEach { photo ->
                        val success = FileUtils.deleteImage(this@PhotoViewActivity, photo, settingsManager)
                        if (success) {
                            deletedPhotos.add(photo)
                        } else {
                            allSuccess = false
                        }
                    }

                    if (deletedPhotos.isNotEmpty()) {
                        WebDavImageLoader.clearCache(this@PhotoViewActivity)
                    }

                    if (allSuccess) {
                        Toast.makeText(this@PhotoViewActivity, getString(R.string.delete_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@PhotoViewActivity, getString(R.string.delete_partial_failed), Toast.LENGTH_SHORT).show()
                    }

                    // 更新列表并退出多选模式
                    val newPhotos = photos.toMutableList()
                    newPhotos.removeAll(deletedPhotos)
                    photos = newPhotos
                    
                    // 更新缓存和状态，以便 MainActivity 同步更新
                    PhotoCache.setPhotos(newPhotos)
                    mediaViewModel.removePhotos(deletedPhotos)
                    
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

                    if (deletedPhotos.isNotEmpty()) {
                        refreshCurrentMediaPage()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupCardMode() {
        // 显示沉浸模式按钮
        binding.immersiveButton.visibility = View.VISIBLE

        // 切换到卡片模式：重置缩放，并禁用 ZoomableRecyclerView 的缩放功能
        // 防止与 Item 内部的 PhotoView 冲突
        binding.recyclerView.resetScale(false)
        binding.recyclerView.setMaxScale(1f) // 禁用 RecyclerView 层级的缩放
        
        val rvLp = binding.recyclerView.layoutParams
        rvLp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        binding.recyclerView.layoutParams = rvLp

        // 设置为水平布局，支持左右滑动，一页显示一张图片
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
        val currentTopPadding = binding.recyclerView.paddingTop
        val currentBottomPadding = binding.recyclerView.paddingBottom
        binding.recyclerView.setPadding(0, currentTopPadding, 0, currentBottomPadding)

        // 添加PagerSnapHelper，实现一次翻一页的效果
        pagerSnapHelper = androidx.recyclerview.widget.PagerSnapHelper()
        pagerSnapHelper?.attachToRecyclerView(binding.recyclerView)
    }

    private fun setupWebtoonMode() {
        // 隐藏沉浸模式按钮
        binding.immersiveButton.visibility = View.GONE
        
        // 移除PagerSnapHelper的吸附效果
        pagerSnapHelper?.attachToRecyclerView(null)
        pagerSnapHelper = null

        // 恢复缩放
        binding.recyclerView.setMaxScale(maxReaderZoomScale)

        // 设置为垂直布局，支持上下滑动，连续滚动
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = webtoonAdapter
        binding.recyclerView.setHasFixedSize(true) 
        val currentTopPadding = binding.recyclerView.paddingTop
        val currentBottomPadding = binding.recyclerView.paddingBottom
        binding.recyclerView.setPadding(0, currentTopPadding, 0, currentBottomPadding)
    }


    private fun setupBottomBar() {
        // 设置线性 (Outlined) 风格图标
            binding.detailButton.setImageResource(R.drawable.ic_ior_info_circle)
            binding.favoriteButton.setImageResource(R.drawable.ic_ior_star)
            binding.downloadButton.setImageResource(R.drawable.ic_ior_download)
            binding.deleteButton.setImageResource(R.drawable.ic_ior_trash)
            binding.selectButton.setImageResource(R.drawable.ic_ior_check_circle)
        
        // 统一设置底栏按钮颜色：深色模式强制白色，其他模式使用主题色
        val tintList = getBottomBarIconTint()

        
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
            if (isSelectionMode) {
                updateSelectedFavorites()
            } else {
                Toast.makeText(this, getString(R.string.favorite_requires_selection), Toast.LENGTH_SHORT).show()
            }
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
            updateFastScrollContainerLayout()
            if (!isImmersiveMode) {
                binding.fastScrollContainer.visibility = View.VISIBLE
                binding.recyclerView.post { updateFastScrollThumbPosition() }
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
    private fun applyRotationLock() {
        if (settingsManager.isRotationLocked()) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 动态配置变化时再次检查旋转锁定，防止系统强制重置方向
        applyRotationLock()
    }

    private fun setupWindowInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // 只有在非沉浸模式下才应用内边距，避免覆盖沉浸模式的全屏设置
            if (!isImmersiveMode) {
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
            }
            binding.root.post { updateFastScrollContainerLayout() }
            insets
        }
    }
    
    private fun toggleImmersiveMode(enabled: Boolean) {
        isImmersiveMode = enabled
        lastImmersiveToggleTime = System.currentTimeMillis()
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        
        if (enabled) {
            // 进入沉浸模式：隐藏状态栏、导航栏和标题栏
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            binding.appBarLayout.visibility = View.GONE
            binding.bottomAppBar.visibility = View.GONE
            binding.fastScrollContainer.visibility = View.GONE
            
            // 沉浸模式下，RecyclerView 占据全屏
            binding.recyclerView.setPadding(0, 0, 0, 0)
        } else {
            // 退出沉浸模式：显示状态栏、导航栏和标题栏
            controller.show(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.visibility = View.VISIBLE
            binding.bottomAppBar.visibility = View.VISIBLE
            updateFastScrollContainerLayout()
            if (!isCardMode) {
                binding.fastScrollContainer.visibility = View.VISIBLE
                binding.recyclerView.post { updateFastScrollThumbPosition() }
            }
            
            // 退出沉浸模式后恢复内边距，确保不被底栏遮挡
            ViewCompat.requestApplyInsets(binding.root)
        }
        
        // 更新底栏图标状态
        updateBottomBarIcons()
    }
    


    private fun observeMediaState() {
        lifecycleScope.launch {
            mediaViewModel.state.collect { state: MediaUiState ->
                // 仅当会话匹配且图片列表确实发生变化时才更新
                if (state.sessionKey.isNotEmpty()) {
                    val currentSortOrder = settingsManager.getPhotoSortOrder()
                    val sortedStatePhotos = MediaManager.sortPhotos(state.photos, currentSortOrder, state.isRecursive)
                    
                    if (sortedStatePhotos != photos) {
                        photos = sortedStatePhotos
                        adapter.setPhotos(photos)
                        webtoonAdapter?.setPhotos(photos)
                        
                        // 更新标题等信息
                        if (currentIndex in photos.indices) {
                            binding.toolbar.title = photos[currentIndex].title
                            updateFavoriteButtonState()
                        }
                    }
                }
            }
        }
    }

    private fun refreshCurrentMediaPage() {
        val state = mediaViewModel.state.value
        if (state.sessionKey.isEmpty()) return

        MediaManager.refresh(
            context = this,
            scope = lifecycleScope,
            sessionKey = state.sessionKey,
            folderPath = state.folderPath,
            isRemote = state.isRemote,
            isRecursive = state.isRecursive,
            isFavorites = state.isFavorites,
            query = state.currentQuery
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 在保存状态前确保 currentIndex 是最新的
        updateCurrentPosition()
        outState.putInt("EXTRA_CURRENT_INDEX", currentIndex)
        outState.putBoolean("EXTRA_IS_CARD_MODE", isCardMode)
        outState.putBoolean("EXTRA_IS_IMMERSIVE_MODE", isImmersiveMode)
    }

    private fun loadData(savedInstanceState: Bundle?) {
        photos = PhotoCache.getPhotos()
        
        // 优先从 savedInstanceState 恢复索引和模式，如果没有则从 intent 或默认值获取
        if (savedInstanceState != null) {
            currentIndex = savedInstanceState.getInt("EXTRA_CURRENT_INDEX", 0)
            isCardMode = savedInstanceState.getBoolean("EXTRA_IS_CARD_MODE", false)
            isImmersiveMode = savedInstanceState.getBoolean("EXTRA_IS_IMMERSIVE_MODE", false)
        } else {
            currentIndex = intent.getIntExtra("EXTRA_CURRENT_INDEX", 0)
            // 初始模式由文件夹类型决定，如果是 webtoon 则默认为 webtoon 模式
            isCardMode = false
            isImmersiveMode = true // 默认开启沉浸模式
        }

        observeMediaState()
            
        isFavorites = intent.getBooleanExtra("EXTRA_IS_FAVORITES", false)
        
        // 检查是否为webtoon文件夹
        isWebtoonFolder = photos.isNotEmpty() && currentIndex in photos.indices && photos[currentIndex].title.contains("webtoon", ignoreCase = true)
        
        // 根据恢复的模式设置 UI
        if (isCardMode) {
            setupCardMode()
        } else {
            setupWebtoonMode()
        }
        
        // 恢复沉浸模式状态
        toggleImmersiveMode(isImmersiveMode)
        
        // 更新适配器数据
        adapter.setPhotos(photos)
        webtoonAdapter?.setPhotos(photos)
        
        // 恢复滚动位置
        binding.recyclerView.post {
            val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
            if (layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(currentIndex, 0)
            } else {
                binding.recyclerView.scrollToPosition(currentIndex)
            }
            
            // 滚动完成后，允许更新 currentIndex
            isInitialLoad = false
        }
        
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
            binding.favoriteButton.setImageResource(R.drawable.ic_ior_star_solid)
        } else {
            binding.favoriteButton.setImageResource(R.drawable.ic_ior_star)
        }
    }

    private fun updateCurrentPosition() {
        if (isInitialLoad) return
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

    private fun updateSelectedFavorites() {
        val selectedPhotos = if (isCardMode) adapter.getSelectedPhotos() else webtoonAdapter?.getSelectedPhotos() ?: emptyList()
        if (selectedPhotos.isEmpty()) return

        val settingsManager = SettingsManager(this)
        val favoriteIds = selectedPhotos
            .filter { settingsManager.isPhotoFavorite(it.id) }
            .mapTo(mutableSetOf()) { it.id }
        val plan = FavoriteSelectionPlanner.buildPlan(
            selectedItems = selectedPhotos,
            favoriteIds = favoriteIds,
            isFavoritesView = isFavorites,
            idSelector = { it.id }
        )

        plan.toAdd.forEach(settingsManager::addFavoritePhoto)
        plan.toRemove.forEach { settingsManager.removeFavoritePhoto(it.id) }

        when {
            plan.toAdd.isNotEmpty() -> {
                Toast.makeText(
                    this,
                    resources.getQuantityString(R.plurals.favorite_added_count, plan.toAdd.size, plan.toAdd.size),
                    Toast.LENGTH_SHORT
                ).show()
            }
            plan.toRemove.isNotEmpty() -> {
                Toast.makeText(
                    this,
                    resources.getQuantityString(R.plurals.favorite_removed_count, plan.toRemove.size, plan.toRemove.size),
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {
                Toast.makeText(this, getString(R.string.favorite_no_changes), Toast.LENGTH_SHORT).show()
            }
        }

        if (plan.toRemove.isNotEmpty() && isFavorites) {
            val removedIds = plan.toRemove.mapTo(mutableSetOf()) { it.id }
            val newPhotos = photos.filterNot { it.id in removedIds }

            PhotoCache.setPhotos(newPhotos)
            mediaViewModel.removePhotos(plan.toRemove)
            photos = newPhotos

            adapter.setPhotos(photos)
            webtoonAdapter?.setPhotos(photos)

            exitSelectionMode()
            refreshCurrentMediaPage()

            if (photos.isEmpty()) {
                finish()
                return
            }

            if (currentIndex >= photos.size) {
                currentIndex = photos.size - 1
            }

            val currentPhoto = photos[currentIndex]
            binding.toolbar.title = currentPhoto.title
            updateFavoriteButtonState()

            if (isCardMode) {
                binding.recyclerView.scrollToPosition(currentIndex)
            }
        } else {
            exitSelectionMode()
        }
    }

    private fun updateBottomBarIcons() {
        if (isCardMode) {
            binding.modeSwitchButton.setImageResource(R.drawable.ic_ior_album_list)
        } else {
            binding.modeSwitchButton.setImageResource(R.drawable.ic_ior_view_grid)
        }
        
        if (isImmersiveMode) {
            binding.immersiveButton.setImageResource(R.drawable.ic_ior_collapse)
        } else {
            binding.immersiveButton.setImageResource(R.drawable.ic_ior_maximize)
        }
        
        // 重新应用颜色，因为setImageResource可能会重置tint
        val tint = getBottomBarIconTint()
        binding.modeSwitchButton.imageTintList = tint
        binding.immersiveButton.imageTintList = tint
    }

    private fun isDarkModeEnabled(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getBottomBarIconTint(): android.content.res.ColorStateList {
        val color = if (isDarkModeEnabled()) {
            android.graphics.Color.WHITE
        } else {
            ContextCompat.getColor(this, R.color.onSurface)
        }
        return android.content.res.ColorStateList.valueOf(color)
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
                        WebDavImageLoader.clearCache(this@PhotoViewActivity)
                        Toast.makeText(this@PhotoViewActivity, getString(R.string.delete_success), Toast.LENGTH_SHORT).show()
                        // 从列表中移除图片并更新UI
                        val newPhotos = photos.toMutableList()
                        val deletedPhoto = photos[currentIndex]
                        newPhotos.removeAt(currentIndex)
                        photos = newPhotos
                        
                        // 更新缓存和状态，以便 MainActivity 同步更新
                        PhotoCache.setPhotos(newPhotos)
                        mediaViewModel.removePhotos(listOf(deletedPhoto))
                        
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
                            refreshCurrentMediaPage()
                        }
                    } else {
                        Toast.makeText(this@PhotoViewActivity, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadReaderZoomSettings()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 不要在这里清除 PhotoCache，因为旋转屏幕可能会重新创建 Activity
    }
}

