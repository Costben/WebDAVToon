package erl.webdavtoon.ui.screen.folder

import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.viewinterop.AndroidView
import erl.webdavtoon.R
import erl.webdavtoon.MediaType
import erl.webdavtoon.WebDavImageLoader
import erl.webdavtoon.detectMediaTypeByUri

/**
 * Material 3 Expressive folder card with a four-slot preview mosaic.
 */
@Composable
fun FolderCardMaterial(
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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (folder.isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            MaterialPreviewGrid(
                folder = folder,
                selected = folder.isSelected,
                modifier = if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
                fillHeight = fillHeight,
            )
            Text(
                text = folder.name.trimEnd('/'),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = materialFolderInfo(folder),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MaterialPreviewGrid(
    folder: FolderItemUi,
    selected: Boolean,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
) {
    Box(
        modifier = modifier
            .then(if (fillHeight) Modifier else Modifier.aspectRatio(1f))
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (folder.previewUris.isEmpty()) {
            Icon(
                painter = painterResource(R.drawable.ic_ior_folder),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                MaterialPreviewRow(folder, 0)
                MaterialPreviewRow(folder, 2)
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x44000000)),
            )
            Icon(
                painter = painterResource(R.drawable.ic_ior_check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.MaterialPreviewRow(folder: FolderItemUi, startIndex: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MaterialPreviewSlot(folder, startIndex, Modifier.weight(1f))
        MaterialPreviewSlot(folder, startIndex + 1, Modifier.weight(1f))
    }
}

@Composable
private fun MaterialPreviewSlot(folder: FolderItemUi, index: Int, modifier: Modifier) {
    val uri = folder.previewUris.getOrNull(index)
    Box(modifier = modifier.fillMaxSize().background(Color(0x10000000))) {
        if (uri != null) {
            MaterialFolderPreviewImage(
                uri = uri,
                isLocal = folder.isLocal,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MaterialFolderPreviewImage(uri: String, isLocal: Boolean, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        },
        update = { imageView ->
            val imageUri = Uri.parse(uri)
            if (detectMediaTypeByUri(imageUri) == MediaType.VIDEO && isLocal) {
                WebDavImageLoader.loadLocalVideoThumbnail(
                    imageView.context, imageUri, imageView, isFolderPreview = true,
                )
            } else if (detectMediaTypeByUri(imageUri) == MediaType.VIDEO) {
                WebDavImageLoader.loadWebDavVideoThumbnail(
                    imageView.context, imageUri, imageView, isFolderPreview = true,
                )
            } else if (isLocal) {
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

@Composable
private fun materialFolderInfo(folder: FolderItemUi): String = if (folder.isLocal) {
    stringResource(R.string.photos_local_suffix, folder.photoCount)
} else {
    stringResource(R.string.webdav_folder)
}
