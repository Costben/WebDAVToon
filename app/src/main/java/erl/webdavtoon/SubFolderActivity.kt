package erl.webdavtoon

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import erl.webdavtoon.databinding.ActivityFolderViewBinding
import kotlinx.coroutines.Dispatchers
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

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadFolders()
        }
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
        loadFolders()
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
            }
        )

        binding.recyclerView.layoutManager = GridLayoutManager(this, settingsManager.getGridColumns()).apply {
            isItemPrefetchEnabled = false
        }
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

        // If folder.hasSubFolders is true, pre-check content to avoid white flash
        binding.toolbarProgressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val repository: PhotoRepository = if (!folder.isLocal) {
                    RustWebDavPhotoRepository(settingsManager)
                } else {
                    LocalPhotoRepository(this@SubFolderActivity)
                }

                val subFolders = withContext(Dispatchers.IO) {
                    repository.getFolders(realPath, false).filterNot { f ->
                        !f.isLocal && (f.name.startsWith(".") || f.path.trim('/').split('/').any { it.startsWith(".") })
                    }
                }

                if (subFolders.isEmpty()) {
                    // No visible subfolders found, go directly to MainActivity
                    val intent = Intent(this@SubFolderActivity, MainActivity::class.java).apply {
                        putExtra("EXTRA_FOLDER_PATH", realPath)
                        putExtra("EXTRA_IS_WEBDAV", !folder.isLocal)
                        putExtra("EXTRA_RECURSIVE", false)
                    }
                    startActivity(intent)
                } else {
                    // Subfolders (or mixed content virtual folder) exist, go to SubFolderActivity (recursive)
                    val intent = Intent(this@SubFolderActivity, SubFolderActivity::class.java).apply {
                        putExtra("EXTRA_FOLDER_PATH", realPath)
                        putExtra("EXTRA_IS_WEBDAV", !folder.isLocal)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("SubFolderActivity", "Folder pre-check failed", e)
                // Fallback: try entering SubFolderActivity anyway
                val intent = Intent(this@SubFolderActivity, SubFolderActivity::class.java).apply {
                    putExtra("EXTRA_FOLDER_PATH", realPath)
                    putExtra("EXTRA_IS_WEBDAV", !folder.isLocal)
                }
                startActivity(intent)
            } finally {
                binding.toolbarProgressBar.visibility = View.GONE
            }
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

        val rotationLockItem = menu.findItem(R.id.action_rotation_lock)
        rotationLockItem?.isChecked = settingsManager.isRotationLocked()

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
                    binding.toolbarProgressBar.visibility = View.VISIBLE
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
                    binding.toolbarProgressBar.visibility = View.GONE
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
        if (!binding.swipeRefreshLayout.isRefreshing) {
            binding.toolbarProgressBar.visibility = View.VISIBLE
        }

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
            } catch (e: Exception) {
                android.util.Log.e("SubFolderActivity", "Folders load failed", e)
            }

            // adapter.setFolders(allFolders) - Removed, handled by applyFilterAndSort
            binding.toolbarProgressBar.visibility = View.GONE
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }
}
