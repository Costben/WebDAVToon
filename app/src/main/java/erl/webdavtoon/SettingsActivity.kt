package erl.webdavtoon

import android.os.Bundle
import android.text.InputType
import android.content.res.Configuration
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import erl.webdavtoon.databinding.ActivitySettingsMd3Binding
import erl.webdavtoon.databinding.DialogServerConfigWebdavBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.google.android.material.color.DynamicColors

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsMd3Binding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        settingsManager = SettingsManager(this)
        LogManager.initialize(this)
        applyRotationLock()
        super.onCreate(savedInstanceState)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightNavigationBars = !isNightModeActive()

        binding = ActivitySettingsMd3Binding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager.setServerType("webdav")

        setupToolbar()
        setupItems()
        refreshUi()

        if (intent.getBooleanExtra("EXTRA_SHOW_ADD_SERVER", false)) {
            // Find next empty slot or just append
            val slots = settingsManager.getAllSlots()
            val nextSlot = (slots.maxOrNull() ?: -1) + 1
            settingsManager.setCurrentSlot(nextSlot)
            showWebDavConfigDialog()
        }
    }

    private fun applyRotationLock() {
        if (::settingsManager.isInitialized && settingsManager.isRotationLocked()) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun isNightModeActive(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        
        // 隐藏不必要的项
        menu.findItem(R.id.action_search)?.isVisible = false
        menu.findItem(R.id.action_settings)?.isVisible = false
        menu.findItem(R.id.action_grid_columns)?.isVisible = false
        menu.findItem(R.id.action_sort_order)?.isVisible = false
        
        val rotationLockItem = menu.findItem(R.id.action_rotation_lock)
        rotationLockItem?.isChecked = settingsManager.isRotationLocked()
        
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
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

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupItems() {
        binding.accountSection.setOnClickListener { showWebDavConfigDialog() }
        binding.manageAccountButton.setOnClickListener { showWebDavConfigDialog() }

        binding.settingServerType.apply {
            icon.setImageResource(R.drawable.ic_ior_server)
            title.text = getString(R.string.server_type)
            summary.text = getString(R.string.webdav_display_value)
            root.setOnClickListener {
                Toast.makeText(this@SettingsActivity, getString(R.string.webdav_only), Toast.LENGTH_SHORT).show()
            }
        }

        binding.settingHost.apply {
            icon.setImageResource(R.drawable.ic_ior_cloud)
            title.text = getString(R.string.host_address)
            root.setOnClickListener { showWebDavConfigDialog() }
        }

        binding.settingTheme.apply {
            icon.setImageResource(R.drawable.ic_ior_palette)
            title.text = getString(R.string.theme)
            root.setOnClickListener { showThemeDialog() }
        }

        binding.settingLanguage.apply {
            icon.setImageResource(R.drawable.ic_ior_translate)
            title.text = getString(R.string.language)
            root.setOnClickListener { showLanguageDialog() }
        }

        binding.settingGridColumns.apply {
            icon.setImageResource(R.drawable.ic_ior_view_grid)
            title.text = getString(R.string.grid_columns)
            root.setOnClickListener { showGridColumnsDialog() }
        }

        binding.settingSortOrder.apply {
            icon.setImageResource(R.drawable.ic_ior_sort)
            title.text = getString(R.string.sort_order)
            root.setOnClickListener { showSortOrderDialog() }
        }

        binding.settingThumbnailQuality.apply {
            icon.setImageResource(R.drawable.ic_ior_multiple_pages)
            title.text = getString(R.string.thumbnail_quality)
            root.setOnClickListener { showThumbnailQualityDialog() }
        }

        binding.settingReaderMaxZoom.apply {
            icon.setImageResource(R.drawable.ic_ior_zoom_in)
            title.text = getString(R.string.reader_max_zoom)
            root.setOnClickListener { showReaderMaxZoomDialog() }
        }

        binding.settingVideoExternalMode.apply {
            icon.setImageResource(R.drawable.ic_ior_open_in_browser)
            title.text = getString(R.string.video_external_player_mode)
            root.setOnClickListener { showVideoExternalPlayerModeDialog() }
        }

        binding.settingClearCache.apply {
            icon.setImageResource(R.drawable.ic_ior_bin)
            title.text = getString(R.string.clear_cache)
            summary.text = getString(R.string.clear_cache_summary)
            root.setOnClickListener { showClearCacheConfirmation() }
        }

        binding.settingAbout.apply {
            icon.setImageResource(R.drawable.ic_ior_info_circle)
            title.text = getString(R.string.about)
            summary.text = getString(R.string.about_summary)
            root.setOnClickListener {
                Toast.makeText(this@SettingsActivity, getString(R.string.about_summary), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshUi() {
        binding.accountTitle.text = getString(R.string.webdav_server)
        val host = settingsManager.getWebDavUrl()
        binding.accountSubtitle.text = if (host.isNotEmpty()) host else getString(R.string.not_configured)

        binding.settingServerType.title.text = getString(R.string.server_type)
        binding.settingServerType.summary.text = getString(R.string.webdav_display_value)
        binding.settingHost.title.text = getString(R.string.host_address)
        binding.settingHost.summary.text = host
        
        binding.settingTheme.title.text = getString(R.string.theme)
        binding.settingTheme.summary.text = ThemeHelper.getThemeName(this, settingsManager.getThemeId())
        
        binding.settingLanguage.title.text = getString(R.string.language)
        val langSummary = when(settingsManager.getLanguage()) {
            "zh" -> getString(R.string.language_chinese)
            "en" -> getString(R.string.language_english)
            else -> getString(R.string.follow_system)
        }
        binding.settingLanguage.summary.text = langSummary

        binding.settingGridColumns.title.text = getString(R.string.grid_columns)
        binding.settingGridColumns.summary.text = getString(R.string.columns_suffix, settingsManager.getGridColumns())

        val sortOrder = when (settingsManager.getSortOrder()) {
            0 -> getString(R.string.sort_name_asc)
            1 -> getString(R.string.sort_name_desc)
            2 -> getString(R.string.sort_date_desc)
            3 -> getString(R.string.sort_date_asc)
            else -> getString(R.string.sort_date_desc)
        }
        binding.settingSortOrder.title.text = getString(R.string.sort_order)
        binding.settingSortOrder.summary.text = sortOrder

        binding.settingThumbnailQuality.title.text = getString(R.string.thumbnail_quality)
        binding.settingThumbnailQuality.summary.text = when (settingsManager.getWaterfallQualityMode()) {
            SettingsManager.WATERFALL_MODE_MAX_WIDTH ->
                getString(R.string.thumbnail_quality_summary_max_width, settingsManager.getWaterfallMaxWidth())
            else ->
                getString(R.string.thumbnail_quality_summary_percent, settingsManager.getWaterfallPercent())
        }

        binding.settingReaderMaxZoom.title.text = getString(R.string.reader_max_zoom)
        binding.settingReaderMaxZoom.summary.text = getString(
            R.string.reader_max_zoom_summary,
            settingsManager.getReaderMaxZoomPercent().coerceIn(100, 500)
        )

        binding.settingVideoExternalMode.title.text = getString(R.string.video_external_player_mode)
        binding.settingVideoExternalMode.summary.text = when (settingsManager.getVideoExternalPlayerMode()) {
            SettingsManager.VIDEO_EXTERNAL_PLAYER_MODE_CHOOSER ->
                getString(R.string.video_external_player_mode_chooser)
            else ->
                getString(R.string.video_external_player_mode_system_default)
        }

        binding.settingAbout.title.text = getString(R.string.about)
        binding.settingAbout.summary.text = getString(R.string.app_version_format, BuildConfig.VERSION_NAME)
    }

    private fun showWebDavConfigDialog() {
        val dialogBinding = DialogServerConfigWebdavBinding.inflate(layoutInflater)

        val protocols = arrayOf("http", "https")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, protocols)
        dialogBinding.protocolEdit.setAdapter(adapter)
        dialogBinding.protocolEdit.setText(settingsManager.getWebDavProtocol(), false)

        dialogBinding.aliasEdit.setText(settingsManager.getWebDavAlias())
        dialogBinding.hostEdit.setText(settingsManager.getWebDavUrl())
        dialogBinding.portEdit.setText(settingsManager.getWebDavPort().toString())
        dialogBinding.usernameEdit.setText(settingsManager.getWebDavUsername())
        dialogBinding.passwordEdit.setText(settingsManager.getWebDavPassword())
        dialogBinding.rememberPasswordCheck.isChecked = settingsManager.isWebDavRememberPassword()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.webdav_config, settingsManager.getCurrentSlot()))
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                settingsManager.saveWebDavConfiguration(
                    alias = dialogBinding.aliasEdit.text.toString(),
                    protocol = dialogBinding.protocolEdit.text.toString(),
                    url = dialogBinding.hostEdit.text.toString(),
                    port = dialogBinding.portEdit.text.toString().toIntOrNull() ?: 443,
                    username = dialogBinding.usernameEdit.text.toString(),
                    password = dialogBinding.passwordEdit.text.toString(),
                    rememberPassword = dialogBinding.rememberPasswordCheck.isChecked,
                    enabled = true
                )

                refreshUi()
                setResult(RESULT_OK)
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.test_connection, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                val protocol = dialogBinding.protocolEdit.text.toString()
                var rawUrl = dialogBinding.hostEdit.text.toString().replace("http://", "").replace("https://", "")
                if (rawUrl.endsWith('/')) rawUrl = rawUrl.dropLast(1)

                val firstSlash = rawUrl.indexOf('/')
                val hostPart = if (firstSlash != -1) rawUrl.substring(0, firstSlash) else rawUrl
                val pathPart = if (firstSlash != -1) rawUrl.substring(firstSlash) else ""

                var host = hostPart
                var port = dialogBinding.portEdit.text.toString().toIntOrNull() ?: 443

                if (host.contains(':')) {
                    val parts = host.split(':')
                    if (parts.size == 2) {
                        host = parts[0]
                        parts[1].toIntOrNull()?.let { port = it }
                    }
                }

                val endpoint = WebDavEndpointNormalizer.normalize(protocol, "$host$pathPart", port)

                val username = dialogBinding.usernameEdit.text.toString()
                val password = dialogBinding.passwordEdit.text.toString()

                Toast.makeText(this, getString(R.string.testing_connection), Toast.LENGTH_SHORT).show()

                lifecycleScope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            val repo = WebDAVToonApplication.rustRepository
                                ?: throw IllegalStateException("Rust repository not initialized: ${WebDAVToonApplication.rustInitError ?: "unknown"}")
                            repo.testWebdav(endpoint, username, password)
                        }

                        MaterialAlertDialogBuilder(this@SettingsActivity)
                            .setTitle(getString(R.string.connection_test_result))
                            .setMessage(result)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    } catch (e: Exception) {
                        MaterialAlertDialogBuilder(this@SettingsActivity)
                            .setTitle(getString(R.string.connection_failed))
                            .setMessage(getString(R.string.error_prefix, e.message ?: "Unknown error"))
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showLanguageDialog() {
        val options = arrayOf(
            getString(R.string.follow_system),
            getString(R.string.language_english),
            getString(R.string.language_chinese)
        )
        val langCodes = arrayOf("default", "en", "zh")
        val current = langCodes.indexOf(settingsManager.getLanguage()).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_language)
            .setSingleChoiceItems(options, current) { dialog, which ->
                val selectedLang = langCodes[which]
                if (settingsManager.getLanguage() != selectedLang) {
                    settingsManager.setLanguage(selectedLang)
                    ThemeHelper.applyTheme(this@SettingsActivity)
                    Toast.makeText(this@SettingsActivity, R.string.language_changed_tip, Toast.LENGTH_LONG).show()
                    recreate()
                    dialog.dismiss()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showThemeDialog() {
        val gridView = android.widget.GridView(this).apply {
            numColumns = 3
            verticalSpacing = 16
            horizontalSpacing = 16
            setPadding(32, 32, 32, 32)
            clipToPadding = false
            selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        }

        val themeIds = mutableListOf<Int>()
        if (DynamicColors.isDynamicColorAvailable()) {
            themeIds.add(ThemeHelper.THEME_FOLLOW_DEVICE)
        }
        themeIds.addAll(ThemeHelper.getThemeNames().indices.toList())
        
        val currentThemeId = settingsManager.getThemeId()

        gridView.adapter = object : android.widget.BaseAdapter() {
            override fun getCount(): Int = themeIds.size
            override fun getItem(position: Int): Any = themeIds[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_theme_picker, parent, false)
                val themeId = themeIds[position]
                val colors = ThemeHelper.getThemeColors(this@SettingsActivity, themeId)
                
                view.findViewById<ThemePreviewView>(R.id.theme_preview).apply {
                    colorPrimary = colors.primary
                    colorSecondary = colors.secondary
                    colorTertiary = colors.tertiary
                    colorSurface = colors.surface
                }
                
                view.findViewById<android.widget.TextView>(R.id.theme_name).text = ThemeHelper.getThemeName(this@SettingsActivity, themeId)
                view.findViewById<android.widget.ImageView>(R.id.theme_checked).visibility = 
                    if (themeId == currentThemeId) View.VISIBLE else View.GONE

                return view
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_app_theme)
            .setView(gridView)
            .setNegativeButton(R.string.cancel, null)
            .create()

        gridView.setOnItemClickListener { _, _, position, _ ->
            settingsManager.setThemeId(themeIds[position])
            refreshUi()
            dialog.dismiss()
            recreate()
        }

        dialog.show()
    }

    private fun showGridColumnsDialog() {
        val options = arrayOf(
            getString(R.string.column_count, 1),
            getString(R.string.columns_count, 2),
            getString(R.string.columns_count, 3),
            getString(R.string.columns_count, 4)
        )
        val current = (settingsManager.getGridColumns() - 1).coerceIn(0, 3)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.grid_columns)
            .setSingleChoiceItems(options, current) { dialog, which ->
                settingsManager.setGridColumns(which + 1)
                refreshUi()
                dialog.dismiss()
            }
            .show()
    }

    private fun showSortOrderDialog() {
        val options = arrayOf(
            getString(R.string.sort_name_asc),
            getString(R.string.sort_name_desc),
            getString(R.string.sort_date_desc),
            getString(R.string.sort_date_asc)
        )
        val current = settingsManager.getSortOrder()

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.sort_order))
            .setSingleChoiceItems(options, current) { dialog, which ->
                settingsManager.setSortOrder(which)
                refreshUi()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showThumbnailQualityDialog() {
        val modes = arrayOf(
            getString(R.string.thumbnail_quality_percent_mode),
            getString(R.string.thumbnail_quality_max_width_mode)
        )
        val modeCodes = arrayOf(SettingsManager.WATERFALL_MODE_PERCENT, SettingsManager.WATERFALL_MODE_MAX_WIDTH)
        var selectedMode = settingsManager.getWaterfallQualityMode()
        var currentModeIdx = modeCodes.indexOf(selectedMode).coerceAtLeast(0)

        // 第一步：选择模式
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.thumbnail_quality_dialog_title))
            .setSingleChoiceItems(modes, currentModeIdx) { _, which ->
                currentModeIdx = which
                selectedMode = modeCodes[which]
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                // 第二步：输入数值
                val editText = android.widget.EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    val hint = if (selectedMode == SettingsManager.WATERFALL_MODE_MAX_WIDTH) {
                        getString(R.string.thumbnail_quality_enter_max_width)
                    } else {
                        getString(R.string.thumbnail_quality_enter_percent)
                    }
                    this.hint = hint
                    val currentVal = if (selectedMode == SettingsManager.WATERFALL_MODE_MAX_WIDTH) {
                        settingsManager.getWaterfallMaxWidth().toString()
                    } else {
                        settingsManager.getWaterfallPercent().toString()
                    }
                    setText(currentVal)
                    selectAll()
                    setPadding(64, 32, 64, 32)
                }

                MaterialAlertDialogBuilder(this)
                    .setTitle(if (selectedMode == SettingsManager.WATERFALL_MODE_MAX_WIDTH)
                        getString(R.string.thumbnail_quality_max_width_mode)
                    else
                        getString(R.string.thumbnail_quality_percent_mode))
                    .setView(editText)
                    .setPositiveButton(R.string.save) { _, _ ->
                        val value = editText.text.toString().toIntOrNull()
                        if (value != null && value > 0) {
                            settingsManager.setWaterfallQualityMode(selectedMode)
                            if (selectedMode == SettingsManager.WATERFALL_MODE_MAX_WIDTH) {
                                settingsManager.setWaterfallMaxWidth(value)
                            } else {
                                settingsManager.setWaterfallPercent(value.coerceIn(10, 100))
                            }
                            refreshUi()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                    .also { d ->
                        d.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                    }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showReaderMaxZoomDialog() {
        val editText = android.widget.EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.reader_max_zoom_hint)
            setText(settingsManager.getReaderMaxZoomPercent().coerceIn(100, 500).toString())
            selectAll()
            setPadding(64, 32, 64, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reader_max_zoom_dialog_title)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val percent = editText.text.toString().toIntOrNull()?.coerceIn(100, 500)
                if (percent != null) {
                    settingsManager.setReaderMaxZoomPercent(percent)
                    refreshUi()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showVideoExternalPlayerModeDialog() {
        val options = arrayOf(
            getString(R.string.video_external_player_mode_system_default),
            getString(R.string.video_external_player_mode_chooser)
        )
        val current = when (settingsManager.getVideoExternalPlayerMode()) {
            SettingsManager.VIDEO_EXTERNAL_PLAYER_MODE_CHOOSER -> 1
            else -> 0
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.video_external_player_mode)
            .setSingleChoiceItems(options, current) { dialog, which ->
                settingsManager.setVideoExternalPlayerMode(
                    if (which == 1) {
                        SettingsManager.VIDEO_EXTERNAL_PLAYER_MODE_CHOOSER
                    } else {
                        SettingsManager.VIDEO_EXTERNAL_PLAYER_MODE_SYSTEM_DEFAULT
                    }
                )
                refreshUi()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showClearCacheConfirmation() {        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.clear_cache))
            .setMessage(getString(R.string.clear_cache_message))
            .setPositiveButton(R.string.delete) { _, _ -> clearImageCache() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearImageCache() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Glide.get(this@SettingsActivity).clearDiskCache()
                withContext(Dispatchers.Main) {
                    Glide.get(this@SettingsActivity).clearMemory()
                    Toast.makeText(this@SettingsActivity, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.cache_clear_failed, e.message ?: "Unknown error"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
