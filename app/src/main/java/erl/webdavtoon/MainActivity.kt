package erl.webdavtoon

import android.animation.ValueAnimator
import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import erl.webdavtoon.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var photoAdapter: PhotoAdapter
    private var isLongPressSelection = false

    fun setLongPressSelection(value: Boolean) {
        isLongPressSelection = value
    }

    fun shouldSuppressPhotoInteraction(): Boolean {
        return isPinchZooming || SystemClock.uptimeMillis() < suppressPhotoInteractionUntilMs
    }

    private var folderPath: String = ""
    private var isRemote: Boolean = false
    private var isRecursive: Boolean = false
    private var isFavorites: Boolean = false
    private var scaleDetector: android.view.ScaleGestureDetector? = null
    private var waterfallLayoutManager: FollowZoomWaterfallLayoutManager? = null
    private var zoomStartColumns = 2f
    private var zoomGestureScale = 1f
    private var isPinchZooming = false
    private var isConsumingMultiTouchSequence = false
    private var pendingPinchTouchPoints: List<Pair<Float, Float>> = emptyList()
    private var suppressPhotoInteractionUntilMs = 0L
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

    private var currentQuery = MediaQuery()
    private var optionsMenu: android.view.Menu? = null
    private var infoMenuItem: android.view.MenuItem? = null
    private var shareMenuItem: android.view.MenuItem? = null
    private var editMenuItem: android.view.MenuItem? = null
    private var deleteMenuItem: android.view.MenuItem? = null
    private var favoriteMenuItem: android.view.MenuItem? = null
    private var pendingDeleteScrollAnchor: DeleteScrollAnchor? = null

    private data class PendingLocalMediaDelete(
        val photos: List<Photo>,
        val sourcePhotos: List<Photo>
    )

    private var pendingLocalMediaDelete: PendingLocalMediaDelete? = null

    private val localMediaDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pending = pendingLocalMediaDelete ?: return@registerForActivityResult
        pendingLocalMediaDelete = null
        if (result.resultCode != RESULT_OK) return@registerForActivityResult

        lifecycleScope.launch {
            val deletedPhotos = LocalMediaDeleteRequest.awaitDeletedPhotos(
                context = this@MainActivity,
                photos = pending.photos
            )
            handleDeletedPhotos(deletedPhotos, pending.sourcePhotos)
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val columns = settingsManager.getPhotoGridColumns()
            UiState.setGridColumns(columns)
            applyWaterfallLayoutPreference()
            applyWaterfallColumns(columns)
            refreshMedia()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (allGranted) {
            refreshMedia()
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
        super.onCreate(savedInstanceState)
        MediaManager.mediaViewModel = androidx.lifecycle.ViewModelProvider(this)[MediaViewModel::class.java]
        applyRotationLock()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UiState.setGridColumns(settingsManager.getPhotoGridColumns())

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

        folderPath = intent.getStringExtra("EXTRA_FOLDER_PATH") ?: ""
        isRemote = intent.getBooleanExtra("EXTRA_IS_WEBDAV", false)
        isRecursive = intent.getBooleanExtra("EXTRA_RECURSIVE", false)
        isFavorites = intent.getBooleanExtra("EXTRA_IS_FAVORITES", false)

        LibraryState.update(
            serverType = if (isRemote) "webdav" else if (isFavorites) "favorites" else "local",
            rootFolderPath = if (isFavorites) "favorites" else folderPath
        )

        setupUi()
        observeState()

        checkPermissionsAndLoad()
    }

    override fun onResume() {
        super.onResume()
        MediaManager.mediaViewModel = androidx.lifecycle.ViewModelProvider(this)[MediaViewModel::class.java]
        applyWaterfallFilenamePreference()
    }

    private fun applyWaterfallFilenamePreference() {
        if (!::photoAdapter.isInitialized) return
        photoAdapter.setShowFilenames(settingsManager.shouldShowWaterfallFilenames())
    }

    private fun setupUi() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(folderPath.isNotEmpty())

        val displayTitle = when {
            isFavorites -> getString(R.string.favorites)
            folderPath.isEmpty() && !isRemote -> getString(R.string.local_photos)
            folderPath.isEmpty() && isRemote -> getString(R.string.remote)
            else -> {
                val lastSegment = folderPath.trimEnd('/').split('/').lastOrNull { it.isNotEmpty() } ?: folderPath
                android.net.Uri.decode(lastSegment)
            }
        }
        supportActionBar?.title = if (isRecursive && !isFavorites) "$displayTitle ${getString(R.string.all_suffix)}" else displayTitle
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.recyclerView.setItemViewCacheSize(16)
        binding.recyclerView.itemAnimator = null


        photoAdapter = PhotoAdapter(
            onPhotoClick = onPhotoClick@{ photos, position ->
                if (photoAdapter.isSelectionMode()) {
                    photoAdapter.toggleSelection(position)
                } else {
                    val clicked = photos.getOrNull(position) ?: return@onPhotoClick
                    if (clicked.mediaType == MediaType.VIDEO) {
                        ExternalVideoOpener.open(this, clicked.imageUri.toString(), clicked.title, !clicked.isLocal, settingsManager)
                        return@onPhotoClick
                    }

                    val imageOnly = photos.filter { it.mediaType == MediaType.IMAGE }
                    val imageIndex = imageOnly.indexOfFirst { it.id == clicked.id }
                    if (imageIndex == -1) return@onPhotoClick

                    PhotoCache.setPhotos(imageOnly)
                    val intent = Intent(this, PhotoViewActivity::class.java).apply {
                        putExtra("EXTRA_CURRENT_INDEX", imageIndex)
                        putExtra("EXTRA_IS_FAVORITES", isFavorites)
                    }
                    startActivity(intent)
                }
            },
            onSelectionChanged = { count ->
                updateSelectionTitle(count)
                if (count == 0 && photoAdapter.isSelectionMode()) {
                    photoAdapter.setSelectionMode(false)
                    isLongPressSelection = false
                    updateSelectionTitle(-1)
                }
            },
            onPhotoDimensionsResolved = { photoId, width, height ->
                onPhotoDimensionsResolved(photoId, width, height)
            }
        )
        binding.recyclerView.adapter = photoAdapter
        applyWaterfallLayoutPreference()
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshMedia(reshuffleClusters = true)
        }

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val last = getLastVisibleAdapterPosition(recyclerView)
                val total = photoAdapter.itemCount
                if (total == 0) return
                if (last != RecyclerView.NO_POSITION && last >= total - 24) {
                    loadNextPage()
                }
            }
        })

        DrawerHelper.setupDrawer(
            this,
            binding.drawerLayout,
            binding.toolbar,
            binding.drawerContent.root,
            settingsLauncher
        )
    }

    private fun observeState() {
        lifecycleScope.launch {
            MediaManager.mediaViewModel?.state?.collect { state: MediaUiState ->
                if (state.sessionKey != buildSessionKey()) return@collect
                binding.progressBar.visibility = if (state.isLoading && state.photos.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyView.visibility = if (!state.isLoading && state.photos.isEmpty()) View.VISIBLE else View.GONE
                if (state.error != null && state.photos.isEmpty()) {
                    binding.emptyView.text = getString(R.string.error_prefix, state.error)
                } else if (state.photos.isEmpty()) {
                    binding.emptyView.text = getString(R.string.no_photos_found)
                }
                val displayedPhotos = state.photos
                binding.swipeRefreshLayout.isRefreshing = state.isLoading && state.photos.isNotEmpty()
                val photosChanged = photoAdapter.setPhotos(displayedPhotos)
                if (photosChanged) {
                    waterfallLayoutManager?.notifyAspectRatiosChanged()
                }
                MediaStateCache.setState(state.copy(photos = displayedPhotos))
                restorePendingDeleteScrollAnchor(displayedPhotos.size)
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
                suppressPhotoInteractionUntilMs = SystemClock.uptimeMillis() + 700L
                cancelActiveRecyclerTouch(ev)
            }
            if (isMultiTouchEvent) {
                pendingPinchTouchPoints = ev.toRecyclerTouchPoints()
            }
            detector?.onTouchEvent(ev)
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                isConsumingMultiTouchSequence = false
                pendingPinchTouchPoints = emptyList()
                suppressPhotoInteractionUntilMs = SystemClock.uptimeMillis() + 700L
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

    private fun applyWaterfallLayoutPreference() {
        if (!::binding.isInitialized || !::photoAdapter.isInitialized) return
        applyWaterfallFilenamePreference()
        if (binding.recyclerView.layoutManager != null) return

        snapAnimator?.cancel()
        pendingZoomColumns = null
        isPinchZooming = false
        isConsumingMultiTouchSequence = false
        pendingPinchTouchPoints = emptyList()
        binding.swipeRefreshLayout.isEnabled = true
        binding.recyclerView.parent?.requestDisallowInterceptTouchEvent(false)

        installFollowZoomWaterfallLayout()
    }

    private fun installFollowZoomWaterfallLayout() {
        val columns = settingsManager.getPhotoGridColumns().coerceIn(1, 4)
        binding.recyclerView.setHasFixedSize(true)
        waterfallLayoutManager = FollowZoomWaterfallLayoutManager(
            spacingPx = resources.getDimensionPixelSize(R.dimen.waterfall_item_spacing),
            aspectRatioProvider = photoAdapterAspectRatioProvider()
        ).also { layoutManager ->
            layoutManager.setVirtualColumns(columns.toFloat())
            binding.recyclerView.layoutManager = layoutManager
        }
        installFollowZoomScaleDetector(columns)
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
                    suppressPhotoInteractionUntilMs = SystemClock.uptimeMillis() + 400L
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
                    suppressPhotoInteractionUntilMs = SystemClock.uptimeMillis() + 250L
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

    private fun getLastVisibleAdapterPosition(recyclerView: RecyclerView): Int {
        return when (val layout = recyclerView.layoutManager) {
            is FollowZoomWaterfallLayoutManager -> layout.getLastVisibleAdapterPosition()
            else -> RecyclerView.NO_POSITION
        }
    }

    private fun photoAdapterAspectRatioProvider(): (Int) -> Float = { position ->
        if (::photoAdapter.isInitialized) {
            photoAdapter.getPhotoAspectRatio(position)
        } else {
            1f
        }
    }

    private fun onPhotoDimensionsResolved(photoId: String, width: Int, height: Int) {
        if (!::photoAdapter.isInitialized || !::binding.isInitialized) return
        if (!photoAdapter.updateResolvedDimensions(photoId, width, height)) return
        if (isAspectRatioRelayoutPosted) return

        isAspectRatioRelayoutPosted = true
        ViewCompat.postOnAnimation(binding.recyclerView, applyPendingAspectRatioRelayout)
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
        UiState.setGridColumns(clampedColumns)
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

    private fun applyWaterfallColumns(columns: Int) {
        val clampedColumns = columns.coerceIn(1, 4)
        waterfallLayoutManager?.setVirtualColumns(clampedColumns.toFloat())
    }

    private fun refreshMedia(reshuffleClusters: Boolean = false) {
        MediaManager.refresh(
            this, lifecycleScope, buildSessionKey(),
            folderPath, isRemote, isRecursive, isFavorites, currentQuery,
            reshuffleClusters = reshuffleClusters
        )
    }

    private fun loadNextPage() {
        MediaManager.loadNextPage(this, lifecycleScope)
    }

    private fun restorePendingDeleteScrollAnchor(itemCount: Int) {
        val anchor = pendingDeleteScrollAnchor ?: return
        pendingDeleteScrollAnchor = null
        DeleteScrollAnchorHelper.restore(binding.recyclerView, anchor, itemCount)
    }

    private fun buildSessionKey(): String {
        return "$folderPath|$isRemote|$isRecursive|$isFavorites|${currentQuery.keyword}"
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
        if (isRemote || isFavorites) {
            refreshMedia()
            return
        }

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (hasStoragePermission()) {
            refreshMedia()
        } else {
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyView.text = getString(R.string.storage_permission_required)
            requestPermissionLauncher.launch(permissions)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        OverflowMenuHelper.enableOptionalIcons(menu)
        optionsMenu = menu

        infoMenuItem = menu.findItem(R.id.action_info)
        infoMenuItem?.isVisible = photoAdapter.isSelectionMode()
        shareMenuItem = menu.findItem(R.id.action_share)
        shareMenuItem?.isVisible = photoAdapter.isSelectionMode()
        editMenuItem = menu.findItem(R.id.action_edit)
        editMenuItem?.isVisible = photoAdapter.isSelectionMode()
        deleteMenuItem = menu.findItem(R.id.action_delete)
        deleteMenuItem?.isVisible = photoAdapter.isSelectionMode()
        favoriteMenuItem = menu.findItem(R.id.action_favorite)
        favoriteMenuItem?.isVisible = photoAdapter.isSelectionMode()
        favoriteMenuItem?.setIcon(if (isFavorites) R.drawable.ic_ior_star_solid else R.drawable.ic_ior_star)

        SearchMenuHelper.configureLiveSearch(
            context = this,
            searchItem = menu.findItem(R.id.action_search),
            hint = getString(R.string.search_photos),
            currentKeyword = { currentQuery.keyword },
            onKeywordChanged = { keyword ->
                currentQuery = currentQuery.copy(keyword = keyword)
                refreshMedia()
            }
        )

        val randomizePhotosItem = menu.findItem(R.id.action_randomize_photos)
        randomizePhotosItem?.isVisible = true
        randomizePhotosItem?.isChecked = currentQuery.randomizePhotos
        val rotationLockItem = menu.findItem(R.id.action_rotation_lock)
        rotationLockItem?.isChecked = settingsManager.isRotationLocked()
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

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home && photoAdapter.isSelectionMode()) {
            photoAdapter.setSelectionMode(false)
            isLongPressSelection = false
            updateSelectionTitle(-1)
            return true
        }
        return when (item.itemId) {
            R.id.action_rotation_lock -> {
                val newLockedState = !item.isChecked
                item.isChecked = newLockedState
                settingsManager.setRotationLocked(newLockedState)
                applyRotationLock()
                true
            }
            R.id.action_select -> {
                isLongPressSelection = false
                photoAdapter.setSelectionMode(true)
                updateSelectionTitle(0)
                true
            }
            R.id.action_delete -> {
                deleteSelectedPhotos()
                true
            }
            R.id.action_info -> {
                showSelectedPhotoDetails()
                true
            }
            R.id.action_share -> {
                shareSelectedPhotos()
                true
            }
            R.id.action_edit -> {
                editSelectedPhotos()
                true
            }
            R.id.action_favorite -> {
                updateSelectedFavorites()
                true
            }
            R.id.action_randomize_photos -> toggleRandomizePhotos(item)
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

    private fun applyRotationLock() {
        if (::settingsManager.isInitialized && settingsManager.isRotationLocked()) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyRotationLock()
    }

    private fun updateGridColumns(columns: Int): Boolean {
        val clampedColumns = columns.coerceIn(1, 4)
        settingsManager.setPhotoGridColumns(clampedColumns)
        UiState.setGridColumns(clampedColumns)
        applyPendingZoomNow()
        applyWaterfallColumns(clampedColumns)
        return true
    }

    private fun updateSortOrder(order: Int): Boolean {
        settingsManager.setPhotoSortOrder(order)
        refreshMedia(reshuffleClusters = order == SettingsManager.SORT_RANDOM_FOLDERS)
        return true
    }

    private fun toggleRandomizePhotos(item: android.view.MenuItem): Boolean {
        val enabled = !item.isChecked
        item.isChecked = enabled
        currentQuery = currentQuery.copy(randomizePhotos = enabled)
        refreshMedia(reshuffleClusters = true)
        return true
    }

    private fun updateSelectionTitle(count: Int) {
        if (count < 0) {
            infoMenuItem?.isVisible = false
            shareMenuItem?.isVisible = false
            editMenuItem?.isVisible = false
            deleteMenuItem?.isVisible = false
            favoriteMenuItem?.isVisible = false
            deleteMenuItem?.title = getString(R.string.delete)
            optionsMenu?.findItem(R.id.action_select)?.isVisible = true
            optionsMenu?.findItem(R.id.action_search)?.isVisible = true
            optionsMenu?.findItem(R.id.action_settings)?.isVisible = true
            optionsMenu?.findItem(R.id.action_grid_columns)?.isVisible = true
            optionsMenu?.findItem(R.id.action_randomize_photos)?.isVisible = true
            optionsMenu?.findItem(R.id.action_randomize_photos)?.isChecked = currentQuery.randomizePhotos
            optionsMenu?.findItem(R.id.action_sort_order)?.isVisible = true
            optionsMenu?.findItem(R.id.action_rotation_lock)?.isVisible = true
            val displayTitle = when {
                isFavorites -> getString(R.string.favorites)
                folderPath.isEmpty() && !isRemote -> getString(R.string.local_photos)
                folderPath.isEmpty() && isRemote -> getString(R.string.remote)
                else -> {
                    val lastSegment = folderPath.trimEnd('/').split('/').lastOrNull { it.isNotEmpty() } ?: folderPath
                    android.net.Uri.decode(lastSegment)
                }
            }
            supportActionBar?.title = if (isRecursive && !isFavorites) "$displayTitle ${getString(R.string.all_suffix)}" else displayTitle
        } else {
            infoMenuItem?.isVisible = true
            shareMenuItem?.isVisible = true
            editMenuItem?.isVisible = true
            deleteMenuItem?.isVisible = true
            favoriteMenuItem?.isVisible = true
            tintDeleteAction()
            optionsMenu?.findItem(R.id.action_select)?.isVisible = false
            optionsMenu?.findItem(R.id.action_search)?.isVisible = false
            optionsMenu?.findItem(R.id.action_settings)?.isVisible = false
            optionsMenu?.findItem(R.id.action_grid_columns)?.isVisible = false
            optionsMenu?.findItem(R.id.action_sort_order)?.isVisible = false
            optionsMenu?.findItem(R.id.action_randomize_photos)?.isVisible = false
            favoriteMenuItem?.setIcon(if (isFavorites) R.drawable.ic_ior_star_solid else R.drawable.ic_ior_star)
            favoriteMenuItem?.icon?.let { icon ->
                DrawableCompat.setTint(
                    icon,
                    ContextCompat.getColor(this, R.color.primary)
                )
            }
            supportActionBar?.title = if (count == 0) getString(R.string.select_items) else getString(R.string.selected_count, count)
        }
        optionsMenu?.let(::tintOverflowMenuIcons)
    }

    private fun updateSelectedFavorites() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isEmpty()) {
            android.widget.Toast.makeText(this, getString(R.string.favorite_requires_selection), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

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
                android.widget.Toast.makeText(
                    this,
                    resources.getQuantityString(R.plurals.favorite_added_count, plan.toAdd.size, plan.toAdd.size),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            plan.toRemove.isNotEmpty() -> {
                android.widget.Toast.makeText(
                    this,
                    resources.getQuantityString(R.plurals.favorite_removed_count, plan.toRemove.size, plan.toRemove.size),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            else -> {
                android.widget.Toast.makeText(this, getString(R.string.favorite_no_changes), android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        photoAdapter.setSelectionMode(false)
        isLongPressSelection = false
        updateSelectionTitle(-1)

        if (plan.toAdd.isNotEmpty() || plan.toRemove.isNotEmpty()) {
            refreshMedia()
        }
    }

    private fun tintDeleteAction() {
        val deleteColor = ContextCompat.getColor(this, R.color.primary_red)
        deleteMenuItem?.icon?.let { icon ->
            DrawableCompat.setTint(icon, deleteColor)
        }
        val deleteTitle = SpannableString(getString(R.string.delete)).apply {
            setSpan(ForegroundColorSpan(deleteColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        deleteMenuItem?.title = deleteTitle
    }

    private fun tintOverflowMenuIcons(menu: Menu) {
        val normalColor = if (isDarkModeEnabled()) {
            android.graphics.Color.WHITE
        } else {
            ContextCompat.getColor(this, R.color.onSurface)
        }
        val deleteColor = ContextCompat.getColor(this, R.color.primary_red)
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
            R.id.action_share,
            R.id.action_edit,
            R.id.action_favorite,
            R.id.action_info,
            R.id.action_select,
            R.id.action_settings,
            R.id.action_grid_columns,
            R.id.action_sort_order,
            R.id.action_randomize_photos,
            R.id.action_rotation_lock
        ).forEach { id ->
            menu.findItem(id)?.icon?.mutate()?.let { DrawableCompat.setTint(it, normalColor) }
        }

        submenuItems.forEach { id ->
            menu.findItem(id)?.icon?.mutate()?.let { DrawableCompat.setTint(it, normalColor) }
        }

        menu.findItem(R.id.action_delete)?.icon?.mutate()?.let { DrawableCompat.setTint(it, deleteColor) }
    }

    private fun isDarkModeEnabled(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    private fun shareSelectedPhotos() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isEmpty()) return

        lifecycleScope.launch {
            runCatching {
                val shareIntent = if (selectedPhotos.all { it.isLocal }) {
                    buildLocalShareIntent(selectedPhotos)
                } else {
                    val shareFiles = withContext(Dispatchers.IO) {
                        selectedPhotos.map { photo ->
                            if (photo.isLocal) photo.imageUri else downloadRemotePhotoForShare(photo)
                        }
                    }
                    buildFileShareIntent(selectedPhotos, shareFiles)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            }.onFailure { error ->
                android.util.Log.e("MainActivity", "Share failed", error)
                android.widget.Toast.makeText(this@MainActivity, getString(R.string.download_failed), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun editSelectedPhotos() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isEmpty()) return
        EditDialogHelper.show(
            activity = this,
            selectedPhotos = selectedPhotos,
            settingsManager = settingsManager,
            onSubmitted = {
                photoAdapter.setSelectionMode(false)
                isLongPressSelection = false
                updateSelectionTitle(-1)
            }
        )
    }

    private fun buildLocalShareIntent(selectedPhotos: List<Photo>): Intent {
        return buildFileShareIntent(selectedPhotos, selectedPhotos.map { it.imageUri })
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

    private fun showSelectedPhotoDetails() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isEmpty()) return

        if (selectedPhotos.size == 1) {
            showPhotoDetailsDialog(selectedPhotos.first())
            return
        }

        val previewPhotos = selectedPhotos.take(10)
        val message = buildString {
            previewPhotos.forEachIndexed { index, photo ->
                if (index > 0) append("\n\n")
                append(formatPhotoDetails(photo))
            }
            if (selectedPhotos.size > 10) {
                append("\n\n")
                append(getString(R.string.photo_details_more_ellipsis))
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.photo_details_selected_title, selectedPhotos.size))
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showPhotoDetailsDialog(photo: Photo) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.photo_details)
            .setMessage(formatPhotoDetails(photo))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun formatPhotoDetails(photo: Photo): String {
        return getString(R.string.file_name_prefix, photo.title) + "\n" +
            getString(R.string.file_size_prefix, android.text.format.Formatter.formatFileSize(this, photo.size)) + "\n" +
            getString(R.string.file_dimension_prefix, photo.width, photo.height) + "\n" +
            getString(R.string.local_prefix, photo.isLocal)
    }

    private fun deleteSelectedPhotos() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isEmpty()) return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_photos)
            .setMessage(getString(R.string.delete_photos_message, selectedPhotos.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                val sourcePhotos = MediaManager.mediaViewModel?.state?.value?.photos.orEmpty()
                if (!isRemote && selectedPhotos.all { it.isLocal } && LocalMediaDeleteRequest.requiresSystemRequest()) {
                    val request = runCatching {
                        LocalMediaDeleteRequest.create(this@MainActivity, selectedPhotos)
                    }.getOrNull()

                    if (request == null) {
                        android.widget.Toast.makeText(this@MainActivity, getString(R.string.delete_failed), android.widget.Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    pendingLocalMediaDelete = PendingLocalMediaDelete(
                        photos = selectedPhotos,
                        sourcePhotos = sourcePhotos
                    )
                    localMediaDeleteLauncher.launch(request)
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val repository: PhotoRepository = if (isRemote) {
                        RustWebDavPhotoRepository(settingsManager)
                    } else {
                        LocalPhotoRepository(this@MainActivity)
                    }

                    val deletedPhotos = withContext(Dispatchers.IO) {
                        val deleted = mutableListOf<Photo>()
                        selectedPhotos.forEach { photo ->
                            if (repository.deletePhoto(photo)) {
                                deleted.add(photo)
                            }
                        }
                        if (deleted.isNotEmpty()) {
                            WebDavImageLoader.clearCache(this@MainActivity)
                        }
                        deleted
                    }

                    handleDeletedPhotos(deletedPhotos, sourcePhotos)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleDeletedPhotos(deletedPhotos: List<Photo>, sourcePhotos: List<Photo>) {
        if (deletedPhotos.isEmpty()) {
            android.widget.Toast.makeText(this, getString(R.string.delete_failed), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        android.widget.Toast.makeText(this, getString(R.string.deleted_count, deletedPhotos.size), android.widget.Toast.LENGTH_SHORT).show()
        pendingDeleteScrollAnchor = DeleteScrollAnchorHelper.forDeletedPhotos(
            deletedPhotos = deletedPhotos,
            sourcePhotos = sourcePhotos,
            recyclerView = binding.recyclerView
        )
        photoAdapter.setSelectionMode(false)
        isLongPressSelection = false
        updateSelectionTitle(-1)
        MediaManager.removePhotosFromCaches(deletedPhotos)
        MediaManager.mediaViewModel?.removePhotos(deletedPhotos)
        MediaManager.mediaViewModel?.state?.value?.let(MediaStateCache::setState)
    }
}

object PhotoCache {
    private val photos = java.util.concurrent.CopyOnWriteArrayList<Photo>()

    fun setPhotos(list: List<Photo>) {
        photos.clear()
        photos.addAll(list)
    }

    fun getPhotos(): List<Photo> = photos.toList()

    fun clear() {
        photos.clear()
    }
}
object MediaStateCache {
    private val lock = Any()
    private var state: MediaUiState? = null

    fun setState(value: MediaUiState) {
        synchronized(lock) {
            state = value.copy(isLoading = false, error = null)
        }
    }

    fun getState(): MediaUiState? = synchronized(lock) { state }

    fun clear() {
        synchronized(lock) { state = null }
    }
}
