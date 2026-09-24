// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import erl.webdavtoon.ui.theme.WebDAVToonTheme
import io.github.suqi8.coui.kmp.basic.Button as MiuixButton
import io.github.suqi8.coui.kmp.basic.Card as MiuixCard
import io.github.suqi8.coui.kmp.basic.Switch as MiuixSwitch
import io.github.suqi8.coui.kmp.basic.Text as MiuixText
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * Slice 1.5 - COUI 冒烟验证 Activity
 * 验证 COUI 主题与核心组件渲染。
 */
class SmokeTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WebDAVToonTheme {
                SmokeTestContent()
            }
        }
    }
}

@Composable
fun SmokeTestContent() {
    var sampleChecked by remember { mutableStateOf(true) }
    var clickCount by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(COUITheme.colorScheme.surface)
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
                text = "WebDAVToon COUI Smoke Test",
                style = COUITheme.textStyles.headline1
            )

            MiuixCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiuixText(
                        text = "COUI Component Showcase",
                        style = COUITheme.textStyles.title3
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiuixText("COUI Switch")
                        MiuixSwitch(
                            checked = sampleChecked,
                            onCheckedChange = { sampleChecked = it }
                        )
                    }
                    MiuixButton(
                        onClick = { clickCount++ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MiuixText("COUI Button (Clicks: $clickCount)")
                    }
                }
            }
        }
    }
}
