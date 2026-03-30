package erl.webdavtoon

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FolderUiState(
    val isLoading: Boolean = false,
    val folders: List<Folder> = emptyList(),
    val error: String? = null
)

object FolderState {
    private val _state = MutableStateFlow(FolderUiState())
    val state: StateFlow<FolderUiState> = _state.asStateFlow()

    fun setLoading() {
        _state.value = _state.value.copy(isLoading = true, error = null)
    }

    fun setResult(folders: List<Folder>) {
        _state.value = FolderUiState(isLoading = false, folders = folders, error = null)
    }

    fun setError(message: String) {
        _state.value = FolderUiState(isLoading = false, folders = emptyList(), error = message)
    }
}
