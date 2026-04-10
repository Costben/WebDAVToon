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
import erl.webdavtoon.databinding.ActivityVideoPlayerBinding

class VideoPlayerActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        settingsManager = SettingsManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        topBarBasePaddingTop = binding.topBar.paddingTop

        mediaUri = intent.getStringExtra("EXTRA_MEDIA_URI").orEmpty()
        mediaTitle = intent.getStringExtra("EXTRA_MEDIA_TITLE").orEmpty()
        isRemote = intent.getBooleanExtra("EXTRA_IS_REMOTE", false)
        gesturesEnabled = settingsManager.isVideoGestureEnabled()

        if (mediaUri.isBlank()) {
            finish()
            return
        }

        if (isAviVideo(mediaTitle) || isAviVideo(mediaUri)) {
            ExternalVideoOpener.open(this, mediaUri, mediaTitle, isRemote, settingsManager)
            finish()
            return
        }

        binding.titleText.text = mediaTitle.ifBlank { mediaUri.substringAfterLast('/') }
        binding.backButton.setOnClickListener { finish() }
        binding.rotateButton.setOnClickListener { toggleTemporaryOrientation() }
        binding.centerPlayPauseButton.setOnClickListener {
            val exoPlayer = player ?: return@setOnClickListener
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            updateCenterPlayPauseButton()
        }

        setupPlayer()
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
                        updateCenterPlayPauseButton()
                    }
                )
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) binding.playerView.hideController()
                        applyImmersiveVideoUi()
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
            if (binding.playerView.isControllerFullyVisible) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
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
                    val threshold = 20f

                    if (gestureMode == GestureMode.NONE && (absDx > threshold || absDy > threshold)) {
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
                            val totalWindowMs = 120_000L
                            val ratio = (dx / binding.playerView.width).coerceIn(-1f, 1f)
                            val deltaMs = (ratio * totalWindowMs).toLong()
                            val duration = (player?.duration ?: 0L).coerceAtLeast(0L)
                            pendingSeekMs = (initialPositionMs + deltaMs).coerceIn(0L, duration)
                            showGestureHint("进度 ${formatTime(pendingSeekMs)}")
                        }
                        GestureMode.VERTICAL_VOLUME -> {
                            val ratio = (-dy / binding.playerView.height).coerceIn(-1f, 1f)
                            val delta = (ratio * maxVolume).toInt()
                            val target = (initialVolume + delta).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                            showGestureHint("音量 ${(target * 100f / maxVolume).toInt()}%")
                        }
                        GestureMode.VERTICAL_BRIGHTNESS -> {
                            val ratio = (-dy / binding.playerView.height).coerceIn(-1f, 1f)
                            val target = (initialBrightness + ratio).coerceIn(0.05f, 1f)
                            setScreenBrightness(target)
                            showGestureHint("亮度 ${(target * 100).toInt()}%")
                        }
                        GestureMode.NONE -> Unit
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (gestureMode == GestureMode.HORIZONTAL_SEEK) {
                        player?.seekTo(pendingSeekMs)
                    } else if (gestureMode == GestureMode.NONE) {
                        if (binding.playerView.isControllerFullyVisible) binding.playerView.hideController()
                        else binding.playerView.showController()
                    }
                    gestureMode = GestureMode.NONE
                    scheduleHideGestureHint()
                }
            }
            true
        }
    }

    private fun updateCenterPlayPauseButton() {
        val isPlaying = player?.isPlaying == true
        binding.centerPlayPauseButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
        binding.centerPlayPauseButton.visibility = if (isPlaying && !isControllerVisible) View.GONE else View.VISIBLE
        binding.centerPlayPauseButton.alpha = if (isPlaying) 0.85f else 1f
    }

    private fun setScreenBrightness(value: Float) {
        val params = window.attributes
        params.screenBrightness = value
        window.attributes = params
    }

    private fun showGestureHint(text: String) {
        binding.gestureHintText.text = text
        binding.gestureHintText.visibility = View.VISIBLE
        scheduleHideGestureHint()
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
        binding.bottomScrim.visibility = View.GONE
    }

    private fun applyImmersiveVideoUi() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars())
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
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyImmersiveVideoUi()
        updateRotateButton()
    }

    override fun onStart() {
        super.onStart()
        applyImmersiveVideoUi()
        player?.playWhenReady = settingsManager.isVideoAutoplayEnabled()
    }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
        player?.pause()
    }

    override fun onDestroy() {
        hideHintHandler.removeCallbacks(hideHintRunnable)
        binding.playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
