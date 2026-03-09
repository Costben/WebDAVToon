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
    private var pinchZoomHelper: PinchZoomHelper? = null

    private var currentOffset = 0
    private val pageSize = 120
    private var isPageLoading = false
    private var currentQuery = MediaQuery()
    private var deleteMenuItem: android.view.MenuItem? = null

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
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
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

        pinchZoomHelper = PinchZoomHelper(binding.recyclerView, binding.zoomContainer) { newSpanCount ->
            settingsManager.setPhotoGridColumns(newSpanCount)
            UiState.setGridColumns(newSpanCount)
        }

        photoAdapter = PhotoAdapter(
            onPhotoClick = { photos, position ->
                if (photoAdapter.isSelectionMode()) {
                    photoAdapter.toggleSelection(position)
                } else {
                    PhotoCache.setPhotos(photos)
                    val intent = Intent(this, PhotoViewActivity::class.java).apply {
                        putExtra("EXTRA_CURRENT_INDEX", position)
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
            MediaState.state.collect { state ->
                if (state.sessionKey != buildSessionKey()) return@collect
                binding.progressBar.visibility = if (state.isLoading && state.photos.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyView.visibility = if (!state.isLoading && state.photos.isEmpty()) View.VISIBLE else View.GONE
                if (state.error != null && state.photos.isEmpty()) {
                    binding.emptyView.text = getString(R.string.error_prefix, state.error)
                } else if (state.photos.isEmpty()) {
                    binding.emptyView.text = getString(R.string.no_photos_found)
                }
                val sortedPhotos = if (isRecursive) {
                    sortPhotosByFolder(state.photos, settingsManager.getPhotoSortOrder())
                } else {
                    state.photos
                }
                photoAdapter.setPhotos(sortedPhotos)
            }
        }
    }

    private fun sortPhotosByFolder(photos: List<Photo>, sortOrder: Int): List<Photo> {
        val grouped = photos.groupBy { it.folderPath }
        
        // Sort folder paths based on the requested order
        val sortedFolderPaths = when (sortOrder) {
            SettingsManager.SORT_NAME_ASC -> grouped.keys.sortedBy { it }
            SettingsManager.SORT_NAME_DESC -> grouped.keys.sortedByDescending { it }
            // For date, we need to pick a representative date for each folder
            // SORT_DATE_DESC: newest photo in folder
            SettingsManager.SORT_DATE_DESC -> grouped.keys.sortedByDescending { path ->
                grouped[path]?.maxOfOrNull { it.dateModified } ?: 0L
            }
            // SORT_DATE_ASC: oldest photo in folder
            SettingsManager.SORT_DATE_ASC -> grouped.keys.sortedBy { path ->
                grouped[path]?.minOfOrNull { it.dateModified } ?: 0L
            }
            else -> grouped.keys.sortedByDescending { path ->
                grouped[path]?.maxOfOrNull { it.dateModified } ?: 0L
            }
        }

        val result = mutableListOf<Photo>()
        for (path in sortedFolderPaths) {
            val folderPhotos = grouped[path] ?: continue
            // Sort photos within each folder as well
            val sortedPhotos = when (sortOrder) {
                SettingsManager.SORT_NAME_ASC -> folderPhotos.sortedBy { it.title }
                SettingsManager.SORT_NAME_DESC -> folderPhotos.sortedByDescending { it.title }
                SettingsManager.SORT_DATE_DESC -> folderPhotos.sortedByDescending { it.dateModified }
                SettingsManager.SORT_DATE_ASC -> folderPhotos.sortedBy { it.dateModified }
                else -> folderPhotos.sortedByDescending { it.dateModified }
            }
            result.addAll(sortedPhotos)
        }
        return result
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) {
            pinchZoomHelper?.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun refreshMedia() {
        currentOffset = 0
        MediaState.start(buildSessionKey())
        loadPage(append = false, forceRefresh = true)
    }

    private fun loadNextPage() {
        val state = MediaState.state.value
        if (state.sessionKey != buildSessionKey()) return
        if (isPageLoading || !state.hasMore) return
        MediaState.startAppend()
        loadPage(append = true, forceRefresh = false)
    }

    private fun loadPage(append: Boolean, forceRefresh: Boolean = false) {
        if (isPageLoading) return
        isPageLoading = true

        val sessionKey = buildSessionKey()
        lifecycleScope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    if (isFavorites) {
                        val allFavorites = settingsManager.getFavoritePhotos()
                        val keyword = currentQuery.keyword.trim()
                        val filtered = if (keyword.isNotEmpty()) {
                            allFavorites.filter { it.title.contains(keyword, ignoreCase = true) }
                        } else {
                            allFavorites
                        }
                        
                        val safeOffset = currentOffset.coerceAtLeast(0)
                        val items = filtered.drop(safeOffset).take(pageSize)
                        val next = safeOffset + items.size
                        MediaPageResult(items = items, hasMore = next < filtered.size, nextOffset = next)
                    } else if (isRemote) {
                        RustWebDavPhotoRepository(settingsManager)
                            .queryMediaPage(folderPath, isRecursive, currentQuery, currentOffset, pageSize, forceRefresh)
                    } else {
                        LocalPhotoRepository(this@MainActivity)
                            .queryMediaPage(folderPath, isRecursive, currentQuery, currentOffset, pageSize, forceRefresh)
                    }
                }

                currentOffset = page.nextOffset
                MediaState.setPage(sessionKey, page.items, page.hasMore, append)
            } catch (e: Exception) {
                val errorMessage = e.message ?: e.toString()
                android.util.Log.e("MainActivity", "Load failed: $errorMessage", e)
                MediaState.setError(sessionKey, errorMessage)
            } finally {
                isPageLoading = false
            }
        }
    }

    private fun buildSessionKey(): String {
        return "$folderPath|$isRemote|$isRecursive|$isFavorites|${currentQuery.keyword}"
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        deleteMenuItem = menu.findItem(R.id.action_delete)
        deleteMenuItem?.isVisible = photoAdapter.isSelectionMode()

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
            R.id.action_settings -> {
                settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_col_1 -> updateGridColumns(1)
            R.id.action_col_2 -> updateGridColumns(2)
            R.id.action_col_3 -> updateGridColumns(3)
            R.id.action_col_4 -> updateGridColumns(4)
            R.id.action_sort_name_asc -> updateSortOrder(0)
            R.id.action_sort_name_desc -> updateSortOrder(1)
            R.id.action_sort_date_desc -> updateSortOrder(2)
            R.id.action_sort_date_asc -> updateSortOrder(3)
            else -> super.onOptionsItemSelected(item)
        }
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
            // Ensure delete icon is red
            deleteMenuItem?.icon?.let { icon ->
                androidx.core.graphics.drawable.DrawableCompat.setTint(icon, android.graphics.Color.RED)
            }
            supportActionBar?.title = if (count == 0) getString(R.string.select_items) else getString(R.string.selected_count, count)
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
                                WebDavImageLoader.clearCache(this@MainActivity, photo)
                                count++
                            }
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
    private var photos: List<Photo> = emptyList()

    fun setPhotos(list: List<Photo>) {
        photos = list
    }

    fun getPhotos(): List<Photo> = photos

    fun clear() {
        photos = emptyList()
    }
}

