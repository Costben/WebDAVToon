package erl.webdavtoon

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaUiState(
    val sessionKey: String = "",
    val isLoading: Boolean = false,
    val photos: List<Photo> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null
)

object MediaState {
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    fun start(sessionKey: String) {
        _state.value = MediaUiState(sessionKey = sessionKey, isLoading = true)
    }

    fun startAppend() {
        _state.value = _state.value.copy(isLoading = true, error = null)
    }

    fun setPage(sessionKey: String, photos: List<Photo>, hasMore: Boolean, append: Boolean) {
        if (_state.value.sessionKey != sessionKey) return
        val merged = if (append) _state.value.photos + photos else photos
        _state.value = _state.value.copy(isLoading = false, photos = merged, hasMore = hasMore, error = null)
    }

    fun setError(sessionKey: String, message: String) {
        if (_state.value.sessionKey != sessionKey) return
        _state.value = _state.value.copy(isLoading = false, error = message)
    }

    fun removePhotos(photosToRemove: List<Photo>) {
        val currentPhotos = _state.value.photos.toMutableList()
        currentPhotos.removeAll(photosToRemove)
        _state.value = _state.value.copy(photos = currentPhotos)
    }
}
