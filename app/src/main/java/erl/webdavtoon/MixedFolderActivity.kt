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
import androidx.activity.addCallback
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var isFavorites: Boolean = false
    private var folderShuffleSeed: Long = Random.nextLong()
    private var pendingDeleteRequest: PendingMixedDelete? = null
    private var hasCompletedInitialLoad = false
    private val webDavSlotMutex = Mutex()
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

    private data class PendingMixedDelete(
        val localPhotos: List<Photo>,
        val otherPhotos: List<Photo>,
        val folders: List<Folder>
    )

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

    private val localMediaDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pending = pendingDeleteRequest ?: return@registerForActivityResult
        pendingDeleteRequest = null
        if (result.resultCode != RESULT_OK) return@registerForActivityResult

        lifecycleScope.launch {
            val deletedLocalPhotos = LocalMediaDeleteRequest.awaitDeletedPhotos(
                context = this@MixedFolderActivity,
                photos = pending.localPhotos
            )
            executeDelete(pending, deletedLocalPhotos)
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
        isFavorites = intent.getBooleanExtra("EXTRA_IS_FAVORITES", false)

        LibraryState.update(
            serverType = if (isFavorites) "favorites" else if (isWebDav) "webdav" else "local",
            rootFolderPath = if (isFavorites) "favorites" else folderPath
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

    override fun onResume() {
        super.onResume()
        if (isFavorites && hasCompletedInitialLoad && !adapter.isSelectionMode) {
            loadMixedContent()
        }
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
            onSelectionChanged = ::updateSelectionUi,
            shouldSuppressItemInteraction = ::shouldSuppressItemInteraction,
            onPhotoDimensionsResolved = ::onPhotoDimensionsResolved,
            onRemotePreviewNeeded = ::resolveRemotePreview,
            remotePreviewGeneration = { settingsManager.getSortOrder().toString() }
        )
        adapter.setShowFilenames(settingsManager.shouldShowWaterfallFilenames())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
        installWaterfallLayout()

        onBackPressedDispatcher.addCallback(this) {
            if (adapter.isSelectionMode) {
                adapter.exitSelectionMode()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

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
                    if (isFavorites) {
                        val sortedMedia = FolderPreviewOrdering.sortPhotos(
                            settingsManager.getFavoritePhotos(),
                            settingsManager.getPhotoSortOrder()
                        )
                        return@withContext MixedWaterfallPlanner.buildItems(
                            folders = settingsManager.getFavoriteFolders(),
                            media = sortedMedia,
                            folderSortOrder = settingsManager.getSortOrder(),
                            folderShuffleSeed = folderShuffleSeed
                        )
                    }

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

                    val sourceSlot = if (isWebDav) settingsManager.getCurrentSlot() else -1
                    val sourcedFolders = resolvedFolders.map { folder ->
                        val resolvedSlot = if (folder.isLocal) -1 else sourceSlot
                        if (folder.sourceSlot == resolvedSlot) folder else folder.copy(sourceSlot = resolvedSlot)
                    }

                    MixedWaterfallPlanner.buildItems(
                        folders = sourcedFolders,
                        media = sortedMedia,
                        folderSortOrder = settingsManager.getSortOrder(),
                        folderShuffleSeed = folderShuffleSeed
                    )
                }

                adapter.setItems(items)
                if (forceRefresh && isWebDav && !isFavorites) {
                    adapter.refreshVisibleRemotePreviews()
                }
                waterfallLayoutManager?.notifyAspectRatiosChanged()
                binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyView.text = getString(R.string.no_photos_found)
                android.util.Log.i(
                    "MixedFolderActivity",
                    "loadMixedContent path=$folderPath isWebDav=$isWebDav isFavorites=$isFavorites forceRefresh=$forceRefresh items=${items.size} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
                )
            } catch (e: Exception) {
                android.util.Log.e("MixedFolderActivity", "Mixed content load failed", e)
                binding.emptyView.visibility = View.VISIBLE
                binding.emptyView.text = getString(R.string.error_prefix, e.message ?: e.toString())
            } finally {
                hasCompletedInitialLoad = true
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
        if (!isFavorites && !folder.hasSubFolders) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", realPath)
                putExtra("EXTRA_IS_WEBDAV", isRemoteFolder)
                putExtra("EXTRA_RECURSIVE", false)
            })
            return
        }

        lifecycleScope.launch {
            try {
                withFolderSourceSlot(folder, restoreAfter = false) {
                    val target = FolderNavigationResolver.resolve(
                        context = this@MixedFolderActivity,
                        settingsManager = settingsManager,
                        folderPath = realPath,
                        isWebDav = isRemoteFolder
                    )
                    android.util.Log.i(
                        "MixedFolderActivity",
                        "resolvedFolderNavigation path=$realPath slot=${folder.sourceSlot} target=${target.javaClass.simpleName}"
                    )
                    FolderNavigationResolver.start(this@MixedFolderActivity, target)
                }
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
            putExtra("EXTRA_IS_FAVORITES", isFavorites)
        })
    }

    private fun resolveRemotePreview(folder: Folder, forceRefresh: Boolean = false) {
        if (folder.isLocal || folder.path.startsWith("virtual://")) return

        lifecycleScope.launch {
            val sortOrder = settingsManager.getSortOrder()
            val preview = withFolderSourceSlot(folder, restoreAfter = true) {
                RustWebDavPhotoRepository(settingsManager).inspectFolder(
                    folderPath = folder.path,
                    sortOrder = sortOrder,
                    forceRefresh = forceRefresh
                )
            } ?: return@launch
            if (settingsManager.getSortOrder() != sortOrder) return@launch
            adapter.updateFolderPreview(folder, preview.previewUris, preview.hasSubFolders)
            val updatedFolder = folder.copy(
                previewUris = preview.previewUris,
                hasSubFolders = folder.hasSubFolders || preview.hasSubFolders
            )
            if (settingsManager.isFolderFavorite(updatedFolder)) {
                settingsManager.addFavoriteFolder(updatedFolder)
            }
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
            isFavorites -> getString(R.string.favorites)
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
        if (isFavorites || isWebDav) {
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
        menu.findItem(R.id.action_rotation_lock)?.isChecked = settingsManager.isRotationLocked()
        updateMenuVisibility(menu)
        tintOverflowMenuIcons(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        OverflowMenuHelper.enableOptionalIcons(menu)
        updateMenuVisibility(menu)
        tintOverflowMenuIcons(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        OverflowMenuHelper.enableOptionalIcons(menu)
        tintOverflowMenuIcons(menu)
        return super.onMenuOpened(featureId, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home && adapter.isSelectionMode) {
            adapter.exitSelectionMode()
            return true
        }
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
            R.id.action_delete -> {
                confirmDeleteSelectedItems()
                true
            }
            R.id.action_favorite -> {
                updateSelectedFavorites()
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

    private fun updateSelectionUi(count: Int) {
        supportActionBar?.title = if (count > 0) {
            getString(R.string.selected_count, count)
        } else {
            displayTitle()
        }
        invalidateOptionsMenu()
    }

    private fun updateMenuVisibility(menu: Menu) {
        val isSelecting = ::adapter.isInitialized && adapter.isSelectionMode
        menu.findItem(R.id.action_share)?.isVisible = false
        menu.findItem(R.id.action_edit)?.isVisible = false
        menu.findItem(R.id.action_info)?.isVisible = false
        menu.findItem(R.id.action_search)?.isVisible = false
        menu.findItem(R.id.action_select)?.isVisible = false
        menu.findItem(R.id.action_randomize_photos)?.isVisible = false
        menu.findItem(R.id.action_favorite)?.apply {
            isVisible = isSelecting
            setIcon(if (isFavorites) R.drawable.ic_ior_star_solid else R.drawable.ic_ior_star)
        }
        menu.findItem(R.id.action_delete)?.isVisible = isSelecting
        menu.findItem(R.id.action_settings)?.isVisible = !isSelecting
        menu.findItem(R.id.action_grid_columns)?.isVisible = !isSelecting
        menu.findItem(R.id.action_sort_order)?.isVisible = !isSelecting
        menu.findItem(R.id.action_rotation_lock)?.isVisible = !isSelecting
    }

    private fun updateSelectedFavorites() {
        val selectedItems = adapter.getSelectedFolders().map { MixedWaterfallItem.FolderTile(it) } +
            adapter.getSelectedPhotos().map { MixedWaterfallItem.MediaTile(it) }
        if (selectedItems.isEmpty()) {
            android.widget.Toast.makeText(
                this,
                getString(R.string.favorite_items_requires_selection),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val favoriteKeys = selectedItems
            .filter { item ->
                when (item) {
                    is MixedWaterfallItem.FolderTile -> settingsManager.isFolderFavorite(item.folder)
                    is MixedWaterfallItem.MediaTile -> settingsManager.isPhotoFavorite(item.photo.id)
                }
            }
            .mapTo(mutableSetOf(), MixedWaterfallIdentity::key)
        val plan = FavoriteSelectionPlanner.buildPlan(
            selectedItems = selectedItems,
            favoriteIds = favoriteKeys,
            isFavoritesView = isFavorites,
            idSelector = MixedWaterfallIdentity::key
        )

        plan.toAdd.forEach { item ->
            when (item) {
                is MixedWaterfallItem.FolderTile -> settingsManager.addFavoriteFolder(item.folder)
                is MixedWaterfallItem.MediaTile -> settingsManager.addFavoritePhoto(item.photo)
            }
        }
        plan.toRemove.forEach { item ->
            when (item) {
                is MixedWaterfallItem.FolderTile -> settingsManager.removeFavoriteFolder(item.folder)
                is MixedWaterfallItem.MediaTile -> settingsManager.removeFavoritePhoto(item.photo.id)
            }
        }

        val changedCount = plan.toAdd.size + plan.toRemove.size
        val message = when {
            plan.toAdd.isNotEmpty() -> resources.getQuantityString(
                R.plurals.favorite_items_added_count,
                changedCount,
                changedCount
            )
            plan.toRemove.isNotEmpty() -> resources.getQuantityString(
                R.plurals.favorite_items_removed_count,
                changedCount,
                changedCount
            )
            else -> getString(R.string.favorite_no_changes)
        }
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()

        adapter.exitSelectionMode()
        if (isFavorites && changedCount > 0) {
            loadMixedContent()
        }
    }

    private fun confirmDeleteSelectedItems() {
        val selectedPhotos = adapter.getSelectedPhotos()
        val selectedFolders = adapter.getSelectedFolders()
        val selectedCount = selectedPhotos.size + selectedFolders.size
        if (selectedCount == 0) return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(getString(R.string.delete_items_message, selectedCount))
            .setPositiveButton(R.string.delete) { _, _ ->
                beginDelete(selectedPhotos, selectedFolders)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun beginDelete(selectedPhotos: List<Photo>, selectedFolders: List<Folder>) {
        val localPhotos = selectedPhotos.filter { it.isLocal }
        if (localPhotos.isNotEmpty() && LocalMediaDeleteRequest.requiresSystemRequest()) {
            val request = runCatching {
                LocalMediaDeleteRequest.create(this, localPhotos)
            }.getOrNull()
            if (request == null) {
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.delete_failed),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return
            }

            pendingDeleteRequest = PendingMixedDelete(
                localPhotos = localPhotos,
                otherPhotos = selectedPhotos.filterNot { it.isLocal },
                folders = selectedFolders
            )
            localMediaDeleteLauncher.launch(request)
            return
        }

        lifecycleScope.launch {
            executeDelete(
                PendingMixedDelete(
                    localPhotos = emptyList(),
                    otherPhotos = selectedPhotos,
                    folders = selectedFolders
                )
            )
        }
    }

    private suspend fun executeDelete(
        pending: PendingMixedDelete,
        alreadyDeletedLocalPhotos: List<Photo> = emptyList()
    ) {
        val deletedPhotos = alreadyDeletedLocalPhotos.toMutableList()
        val deletedFolders = mutableListOf<Folder>()

        pending.otherPhotos.forEach { photo ->
            val repository: PhotoRepository = if (photo.isLocal) {
                LocalPhotoRepository(this@MixedFolderActivity)
            } else {
                RustWebDavPhotoRepository(settingsManager)
            }
            if (repository.deletePhoto(photo)) {
                deletedPhotos.add(photo)
            }
        }

        pending.folders.forEach { folder ->
            val deleted = if (folder.isLocal) {
                LocalPhotoRepository(this@MixedFolderActivity).deleteFolder(folder)
            } else {
                withFolderSourceSlot(folder, restoreAfter = true) {
                    RustWebDavPhotoRepository(settingsManager).deleteFolder(folder)
                }
            }
            if (deleted) {
                deletedFolders.add(folder)
            }
        }

        deletedPhotos.forEach { settingsManager.removeFavoritePhoto(it.id) }
        deletedFolders.forEach(settingsManager::removeFavoriteFolder)

        val requestedCount = pending.localPhotos.size + pending.otherPhotos.size + pending.folders.size
        val deletedCount = deletedPhotos.size + deletedFolders.size
        if (deletedCount == 0) {
            android.widget.Toast.makeText(
                this,
                getString(R.string.delete_failed),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (deletedPhotos.any { !it.isLocal } || deletedFolders.any { !it.isLocal }) {
            WebDavImageLoader.clearCache(this)
        } else {
            com.bumptech.glide.Glide.get(this).clearMemory()
        }

        val message = if (deletedCount == requestedCount) {
            getString(R.string.deleted_items_count, deletedCount)
        } else {
            getString(R.string.deleted_items_partial, deletedCount, requestedCount)
        }
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        adapter.exitSelectionMode()
        loadMixedContent(forceRefresh = !isFavorites)
    }

    private suspend fun <T> withFolderSourceSlot(
        folder: Folder,
        restoreAfter: Boolean,
        block: suspend () -> T
    ): T {
        if (folder.isLocal || folder.sourceSlot < 0) {
            return block()
        }

        return webDavSlotMutex.withLock {
            val originalSlot = settingsManager.getCurrentSlot()
            if (originalSlot != folder.sourceSlot) {
                settingsManager.setCurrentSlot(folder.sourceSlot)
            }
            try {
                block()
            } finally {
                if (restoreAfter && originalSlot != folder.sourceSlot) {
                    settingsManager.setCurrentSlot(originalSlot)
                }
            }
        }
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
        if (::adapter.isInitialized && adapter.isSelectionMode) {
            menu.findItem(R.id.action_delete)?.icon?.mutate()?.let { icon ->
                DrawableCompat.setTint(icon, ContextCompat.getColor(this, R.color.primary_red))
            }
            menu.findItem(R.id.action_favorite)?.icon?.mutate()?.let { icon ->
                DrawableCompat.setTint(icon, ContextCompat.getColor(this, R.color.primary))
            }
        }
    }

    private fun isDarkModeEnabled(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}
