package erl.webdavtoon

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import erl.webdavtoon.ui.screen.reader.ReaderScreen
import erl.webdavtoon.ui.screen.reader.ReaderViewModel
import erl.webdavtoon.ui.screen.reader.ReadingMode
import erl.webdavtoon.ui.theme.WebDAVToonTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Modern Compose-based immersive reader Activity supporting Webtoon continuous scroll
 * and Card flip engines with dual-track (Miuix / Material 3) floating overlays.
 */
class PhotoViewActivity : AppCompatActivity() {

    private val viewModel: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionId = intent.getStringExtra(ReaderSessions.EXTRA_SESSION_ID)
        val intentIndex = intent.getIntExtra("EXTRA_CURRENT_INDEX", 0)
        val savedIndex = savedInstanceState?.getInt("EXTRA_CURRENT_INDEX")
        val isFavorites = intent.getBooleanExtra("EXTRA_IS_FAVORITES", false)

        if (savedInstanceState != null && savedInstanceState.containsKey("EXTRA_IS_CARD_MODE")) {
            val isCardMode = savedInstanceState.getBoolean("EXTRA_IS_CARD_MODE")
            viewModel.setReadingMode(if (isCardMode) ReadingMode.CARD else ReadingMode.WEBTOON)
        }

        viewModel.initialize(sessionId, savedIndex ?: intentIndex, isFavorites)

        // 屏幕方向联动
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.isOrientationLocked }
                    .distinctUntilChanged()
                    .collect { locked ->
                        requestedOrientation = if (locked) {
                            ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            WebDAVToonTheme(uiMode = uiState.uiMode) {
                ReaderScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.pauseSlideshow()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = viewModel.uiState.value
        outState.putInt("EXTRA_CURRENT_INDEX", state.currentIndex)
        outState.putBoolean("EXTRA_IS_CARD_MODE", state.readingMode == ReadingMode.CARD)
        outState.putString(ReaderSessions.EXTRA_SESSION_ID, state.sessionId)
    }
}
