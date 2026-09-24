package erl.webdavtoon.ui.screen.folder

import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import erl.webdavtoon.R
import erl.webdavtoon.MediaType
import erl.webdavtoon.WebDavImageLoader
import erl.webdavtoon.detectMediaTypeByUri
import erl.webdavtoon.ui.component.FolderCardLabelHeight
import erl.webdavtoon.ui.component.PreviewTileCornerRadius
import erl.webdavtoon.ui.component.PreviewTilePlaceholder
import erl.webdavtoon.ui.screen.waterfall.MediaCardImageMargin
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Folder
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * HyperOS-styled folder card with a four-slot preview mosaic.
 */
@Composable
fun FolderCardMiuix(
    folder: FolderItemUi,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onVisibilityChanged: ((path: String, visible: Boolean) -> Unit)? = null,
    fillHeight: Boolean = false,
) {
    DisposableEffect(folder.path, onVisibilityChanged) {
        onVisibilityChanged?.invoke(folder.path, true)
        onDispose { onVisibilityChanged?.invoke(folder.path, false) }
    }

    Card(
        modifier = (if (fillHeight) modifier.fillMaxSize() else modifier.fillMaxWidth())
            .pointerInput(folder.path, isSelectionMode) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            },
    ) {
        Column(modifier = Modifier.padding(MediaCardImageMargin)) {
            MiuixPreviewGrid(
                folder = folder,
                selected = folder.isSelected,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = folder.name.trimEnd('/'),
                modifier = Modifier
                    .padding(start = 4.dp, end = 4.dp, top = 6.dp)
                    .height(FolderCardLabelHeight),
                style = COUITheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folderInfo(folder),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                style = COUITheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MiuixPreviewGrid(
    folder: FolderItemUi,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        if (folder.previewUris.isEmpty()) {
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
                MiuixPreviewRow(folder, 0)
                MiuixPreviewRow(folder, 2)
            }
        }
        if (selected) {
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
        }
    }
}

@Composable
private fun ColumnScope.MiuixPreviewRow(folder: FolderItemUi, startIndex: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MiuixPreviewSlot(folder, startIndex, Modifier.weight(1f))
        MiuixPreviewSlot(folder, startIndex + 1, Modifier.weight(1f))
    }
}

@Composable
private fun MiuixPreviewSlot(folder: FolderItemUi, index: Int, modifier: Modifier) {
    val uri = folder.previewUris.getOrNull(index)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(PreviewTileCornerRadius))
            .background(PreviewTilePlaceholder),
    ) {
        if (uri != null) {
            FolderPreviewImage(uri = uri, isLocal = folder.isLocal, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun FolderPreviewImage(uri: String, isLocal: Boolean, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        },
        update = { imageView ->
            val lastUri = imageView.getTag(R.id.tag_media_bind_key) as? String
            if (lastUri == uri && imageView.drawable != null) return@AndroidView
            imageView.setTag(R.id.tag_media_bind_key, uri)

            val imageUri = Uri.parse(uri)
            val hasExisting = imageView.drawable != null
            val crossFadeMs = if (hasExisting) 300 else 0
            if (detectMediaTypeByUri(imageUri) == MediaType.VIDEO && isLocal) {
                WebDavImageLoader.loadLocalVideoThumbnail(
                    imageView.context,
                    imageUri,
                    imageView,
                    isFolderPreview = true,
                    preserveCurrentDrawable = true,
                    crossFadeDurationMs = crossFadeMs,
                )
            } else if (detectMediaTypeByUri(imageUri) == MediaType.VIDEO) {
                WebDavImageLoader.loadWebDavVideoThumbnail(
                    imageView.context,
                    imageUri,
                    imageView,
                    isFolderPreview = true,
                    preserveCurrentDrawable = true,
                    crossFadeDurationMs = crossFadeMs,
                )
            } else if (isLocal) {
                WebDavImageLoader.loadLocalImage(
                    imageView.context,
                    imageUri,
                    imageView,
                    isFolderPreview = true,
                    preserveCurrentDrawable = true,
                    crossFadeDurationMs = crossFadeMs,
                )
            } else {
                WebDavImageLoader.loadWebDavImage(
                    imageView.context,
                    imageUri,
                    imageView,
                    isFolderPreview = true,
                    preserveCurrentDrawable = true,
                    crossFadeDurationMs = crossFadeMs,
                )
            }
        },
        onRelease = { imageView ->
            imageView.setTag(R.id.tag_media_bind_key, null)
            WebDavImageLoader.clear(imageView)
        },
    )
}

@Composable
private fun folderInfo(folder: FolderItemUi): String = if (folder.isLocal) {
    stringResource(R.string.photos_local_suffix, folder.photoCount)
} else {
    stringResource(R.string.webdav_folder)
}
