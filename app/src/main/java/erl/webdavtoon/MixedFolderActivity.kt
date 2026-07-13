package erl.webdavtoon

import android.animation.ValueAnimator
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import erl.webdavtoon.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

class MixedFolderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var adapter: MixedWaterfallAdapter
    private var folderPath: String = ""
    private var isWebDav: Boolean = false
    private var folderShuffleSeed: Long = Random.nextLong()
    private var waterfallLayoutManager: FollowZoomWaterfallLayoutManager? = null
    private var scaleDetector: android.view.ScaleGestureDetector? = null
    private var zoomStartColumns = 2f
    private var zoomGestureScale = 1f
    private var isPinchZooming = false
    private var isConsumingMultiTouchSequence = false
    private var pendingPinchTouchPoints: List<Pair<Float, Float>> = emptyList()
    private var suppressItemInteractionUntilMs = 0L
    private var snapAnimator: ValueAnimator? = null
    private var pendingZoomColumns: Float? = null
    private var pendingZoomFocusX = 0f
    private var pendingZoomFocusY = 0f
    private var isZoomFramePosted = false
    private var isAspectRatioRelayoutPosted = false

    private val applyPendingZoomFrame = Runnable {
        isZoomFramePosted = false
        val columns = pendingZoomColumns ?: return@Runnable
        pendingZoomColumns = null
        waterfallLayoutManager?.previewVirtualColumns(
            columns = columns,
            focusX = pendingZoomFocusX,
            focusY = pendingZoomFocusY
        )
    }

    private val applyPendingAspectRatioRelayout = Runnable {
        isAspectRatioRelayoutPosted = false
        waterfallLayoutManager?.notifyAspectRatiosChanged()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            adapter.setShowFilenames(settingsManager.shouldShowWaterfallFilenames())
            installWaterfallLayout()
            loadMixedContent(forceRefresh = true)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (allGranted) {
            loadMixedContent()
        } else {
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyView.text = getString(R.string.storage_permission_required)
            android.widget.Toast.makeText(this, getString(R.string.storage_permission_required), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        settingsManager = SettingsManager(this)
        LogManager.initialize(this)
        applyRotationLock()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        folderPath = intent.getStringExtra("EXTRA_FOLDER_PATH") ?: ""
        isWebDav = intent.getBooleanExtra("EXTRA_IS_WEBDAV", false)

        LibraryState.update(
            serverType = if (isWebDav) "webdav" else "local",
            rootFolderPath = folderPath
        )

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
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

        setupUi()
        checkPermissionsAndLoad()
    }

    private fun setupUi() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = displayTitle()
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adapter = MixedWaterfallAdapter(
            onFolderClick = ::openFolder,
            onMediaClick = ::openMedia,
            shouldSuppressItemInteraction = ::shouldSuppressItemInteraction,
            onPhotoDimensionsResolved = ::onPhotoDimensionsResolved,
            onRemotePreviewNeeded = ::resolveRemotePreview,
            remotePreviewGeneration = { settingsManager.getSortOrder().toString() }
        )
        adapter.setShowFilenames(settingsManager.shouldShowWaterfallFilenames())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
        installWaterfallLayout()

        binding.swipeRefreshLayout.setOnRefreshListener {
            resetFolderShuffleIfRandomSort()
            loadMixedContent(forceRefresh = true)
        }
    }

    private fun installWaterfallLayout() {
        val columns = settingsManager.getPhotoGridColumns().coerceIn(1, 4)
        binding.recyclerView.setHasFixedSize(true)
        waterfallLayoutManager = FollowZoomWaterfallLayoutManager(
            spacingPx = resources.getDimensionPixelSize(R.dimen.waterfall_item_spacing),
            aspectRatioProvider = { position -> adapter.getItemAspectRatio(position) }
        ).also { layoutManager ->
            layoutManager.setVirtualColumns(columns.toFloat())
            binding.recyclerView.layoutManager = layoutManager
        }
        installFollowZoomScaleDetector(columns)
    }

    private fun loadMixedContent(forceRefresh: Boolean = false) {
        binding.progressBar.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        binding.emptyView.visibility = View.GONE
        val startedAt = SystemClock.elapsedRealtime()

        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val repository: PhotoRepository = if (isWebDav) {
                        RustWebDavPhotoRepository(settingsManager)
                    } else {
                        LocalPhotoRepository(this@MixedFolderActivity)
                    }

                    val folders = repository.getFolders(folderPath, forceRefresh)
                        .asSequence()
                        .filterNot { it.path.startsWith("virtual://internal_photos") }
                        .filterNot { folder ->
                            !folder.isLocal && (
                                folder.name.startsWith(".") ||
                                    folder.path.trim('/').split('/').any { it.startsWith(".") }
                                )
                        }
                        .toList()

                    val directMedia = repository.getPhotos(
                        folderPath = folderPath,
                        recursive = false,
                        forceRefresh = forceRefresh
                    )
                    val resolvedFolders = if (isWebDav && folders.isEmpty()) {
                        val recursiveMedia = repository.getPhotos(
                            folderPath = folderPath,
                            recursive = true,
                            forceRefresh = forceRefresh
                        )
                        val synthesizedFolders = RemoteFolderSynthesizer.synthesizeFromRecursivePhotos(
                            currentFolderPath = folderPath,
                            photos = recursiveMedia,
                            endpoint = settingsManager.getFullWebDavUrl(),
                            sortOrder = settingsManager.getSortOrder()
                        )
                        android.util.Log.i(
                            "MixedFolderActivity",
                            "remoteFallback path=$folderPath directMedia=${directMedia.size} recursiveMedia=${recursiveMedia.size} synthesizedFolders=${synthesizedFolders.size}"
                        )
                        synthesizedFolders
                    } else {
                        folders
                    }
                    val sortedMedia = FolderPreviewOrdering.sortPhotos(
                        directMedia,
                        settingsManager.getPhotoSortOrder()
                    )

                    MixedWaterfallPlanner.buildItems(
                        folders = resolvedFolders,
                        media = sortedMedia,
                        folderSortOrder = settingsManager.getSortOrder(),
                        folderShuffleSeed = folderShuffleSeed
                    )
                }

                adapter.setItems(items)
                if (forceRefresh && isWebDav) {
                    adapter.refreshVisibleRemotePreviews()
                }
                waterfallLayoutManager?.notifyAspectRatiosChanged()
                binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyView.text = getString(R.string.no_photos_found)
                android.util.Log.i(
                    "MixedFolderActivity",
                    "loadMixedContent path=$folderPath isWebDav=$isWebDav forceRefresh=$forceRefresh items=${items.size} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
                )
            } catch (e: Exception) {
                android.util.Log.e("MixedFolderActivity", "Mixed content load failed", e)
                binding.emptyView.visibility = View.VISIBLE
                binding.emptyView.text = getString(R.string.error_prefix, e.message ?: e.toString())
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val detector = scaleDetector
        val action = ev.actionMasked
        val isMultiTouchEvent = ev.pointerCount > 1
        if (isMultiTouchEvent || isConsumingMultiTouchSequence || isPinchZooming || detector?.isInProgress == true) {
            if (!isConsumingMultiTouchSequence && isMultiTouchEvent) {
                isConsumingMultiTouchSequence = true
                suppressItemInteractionUntilMs = SystemClock.uptimeMillis() + 700L
                cancelActiveRecyclerTouch(ev)
            }
            if (isMultiTouchEvent) {
                pendingPinchTouchPoints = ev.toRecyclerTouchPoints()
            }
            detector?.onTouchEvent(ev)
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                isConsumingMultiTouchSequence = false
                pendingPinchTouchPoints = emptyList()
                suppressItemInteractionUntilMs = SystemClock.uptimeMillis() + 700L
                binding.swipeRefreshLayout.isEnabled = true
                binding.recyclerView.parent?.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun cancelActiveRecyclerTouch(ev: MotionEvent) {
        if (!::binding.isInitialized) return
        val cancelEvent = MotionEvent.obtain(ev).apply {
            action = MotionEvent.ACTION_CANCEL
        }
        super.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()
        binding.recyclerView.cancelPendingInputEvents()
        binding.recyclerView.isPressed = false
    }

    private fun MotionEvent.toRecyclerTouchPoints(): List<Pair<Float, Float>> {
        if (!::binding.isInitialized) return emptyList()
        return List(pointerCount) { index ->
            (getX(index) - binding.recyclerView.left) to (getY(index) - binding.recyclerView.top)
        }
    }

    private fun installFollowZoomScaleDetector(initialColumns: Int) {
        scaleDetector = android.view.ScaleGestureDetector(
            this,
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: android.view.ScaleGestureDetector): Boolean {
                    snapAnimator?.cancel()
                    zoomStartColumns = waterfallLayoutManager?.getVirtualColumns() ?: initialColumns.toFloat()
                    zoomGestureScale = 1f
                    isPinchZooming = true
                    suppressItemInteractionUntilMs = SystemClock.uptimeMillis() + 400L
                    binding.swipeRefreshLayout.isEnabled = false
                    binding.recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                    waterfallLayoutManager?.beginInteractiveZoom(
                        focusX = detector.focusX,
                        focusY = detector.focusY - binding.recyclerView.top,
                        touchPoints = pendingPinchTouchPoints
                    )
                    return true
                }

                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    zoomGestureScale *= detector.scaleFactor
                    val targetColumns = (zoomStartColumns / zoomGestureScale).coerceIn(1f, 4f)
                    scheduleVirtualColumnsUpdate(
                        columns = targetColumns,
                        focusX = detector.focusX,
                        focusY = detector.focusY - binding.recyclerView.top
                    )
                    return true
                }

                override fun onScaleEnd(detector: android.view.ScaleGestureDetector) {
                    isPinchZooming = false
                    suppressItemInteractionUntilMs = SystemClock.uptimeMillis() + 250L
                    applyPendingZoomNow()
                    val currentColumns = waterfallLayoutManager?.getVirtualColumns() ?: initialColumns.toFloat()
                    snapToGridColumns(
                        columns = currentColumns.roundToInt().coerceIn(1, 4),
                        focusX = detector.focusX,
                        focusY = detector.focusY - binding.recyclerView.top
                    )
                    binding.swipeRefreshLayout.isEnabled = true
                    binding.recyclerView.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        )
    }

    private fun scheduleVirtualColumnsUpdate(columns: Float, focusX: Float, focusY: Float) {
        pendingZoomColumns = columns
        pendingZoomFocusX = focusX
        pendingZoomFocusY = focusY
        if (isZoomFramePosted || !::binding.isInitialized) return

        isZoomFramePosted = true
        ViewCompat.postOnAnimation(binding.recyclerView, applyPendingZoomFrame)
    }

    private fun applyPendingZoomNow() {
        if (!::binding.isInitialized) return
        binding.recyclerView.removeCallbacks(applyPendingZoomFrame)
        isZoomFramePosted = false
        val columns = pendingZoomColumns ?: return
        pendingZoomColumns = null
        waterfallLayoutManager?.previewVirtualColumns(
            columns = columns,
            focusX = pendingZoomFocusX,
            focusY = pendingZoomFocusY
        )
    }

    private fun snapToGridColumns(columns: Int, focusX: Float, focusY: Float) {
        val clampedColumns = columns.coerceIn(1, 4)
        settingsManager.setPhotoGridColumns(clampedColumns)
        val layoutManager = waterfallLayoutManager ?: return
        applyPendingZoomNow()
        val start = layoutManager.getVirtualColumns()
        if (abs(start - clampedColumns.toFloat()) < 0.001f) {
            layoutManager.commitInteractiveZoom(clampedColumns.toFloat(), focusX, focusY)
            layoutManager.endInteractiveZoom()
            return
        }

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(start, clampedColumns.toFloat()).apply {
            duration = 180L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { animator ->
                layoutManager.previewVirtualColumns(
                    columns = animator.animatedValue as Float,
                    focusX = focusX,
                    focusY = focusY
                )
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var wasCancelled = false

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!wasCancelled) {
                        layoutManager.commitInteractiveZoom(clampedColumns.toFloat(), focusX, focusY)
                    }
                    layoutManager.endInteractiveZoom()
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    wasCancelled = true
                    layoutManager.commitInteractiveZoom(layoutManager.getVirtualColumns(), focusX, focusY)
                }
            })
            start()
        }
    }

    private fun shouldSuppressItemInteraction(): Boolean {
        return isPinchZooming || SystemClock.uptimeMillis() < suppressItemInteractionUntilMs
    }

    private fun openFolder(folder: Folder) {
        if (shouldSuppressItemInteraction()) return
        val realPath = folder.path
        val isRemoteFolder = !folder.isLocal
        if (!folder.hasSubFolders) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", realPath)
                putExtra("EXTRA_IS_WEBDAV", isRemoteFolder)
                putExtra("EXTRA_RECURSIVE", false)
            })
            return
        }

        lifecycleScope.launch {
            try {
                val target = FolderNavigationResolver.resolve(
                    context = this@MixedFolderActivity,
                    settingsManager = settingsManager,
                    folderPath = realPath,
                    isWebDav = isRemoteFolder
                )
                android.util.Log.i(
                    "MixedFolderActivity",
                    "resolvedFolderNavigation path=$realPath target=${target.javaClass.simpleName}"
                )
                FolderNavigationResolver.start(this@MixedFolderActivity, target)
            } catch (e: Exception) {
                android.util.Log.e("MixedFolderActivity", "Folder navigation resolve failed path=$realPath", e)
                startActivity(Intent(this@MixedFolderActivity, SubFolderActivity::class.java).apply {
                    putExtra("EXTRA_FOLDER_PATH", realPath)
                    putExtra("EXTRA_IS_WEBDAV", isRemoteFolder)
                })
            }
        }
    }

    private fun openMedia(photo: Photo) {
        if (shouldSuppressItemInteraction()) return
        if (photo.mediaType == MediaType.VIDEO) {
            ExternalVideoOpener.open(this, photo.imageUri.toString(), photo.title, !photo.isLocal, settingsManager)
            return
        }

        val imageOnly = adapter.mediaPhotos().filter { it.mediaType == MediaType.IMAGE }
        val imageIndex = imageOnly.indexOfFirst { it.id == photo.id }
        if (imageIndex == -1) return

        PhotoCache.setPhotos(imageOnly)
        startActivity(Intent(this, PhotoViewActivity::class.java).apply {
            putExtra("EXTRA_CURRENT_INDEX", imageIndex)
        })
    }

    private fun resolveRemotePreview(folder: Folder, forceRefresh: Boolean = false) {
        if (folder.isLocal || folder.path.startsWith("virtual://")) return

        lifecycleScope.launch {
            val sortOrder = settingsManager.getSortOrder()
            val preview = RustWebDavPhotoRepository(settingsManager).inspectFolder(
                folderPath = folder.path,
                sortOrder = sortOrder,
                forceRefresh = forceRefresh
            ) ?: return@launch
            if (settingsManager.getSortOrder() != sortOrder) return@launch
            adapter.updateFolderPreview(folder.path, preview.previewUris, preview.hasSubFolders)
        }
    }

    private fun onPhotoDimensionsResolved(photoId: String, width: Int, height: Int) {
        if (!::adapter.isInitialized || !::binding.isInitialized) return
        if (!adapter.updateResolvedDimensions(photoId, width, height)) return
        if (isAspectRatioRelayoutPosted) return

        isAspectRatioRelayoutPosted = true
        ViewCompat.postOnAnimation(binding.recyclerView, applyPendingAspectRatioRelayout)
    }

    private fun displayTitle(): String {
        return when {
            folderPath.isEmpty() && !isWebDav -> getString(R.string.local_photos)
            folderPath.isEmpty() && isWebDav -> getString(R.string.remote)
            else -> {
                val lastSegment = folderPath.trimEnd('/').split('/').lastOrNull { it.isNotEmpty() } ?: folderPath
                android.net.Uri.decode(lastSegment)
            }
        }
    }

    private fun resetFolderShuffleIfRandomSort() {
        if (settingsManager.getSortOrder() == SettingsManager.SORT_RANDOM_FOLDERS) {
            folderShuffleSeed = Random.nextLong()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val imageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val videoGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            imageGranted && videoGranted
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissionsAndLoad() {
        if (isWebDav) {
            loadMixedContent()
            return
        }

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (hasStoragePermission()) {
            loadMixedContent()
        } else {
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyView.text = getString(R.string.storage_permission_required)
            requestPermissionLauncher.launch(permissions)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        OverflowMenuHelper.enableOptionalIcons(menu)

        menu.findItem(R.id.action_share)?.isVisible = false
        menu.findItem(R.id.action_edit)?.isVisible = false
        menu.findItem(R.id.action_favorite)?.isVisible = false
        menu.findItem(R.id.action_info)?.isVisible = false
        menu.findItem(R.id.action_delete)?.isVisible = false
        menu.findItem(R.id.action_search)?.isVisible = false
        menu.findItem(R.id.action_select)?.isVisible = false
        menu.findItem(R.id.action_randomize_photos)?.isVisible = false

        menu.findItem(R.id.action_rotation_lock)?.isChecked = settingsManager.isRotationLocked()
        tintOverflowMenuIcons(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        OverflowMenuHelper.enableOptionalIcons(menu)
        tintOverflowMenuIcons(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        OverflowMenuHelper.enableOptionalIcons(menu)
        tintOverflowMenuIcons(menu)
        return super.onMenuOpened(featureId, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_rotation_lock -> {
                val newLockedState = !item.isChecked
                item.isChecked = newLockedState
                settingsManager.setRotationLocked(newLockedState)
                applyRotationLock()
                true
            }
            R.id.action_settings -> {
                settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_col_1 -> updateGridColumns(1)
            R.id.action_col_2 -> updateGridColumns(2)
            R.id.action_col_3 -> updateGridColumns(3)
            R.id.action_col_4 -> updateGridColumns(4)
            R.id.action_sort_name_asc -> updateSortOrder(SettingsManager.SORT_NAME_ASC)
            R.id.action_sort_name_desc -> updateSortOrder(SettingsManager.SORT_NAME_DESC)
            R.id.action_sort_date_desc -> updateSortOrder(SettingsManager.SORT_DATE_DESC)
            R.id.action_sort_date_asc -> updateSortOrder(SettingsManager.SORT_DATE_ASC)
            R.id.action_sort_random_folders -> updateSortOrder(SettingsManager.SORT_RANDOM_FOLDERS)
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateGridColumns(columns: Int): Boolean {
        val clampedColumns = columns.coerceIn(1, 4)
        settingsManager.setPhotoGridColumns(clampedColumns)
        waterfallLayoutManager?.setVirtualColumns(clampedColumns.toFloat())
        return true
    }

    private fun updateSortOrder(order: Int): Boolean {
        settingsManager.setSortOrder(order)
        settingsManager.setPhotoSortOrder(order)
        if (order == SettingsManager.SORT_RANDOM_FOLDERS) {
            folderShuffleSeed = Random.nextLong()
        }
        loadMixedContent()
        return true
    }

    private fun applyRotationLock() {
        if (::settingsManager.isInitialized && settingsManager.isRotationLocked()) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyRotationLock()
    }

    private fun tintOverflowMenuIcons(menu: Menu) {
        val normalColor = if (isDarkModeEnabled()) {
            android.graphics.Color.WHITE
        } else {
            ContextCompat.getColor(this, R.color.onSurface)
        }
        val submenuItems = listOf(
            R.id.action_col_1,
            R.id.action_col_2,
            R.id.action_col_3,
            R.id.action_col_4,
            R.id.action_sort_name_asc,
            R.id.action_sort_name_desc,
            R.id.action_sort_date_desc,
            R.id.action_sort_date_asc,
            R.id.action_sort_random_folders
        )

        listOf(
            R.id.action_settings,
            R.id.action_grid_columns,
            R.id.action_sort_order,
            R.id.action_rotation_lock
        ).forEach { id ->
            menu.findItem(id)?.icon?.mutate()?.let { DrawableCompat.setTint(it, normalColor) }
        }
        submenuItems.forEach { id ->
            menu.findItem(id)?.icon?.mutate()?.let { DrawableCompat.setTint(it, normalColor) }
        }
    }

    private fun isDarkModeEnabled(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}
