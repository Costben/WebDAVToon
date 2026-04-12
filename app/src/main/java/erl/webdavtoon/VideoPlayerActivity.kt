package erl.webdavtoon

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.util.Base64
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3UiR
import androidx.media3.ui.TimeBar
import erl.webdavtoon.databinding.ActivityVideoPlayerBinding

class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        private const val PLAYER_CONTROL_TIMEOUT_MS = 3500
        private const val HORIZONTAL_SEEK_WINDOW_MS = 120_000L
        private const val DOUBLE_TAP_LEFT_REGION_RATIO = 0.375f
        private const val DOUBLE_TAP_RIGHT_REGION_START_RATIO = 0.625f
    }

    private enum class GestureMode {
        NONE,
        HORIZONTAL_SEEK,
        VERTICAL_BRIGHTNESS,
        VERTICAL_VOLUME
    }

    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var audioManager: AudioManager
    private var player: ExoPlayer? = null

    private val hideHintHandler = Handler(Looper.getMainLooper())
    private val hideHintRunnable = Runnable { binding.gestureHintText.visibility = View.GONE }
    private val singleTapHandler = Handler(Looper.getMainLooper())
    private val singleTapRunnable = Runnable {
        if (binding.playerView.isControllerFullyVisible) binding.playerView.hideController()
        else binding.playerView.showController()
    }

    private var gestureMode: GestureMode = GestureMode.NONE
    private var downX = 0f
    private var downY = 0f
    private var initialPositionMs = 0L
    private var pendingSeekMs = 0L
    private var initialBrightness = -1f
    private var initialVolume = 0
    private var maxVolume = 0

    private var mediaUri: String = ""
    private var mediaTitle: String = ""
    private var isRemote: Boolean = false
    private var gesturesEnabled: Boolean = true
    private var isControllerVisible: Boolean = false
    private var topBarBasePaddingTop = 0
    private var touchSlop = 0
    private var doubleTapSlop = 0
    private var isPotentialTap = false
    private var lastTapUpTimeMs = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        settingsManager = SettingsManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop.coerceAtLeast(20)
        doubleTapSlop = ViewConfiguration.get(this).scaledDoubleTapSlop

        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        topBarBasePaddingTop = binding.topBar.paddingTop
        binding.playerView.controllerShowTimeoutMs = PLAYER_CONTROL_TIMEOUT_MS

        mediaUri = intent.getStringExtra("EXTRA_MEDIA_URI").orEmpty()
        mediaTitle = intent.getStringExtra("EXTRA_MEDIA_TITLE").orEmpty()
        isRemote = intent.getBooleanExtra("EXTRA_IS_REMOTE", false)
        gesturesEnabled = settingsManager.isVideoGestureEnabled()

        if (mediaUri.isBlank()) {
            finish()
            return
        }

        ExternalVideoOpener.open(this, mediaUri, mediaTitle, isRemote, settingsManager)
        finish()
        return

        binding.titleText.text = mediaTitle.ifBlank { mediaUri.substringAfterLast('/') }
        binding.backButton.setOnClickListener { finish() }
        binding.rotateButton.setOnClickListener { toggleTemporaryOrientation() }
        binding.centerPlayPauseButton.setOnClickListener {
            val exoPlayer = player ?: return@setOnClickListener
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            updateCenterPlayPauseButton()
        }

        setupPlayer()
        setupProgressHint()
        setupGestureHandling()
        applyImmersiveVideoUi()
        updateRotateButton()
        updateChromeVisibility(false)
    }

    private fun setupPlayer() {
        val dataSourceFactory = if (isRemote && mediaUri.startsWith("http", ignoreCase = true)) {
            val headers = mutableMapOf<String, String>()
            val username = settingsManager.getWebDavUsername()
            val password = settingsManager.getWebDavPassword()
            if (username.isNotEmpty() && password.isNotEmpty()) {
                val authHeader = "Basic " + Base64.encodeToString(
                    "$username:$password".toByteArray(),
                    Base64.NO_WRAP
                )
                headers["Authorization"] = authHeader
            }

            val httpFactory = DefaultHttpDataSource.Factory().apply {
                setUserAgent("WebDAVToon/${BuildConfig.VERSION_NAME}")
                if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
                setAllowCrossProtocolRedirects(true)
            }
            DefaultDataSource.Factory(this, httpFactory)
        } else {
            DefaultDataSource.Factory(this)
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { exoPlayer ->
                binding.playerView.player = exoPlayer
                binding.playerView.setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        isControllerVisible = visibility == View.VISIBLE
                        updateChromeVisibility(isControllerVisible)
                        reapplyImmersiveVideoUi()
                        updateCenterPlayPauseButton()
                    }
                )
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) binding.playerView.hideController()
                        reapplyImmersiveVideoUi()
                        updateCenterPlayPauseButton()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateCenterPlayPauseButton()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        android.util.Log.e(
                            "VideoPlayerActivity",
                            "exo playback failed uri=$mediaUri title=$mediaTitle mime=${resolveVideoMimeType()}",
                            error
                        )
                    }
                })
                exoPlayer.setMediaItem(
                    MediaItem.Builder()
                        .setUri(mediaUri)
                        .setMimeType(resolveVideoMimeType())
                        .build()
                )
                exoPlayer.prepare()
                exoPlayer.playWhenReady = settingsManager.isVideoAutoplayEnabled()
                updateCenterPlayPauseButton()
            }
    }

    private fun resolveVideoMimeType(): String {
        return detectVideoMimeType(mediaTitle)
            ?: detectVideoMimeType(mediaUri)
            ?: MimeTypes.APPLICATION_MP4
    }

    private fun setupGestureHandling() {
        if (!gesturesEnabled) {
            binding.gestureHintText.visibility = View.GONE
            binding.playerView.setOnTouchListener(null)
            return
        }

        binding.playerView.setOnTouchListener { _, event ->
            if (binding.playerView.isControllerFullyVisible) {
                cancelPendingSingleTap()
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    isPotentialTap = true
                    gestureMode = GestureMode.NONE
                    initialPositionMs = player?.currentPosition ?: 0L
                    pendingSeekMs = initialPositionMs
                    initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    initialBrightness = window.attributes.screenBrightness
                    if (initialBrightness < 0f) initialBrightness = 0.5f
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val absDx = kotlin.math.abs(dx)
                    val absDy = kotlin.math.abs(dy)
                    val threshold = touchSlop.toFloat()

                    if (absDx > threshold || absDy > threshold) {
                        isPotentialTap = false
                    }

                    if (gestureMode == GestureMode.NONE && (absDx > threshold || absDy > threshold)) {
                        cancelPendingSingleTap()
                        gestureMode = if (absDx >= absDy) {
                            GestureMode.HORIZONTAL_SEEK
                        } else if (downX < binding.playerView.width * 0.5f) {
                            GestureMode.VERTICAL_BRIGHTNESS
                        } else {
                            GestureMode.VERTICAL_VOLUME
                        }
                    }

                    when (gestureMode) {
                        GestureMode.HORIZONTAL_SEEK -> {
                            val ratio = (dx / binding.playerView.width).coerceIn(-1f, 1f)
                            val deltaMs = (ratio * HORIZONTAL_SEEK_WINDOW_MS).toLong()
                            val duration = (player?.duration ?: 0L).coerceAtLeast(0L)
                            pendingSeekMs = (initialPositionMs + deltaMs).coerceIn(0L, duration)
                            showGestureHint("Seek ${formatTime(pendingSeekMs)}", autoHide = false)
                        }
                        GestureMode.VERTICAL_VOLUME -> {
                            val ratio = (-dy / binding.playerView.height).coerceIn(-1f, 1f)
                            val delta = (ratio * maxVolume).toInt()
                            val target = (initialVolume + delta).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                            showGestureHint("Volume ${(target * 100f / maxVolume).toInt()}%", autoHide = false)
                        }
                        GestureMode.VERTICAL_BRIGHTNESS -> {
                            val ratio = (-dy / binding.playerView.height).coerceIn(-1f, 1f)
                            val target = (initialBrightness + ratio).coerceIn(0.05f, 1f)
                            setScreenBrightness(target)
                            showGestureHint("Brightness ${(target * 100).toInt()}%", autoHide = false)
                        }
                        GestureMode.NONE -> Unit
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (gestureMode == GestureMode.HORIZONTAL_SEEK) {
                        player?.seekTo(pendingSeekMs)
                    } else if (gestureMode == GestureMode.NONE && event.actionMasked == MotionEvent.ACTION_UP) {
                        handleTapRelease(event)
                    }
                    gestureMode = GestureMode.NONE
                    isPotentialTap = false
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        cancelPendingSingleTap()
                    }
                    if (binding.gestureHintText.visibility == View.VISIBLE) {
                        scheduleHideGestureHint()
                    }
                }
            }
            true
        }
    }

    private fun handleTapRelease(event: MotionEvent) {
        if (!isPotentialTap) return

        val isDoubleTap = lastTapUpTimeMs != 0L &&
            event.eventTime - lastTapUpTimeMs <= ViewConfiguration.getDoubleTapTimeout() &&
            squaredDistance(event.x, event.y, lastTapX, lastTapY) <=
            doubleTapSlop.toFloat() * doubleTapSlop.toFloat()

        if (isDoubleTap) {
            cancelPendingSingleTap()
            lastTapUpTimeMs = 0L
            performDoubleTapAction(event.x)
        } else {
            lastTapUpTimeMs = event.eventTime
            lastTapX = event.x
            lastTapY = event.y
            scheduleSingleTap()
        }
    }

    private fun scheduleSingleTap() {
        cancelPendingSingleTap()
        singleTapHandler.postDelayed(singleTapRunnable, ViewConfiguration.getDoubleTapTimeout().toLong())
    }

    private fun cancelPendingSingleTap() {
        singleTapHandler.removeCallbacks(singleTapRunnable)
    }

    private fun performDoubleTapAction(x: Float) {
        val playerWidth = binding.playerView.width.takeIf { it > 0 } ?: return
        val tapRatio = x / playerWidth.toFloat()
        val seekIntervalSeconds = settingsManager.getVideoDoubleTapSeekSeconds()
        val seekIntervalMs = seekIntervalSeconds * 1000L

        when {
            tapRatio < DOUBLE_TAP_LEFT_REGION_RATIO -> {
                val targetPosition = seekBy(-seekIntervalMs)
                android.util.Log.d(
                    "VideoPlayerActivity",
                    "doubleTap action=rewind intervalSeconds=$seekIntervalSeconds targetMs=$targetPosition region=left"
                )
                showGestureHint("-${seekIntervalSeconds}s")
            }

            tapRatio > DOUBLE_TAP_RIGHT_REGION_START_RATIO -> {
                val targetPosition = seekBy(seekIntervalMs)
                android.util.Log.d(
                    "VideoPlayerActivity",
                    "doubleTap action=fastForward intervalSeconds=$seekIntervalSeconds targetMs=$targetPosition region=right"
                )
                showGestureHint("+${seekIntervalSeconds}s")
            }

            else -> {
                val exoPlayer = player ?: return
                val actionText = if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    getString(Media3UiR.string.exo_controls_pause_description)
                } else {
                    exoPlayer.play()
                    getString(Media3UiR.string.exo_controls_play_description)
                }
                android.util.Log.d(
                    "VideoPlayerActivity",
                    "doubleTap action=${actionText.lowercase()} region=center"
                )
                updateCenterPlayPauseButton()
                showGestureHint(actionText)
            }
        }
    }

    private fun seekBy(deltaMs: Long): Long {
        val exoPlayer = player ?: return 0L
        val currentPosition = exoPlayer.currentPosition
        val duration = exoPlayer.duration
        val targetPosition = if (duration > 0L) {
            (currentPosition + deltaMs).coerceIn(0L, duration)
        } else {
            (currentPosition + deltaMs).coerceAtLeast(0L)
        }
        exoPlayer.seekTo(targetPosition)
        return targetPosition
    }

    private fun squaredDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    private fun setupProgressHint() {
        val progressBar = binding.playerView.findViewById<View>(Media3UiR.id.exo_progress) as? TimeBar ?: return
        progressBar.addListener(
            object : TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: TimeBar, position: Long) {
                    showGestureHint(formatTime(position), autoHide = false)
                }

                override fun onScrubMove(timeBar: TimeBar, position: Long) {
                    showGestureHint(formatTime(position), autoHide = false)
                }

                override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                    showGestureHint(formatTime(position))
                }
            }
        )
    }

    private fun updateCenterPlayPauseButton() {
        val isPlaying = player?.isPlaying == true
        binding.centerPlayPauseButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
        binding.centerPlayPauseButton.contentDescription = getString(
            if (isPlaying) Media3UiR.string.exo_controls_pause_description
            else Media3UiR.string.exo_controls_play_description
        )
        binding.centerPlayPauseButton.visibility =
            if (!isPlaying && !isControllerVisible) View.VISIBLE else View.GONE
        binding.centerPlayPauseButton.alpha = 1f
        updateChromeVisibility(isControllerVisible)
    }

    private fun setScreenBrightness(value: Float) {
        val params = window.attributes
        params.screenBrightness = value
        window.attributes = params
    }

    private fun showGestureHint(text: String, autoHide: Boolean = true) {
        binding.gestureHintText.text = text
        binding.gestureHintText.visibility = View.VISIBLE
        if (autoHide) {
            scheduleHideGestureHint()
        } else {
            hideHintHandler.removeCallbacks(hideHintRunnable)
        }
    }

    private fun scheduleHideGestureHint() {
        hideHintHandler.removeCallbacks(hideHintRunnable)
        hideHintHandler.postDelayed(hideHintRunnable, 900L)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }

    private fun toggleTemporaryOrientation() {
        requestedOrientation = if (isLandscapeMode()) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        updateRotateButton()
    }

    private fun isLandscapeMode(): Boolean = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun updateRotateButton() {
        binding.rotateButton.contentDescription = getString(
            if (isLandscapeMode()) R.string.video_rotate_portrait else R.string.video_rotate_landscape
        )
        binding.rotateButton.alpha = if (isLandscapeMode()) 1f else 0.9f
    }

    private fun updateChromeVisibility(visible: Boolean) {
        binding.topBar.visibility = if (visible) View.VISIBLE else View.GONE
        binding.topScrim.visibility = if (visible) View.VISIBLE else View.GONE
        val shouldShowBottomChrome = visible || player?.isPlaying != true
        binding.bottomScrim.visibility = if (shouldShowBottomChrome) View.VISIBLE else View.GONE
    }

    private fun applyImmersiveVideoUi() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.topBar.setPadding(
                binding.topBar.paddingLeft,
                topBarBasePaddingTop + statusBars.top,
                binding.topBar.paddingRight,
                binding.topBar.paddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun reapplyImmersiveVideoUi() {
        binding.root.post { applyImmersiveVideoUi() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reapplyImmersiveVideoUi()
        updateRotateButton()
    }

    override fun onStart() {
        super.onStart()
        reapplyImmersiveVideoUi()
        player?.playWhenReady = settingsManager.isVideoAutoplayEnabled()
    }

    override fun onResume() {
        super.onResume()
        reapplyImmersiveVideoUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            reapplyImmersiveVideoUi()
        }
    }

    override fun onStop() {
        super.onStop()
        cancelPendingSingleTap()
        player?.playWhenReady = false
        player?.pause()
    }

    override fun onDestroy() {
        hideHintHandler.removeCallbacks(hideHintRunnable)
        cancelPendingSingleTap()
        binding.playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
