// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * A [Scaffold] whose top bar wears the ColorOS 17 gradient glass.
 *
 * The bar material samples whatever is drawn underneath it, so the content has to reach the top
 * of the window instead of starting below the bar: the scaffold runs edge to edge, reports the
 * bar's measured height through [PaddingValues], and the caller feeds that top value into the
 * scrollable's own `contentPadding`. Items then start below the bar at rest and slide under it
 * while scrolling, which is the only state in which the blur has anything to blur.
 *
 * The content subtree is recorded into a backdrop layer that the bar reads. The bar itself is a
 * sibling of that layer, never part of it — a bar sampling its own output would blur itself.
 *
 * @param topBar the app bar, typically a COUI `TopAppBar` with `gradientGlass = true`.
 * @param containerColor the page colour. It is also the material's base colour, so the bar never
 *   reads as a band of its own over the page.
 * @param content the page body. Apply the received top padding to the scrollable's content
 *   padding, not to the root, or nothing will scroll under the bar.
 */
@Composable
fun GlassTopBarScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = COUITheme.colorScheme.surface,
    content: @Composable (PaddingValues) -> Unit,
) {
    // An opaque base keeps the blur from reading the layer's clear colour, which washes a dark
    // bar out to near-white as soon as the ramp lets the blur through.
    val backdrop = rememberLayerBackdrop {
        drawRect(containerColor)
        drawContent()
    }
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Scaffold(
            modifier = modifier,
            topBar = topBar,
            containerColor = containerColor,
            edgeToEdge = true,
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                content(padding)
            }
        }
    }
}
