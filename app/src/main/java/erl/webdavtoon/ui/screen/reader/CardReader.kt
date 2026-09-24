// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import erl.webdavtoon.Photo

/**
 * Horizontal card flip reader engine backed by HorizontalPager and ZoomableImage.
 */
@Composable
fun CardReader(
    photos: List<Photo>,
    currentIndex: Int,
    onPageChanged: (Int) -> Unit,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val initialPage = currentIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = initialPage) { photos.size }

    // Notify caller when pager's current page changes
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentIndex) {
            onPageChanged(pagerState.currentPage)
        }
    }

    // Scroll to page when external currentIndex changes (slider, chapter jump, etc.)
    LaunchedEffect(currentIndex) {
        val targetPage = currentIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
        if (targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { page -> photos.getOrNull(page)?.id ?: page }
    ) { page ->
        val photo = photos.getOrNull(page)
        if (photo != null) {
            ZoomableImage(
                photo = photo,
                isCurrentPage = (page == pagerState.currentPage),
                onSingleTap = onSingleTap
            )
        }
    }
}
