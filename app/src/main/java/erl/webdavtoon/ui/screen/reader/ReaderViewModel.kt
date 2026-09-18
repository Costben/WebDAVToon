package erl.webdavtoon.ui.screen.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import erl.webdavtoon.AppSettingsStore
import erl.webdavtoon.FavoritePhotoStore
import erl.webdavtoon.Photo
import erl.webdavtoon.PhotoCache
import erl.webdavtoon.ReaderSessions
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.WebDavImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val appSettings = AppSettingsStore(context)
    private val settingsManager = SettingsManager(context)
    private val favoriteStore = FavoritePhotoStore.getInstance(context)

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            uiMode = appSettings.getUiMode(),
            isOrientationLocked = settingsManager.isRotationLocked()
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var slideshowJob: Job? = null
    private var lastSessionId: String? = null
    private var isFavoritesSource: Boolean = false

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            appSettings.observeUiMode().collect { mode ->
                _uiState.update { it.copy(uiMode = mode) }
            }
        }
        viewModelScope.launch {
            appSettings.observeBoolean(AppSettingsStore.ROTATION_LOCKED, false).collect { locked ->
                _uiState.update { it.copy(isOrientationLocked = locked) }
            }
        }
    }

    fun initialize(sessionId: String?, initialIndex: Int = 0, isFavorites: Boolean = false) {
        lastSessionId = sessionId
        isFavoritesSource = isFavorites

        _uiState.update { it.copy(isLoading = true, errorMessage = null, sessionId = sessionId) }

        val resolvedSession = if (sessionId != null) {
            ReaderSessions.resolve(
                requestedSessionId = sessionId,
                restoredSessionId = null,
                savedIndex = null,
                intentIndex = initialIndex
            )
        } else null

        val loadedPhotos = when {
            resolvedSession != null -> resolvedSession.snapshot.items
            sessionId == null -> PhotoCache.getPhotos()
            else -> PhotoCache.getPhotos().ifEmpty { emptyList() }
        }

        if (loadedPhotos.isEmpty()) {
            _uiState.update {
                it.copy(
                    photos = emptyList(),
                    currentIndex = 0,
                    isLoading = false,
                    isFavorite = false,
                    errorMessage = if (sessionId != null && resolvedSession == null) {
                        "Session expired or not found"
                    } else {
                        "No photos available"
                    }
                )
            }
            return
        }

        val targetIndex = when {
            resolvedSession != null -> resolvedSession.index
            else -> initialIndex.coerceIn(0, loadedPhotos.lastIndex)
        }

        val currentPhoto = loadedPhotos.getOrNull(targetIndex)
        val isFav = currentPhoto?.let { favoriteStore.isFavorite(it.id) } ?: false

        val defaultMode = when (settingsManager.getDefaultReaderMode()) {
            SettingsManager.DEFAULT_READER_MODE_CARD -> ReadingMode.CARD
            else -> ReadingMode.WEBTOON
        }

        _uiState.update {
            it.copy(
                photos = loadedPhotos,
                currentIndex = targetIndex,
                readingMode = defaultMode,
                isOrientationLocked = settingsManager.isRotationLocked(),
                isFavorite = isFav,
                isLoading = false,
                errorMessage = null,
                sessionId = resolvedSession?.snapshot?.id ?: sessionId
            )
        }

        preloadAdjacentPhotos(targetIndex)
    }

    fun updateCurrentIndex(newIndex: Int) {
        val state = _uiState.value
        if (state.photos.isEmpty()) return

        val clampedIndex = newIndex.coerceIn(0, state.photos.lastIndex)
        val currentPhoto = state.photos.getOrNull(clampedIndex)
        val isFav = currentPhoto?.let { favoriteStore.isFavorite(it.id) } ?: false

        if (clampedIndex == state.currentIndex) {
            if (state.isFavorite != isFav) {
                _uiState.update { it.copy(isFavorite = isFav) }
            }
            return
        }

        _uiState.update {
            it.copy(
                currentIndex = clampedIndex,
                isFavorite = isFav
            )
        }

        preloadAdjacentPhotos(clampedIndex)
    }

    fun toggleReadingMode() {
        val newMode = if (_uiState.value.readingMode == ReadingMode.CARD) {
            ReadingMode.WEBTOON
        } else {
            ReadingMode.CARD
        }
        setReadingMode(newMode)
    }

    fun setReadingMode(mode: ReadingMode) {
        val modeString = when (mode) {
            ReadingMode.CARD -> SettingsManager.DEFAULT_READER_MODE_CARD
            ReadingMode.WEBTOON -> SettingsManager.DEFAULT_READER_MODE_WEBTOON
        }
        settingsManager.setDefaultReaderMode(modeString)
        _uiState.update { it.copy(readingMode = mode) }
        if (mode != ReadingMode.CARD && _uiState.value.isSlideshowPlaying) {
            stopSlideshow()
        }
    }

    fun toggleImmersive() {
        _uiState.update { it.copy(isImmersive = !it.isImmersive) }
    }

    fun setImmersive(enabled: Boolean) {
        _uiState.update { it.copy(isImmersive = enabled) }
    }

    fun toggleSlideshow(loop: Boolean = false) {
        if (_uiState.value.isSlideshowPlaying) {
            stopSlideshow()
        } else {
            startSlideshow(loop)
        }
    }

    fun startSlideshow(loop: Boolean = false) {
        val state = _uiState.value
        if (state.photos.size <= 1) return
        if (!loop && state.currentIndex >= state.photos.lastIndex) return

        stopSlideshow()
        _uiState.update { it.copy(isSlideshowPlaying = true) }

        slideshowJob = viewModelScope.launch {
            while (isActive) {
                delay(_uiState.value.slideshowIntervalMs)
                val currentState = _uiState.value
                if (currentState.photos.isEmpty() || !currentState.isSlideshowPlaying) break

                val nextIndex = currentState.currentIndex + 1
                if (nextIndex < currentState.photos.size) {
                    updateCurrentIndex(nextIndex)
                } else if (loop) {
                    updateCurrentIndex(0)
                } else {
                    stopSlideshow()
                    break
                }
            }
        }
    }

    fun stopSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
        _uiState.update { it.copy(isSlideshowPlaying = false) }
    }

    fun pauseSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
        _uiState.update { it.copy(isSlideshowPlaying = false) }
    }

    fun resumeSlideshow(loop: Boolean = false) {
        if (!_uiState.value.isSlideshowPlaying && _uiState.value.photos.size > 1) {
            startSlideshow(loop)
        }
    }

    fun setSlideshowInterval(intervalMs: Long) {
        val clamped = intervalMs.coerceAtLeast(100L)
        _uiState.update { it.copy(slideshowIntervalMs = clamped) }
    }

    fun toggleFavorite() {
        val state = _uiState.value
        val photo = state.currentPhoto ?: return
        val currentlyFavorite = favoriteStore.isFavorite(photo.id)
        if (currentlyFavorite) {
            favoriteStore.remove(photo.id)
            _uiState.update { it.copy(isFavorite = false) }
        } else {
            favoriteStore.add(photo)
            _uiState.update { it.copy(isFavorite = true) }
        }
    }

    fun refreshFavoriteStatus() {
        val state = _uiState.value
        val currentPhoto = state.currentPhoto ?: return
        val isFav = favoriteStore.isFavorite(currentPhoto.id)
        if (state.isFavorite != isFav) {
            _uiState.update { it.copy(isFavorite = isFav) }
        }
    }

    fun toggleOrientationLock() {
        val newLocked = !_uiState.value.isOrientationLocked
        settingsManager.setRotationLocked(newLocked)
        _uiState.update { it.copy(isOrientationLocked = newLocked) }
    }

    fun setOrientationLock(locked: Boolean) {
        settingsManager.setRotationLocked(locked)
        _uiState.update { it.copy(isOrientationLocked = locked) }
    }

    fun preloadAdjacentPhotos(currentIndex: Int, window: Int = 3, width: Int? = null, height: Int? = null) {
        val state = _uiState.value
        val photos = state.photos
        if (photos.isEmpty() || currentIndex !in photos.indices) return

        val startIndex = (currentIndex - window).coerceAtLeast(0)
        val endIndex = (currentIndex + window).coerceAtMost(photos.lastIndex)

        val targetPhotos = mutableListOf<Photo>()
        for (i in startIndex..endIndex) {
            if (i != currentIndex) {
                targetPhotos.add(photos[i])
            }
        }

        if (targetPhotos.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val isWebtoon = state.readingMode == ReadingMode.WEBTOON
            for (photo in targetPhotos) {
                try {
                    if (photo.isLocal) {
                        WebDavImageLoader.preloadLocalImage(
                            context = context,
                            imageUri = photo.imageUri,
                            limitSize = false,
                            isWebtoonReader = isWebtoon,
                            width = width,
                            height = height
                        )
                    } else {
                        WebDavImageLoader.preloadWebDavImage(
                            context = context,
                            imageUri = photo.imageUri,
                            limitSize = false,
                            isWebtoonReader = isWebtoon,
                            width = width,
                            height = height
                        )
                    }
                } catch (_: Exception) {
                    // Ignore silent preload failures
                }
            }
        }
    }

    fun updatePhotos(newPhotos: List<Photo>) {
        val state = _uiState.value
        val newIndex = if (newPhotos.isEmpty()) 0 else state.currentIndex.coerceIn(0, newPhotos.lastIndex)
        val currentPhoto = newPhotos.getOrNull(newIndex)
        val isFav = currentPhoto?.let { favoriteStore.isFavorite(it.id) } ?: false

        _uiState.update {
            it.copy(
                photos = newPhotos,
                currentIndex = newIndex,
                isFavorite = isFav
            )
        }
        state.sessionId?.let { sid ->
            ReaderSessions.replacePhotos(sid, newPhotos)
        }
    }

    fun retry() {
        val current = _uiState.value
        initialize(
            sessionId = current.sessionId ?: lastSessionId,
            initialIndex = current.currentIndex,
            isFavorites = isFavoritesSource
        )
    }

    fun resetState() {
        stopSlideshow()
        _uiState.value = ReaderUiState(
            uiMode = appSettings.getUiMode(),
            isOrientationLocked = settingsManager.isRotationLocked()
        )
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopSlideshow()
    }
}
