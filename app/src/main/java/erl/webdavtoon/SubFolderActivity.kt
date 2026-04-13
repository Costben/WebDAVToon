package erl.webdavtoon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.content.res.Configuration
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import erl.webdavtoon.databinding.ActivityFolderViewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubFolderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderViewBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var adapter: FolderAdapter
    private var folderPath: String = ""
    private var isWebDav: Boolean = false
    private var currentAllFolders: List<Folder> = emptyList()
    private var currentSearchKeyword: String = ""
    private var currentLoadUsesToolbarPill: Boolean = false
    private var toolbarRefreshHideJob: Job? = null

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadFolders()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (!allGranted) {
            android.widget.Toast.makeText(this, getString(R.string.storage_permission_required), android.widget.Toast.LENGTH_LONG).show()
        }
        loadFolders()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        settingsManager = SettingsManager(this)
        LogManager.initialize(this)
        applyRotationLock()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityFolderViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        isWebDav = intent.getBooleanExtra("EXTRA_IS_WEBDAV", false)

        setupUI()
        checkPermissionsAndLoad()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val displayTitle = when {
            folderPath.isEmpty() && !isWebDav -> getString(R.string.local_photos)
            folderPath.isEmpty() && isWebDav -> getString(R.string.remote)
            else -> {
                val lastSegment = folderPath.trimEnd('/').split('/').lastOrNull { it.isNotEmpty() } ?: folderPath
                android.net.Uri.decode(lastSegment)
            }
        }
        val originalTitle = displayTitle
        supportActionBar?.title = displayTitle
        binding.toolbar.setNavigationOnClickListener { 
            if (adapter.isSelectionMode) {
                adapter.exitSelectionMode()
            } else {
                onBackPressedDispatcher.onBackPressed() 
            }
        }

        adapter = FolderAdapter(
            onFolderClick = { folder ->
                onFolderClick(folder)
            },
            onSelectionChanged = { count ->
                if (count > 0) {
                    supportActionBar?.title = getString(R.string.selected_count, count)
                } else {
                    supportActionBar?.title = originalTitle
                }
                invalidateOptionsMenu()
            },
            onRemotePreviewNeeded = { folder ->
                resolveRemotePreview(folder)
            }
        )

        binding.recyclerView.layoutManager = GridLayoutManager(this, settingsManager.getGridColumns()).apply {
            isItemPrefetchEnabled = false
        }
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.itemAnimator = null
        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadFolders(forceRefresh = true)
        }

        binding.settingsFab.visibility = View.VISIBLE
        binding.settingsFab.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", folderPath)
                putExtra("EXTRA_IS_WEBDAV", isWebDav)
                putExtra("EXTRA_RECURSIVE", true)
            }
            startActivity(intent)
        }

        DrawerHelper.setupDrawer(
            this,
            binding.drawerLayout,
            binding.toolbar,
            binding.drawerContent.root,
            settingsLauncher
        )
    }

    private fun onFolderClick(folder: Folder) {
        if (folder.path == "virtual://local_all") {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", "")
                putExtra("EXTRA_IS_WEBDAV", false)
                putExtra("EXTRA_RECURSIVE", true)
            }
            startActivity(intent)
            return
        }

        val isInternalPhotos = folder.path.startsWith("virtual://internal_photos")
        val realPath = if (isInternalPhotos) folder.path.substringAfter("path=") else folder.path

        if (isInternalPhotos || !folder.hasSubFolders) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", realPath)
                putExtra("EXTRA_IS_WEBDAV", !folder.isLocal)
                putExtra("EXTRA_RECURSIVE", false)
            }
            startActivity(intent)
            return
        }

        val intent = Intent(this, SubFolderActivity::class.java).apply {
            putExtra("EXTRA_FOLDER_PATH", realPath)
            putExtra("EXTRA_IS_WEBDAV", !folder.isLocal)
        }
        startActivity(intent)
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
            loadFolders()
            return
        }

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (hasStoragePermission()) {
            loadFolders()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        (menu as? MenuBuilder)?.setOptionalIconsVisible(true)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView
        searchView?.queryHint = getString(R.string.search_folders)
        searchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentSearchKeyword = query.orEmpty().trim()
                applyFilterAndSort()
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchKeyword = newText.orEmpty().trim()
                applyFilterAndSort()
                return true
            }
        })

        val rotationLockItem = menu.findItem(R.id.action_rotation_lock)
        rotationLockItem?.isChecked = settingsManager.isRotationLocked()
        tintOverflowMenuIcons(menu)

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        (menu as? MenuBuilder)?.setOptionalIconsVisible(true)
        val isSelectionMode = adapter.isSelectionMode
        val deleteItem = menu.findItem(R.id.action_delete)
        deleteItem?.isVisible = isSelectionMode
        if (isSelectionMode) {
            deleteItem?.icon?.let { icon ->
                DrawableCompat.setTint(icon, android.graphics.Color.RED)
            }
        }
        menu.findItem(R.id.action_search)?.isVisible = !isSelectionMode
        menu.findItem(R.id.action_settings)?.isVisible = !isSelectionMode
        menu.findItem(R.id.action_grid_columns)?.isVisible = !isSelectionMode
        menu.findItem(R.id.action_sort_order)?.isVisible = !isSelectionMode
        tintOverflowMenuIcons(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        (menu as? MenuBuilder)?.setOptionalIconsVisible(true)
        tintOverflowMenuIcons(menu)
        return super.onMenuOpened(featureId, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_rotation_lock -> {
                val newLockedState = !item.isChecked
                item.isChecked = newLockedState
                settingsManager.setRotationLocked(newLockedState)
                applyRotationLock()
                true
            }
            R.id.action_delete -> {
                deleteSelectedFolders()
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

    private fun deleteSelectedFolders() {
        val selectedFolders = adapter.getSelectedFolders()
        if (selectedFolders.isEmpty()) return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(getString(R.string.delete_folders_message, selectedFolders.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    showToolbarRefreshing()
                    var count = 0
                    selectedFolders.forEach { folder ->
                        val repository: PhotoRepository = if (!folder.isLocal) {
                            RustWebDavPhotoRepository(settingsManager)
                        } else {
                            LocalPhotoRepository(this@SubFolderActivity)
                        }
                        
                        if (repository.deleteFolder(folder)) {
                            // Try to clear memory cache to avoid showing stale data
                            lifecycleScope.launch(Dispatchers.Main) {
                                com.bumptech.glide.Glide.get(this@SubFolderActivity).clearMemory()
                            }
                            count++
                        }
                    }
                    android.widget.Toast.makeText(this@SubFolderActivity, getString(R.string.deleted_folders_count, count), android.widget.Toast.LENGTH_SHORT).show()
                    adapter.exitSelectionMode()
                    loadFolders(forceRefresh = true)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateGridColumns(columns: Int): Boolean {
        settingsManager.setGridColumns(columns)
        (binding.recyclerView.layoutManager as? GridLayoutManager)?.spanCount = columns
        return true
    }

    private fun updateSortOrder(order: Int): Boolean {
        settingsManager.setSortOrder(order)
        applyFilterAndSort()
        return true
    }

    private fun applyFilterAndSort() {
        val filtered = if (currentSearchKeyword.isEmpty()) {
            currentAllFolders
        } else {
            currentAllFolders.filter {
                it.name.contains(currentSearchKeyword, ignoreCase = true)
            }
        }

        val sortedFolders = when (settingsManager.getSortOrder()) {
            SettingsManager.SORT_NAME_ASC -> filtered.sortedBy { it.name }
            SettingsManager.SORT_NAME_DESC -> filtered.sortedByDescending { it.name }
            SettingsManager.SORT_DATE_DESC -> filtered.sortedByDescending { it.dateModified }
            SettingsManager.SORT_DATE_ASC -> filtered.sortedBy { it.dateModified }
            else -> filtered
        }

        adapter.setFolders(sortedFolders)
    }

    private fun loadFolders(forceRefresh: Boolean = false) {
        beginFolderLoading(forceRefresh)
        val startedAt = SystemClock.elapsedRealtime()

        lifecycleScope.launch {
            val allFolders = mutableListOf<Folder>()
            try {
                val repository: PhotoRepository = if (isWebDav) {
                    RustWebDavPhotoRepository(settingsManager)
                } else {
                    LocalPhotoRepository(this@SubFolderActivity)
                }

                val folders = repository.getFolders(folderPath, forceRefresh).filterNot { f ->
                    !f.isLocal && (f.name.startsWith(".") || f.path.trim('/').split('/').any { it.startsWith(".") })
                }

                if (folders.isEmpty()) {
                    android.util.Log.i(
                        "SubFolderActivity",
                        "loadFolders path=$folderPath forceRefresh=$forceRefresh empty=true elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
                    )
                    val intent = Intent(this@SubFolderActivity, MainActivity::class.java).apply {
                        putExtra("EXTRA_FOLDER_PATH", folderPath)
                        putExtra("EXTRA_IS_WEBDAV", isWebDav)
                        putExtra("EXTRA_RECURSIVE", false)
                    }
                    startActivity(intent)
                    finish()
                    return@launch
                }

                allFolders.addAll(folders)
                currentAllFolders = allFolders.toList()
                applyFilterAndSort()
                android.util.Log.i(
                    "SubFolderActivity",
                    "loadFolders path=$folderPath forceRefresh=$forceRefresh count=${allFolders.size} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
                )
            } catch (e: Exception) {
                android.util.Log.e("SubFolderActivity", "Folders load failed", e)
                hideToolbarRefreshPill()
            }

            if (currentLoadUsesToolbarPill) {
                showToolbarRefreshCompleted()
            } else {
                hideToolbarRefreshPill()
            }
            binding.swipeRefreshLayout.isRefreshing = false
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun beginFolderLoading(forceRefresh: Boolean) {
        val isInitialLoad = !forceRefresh && currentAllFolders.isEmpty() && adapter.itemCount == 0
        currentLoadUsesToolbarPill = !isInitialLoad
        if (isInitialLoad) {
            binding.progressBar.visibility = View.VISIBLE
            hideToolbarRefreshPill()
        } else {
            binding.progressBar.visibility = View.GONE
            showToolbarRefreshing()
        }
    }

    private fun showToolbarRefreshing() {
        toolbarRefreshHideJob?.cancel()
        binding.toolbarProgressBar.root.alpha = 0f
        binding.toolbarProgressBar.root.visibility = View.VISIBLE
        binding.toolbarProgressBar.toolbarRefreshSpinner.visibility = View.VISIBLE
        binding.toolbarProgressBar.toolbarRefreshDoneIcon.visibility = View.GONE
        binding.toolbarProgressBar.toolbarRefreshText.setText(R.string.refresh_status_refreshing)
        binding.toolbarProgressBar.root.animate().alpha(1f).setDuration(180L).start()
    }

    private fun showToolbarRefreshCompleted() {
        toolbarRefreshHideJob?.cancel()
        binding.toolbarProgressBar.root.visibility = View.VISIBLE
        binding.toolbarProgressBar.root.alpha = 1f
        binding.toolbarProgressBar.toolbarRefreshSpinner.visibility = View.GONE
        binding.toolbarProgressBar.toolbarRefreshDoneIcon.visibility = View.VISIBLE
        binding.toolbarProgressBar.toolbarRefreshText.setText(R.string.refresh_status_completed)
        toolbarRefreshHideJob = lifecycleScope.launch {
            delay(700L)
            hideToolbarRefreshPill()
        }
    }

    private fun hideToolbarRefreshPill() {
        toolbarRefreshHideJob?.cancel()
        binding.toolbarProgressBar.root.animate()
            .alpha(0f)
            .setDuration(160L)
            .withEndAction {
                binding.toolbarProgressBar.root.visibility = View.GONE
                binding.toolbarProgressBar.root.alpha = 1f
                binding.toolbarProgressBar.toolbarRefreshSpinner.visibility = View.VISIBLE
                binding.toolbarProgressBar.toolbarRefreshDoneIcon.visibility = View.GONE
            }
            .start()
    }

    private fun resolveRemotePreview(folder: Folder) {
        if (folder.isLocal || folder.previewUris.isNotEmpty()) return

        lifecycleScope.launch {
            val preview = RustWebDavPhotoRepository(settingsManager).inspectFolder(folder.path) ?: return@launch
            val updatedPreviewUris = if (preview.previewUris.isNotEmpty()) preview.previewUris else folder.previewUris

            currentAllFolders = currentAllFolders.map { current ->
                if (current.path == folder.path) {
                    current.copy(
                        previewUris = updatedPreviewUris,
                        hasSubFolders = preview.hasSubFolders
                    )
                } else {
                    current
                }
            }

            adapter.updateFolderPreview(folder.path, updatedPreviewUris, preview.hasSubFolders)
        }
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
            R.id.action_sort_date_asc
        )

        listOf(
            R.id.action_select,
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

        menu.findItem(R.id.action_delete)?.icon?.mutate()?.let { DrawableCompat.setTint(it, deleteColor) }
    }

    private fun isDarkModeEnabled(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}
