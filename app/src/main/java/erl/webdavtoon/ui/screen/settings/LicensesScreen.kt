// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.webdavtoon.R
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.ExpandLess
import io.github.suqi8.coui.kmp.icon.extended.ExpandMore
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The open-source license screen: the app's own GPL-3.0 plus every third-party group of
 * [LicensesData], each expandable to the full text read from the APK assets.
 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    viewModel: LicensesViewModel,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.oss_licenses),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(COUIIcons.Light.Back, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        containerColor = COUITheme.colorScheme.surface,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 12.dp,
                bottom = paddingValues.calculateBottomPadding(),
            ),
        ) {
            item(key = "app") {
                LicenseSection(
                    title = stringResource(R.string.oss_licenses_app_section),
                    entries = LicensesData.appEntries,
                    viewModel = viewModel,
                )
            }
            item(key = "thirdParty") {
                LicenseSection(
                    title = stringResource(R.string.oss_licenses_third_party_section),
                    entries = LicensesData.thirdPartyEntries,
                    viewModel = viewModel,
                )
            }
            item(key = "tail") { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

/** A [SmallTitle] followed by one card per entry, mirroring the settings page's group layout. */
@Composable
private fun LicenseSection(
    title: String,
    entries: List<LicenseEntry>,
    viewModel: LicensesViewModel,
) {
    Column {
        SmallTitle(text = title)
        entries.forEachIndexed { index, entry ->
            LicenseEntryCard(
                entry = entry,
                viewModel = viewModel,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = if (index == entries.lastIndex) 16.dp else 8.dp),
            )
        }
    }
}

@Composable
private fun LicenseEntryCard(
    entry: LicenseEntry,
    viewModel: LicensesViewModel,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        BasicComponent(
            title = entry.title,
            summary = entry.spdxId,
            onClick = { expanded = !expanded },
            endActions = {
                Icon(
                    imageVector = if (expanded) COUIIcons.Light.ExpandLess else COUIIcons.Light.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.oss_licenses_collapse else R.string.oss_licenses_expand,
                    ),
                )
            },
        )
        if (expanded) {
            LicenseEntryBody(entry = entry, viewModel = viewModel)
        }
    }
}

@Composable
private fun LicenseEntryBody(entry: LicenseEntry, viewModel: LicensesViewModel) {
    val context = LocalContext.current
    var text by remember(entry.assetPath) { mutableStateOf(viewModel.cachedText(entry.assetPath)) }
    var failed by remember(entry.assetPath) { mutableStateOf(false) }

    LaunchedEffect(entry.assetPath) {
        if (text == null) {
            val loaded = runCatching { viewModel.loadText(context, entry.assetPath) }.getOrNull()
            if (loaded == null) failed = true else text = loaded
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = entry.components.joinToString(separator = "\n") { "\u00b7 $it" },
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        entry.sourceUrl?.let { sourceUrl ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.oss_licenses_source_hint, sourceUrl),
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        val loadedText = text
        when {
            loadedText != null -> SelectionContainer {
                Text(
                    text = loadedText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    color = COUITheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            failed -> Text(
                text = stringResource(R.string.oss_licenses_load_failed),
                color = COUITheme.colorScheme.error,
            )
            else -> Box(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
