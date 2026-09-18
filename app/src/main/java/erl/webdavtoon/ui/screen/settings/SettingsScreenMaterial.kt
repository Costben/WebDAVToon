package erl.webdavtoon.ui.screen.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// TEMPORARY: replaced entirely by Slice 2.3
@Composable
fun SettingsScreenMaterial(
    uiState: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { paddingValues ->
        Text(text = androidx.compose.ui.res.stringResource(erl.webdavtoon.R.string.material_settings_placeholder), modifier = Modifier.padding(paddingValues))
    }
}

