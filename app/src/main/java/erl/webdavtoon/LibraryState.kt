package erl.webdavtoon

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LibraryUiState(
    val serverType: String = "webdav",
    val rootFolderPath: String = ""
)

object LibraryState {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun update(serverType: String, rootFolderPath: String) {
        _state.value = LibraryUiState(serverType = serverType, rootFolderPath = rootFolderPath)
    }
}
