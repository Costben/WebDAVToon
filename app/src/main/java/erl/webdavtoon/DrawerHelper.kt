package erl.webdavtoon

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.ImageView
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import erl.webdavtoon.databinding.DialogServerConfigWebdavBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

object DrawerHelper {
    fun setupDrawer(
        activity: AppCompatActivity,
        drawerLayout: DrawerLayout,
        toolbar: androidx.appcompat.widget.Toolbar,
        navView: View, // Now a generic View (the include layout)
        settingsLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) {
        val toggle = ActionBarDrawerToggle(
            activity, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Apply the user-configured swipe trigger width (% of screen) before expanding the edge area.
        ExpandedEdgeDrawerLayout.setWidthPercent(SettingsManager(activity).getDrawerEdgeWidthPercent())
        expandDrawerEdgeArea(drawerLayout)

        val serverList = navView.findViewById<RecyclerView>(R.id.server_list)
        val addServerBtn = navView.findViewById<View>(R.id.nav_add_server)
        val favoritesBtn = navView.findViewById<View>(R.id.nav_favorites)
        val settingsBtn = navView.findViewById<View>(R.id.nav_settings)
        val headerView = navView.findViewById<View>(R.id.nav_header)
        val privacyIndicator = headerView?.findViewById<ImageView>(R.id.privacy_indicator)
        val exitPrivacyBtn = navView.findViewById<View>(R.id.nav_exit_privacy)

        fun refreshPrivacyUI() {
            val isPrivacy = PrivacyModeState.isPrivacyMode
            privacyIndicator?.visibility = if (isPrivacy) View.VISIBLE else View.GONE
            exitPrivacyBtn?.visibility = if (isPrivacy) View.VISIBLE else View.GONE
        }
        refreshPrivacyUI()

        // Handle System Windows Insets (Status Bar)
        ViewCompat.setOnApplyWindowInsetsListener(navView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            headerView?.setPadding(
                headerView.paddingLeft,
                systemBars.top, // Add top padding for status bar
                headerView.paddingRight,
                headerView.paddingBottom
            )
            insets
        }

        // Setup Server List
        val settingsManager = SettingsManager(activity)
        val adapter = ServerAdapter(activity, settingsManager, { slot ->
            // Switch server
            if (settingsManager.getCurrentSlot() != slot) {
                settingsManager.setCurrentSlot(slot)
                Toast.makeText(activity, activity.getString(R.string.switched_server), Toast.LENGTH_SHORT).show()
                // Restart from FolderViewActivity to refresh the entire state
                val intent = Intent(activity, FolderViewActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            }
            drawerLayout.closeDrawer(GravityCompat.START)
        }, { slot ->
            // Edit server via Dialog
            showWebDavConfigDialog(activity, settingsManager, slot) {
                // Refresh list after edit
                (serverList.adapter as? ServerAdapter)?.refreshData()
            }
        }, { slot ->
            // Duplicate server
            duplicateSlot(activity, settingsManager, slot)
            (serverList.adapter as? ServerAdapter)?.refreshData()
        })
        
        serverList.layoutManager = LinearLayoutManager(activity)
        serverList.adapter = adapter

        // Static Buttons
        addServerBtn.setOnClickListener {
            val slots = settingsManager.getAllSlotsUnfiltered()
            val nextSlot = (slots.maxOrNull() ?: -1) + 1
            showWebDavConfigDialog(activity, settingsManager, nextSlot) {
                (serverList.adapter as? ServerAdapter)?.refreshData()
            }
        }

        addServerBtn.setOnLongClickListener {
            if (PrivacyModeState.isPrivacyMode) {
                return@setOnLongClickListener false
            }
            tryEnterPrivacyMode(activity) {
                (serverList.adapter as? ServerAdapter)?.refreshData()
                refreshPrivacyUI()
            }
            true
        }

        exitPrivacyBtn?.setOnClickListener {
            PrivacyModeState.exit(activity)
            // After exit, currentSlot may point at a private slot; switch it to the first
            // available normal slot before restarting the Activity so the restarted UI
            // immediately loads non-private content.
            val normalSlots = settingsManager.getAllSlots()
            val newCurrent = normalSlots.firstOrNull() ?: 0
            if (settingsManager.getCurrentSlot() != newCurrent) {
                settingsManager.setCurrentSlot(newCurrent)
            }
            val intent = Intent(activity, FolderViewActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }

        favoritesBtn.setOnClickListener {
            val intent = Intent(activity, MainActivity::class.java).apply {
                putExtra("EXTRA_IS_FAVORITES", true)
            }
            activity.startActivity(intent)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        settingsBtn.setOnClickListener {
            settingsLauncher.launch(Intent(activity, SettingsActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun expandDrawerEdgeArea(drawerLayout: DrawerLayout) {
        val targetEdgePx =
            (drawerLayout.resources.displayMetrics.widthPixels * ExpandedEdgeDrawerLayout.widthFraction).toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawerLayout.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (targetEdgePx <= 0) {
                    v.systemGestureExclusionRects = emptyList()
                } else {
                    val rect = Rect(0, 0, targetEdgePx, v.height)
                    v.systemGestureExclusionRects = listOf(rect)
                }
            }
            if (targetEdgePx <= 0) {
                drawerLayout.systemGestureExclusionRects = emptyList()
            } else {
                val rect = Rect(0, 0, targetEdgePx, drawerLayout.height)
                drawerLayout.systemGestureExclusionRects = listOf(rect)
            }
        }
    }

    private fun tryEnterPrivacyMode(activity: AppCompatActivity, onSuccess: () -> Unit) {
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km?.isDeviceSecure != true) {
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    PrivacyModeState.enter(activity)
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = Unit
                override fun onAuthenticationFailed() = Unit
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_title))
            .setSubtitle(activity.getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        try {
            prompt.authenticate(info)
        } catch (_: Throwable) {
            // Silent failure — privacy mode entry is a no-op when BiometricPrompt is unavailable.
        }
    }

    private fun showWebDavConfigDialog(
        activity: AppCompatActivity,
        settingsManager: SettingsManager,
        slot: Int,
        onSaved: () -> Unit
    ) {
        val dialogBinding = DialogServerConfigWebdavBinding.inflate(activity.layoutInflater)
        val slotExisted = settingsManager.getAllSlotsUnfiltered().contains(slot)

        val protocols = arrayOf("http", "https")
        val adapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, protocols)
        dialogBinding.protocolEdit.setAdapter(adapter)

        // Load existing data for this slot
        dialogBinding.aliasEdit.setText(settingsManager.getWebDavAlias(slot))
        dialogBinding.protocolEdit.setText(settingsManager.getWebDavProtocol(slot), false)
        dialogBinding.hostEdit.setText(settingsManager.getWebDavUrl(slot))
        dialogBinding.portEdit.setText(settingsManager.getWebDavPort(slot).toString())
        dialogBinding.usernameEdit.setText(settingsManager.getWebDavUsername(slot))
        dialogBinding.passwordEdit.setText(settingsManager.getWebDavPassword(slot))
        dialogBinding.rememberPasswordCheck.isChecked = settingsManager.isWebDavRememberPassword(slot)

        if (PrivacyModeState.isPrivacyMode) {
            dialogBinding.privateServerCheck.visibility = View.VISIBLE
            dialogBinding.privateServerCheck.isChecked = if (slotExisted) {
                settingsManager.isWebDavPrivate(slot)
            } else {
                true
            }
        } else {
            dialogBinding.privateServerCheck.visibility = View.GONE
            dialogBinding.privateServerCheck.isChecked = false
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.webdav_config, slot))
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val isPrivate = if (PrivacyModeState.isPrivacyMode) {
                    dialogBinding.privateServerCheck.isChecked
                } else if (slotExisted) {
                    settingsManager.isWebDavPrivate(slot)
                } else {
                    false
                }
                settingsManager.saveWebDavConfiguration(
                    slot = slot,
                    alias = dialogBinding.aliasEdit.text.toString(),
                    protocol = dialogBinding.protocolEdit.text.toString(),
                    url = dialogBinding.hostEdit.text.toString(),
                    port = dialogBinding.portEdit.text.toString().toIntOrNull() ?: 443,
                    username = dialogBinding.usernameEdit.text.toString(),
                    password = dialogBinding.passwordEdit.text.toString(),
                    rememberPassword = dialogBinding.rememberPasswordCheck.isChecked,
                    enabled = true,
                    switchToSlotOnSave = !slotExisted,
                    isPrivate = isPrivate
                )

                onSaved()
                Toast.makeText(activity, activity.getString(R.string.server_saved), Toast.LENGTH_SHORT).show()
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

                Toast.makeText(activity, activity.getString(R.string.testing_connection), Toast.LENGTH_SHORT).show()

                activity.lifecycleScope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            val repo = WebDAVToonApplication.rustRepository
                                ?: throw IllegalStateException("Rust core not initialized")
                            repo.testWebdav(endpoint, username, password)
                        }

                        MaterialAlertDialogBuilder(activity)
                            .setTitle(activity.getString(R.string.connection_test_result))
                            .setMessage(result)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    } catch (e: Exception) {
                        MaterialAlertDialogBuilder(activity)
                            .setTitle(activity.getString(R.string.connection_failed))
                            .setMessage(activity.getString(R.string.error_prefix, e.message ?: "Unknown error"))
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun duplicateSlot(
        activity: AppCompatActivity,
        settingsManager: SettingsManager,
        sourceSlot: Int
    ) {
        val slots = settingsManager.getAllSlotsUnfiltered()
        val newSlot = (slots.maxOrNull() ?: -1) + 1
        val sourceAlias = settingsManager.getWebDavAlias(sourceSlot)
        val newAlias = sourceAlias + " Copy"

        settingsManager.saveWebDavConfiguration(
            slot = newSlot,
            alias = newAlias,
            protocol = settingsManager.getWebDavProtocol(sourceSlot),
            url = settingsManager.getWebDavUrl(sourceSlot),
            port = settingsManager.getWebDavPort(sourceSlot),
            username = settingsManager.getWebDavUsername(sourceSlot),
            password = settingsManager.getWebDavPassword(sourceSlot),
            rememberPassword = settingsManager.isWebDavRememberPassword(sourceSlot),
            enabled = settingsManager.isWebDavEnabled(sourceSlot),
            switchToSlotOnSave = false,
            isPrivate = settingsManager.isWebDavPrivate(sourceSlot)
        )

        Toast.makeText(activity, activity.getString(R.string.server_duplicated), Toast.LENGTH_SHORT).show()
    }
}

class ServerAdapter(
    private val context: android.content.Context,
    private val settingsManager: SettingsManager,
    private val onClick: (Int) -> Unit,
    private val onEdit: (Int) -> Unit,
    private val onCopy: (Int) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {

    private var slots = settingsManager.getAllSlots().toMutableList()
    private var currentSlot = settingsManager.getCurrentSlot()

    fun refreshData() {
        slots = settingsManager.getAllSlots().toMutableList()
        currentSlot = settingsManager.getCurrentSlot()
        notifyDataSetChanged()
    }

    // Swipe State
    private var openPosition: Int = RecyclerView.NO_POSITION
    private val swipeThreshold = dpToPx(76f) // Drag at least half way to snap open (152/2)
    private val maxSwipe = dpToPx(152f) // Width of buttons area + gaps (8+40+8+40+8+40+8)

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.server_title)
        val content: View = view.findViewById(R.id.item_content)
        val editBtn: View = view.findViewById(R.id.btn_edit)
        val copyBtn: View = view.findViewById(R.id.btn_copy)
        val deleteBtn: View = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(context).inflate(R.layout.item_server_swipe, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val slot = slots[position]
        val alias = settingsManager.getWebDavAlias(slot)
        val url = settingsManager.getWebDavUrl(slot)
        var displayTitle = if (alias.isNotEmpty()) alias else if (url.isNotEmpty()) url else context.getString(R.string.default_server)
        if (displayTitle.isEmpty()) displayTitle = context.getString(R.string.server_slot, slot)

        holder.title.text = displayTitle
        
        // Highlight current
        if (slot == currentSlot) {
            holder.content.setBackgroundResource(R.drawable.bg_server_item_selected)
        } else {
            holder.content.setBackgroundResource(R.drawable.bg_server_item)
        }

        // Restore swipe state - Ensure translation is applied
        holder.content.post {
            if (position == openPosition) {
                holder.content.translationX = maxSwipe
            } else {
                holder.content.translationX = 0f
            }
        }

        // Handle Swipe and Click
        holder.content.setOnTouchListener(object : View.OnTouchListener {
            private var dX = 0f
            private var startX = 0f
            private var startY = 0f
            private var isDragging = false
            private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        dX = v.translationX
                        // Close other rows if opening a new one
                        if (openPosition != -1 && openPosition != holder.adapterPosition) {
                            val oldPos = openPosition
                            openPosition = -1
                            notifyItemChanged(oldPos)
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - startX
                        val deltaY = event.rawY - startY
                        
                        // Check if it's a horizontal scroll
                        if (!isDragging && abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY)) {
                            isDragging = true
                            // Disallow parent to intercept touch events if we confirm a horizontal drag
                            v.parent.requestDisallowInterceptTouchEvent(true)
                        }

                        if (isDragging) {
                            // Calculate new translation
                            // We only allow swiping RIGHT (positive translation)
                            // Initial position could be 0 or maxSwipe
                            var targetX = dX + deltaX
                            targetX = targetX.coerceIn(0f, maxSwipe * 1.5f) // Allow some overscroll
                            v.translationX = targetX
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val endX = v.translationX
                        isDragging = false
                        
                        // Check for click
                        if (abs(event.rawX - startX) < touchSlop && abs(event.rawY - startY) < touchSlop) {
                            // It's a click
                            if (openPosition == holder.adapterPosition) {
                                // Close if already open
                                closeItem(holder)
                            } else {
                                // Perform normal click action
                                onClick(slot)
                            }
                            v.performClick()
                            return true
                        }

                        // Handle Snap
                        if (endX > swipeThreshold) {
                            openItem(holder)
                        } else {
                            closeItem(holder)
                        }
                        return true
                    }
                }
                return false
            }
        })
        
        holder.editBtn.setOnClickListener {
            onEdit(slot)
            closeItem(holder)
        }

        holder.copyBtn.setOnClickListener {
            onCopy(slot)
            closeItem(holder)
        }

        holder.deleteBtn.setOnClickListener {
            MaterialAlertDialogBuilder(context)
            .setTitle(R.string.delete_server)
            .setMessage(R.string.delete_server_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                settingsManager.deleteSlot(slot)
                slots.removeAt(holder.adapterPosition)
                notifyItemRemoved(holder.adapterPosition)
                openPosition = -1 // Reset open position
                Toast.makeText(context, context.getString(R.string.server_deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        }
    }

    private fun openItem(holder: ViewHolder) {
        val animator = ObjectAnimator.ofFloat(holder.content, "translationX", maxSwipe)
        animator.duration = 200
        animator.start()
        openPosition = holder.adapterPosition
    }

    private fun closeItem(holder: ViewHolder) {
        val animator = ObjectAnimator.ofFloat(holder.content, "translationX", 0f)
        animator.duration = 200
        animator.start()
        if (openPosition == holder.adapterPosition) {
            openPosition = RecyclerView.NO_POSITION
        }
    }

    override fun getItemCount(): Int = slots.size
}
