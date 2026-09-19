package erl.webdavtoon.ui.screen.reader

import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import erl.webdavtoon.Photo
import erl.webdavtoon.WebDavImageLoader

/**
 * Vertical Webtoon reader engine supporting zero-gap image stitching and responsive fast scrolling.
 */
@Composable
fun WebtoonReader(
    photos: List<Photo>,
    currentIndex: Int,
    onIndexChanged: (Int) -> Unit,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black))
        return
    }

    val initialPage = currentIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

    // 1. Sync scroll position when user actively scrolls LazyColumn
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleIndex, listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val safeIndex = firstVisibleIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
            if (safeIndex != currentIndex) {
                onIndexChanged(safeIndex)
            }
        }
    }

    // 2. Scroll to item when external currentIndex changes (e.g. bottom slider, chapter jump)
    LaunchedEffect(currentIndex) {
        if (!listState.isScrollInProgress) {
            val targetIndex = currentIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
            if (targetIndex != listState.firstVisibleItemIndex) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Zero-gap vertical list for seamless comic strip stitching
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            items(
                count = photos.size,
                key = { index -> photos.getOrNull(index)?.id ?: index }
            ) { index ->
                val photo = photos[index]
                WebtoonImageItem(
                    photo = photo,
                    onSingleTap = onSingleTap
                )
            }
        }
    }
}

/**
 * Individual image slice item in the vertical Webtoon strip.
 */
@Composable
private fun WebtoonImageItem(
    photo: Photo,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Remote photos arrive from the Rust listing without intrinsic dimensions, so
    // the ratio starts unknown and is learned once the drawable is ready. Without
    // it a fast scroll measures the tile against the square placeholder and the
    // real image ends up letterboxed inside that square.
    var resolvedAspectRatio by remember(photo.id) {
        mutableStateOf(
            if (photo.width > 0 && photo.height > 0) {
                photo.width.toFloat() / photo.height.toFloat()
            } else {
                null
            }
        )
    }

    val aspectRatioModifier = resolvedAspectRatio?.let { Modifier.aspectRatio(it) } ?: Modifier
    val imageModifier = if (resolvedAspectRatio != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(aspectRatioModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSingleTap
            )
    ) {
        AndroidView(
            modifier = imageModifier,
            factory = { ctx ->
                ImageView(ctx).apply {
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    isClickable = false
                    isFocusable = false
                }
            },
            update = { imageView ->
                val onDimensionsReady: (Int, Int) -> Unit = { width, height ->
                    if (width > 0 && height > 0) {
                        resolvedAspectRatio = width.toFloat() / height.toFloat()
                    }
                }
                if (photo.isLocal) {
                    WebDavImageLoader.loadLocalImage(
                        context = imageView.context,
                        imageUri = photo.imageUri,
                        imageView = imageView,
                        progressBar = null,
                        limitSize = false,
                        isWebtoonReader = true,
                        width = if (photo.width > 0) photo.width else null,
                        height = if (photo.height > 0) photo.height else null,
                        onDimensionsReady = onDimensionsReady
                    )
                } else {
                    WebDavImageLoader.loadWebDavImage(
                        context = imageView.context,
                        imageUri = photo.imageUri,
                        imageView = imageView,
                        progressBar = null,
                        limitSize = false,
                        isWebtoonReader = true,
                        width = if (photo.width > 0) photo.width else null,
                        height = if (photo.height > 0) photo.height else null,
                        onDimensionsReady = onDimensionsReady
                    )
                }
            },
            onRelease = WebDavImageLoader::clear
        )
    }
}
