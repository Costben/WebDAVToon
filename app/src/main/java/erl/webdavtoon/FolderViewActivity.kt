package erl.webdavtoon

import android.Manifest
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import erl.webdavtoon.databinding.ActivityFolderViewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderViewBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var adapter: FolderAdapter
    private var currentAllFolders: List<Folder> = emptyList()
    private var currentSearchKeyword: String = ""

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadFolders()
        } else {
            Toast.makeText(this, getString(R.string.storage_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadFolders()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true

        binding = ActivityFolderViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        LogManager.initialize(this)

        if (WebDAVToonApplication.rustRepository == null) {
            Toast.makeText(this, getString(R.string.rust_core_not_initialized), Toast.LENGTH_LONG).show()
        }

        LibraryState.update("webdav", "")

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            binding.recyclerView.setPadding(
                binding.recyclerView.paddingLeft,
                binding.recyclerView.paddingTop,
                binding.recyclerView.paddingRight,
                systemBars.bottom + 16
            )
            insets
        }

        setupUI()
        observeState()
        checkPermissionsAndLoad()
    }

    private fun observeState() {
        lifecycleScope.launch {
            FolderState.state.collect { state ->
                binding.toolbarProgressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                binding.progressBar.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
                if (state.error != null) {
                    Toast.makeText(this@FolderViewActivity, state.error, Toast.LENGTH_SHORT).show()
                }
                adapter.setFolders(state.folders)
            }
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

    private fun checkPermissionsAndLoad() {
        val isRemoteEnabled = settingsManager.isWebDavEnabled()

        if (isRemoteEnabled) {
            loadFolders()
            return
        }

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (hasStoragePermission()) {
            loadFolders()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun onFolderClick(folder: Folder) {
        val isInternalPhotos = folder.path.startsWith("virtual://internal_photos")
        val realPath = if (isInternalPhotos) folder.path.substringAfter("path=") else folder.path

        if (folder.path == "virtual://local_all") {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", "")
                putExtra("EXTRA_IS_WEBDAV", false)
                putExtra("EXTRA_RECURSIVE", true)
            }
            startActivity(intent)
            return
        }

        if (folder.path == "virtual://local_root") {
            val intent = Intent(this, SubFolderActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", "")
                putExtra("EXTRA_IS_WEBDAV", false)
            }
            startActivity(intent)
            return
        }

        if (isInternalPhotos || !folder.hasSubFolders) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", realPath)
                putExtra("EXTRA_IS_WEBDAV", !folder.isLocal)
                putExtra("EXTRA_RECURSIVE", false)
            }
            startActivity(intent)
            return
        }

        // If folder.hasSubFolders is true, pre-check content to avoid white flash
        binding.toolbarProgressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val repository: PhotoRepository = if (!folder.isLocal) {
                    RustWebDavPhotoRepository(settingsManager)
                } else {
                    LocalPhotoRepository(this@FolderViewActivity)
                }

                val subFolders = withContext(Dispatchers.IO) {
                    repository.getFolders(realPath, false).filterNot { f ->
                        !f.isLocal && (f.name.startsWith(".") || f.path.trim('/').split('/').any { it.startsWith(".") })
                    }
                }

                if (subFolders.isEmpty()) {
                    // No visible subfolders found, go directly to MainActivity
                    val intent = Intent(this@FolderViewActivity, MainActivity::class.java).apply {
                        putExtra("EXTRA_FOLDER_PATH", realPath)
                        putExtra("EXTRA_IS_WEBDAV", !folder.isLocal)
                        putExtra("EXTRA_RECURSIVE", false)
                    }
                    startActivity(intent)
                } else {
                    // Subfolders (or mixed content virtual folder) exist, go to SubFolderActivity
                    val intent = Intent(this@FolderViewActivity, SubFolderActivity::class.java).apply {
                        putExtra("EXTRA_FOLDER_PATH", realPath)
                        putExtra("EXTRA_IS_WEBDAV", !folder.isLocal)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("FolderViewActivity", "Folder pre-check failed", e)
                // Fallback: try entering SubFolderActivity anyway
                val intent = Intent(this@FolderViewActivity, SubFolderActivity::class.java).apply {
                    putExtra("EXTRA_FOLDER_PATH", realPath)
                    putExtra("EXTRA_IS_WEBDAV", !folder.isLocal)
                }
                startActivity(intent)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)

        adapter = FolderAdapter(
            onFolderClick = { folder ->
                onFolderClick(folder)
            },
            onSelectionChanged = { count ->
                val isSelectionMode = count > 0
                binding.swipeRefreshLayout.isEnabled = !isSelectionMode
                if (isSelectionMode) {
                    supportActionBar?.title = getString(R.string.selected_count, count)
                } else {
                    supportActionBar?.title = getString(R.string.app_name)
                }
                invalidateOptionsMenu()
            }
        )

        binding.recyclerView.layoutManager = GridLayoutManager(this, settingsManager.getGridColumns()).apply {
            isItemPrefetchEnabled = false
        }
        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadFolders(forceRefresh = true)
        }

        binding.settingsFab.setOnClickListener {
            val isWebDav = settingsManager.isWebDavEnabled()
            val path = if (isWebDav) "/" else ""
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("EXTRA_FOLDER_PATH", path)
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

    override fun onBackPressed() {
        if (adapter.isSelectionMode) {
            adapter.exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

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

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val isSelectionMode = adapter.isSelectionMode
        val deleteItem = menu.findItem(R.id.action_delete)
        deleteItem?.isVisible = isSelectionMode
        if (isSelectionMode) {
            deleteItem?.icon?.let { icon ->
                androidx.core.graphics.drawable.DrawableCompat.setTint(icon, android.graphics.Color.RED)
            }
        }
        menu.findItem(R.id.action_search)?.isVisible = !isSelectionMode
        menu.findItem(R.id.action_settings)?.isVisible = !isSelectionMode
        menu.findItem(R.id.action_grid_columns)?.isVisible = !isSelectionMode
        menu.findItem(R.id.action_sort_order)?.isVisible = !isSelectionMode
        return super.onPrepareOptionsMenu(menu)
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

        FolderState.setResult(sortedFolders)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
            R.id.action_sort_name_asc -> updateSortOrder(0)
            R.id.action_sort_name_desc -> updateSortOrder(1)
            R.id.action_sort_date_desc -> updateSortOrder(2)
            R.id.action_sort_date_asc -> updateSortOrder(3)
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun deleteSelectedFolders() {
        val selectedFolders = adapter.getSelectedFolders()
        if (selectedFolders.isEmpty()) return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(getString(R.string.delete_folders_message, selectedFolders.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    binding.toolbarProgressBar.visibility = View.VISIBLE
                    var count = 0
                    selectedFolders.forEach { folder ->
                        val repository: PhotoRepository = if (!folder.isLocal) {
                            RustWebDavPhotoRepository(settingsManager)
                        } else {
                            LocalPhotoRepository(this@FolderViewActivity)
                        }
                        
                        if (repository.deleteFolder(folder)) {
                            // Try to clear memory cache to avoid showing stale data
                            lifecycleScope.launch(Dispatchers.Main) {
                                com.bumptech.glide.Glide.get(this@FolderViewActivity).clearMemory()
                            }
                            count++
                        }
                    }
                    binding.toolbarProgressBar.visibility = View.GONE
                    Toast.makeText(this@FolderViewActivity, getString(R.string.deleted_folders_count, count), Toast.LENGTH_SHORT).show()
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
        // If we want to refresh grid spacing or other UI elements that depend on column count, we might need to recreate adapter or something,
        // but GridLayoutManager handles span count change dynamically.
        return true
    }

    private fun updateSortOrder(order: Int): Boolean {
        settingsManager.setSortOrder(order)
        applyFilterAndSort()
        return true
    }

    private fun loadFolders(forceRefresh: Boolean = false) {
        FolderState.setLoading()

        lifecycleScope.launch {
            try {
                val allFolders = withContext(Dispatchers.IO) {
                    val folders = mutableListOf<Folder>()
                    val isRemoteEnabled = settingsManager.isWebDavEnabled()

                    if (isRemoteEnabled) {
                        val remoteRepo = RustWebDavPhotoRepository(settingsManager)
                        val remoteRoot = "/"
                        val remoteFolders = remoteRepo.getFolders(remoteRoot, forceRefresh).filterNot { f ->
                            f.name.startsWith(".") || f.path.trim('/').split('/').any { it.startsWith(".") }
                        }
                        folders.addAll(remoteFolders)
                    }

                    try {
                        val localRepo = LocalPhotoRepository(this@FolderViewActivity)
                        val localFolders = localRepo.getFolders("", forceRefresh)

                        if (localFolders.isNotEmpty()) {
                            val totalPhotos = localFolders.sumOf { it.photoCount }
                            val allPreviews = localFolders.flatMap { it.previewUris }.take(4)
                            folders.add(
                                Folder(
                                    path = "virtual://local_root",
                                    name = getString(R.string.local_photos),
                                    isLocal = true,
                                    photoCount = totalPhotos,
                                    previewUris = allPreviews,
                                    hasSubFolders = true
                                )
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FolderViewActivity", "Local folders load failed: ${e.message}", e)
                    }
                    folders.toList()
                }

                currentAllFolders = allFolders
                applyFilterAndSort()
            } catch (e: Exception) {
                android.util.Log.e("FolderViewActivity", "Remote folders load failed: ${e.message}", e)
                FolderState.setError(e.message ?: "Folder load failed")
            }
        }
    }
}


