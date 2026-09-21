package erl.webdavtoon.ui.screen.waterfall

import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.ceil
import erl.webdavtoon.Folder
import erl.webdavtoon.MediaType
import erl.webdavtoon.R
import erl.webdavtoon.WebDavImageLoader
import erl.webdavtoon.detectMediaTypeByUri
import erl.webdavtoon.ui.component.FolderCardLabelHeight
import erl.webdavtoon.ui.component.PreviewTileCornerRadius
import erl.webdavtoon.ui.component.PreviewTilePlaceholder
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.FavoritesFill
import io.github.suqi8.coui.kmp.icon.extended.Folder
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme

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
    targetWidthPx: Int = 0,
    targetHeightPx: Int = 0,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
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
                targetWidthPx = targetWidthPx,
                targetHeightPx = targetHeightPx,
                modifier = modifier,
                fillHeight = fillHeight,
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
                fillHeight = fillHeight,
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
    fillHeight: Boolean = false,
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
            fillHeight = fillHeight,
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
    fillHeight: Boolean = false,
) {
    DisposableEffect(item.key, onVisibilityChanged) {
        onVisibilityChanged?.invoke(item.folder, true)
        onDispose { onVisibilityChanged?.invoke(item.folder, false) }
    }

    val cardShape = RoundedCornerShape(12.dp)
    val innerShape = RoundedCornerShape(12.dp)
    val innerPadding = if (showFilename) 8.dp else 0.dp

    BoxWithConstraints(
        modifier = if (fillHeight) modifier.fillMaxSize() else modifier.fillMaxWidth(),
    ) {
        // Round the lane width to a whole dp so both grid lanes produce exactly the same
        // preview height. With a sub-pixel lane width the two columns end up 1px different
        // per row, which the staggered grid accumulates until it misplaces the last folder.
        val previewSide = Dp(ceil(maxWidth.value)) - innerPadding * 2

        Card(
            modifier = (if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .clip(cardShape)
                .pointerInput(item.key, isSelectionMode) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() },
                    )
                },
        ) {
            Column(modifier = Modifier.padding(innerPadding)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (fillHeight) Modifier.weight(1f) else Modifier.height(previewSide))
                        .clip(innerShape),
                ) {
                    if (item.previewUris.isEmpty()) {
                        Icon(
                            imageVector = COUIIcons.Light.Folder,
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
                            imageVector = COUIIcons.Light.Ok,
                            contentDescription = null,
                            tint = COUITheme.colorScheme.primary,
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
                        modifier = Modifier
                            .padding(start = 4.dp, end = 4.dp, top = 6.dp)
                            .height(FolderCardLabelHeight),
                        style = COUITheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (item.isLocal) {
                            stringResource(R.string.photos_local_suffix, item.photoCount)
                        } else {
                            stringResource(R.string.webdav_folder)
                        },
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        style = COUITheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
    targetWidthPx: Int = 0,
    targetHeightPx: Int = 0,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
) {
    val cardShape = RoundedCornerShape(12.dp)
    val innerShape = RoundedCornerShape(12.dp)
    // Must be the unmodified ratio: the waterfall engine sized this cell from the very same
    // value, so clamping here would make the box a different shape than the image inside it
    // and leave a letterbox band showing through.
    val tileAspectRatio = item.aspectRatio.takeIf { it > 0f } ?: 1f

    Card(
        modifier = (if (fillHeight) modifier.fillMaxSize() else modifier.fillMaxWidth())
            .clip(cardShape)
            .pointerInput(item.key, isSelectionMode) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            },
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.weight(1f) else Modifier.aspectRatio(tileAspectRatio))
                    .clip(innerShape),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        ImageView(context).apply {
                            // Glide derives its downsample strategy from this scale type: with
                            // CENTER_CROP it decodes (and crops) to the exact request box, so the
                            // image's real aspect ratio would be lost. FIT_CENTER keeps the aspect
                            // ratio, which the waterfall layout needs to size each cell.
                            scaleType = ImageView.ScaleType.FIT_CENTER
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
                            val targetWidth = targetWidthPx.takeIf { it > 0 }
                            val targetHeight = targetHeightPx.takeIf { it > 0 }
                            if (item.isLocal) {
                                WebDavImageLoader.loadLocalImage(
                                    context = imageView.context,
                                    imageUri = item.uri,
                                    imageView = imageView,
                                    isWaterfall = true,
                                    width = targetWidth,
                                    height = targetHeight,
                                    onDimensionsReady = dimensionsCallback,
                                    preserveCurrentDrawable = true,
                                )
                            } else {
                                WebDavImageLoader.loadWebDavImage(
                                    context = imageView.context,
                                    imageUri = item.uri,
                                    imageView = imageView,
                                    isWaterfall = true,
                                    width = targetWidth,
                                    height = targetHeight,
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
                            imageVector = COUIIcons.Light.Play,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                        if (item.durationText.isNotBlank()) {
                            Text(
                                text = item.durationText,
                                color = Color.White,
                                style = COUITheme.textStyles.footnote1.copy(fontSize = 11.sp),
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
                            imageVector = COUIIcons.Light.FavoritesFill,
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
                        imageVector = COUIIcons.Light.Ok,
                        contentDescription = null,
                        tint = COUITheme.colorScheme.primary,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 2.dp)
                        .height(MediaCardLabelHeight),
                    style = COUITheme.textStyles.footnote1,
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
            .clip(RoundedCornerShape(PreviewTileCornerRadius))
            .background(PreviewTilePlaceholder),
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
