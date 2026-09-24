// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.webdavtoon.FileUtils
import erl.webdavtoon.MediaShareHelper
import erl.webdavtoon.R
import erl.webdavtoon.SettingsManager
import kotlinx.coroutines.launch

/**
 * Modern Compose-based ReaderScreen coordinating:
 * 1. Dual reader engines (CardReader & WebtoonReader)
 * 2. Immersive full-screen system bars control via WindowInsetsControllerCompat
 * 3. Dual-track floating overlay (Miuix & Material 3)
 * 4. Image actions: save, share, favorite, mode toggle, slideshow, orientation lock
 */
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val window = activity?.window

    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    DisposableEffect(uiState.isImmersive, insetsController) {
        if (insetsController != null) {
            if (uiState.isImmersive) {
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val currentPhoto = uiState.currentPhoto
    val currentPhotoTitle = currentPhoto?.title ?: ""
    val settingsManager = remember(context) { SettingsManager(context) }
    val downloadSuccessMessage = stringResource(R.string.download_success)
    val downloadFailedMessage = stringResource(R.string.download_failed)

    val onSaveImage: () -> Unit = remember(
        context,
        currentPhoto,
        downloadSuccessMessage,
        downloadFailedMessage
    ) {
        {
            if (currentPhoto != null) {
                coroutineScope.launch {
                    val success = FileUtils.downloadImage(context, currentPhoto)
                    val message = if (success) downloadSuccessMessage else downloadFailedMessage
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val onShare: () -> Unit = remember(context, coroutineScope, settingsManager, currentPhoto) {
        {
            if (currentPhoto != null) {
                MediaShareHelper.sharePhotos(
                    context = context,
                    scope = coroutineScope,
                    settingsManager = settingsManager,
                    photos = listOf(currentPhoto)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (uiState.readingMode) {
            ReadingMode.CARD -> {
                CardReader(
                    photos = uiState.photos,
                    currentIndex = uiState.currentIndex,
                    onPageChanged = { viewModel.updateCurrentIndex(it) },
                    onSingleTap = { viewModel.toggleImmersive() }
                )
            }
            ReadingMode.WEBTOON -> {
                WebtoonReader(
                    photos = uiState.photos,
                    currentIndex = uiState.currentIndex,
                    onIndexChanged = { viewModel.updateCurrentIndex(it) },
                    onSingleTap = { viewModel.toggleImmersive() }
                )
            }
        }

        val visible = !uiState.isImmersive

        ReaderOverlayMiuix(
            visible = visible,
            title = currentPhotoTitle,
            currentIndex = uiState.currentIndex,
            totalCount = uiState.totalCount,
            readingMode = uiState.readingMode,
            isSlideshowPlaying = uiState.isSlideshowPlaying,
            isOrientationLocked = uiState.isOrientationLocked,
            isFavorite = uiState.isFavorite,
            onBack = onBack,
            onSeek = { viewModel.updateCurrentIndex(it) },
            onToggleMode = { viewModel.toggleReadingMode() },
            onToggleSlideshow = { viewModel.toggleSlideshow() },
            onToggleOrientationLock = { viewModel.toggleOrientationLock() },
            onToggleFavorite = { viewModel.toggleFavorite() },
            onSaveImage = onSaveImage,
            onShare = onShare,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}
