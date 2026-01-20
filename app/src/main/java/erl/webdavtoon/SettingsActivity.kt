package erl.webdavtoon

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import erl.webdavtoon.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

/**
 * 设置界面
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用沉浸式，让导航栏自然悬浮在app之上
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 设置导航栏背景透明
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // 设置导航栏内容浅色/深色模式
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        setupUI()
        
        // 设置系统栏间距
        setupWindowInsetsListener()
    }
    
    /**
     * 设置系统栏间距监听器
     */
    private fun setupWindowInsetsListener() {
        // 获取初始的底部边距（来自 XML 的 16dp）
        val initialBottomMargin = (binding.saveButton.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // 设置标题栏顶部间距
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            
            // 设置保存按钮的底部边距，避开导航栏
            // 注意：这里不再修改 padding，而是修改 margin，以保留按钮内部的 padding 样式
            val params = binding.saveButton.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = initialBottomMargin + systemBars.bottom
            binding.saveButton.layoutParams = params
            
            insets
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // 初始化槽位选择
        val currentSlot = settingsManager.getCurrentSlot()
        when (currentSlot) {
            0 -> binding.slot0.isChecked = true
            1 -> binding.slot1.isChecked = true
            2 -> binding.slot2.isChecked = true
        }

        loadSettings()

        // 槽位切换监听
        binding.slotRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newSlot = when (checkedId) {
                R.id.slot0 -> 0
                R.id.slot1 -> 1
                R.id.slot2 -> 2
                else -> 0
            }
            // 切换槽位前不保存当前修改，直接加载新槽位的数据
            settingsManager.setCurrentSlot(newSlot)
            loadSettings()
            Toast.makeText(this, "已切换至槽位 ${newSlot + 1}", Toast.LENGTH_SHORT).show()
        }

        binding.testConnectionButton.setOnClickListener {
            testWebDavConnection()
        }

        binding.saveToSlotButton.setOnClickListener {
            saveWebDavToCurrentSlot()
            Toast.makeText(this, "预设已保存", Toast.LENGTH_SHORT).show()
        }

        binding.saveButton.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
        
        // 清理缓存按钮点击事件
        binding.clearCacheButton.setOnClickListener {
            showClearCacheConfirmation()
        }
    }

    private fun testWebDavConnection() {
        // 先临时保存当前界面上的设置以便测试
        val protocol = if (binding.protocolHttps.isChecked) "https" else "http"
        val url = binding.webDavUrlEdit.text.toString().trim()
        val port = binding.portEdit.text.toString().toIntOrNull() ?: (if (protocol == "https") 443 else 80)
        val username = binding.usernameEdit.text.toString().trim()
        val password = binding.passwordEdit.text.toString()

        if (url.isEmpty()) {
            Toast.makeText(this, "请输入 WebDAV 地址", Toast.LENGTH_SHORT).show()
            return
        }

        // 创建一个临时的 SettingsManager 用于测试，避免修改当前保存的设置
        // 或者直接使用现有的 settingsManager 临时设置值
        val oldProtocol = settingsManager.getWebDavProtocol()
        val oldUrl = settingsManager.getWebDavUrl()
        val oldPort = settingsManager.getWebDavPort()
        val oldUsername = settingsManager.getWebDavUsername()
        val oldPassword = settingsManager.getWebDavPassword()

        settingsManager.setWebDavProtocol(protocol)
        settingsManager.setWebDavUrl(url)
        settingsManager.setWebDavPort(port)
        settingsManager.setWebDavUsername(username)
        settingsManager.setWebDavPassword(password)

        lifecycleScope.launch {
            try {
                val client = WebDavClient(settingsManager)
                showDirectoryPicker("/")
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "连接失败: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                // 恢复旧设置
                settingsManager.setWebDavProtocol(oldProtocol)
                settingsManager.setWebDavUrl(oldUrl)
                settingsManager.setWebDavPort(oldPort)
                settingsManager.setWebDavUsername(oldUsername)
                settingsManager.setWebDavPassword(oldPassword)
            }
        }
    }

    private fun showDirectoryPicker(path: String) {
        lifecycleScope.launch {
            try {
                val client = WebDavClient(settingsManager)
                val resources = client.listFiles(path)
                
                val items = resources.map { 
                    if (it.isCollection) "[目录] ${it.displayName}" else "[文件] ${it.displayName}"
                }.toTypedArray()

                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("目录结构: $path")
                    .setItems(items) { _, which ->
                        val selected = resources[which]
                        if (selected.isCollection) {
                            showDirectoryPicker(selected.href)
                        } else {
                            Toast.makeText(this@SettingsActivity, "这是一个文件: ${selected.displayName}", Toast.LENGTH_SHORT).show()
                            // 重新打开当前目录
                            showDirectoryPicker(path)
                        }
                    }
                    .setNegativeButton("关闭", null)
                    .setPositiveButton("返回上级") { _, _ ->
                        if (path != "/" && path.isNotEmpty()) {
                            val parentPath = path.trimEnd('/').split('/').dropLast(1).joinToString("/")
                            showDirectoryPicker(if (parentPath.isEmpty()) "/" else parentPath)
                        } else {
                            Toast.makeText(this@SettingsActivity, "已经在根目录", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSettings() {
        binding.webDavSwitch.isChecked = settingsManager.isWebDavEnabled()
        
        // 更新 RadioButton 别名显示
        updateSlotAliasesDisplay()

        val protocol = settingsManager.getWebDavProtocol()
        if (protocol == "https") {
            binding.protocolHttps.isChecked = true
        } else {
            binding.protocolHttp.isChecked = true
        }

        binding.webDavUrlEdit.setText(settingsManager.getWebDavUrl())
        binding.portEdit.setText(settingsManager.getWebDavPort().toString())
        binding.usernameEdit.setText(settingsManager.getWebDavUsername())
        binding.passwordEdit.setText(settingsManager.getWebDavPassword())
        binding.rememberPasswordCheck.isChecked = settingsManager.isWebDavRememberPassword()
        
        binding.columnsEdit.setText(settingsManager.getGridColumns().toString())
        binding.aliasEdit.setText(settingsManager.getWebDavAlias())

        val sortOrder = settingsManager.getSortOrder()
        when (sortOrder) {
            0 -> binding.sortAZ.isChecked = true
            1 -> binding.sortZA.isChecked = true
            2 -> binding.sortNewest.isChecked = true
        }
    }

    private fun saveSettings() {
        val oldSortOrder = settingsManager.getSortOrder()
        val oldWebDavEnabled = settingsManager.isWebDavEnabled()
        
        saveWebDavToCurrentSlot()
        
        val columns = binding.columnsEdit.text.toString().toIntOrNull() ?: 2
        settingsManager.setGridColumns(columns)

        val newSortOrder = when (binding.sortRadioGroup.checkedRadioButtonId) {
            R.id.sortAZ -> 0
            R.id.sortZA -> 1
            R.id.sortNewest -> 2
            else -> 2
        }
        settingsManager.setSortOrder(newSortOrder)
        
        if (oldSortOrder != newSortOrder || oldWebDavEnabled != settingsManager.isWebDavEnabled()) {
            setResult(RESULT_OK)
        }
    }

    private fun updateSlotAliasesDisplay() {
        val alias0 = settingsManager.getWebDavAlias(0)
        val alias1 = settingsManager.getWebDavAlias(1)
        val alias2 = settingsManager.getWebDavAlias(2)
        
        binding.slot0.text = if (alias0.isNotEmpty()) alias0 else "槽位 1"
        binding.slot1.text = if (alias1.isNotEmpty()) alias1 else "槽位 2"
        binding.slot2.text = if (alias2.isNotEmpty()) alias2 else "槽位 3"
    }

    private fun saveWebDavToCurrentSlot() {
        settingsManager.setWebDavEnabled(binding.webDavSwitch.isChecked)
        settingsManager.setWebDavAlias(binding.aliasEdit.text.toString().trim())
        
        val protocol = if (binding.protocolHttps.isChecked) "https" else "http"
        settingsManager.setWebDavProtocol(protocol)
        
        settingsManager.setWebDavUrl(binding.webDavUrlEdit.text.toString().trim())
        
        val port = binding.portEdit.text.toString().toIntOrNull() ?: (if (protocol == "https") 443 else 80)
        settingsManager.setWebDavPort(port)
        
        settingsManager.setWebDavUsername(binding.usernameEdit.text.toString().trim())
        
        val rememberPassword = binding.rememberPasswordCheck.isChecked
        settingsManager.setWebDavRememberPassword(rememberPassword)
        if (rememberPassword) {
            settingsManager.setWebDavPassword(binding.passwordEdit.text.toString())
        } else {
            settingsManager.setWebDavPassword("")
        }
        
        // 保存后刷新槽位名称显示
        updateSlotAliasesDisplay()
    }
    
    /**
     * 显示清理缓存确认对话框
     */
    private fun showClearCacheConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("清理缓存")
            .setMessage("确定要清理所有缩略图缓存吗？这将删除所有本地缓存的图片，下次查看图片时需要重新加载。")
            .setPositiveButton("确定") { _, _ ->
                clearImageCache()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 清理图片缓存
     */
    private fun clearImageCache() {
        lifecycleScope.launch {
            try {
                // 清除 Glide 的缓存
                Glide.get(this@SettingsActivity).clearDiskCache()
                Glide.get(this@SettingsActivity).clearMemory()
                Toast.makeText(this@SettingsActivity, "缓存清理完成", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "缓存清理失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
