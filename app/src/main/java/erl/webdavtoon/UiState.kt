package erl.webdavtoon

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiViewState(
    val gridColumns: Int = 2,
    val isImmersive: Boolean = false
)

object UiState {
    private val _state = MutableStateFlow(UiViewState())
    val state: StateFlow<UiViewState> = _state.asStateFlow()

    fun setGridColumns(columns: Int) {
        _state.value = _state.value.copy(gridColumns = columns)
    }

    fun setImmersive(enabled: Boolean) {
        _state.value = _state.value.copy(isImmersive = enabled)
    }
}
