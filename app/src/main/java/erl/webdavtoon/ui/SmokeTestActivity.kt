package erl.webdavtoon.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.ui.theme.WebDAVToonTheme

// Aliases for Miuix components
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText

// Aliases for Material 3 components
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.Text as MaterialText

/**
 * Slice 1.5 - 双主题冒烟验证 Activity
 * 支持 Miuix 与 Material 模式实时切换、DataStore 持久化与核心组件渲染验证
 */
class SmokeTestActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        handleModeIntent(intent)

        setContent {
            val currentMode by settingsManager.observeUiMode()
                .collectAsState(initial = settingsManager.getUiMode())

            WebDAVToonTheme(uiMode = currentMode) {
                SmokeTestContent(
                    currentMode = currentMode,
                    onToggleMode = {
                        val nextMode = if (currentMode == UiMode.Miuix) UiMode.Material else UiMode.Miuix
                        settingsManager.setUiMode(nextMode)
                    },
                    onSetMode = { mode ->
                        settingsManager.setUiMode(mode)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleModeIntent(intent)
    }

    private fun handleModeIntent(intent: Intent?) {
        val modeStr = intent?.getStringExtra("ui_mode")
        val modeCode = if (intent?.hasExtra("ui_mode_code") == true) intent.getIntExtra("ui_mode_code", -1) else -1
        val targetMode = when {
            !modeStr.isNullOrBlank() -> UiMode.fromValue(modeStr)
            modeCode >= 0 -> UiMode.fromCode(modeCode)
            else -> null
        }
        if (targetMode != null) {
            settingsManager.setUiMode(targetMode)
        }
    }
}

@Composable
fun SmokeTestContent(
    currentMode: UiMode,
    onToggleMode: () -> Unit,
    onSetMode: (UiMode) -> Unit
) {
    var sampleChecked by remember { mutableStateOf(true) }
    var clickCount by remember { mutableIntStateOf(0) }

    when (currentMode) {
        UiMode.Material -> {
            val bgColor = androidx.compose.material3.MaterialTheme.colorScheme.background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MaterialText(
                        text = "WebDAVToon Dual Theme Smoke Test",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
                    )

                    MaterialText(
                        text = "Current UiMode: ${currentMode.name} (${currentMode.value})",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                    )

                    // 模式切换区域
                    MaterialCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MaterialText(
                                text = "Theme Mode Controls",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MaterialText("Material Mode Enabled")
                                MaterialSwitch(
                                    checked = true,
                                    onCheckedChange = { checked ->
                                        onSetMode(if (checked) UiMode.Material else UiMode.Miuix)
                                    }
                                )
                            }
                            MaterialButton(
                                onClick = onToggleMode,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MaterialText("Switch to Miuix Mode")
                            }
                        }
                    }

                    // Material3 组件展台
                    MaterialCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MaterialText(
                                text = "Material3 Component Showcase",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MaterialText("Material Switch")
                                MaterialSwitch(
                                    checked = sampleChecked,
                                    onCheckedChange = { sampleChecked = it }
                                )
                            }
                            MaterialButton(
                                onClick = { clickCount++ },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MaterialText("Material Button (Clicks: $clickCount)")
                            }
                        }
                    }
                }
            }
        }
        UiMode.Miuix -> {
            val bgColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MiuixText(
                        text = "WebDAVToon Dual Theme Smoke Test",
                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.headline1
                    )

                    MiuixText(
                        text = "Current UiMode: ${currentMode.name} (${currentMode.value})",
                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title2
                    )

                    // 模式切换区域
                    MiuixCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MiuixText(
                                text = "Theme Mode Controls",
                                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title3
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MiuixText("Material Mode Enabled")
                                MiuixSwitch(
                                    checked = false,
                                    onCheckedChange = { checked ->
                                        onSetMode(if (checked) UiMode.Material else UiMode.Miuix)
                                    }
                                )
                            }
                            MiuixButton(
                                onClick = onToggleMode,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MiuixText("Switch to Material Mode")
                            }
                        }
                    }

                    // Miuix 组件展台
                    MiuixCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MiuixText(
                                text = "Miuix Component Showcase",
                                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title3
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MiuixText("Miuix Switch")
                                MiuixSwitch(
                                    checked = sampleChecked,
                                    onCheckedChange = { sampleChecked = it }
                                )
                            }
                            MiuixButton(
                                onClick = { clickCount++ },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MiuixText("Miuix Button (Clicks: $clickCount)")
                            }
                        }
                    }
                }
            }
        }
    }
}
