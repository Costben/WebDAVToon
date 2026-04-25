package erl.webdavtoon

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaUiState(
    val sessionKey: String = "",
    val isLoading: Boolean = false,
    val photos: List<Photo> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null,
    val folderPath: String = "",
    val isRemote: Boolean = false,
    val isRecursive: Boolean = false,
    val isFavorites: Boolean = false,
    val currentQuery: MediaQuery = MediaQuery(),
    val currentOffset: Int = 0,
    val clusterShuffleSeed: Long = 0L,
    val photoShuffleSeed: Long = 0L
)

class MediaViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private val sessionKeyKey = "media_session_key"

    fun start(
        sessionKey: String,
        folderPath: String,
        isRemote: Boolean,
        isRecursive: Boolean,
        isFavorites: Boolean,
        query: MediaQuery,
        clusterShuffleSeed: Long,
        photoShuffleSeed: Long
    ) {
        savedStateHandle[sessionKeyKey] = sessionKey
        _state.value = MediaUiState(
            sessionKey = sessionKey,
            isLoading = true,
            folderPath = folderPath,
            isRemote = isRemote,
            isRecursive = isRecursive,
            isFavorites = isFavorites,
            currentQuery = query,
            currentOffset = 0,
            clusterShuffleSeed = clusterShuffleSeed,
            photoShuffleSeed = photoShuffleSeed
        )
    }

    fun startAppend() {
        _state.value = _state.value.copy(isLoading = true, error = null)
    }

    fun restore(state: MediaUiState) {
        savedStateHandle[sessionKeyKey] = state.sessionKey
        _state.value = state.copy(isLoading = false, error = null)
    }

    fun setPage(photos: List<Photo>, hasMore: Boolean, nextOffset: Int, append: Boolean) {
        val merged = if (append) _state.value.photos + photos else photos
        _state.value = _state.value.copy(
            isLoading = false,
            photos = merged,
            hasMore = hasMore,
            currentOffset = nextOffset,
            error = null
        )
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(isLoading = false, error = message)
    }

    fun removePhotos(photosToRemove: List<Photo>) {
        val currentPhotos = _state.value.photos.toMutableList()
        currentPhotos.removeAll(photosToRemove)
        _state.value = _state.value.copy(photos = currentPhotos)
    }

    fun currentSessionKey(): String = savedStateHandle[sessionKeyKey] ?: _state.value.sessionKey
}
