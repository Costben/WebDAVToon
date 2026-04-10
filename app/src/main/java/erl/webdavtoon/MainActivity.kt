package erl.webdavtoon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import erl.webdavtoon.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var photoAdapter: PhotoAdapter
    private var isLongPressSelection = false

    fun setLongPressSelection(value: Boolean) {
        isLongPressSelection = value
    }

    private var folderPath: String = ""
    private var isRemote: Boolean = false
    private var isRecursive: Boolean = false
    private var isFavorites: Boolean = false
    private var scaleDetector: android.view.ScaleGestureDetector? = null

    private var currentQuery = MediaQuery()
    private var deleteMenuItem: android.view.MenuItem? = null
    private var favoriteMenuItem: android.view.MenuItem? = null

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val columns = settingsManager.getPhotoGridColumns()
            UiState.setGridColumns(columns)
            (binding.recyclerView.layoutManager as? StaggeredGridLayoutManager)?.spanCount = columns
            refreshMedia()
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

        if (isRemote || hasStoragePermission()) {
            refreshMedia()
        }
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

        val columns = settingsManager.getPhotoGridColumns()
        binding.recyclerView.layoutManager = StaggeredGridLayoutManager(columns, StaggeredGridLayoutManager.VERTICAL)
        binding.recyclerView.setItemViewCacheSize(30)
        binding.recyclerView.setHasFixedSize(true)

        // 使用简单的缩放手势处理器，仅用于改变列数，不使用叠加层
        scaleDetector = android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var lastScaleTime = 0L
            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastScaleTime < 300) return false // 防止缩放太快导致卡顿

                val scaleFactor = detector.scaleFactor
                val currentColumns = (binding.recyclerView.layoutManager as? StaggeredGridLayoutManager)?.spanCount ?: 1
                
                if (scaleFactor > 1.2f && currentColumns > 1) {
                    // 放大手势 -> 减少列数
                    updateGridColumns(currentColumns - 1)
                    lastScaleTime = currentTime
                    return true
                } else if (scaleFactor < 0.8f && currentColumns < 5) {
                    // 缩小手势 -> 增加列数
                    updateGridColumns(currentColumns + 1)
                    lastScaleTime = currentTime
                    return true
                }
                return false
            }
        })

        photoAdapter = PhotoAdapter(
            onPhotoClick = onPhotoClick@{ photos, position ->
                if (photoAdapter.isSelectionMode()) {
                    photoAdapter.toggleSelection(position)
                } else {
                    val clicked = photos.getOrNull(position) ?: return@onPhotoClick
                    if (clicked.mediaType == MediaType.VIDEO) {
                        if (isAviVideo(clicked.title) || isAviVideo(clicked.imageUri.toString())) {
                            ExternalVideoOpener.open(this, clicked.imageUri.toString(), clicked.title, !clicked.isLocal, settingsManager)
                        } else {
                            val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                                putExtra("EXTRA_MEDIA_URI", clicked.imageUri.toString())
                                putExtra("EXTRA_MEDIA_TITLE", clicked.title)
                                putExtra("EXTRA_IS_REMOTE", !clicked.isLocal)
                                putExtra("EXTRA_IS_FAVORITES", isFavorites)
                            }
                            startActivity(intent)
                        }
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
            }
        )
        binding.recyclerView.adapter = photoAdapter

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layout = recyclerView.layoutManager as? StaggeredGridLayoutManager ?: return
                val last = layout.findLastVisibleItemPositions(null).maxOrNull() ?: return
                val total = photoAdapter.itemCount
                if (total == 0) return
                if (last >= total - 24) {
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
                val sortedPhotos = MediaManager.sortPhotos(state.photos, settingsManager.getPhotoSortOrder(), isRecursive)
                photoAdapter.setPhotos(sortedPhotos)
            }
        }
    }


    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) {
            scaleDetector?.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun refreshMedia() {
        MediaManager.refresh(
            this, lifecycleScope, buildSessionKey(),
            folderPath, isRemote, isRecursive, isFavorites, currentQuery
        )
    }

    private fun loadNextPage() {
        MediaManager.loadNextPage(this, lifecycleScope)
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

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        deleteMenuItem = menu.findItem(R.id.action_delete)
        deleteMenuItem?.isVisible = photoAdapter.isSelectionMode()
        favoriteMenuItem = menu.findItem(R.id.action_favorite)
        favoriteMenuItem?.isVisible = photoAdapter.isSelectionMode()
        favoriteMenuItem?.setIcon(if (isFavorites) R.drawable.ic_star_filled_md3 else R.drawable.ic_star_outlined)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.queryHint = getString(R.string.search_photos)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentQuery = currentQuery.copy(keyword = query.orEmpty().trim())
                refreshMedia()
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val keyword = newText.orEmpty().trim()
                if (keyword.isEmpty() && currentQuery.keyword.isNotEmpty()) {
                    currentQuery = currentQuery.copy(keyword = "")
                    refreshMedia()
                }
                return true
            }
        })

        val rotationLockItem = menu.findItem(R.id.action_rotation_lock)
        rotationLockItem?.isChecked = settingsManager.isRotationLocked()

        return true
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
            R.id.action_favorite -> {
                updateSelectedFavorites()
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
        settingsManager.setPhotoGridColumns(columns)
        UiState.setGridColumns(columns)
        (binding.recyclerView.layoutManager as? StaggeredGridLayoutManager)?.spanCount = columns
        return true
    }

    private fun updateSortOrder(order: Int): Boolean {
        settingsManager.setPhotoSortOrder(order)
        refreshMedia()
        return true
    }

    private fun updateSelectionTitle(count: Int) {
        if (count < 0) {
            deleteMenuItem?.isVisible = false
            favoriteMenuItem?.isVisible = false
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
            deleteMenuItem?.isVisible = true
            favoriteMenuItem?.isVisible = true
            // Ensure delete icon is red
            deleteMenuItem?.icon?.let { icon ->
                androidx.core.graphics.drawable.DrawableCompat.setTint(icon, android.graphics.Color.RED)
            }
            favoriteMenuItem?.setIcon(if (isFavorites) R.drawable.ic_star_filled_md3 else R.drawable.ic_star_outlined)
            favoriteMenuItem?.icon?.let { icon ->
                androidx.core.graphics.drawable.DrawableCompat.setTint(
                    icon,
                    ContextCompat.getColor(this, R.color.primary)
                )
            }
            supportActionBar?.title = if (count == 0) getString(R.string.select_items) else getString(R.string.selected_count, count)
        }
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

    private fun deleteSelectedPhotos() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isEmpty()) return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_photos)
            .setMessage(getString(R.string.delete_photos_message, selectedPhotos.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    val repository: PhotoRepository = if (isRemote) {
                        RustWebDavPhotoRepository(settingsManager)
                    } else {
                        LocalPhotoRepository(this@MainActivity)
                    }

                    val successCount = withContext(Dispatchers.IO) {
                        var count = 0
                        selectedPhotos.forEach { photo ->
                            if (repository.deletePhoto(photo)) {
                                count++
                            }
                        }
                        if (count > 0) {
                            WebDavImageLoader.clearCache(this@MainActivity)
                        }
                        count
                    }

                    if (successCount > 0) {
                        android.widget.Toast.makeText(this@MainActivity, getString(R.string.deleted_count, successCount), android.widget.Toast.LENGTH_SHORT).show()
                        photoAdapter.setSelectionMode(false)
                        isLongPressSelection = false
                        updateSelectionTitle(-1)
                        refreshMedia()
                    } else {
                        android.widget.Toast.makeText(this@MainActivity, getString(R.string.delete_failed), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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

