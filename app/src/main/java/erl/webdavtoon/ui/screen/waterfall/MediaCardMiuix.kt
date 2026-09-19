package erl.webdavtoon.ui.screen.waterfall

import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import erl.webdavtoon.Folder
import erl.webdavtoon.MediaType
import erl.webdavtoon.R
import erl.webdavtoon.WebDavImageLoader
import erl.webdavtoon.detectMediaTypeByUri
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HyperOS/Miuix-styled media/folder card for waterfall grid.
 */
@Composable
fun MediaCardMiuix(
    item: MixedWaterfallItemUi,
    showFilename: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDimensionsResolved: ((photoId: String, width: Int, height: Int) -> Unit)? = null,
    onFolderVisibilityChanged: ((folder: Folder, visible: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is MixedWaterfallItemUi.MediaItem -> {
            WaterfallMediaCardMiuix(
                item = item,
                showFilename = showFilename,
                isSelectionMode = isSelectionMode,
                onClick = onClick,
                onLongClick = onLongClick,
                onDimensionsResolved = onDimensionsResolved,
                modifier = modifier,
            )
        }
        is MixedWaterfallItemUi.FolderItem -> {
            WaterfallFolderCardMiuix(
                item = item,
                showFilename = showFilename,
                isSelectionMode = isSelectionMode,
                onClick = onClick,
                onLongClick = onLongClick,
                onVisibilityChanged = onFolderVisibilityChanged,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun WaterfallFolderCardMiuix(
    item: MixedWaterfallItemUi,
    showFilename: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onVisibilityChanged: ((folder: Folder, visible: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (item is MixedWaterfallItemUi.FolderItem) {
        WaterfallFolderCardMiuix(
            item = item,
            showFilename = showFilename,
            isSelectionMode = isSelectionMode,
            onClick = onClick,
            onLongClick = onLongClick,
            onVisibilityChanged = onVisibilityChanged,
            modifier = modifier,
        )
    }
}

@Composable
fun WaterfallFolderCardMiuix(
    item: MixedWaterfallItemUi.FolderItem,
    showFilename: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onVisibilityChanged: ((folder: Folder, visible: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(item.key, onVisibilityChanged) {
        onVisibilityChanged?.invoke(item.folder, true)
        onDispose { onVisibilityChanged?.invoke(item.folder, false) }
    }

    val cardShape = RoundedCornerShape(16.dp)
    val innerShape = if (showFilename) RoundedCornerShape(12.dp) else RoundedCornerShape(16.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .pointerInput(item.key, isSelectionMode) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            },
    ) {
        Column(modifier = Modifier.padding(if (showFilename) 6.dp else 0.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(innerShape)
                    .background(MiuixTheme.colorScheme.surfaceVariant),
            ) {
                if (item.previewUris.isEmpty()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ior_folder),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        MiuixFolderPreviewRow(item, 0)
                        MiuixFolderPreviewRow(item, 2)
                    }
                }

                if (item.isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x44000000)),
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_ior_check_circle),
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp),
                    )
                } else if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(22.dp)
                            .background(Color(0x33000000), CircleShape)
                            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                    )
                }
            }

            if (showFilename) {
                Text(
                    text = item.name.trimEnd('/'),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (item.isLocal) {
                        stringResource(R.string.photos_local_suffix, item.photoCount)
                    } else {
                        stringResource(R.string.webdav_folder)
                    },
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 1.dp, bottom = 2.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WaterfallMediaCardMiuix(
    item: MixedWaterfallItemUi.MediaItem,
    showFilename: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDimensionsResolved: ((photoId: String, width: Int, height: Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(16.dp)
    val innerShape = if (showFilename) RoundedCornerShape(12.dp) else RoundedCornerShape(16.dp)
    val clampedAspectRatio = item.aspectRatio.coerceIn(0.2f, 5.0f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .pointerInput(item.key, isSelectionMode) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            },
    ) {
        Column(modifier = Modifier.padding(if (showFilename) 6.dp else 0.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(clampedAspectRatio)
                    .clip(innerShape)
                    .background(MiuixTheme.colorScheme.surfaceVariant),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    update = { imageView ->
                        if (item.isVideo) {
                            if (item.isLocal) {
                                WebDavImageLoader.loadLocalVideoThumbnail(
                                    context = imageView.context,
                                    videoUri = item.uri,
                                    imageView = imageView,
                                    isFolderPreview = false,
                                    preserveCurrentDrawable = true,
                                )
                            } else {
                                WebDavImageLoader.loadWebDavVideoThumbnail(
                                    context = imageView.context,
                                    videoUri = item.uri,
                                    imageView = imageView,
                                    isFolderPreview = false,
                                    preserveCurrentDrawable = true,
                                )
                            }
                        } else {
                            val dimensionsCallback: ((Int, Int) -> Unit)? = onDimensionsResolved?.let { callback ->
                                { width, height -> callback(item.id, width, height) }
                            }
                            if (item.isLocal) {
                                WebDavImageLoader.loadLocalImage(
                                    context = imageView.context,
                                    imageUri = item.uri,
                                    imageView = imageView,
                                    isWaterfall = true,
                                    onDimensionsReady = dimensionsCallback,
                                    preserveCurrentDrawable = true,
                                )
                            } else {
                                WebDavImageLoader.loadWebDavImage(
                                    context = imageView.context,
                                    imageUri = item.uri,
                                    imageView = imageView,
                                    isWaterfall = true,
                                    onDimensionsReady = dimensionsCallback,
                                    preserveCurrentDrawable = true,
                                )
                            }
                        }
                    },
                    onRelease = WebDavImageLoader::clear,
                )

                // Video Badge: translucent pill at bottom-start
                if (item.isVideo) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x99000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ior_play_solid),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                        if (item.durationText.isNotBlank()) {
                            Text(
                                text = item.durationText,
                                color = Color.White,
                                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
                            )
                        }
                    }
                }

                // Favorite Badge: subtle heart icon badge at top-start
                if (item.isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color(0x99000000))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_heart_filled),
                            contentDescription = null,
                            tint = Color(0xFFFF4D4F),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                // Selection State: dim overlay + check circle or unselected circular outline
                if (item.isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x44000000)),
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_ior_check_circle),
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp),
                    )
                } else if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(22.dp)
                            .background(Color(0x33000000), CircleShape)
                            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                    )
                }
            }

            if (showFilename) {
                Text(
                    text = item.title,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.MiuixFolderPreviewRow(item: MixedWaterfallItemUi.FolderItem, startIndex: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MiuixFolderPreviewSlot(item, startIndex, Modifier.weight(1f))
        MiuixFolderPreviewSlot(item, startIndex + 1, Modifier.weight(1f))
    }
}

@Composable
private fun MiuixFolderPreviewSlot(item: MixedWaterfallItemUi.FolderItem, index: Int, modifier: Modifier) {
    val uri = item.previewUris.getOrNull(index)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x10000000)),
    ) {
        if (uri != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                },
                update = { imageView ->
                    val imageUri = Uri.parse(uri)
                    if (detectMediaTypeByUri(imageUri) == MediaType.VIDEO && item.isLocal) {
                        WebDavImageLoader.loadLocalVideoThumbnail(
                            imageView.context, imageUri, imageView, isFolderPreview = true,
                        )
                    } else if (detectMediaTypeByUri(imageUri) == MediaType.VIDEO) {
                        WebDavImageLoader.loadWebDavVideoThumbnail(
                            imageView.context, imageUri, imageView, isFolderPreview = true,
                        )
                    } else if (item.isLocal) {
                        WebDavImageLoader.loadLocalImage(
                            imageView.context, imageUri, imageView, isFolderPreview = true,
                        )
                    } else {
                        WebDavImageLoader.loadWebDavImage(
                            imageView.context, imageUri, imageView, isFolderPreview = true,
                        )
                    }
                },
                onRelease = WebDavImageLoader::clear,
            )
        }
    }
}
