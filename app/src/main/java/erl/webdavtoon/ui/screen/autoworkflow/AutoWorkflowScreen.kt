// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.autoworkflow

import android.net.Uri
import android.text.format.Formatter
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import erl.webdavtoon.AutoWorkflowFolder
import erl.webdavtoon.AutoWorkflowImage
import erl.webdavtoon.R
import erl.webdavtoon.WebDavImageLoader
import erl.webdavtoon.autoWorkflowFileName
import erl.webdavtoon.ui.component.GalleryTopAppBar
import erl.webdavtoon.ui.component.GlassTopBarScaffold
import erl.webdavtoon.ui.component.HighlightedTopBarAction
import erl.webdavtoon.ui.component.PreviewTileCornerRadius
import erl.webdavtoon.ui.component.PreviewTilePlaceholder
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.theme.COUITheme

/** Callbacks the AutoWorkflow output browser needs from its host. */
data class AutoWorkflowActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onUp: () -> Unit,
    val onOpenFolder: (relPath: String) -> Unit,
    val onLoadMore: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * Browses the ComfyUI output folder through the AutoWorkflow backend.
 *
 * Each generated image carries the task that produced it, so the grid can show the task number
 * on the tile and the original input image as a small inset. Tapping a tile opens a full-screen
 * comparison of original vs. generated.
 */
@Composable
fun AutoWorkflowScreen(
    state: AutoWorkflowUiState,
    actions: AutoWorkflowActions,
    modifier: Modifier = Modifier,
) {
    var compareImage by remember { mutableStateOf<AutoWorkflowImage?>(null) }
    val rootTitle = stringResource(R.string.autoworkflow_output_title)
    val title = if (state.isRoot) {
        rootTitle
    } else {
        state.breadcrumbs.lastOrNull()?.name?.takeIf { it.isNotBlank() } ?: rootTitle
    }

    BackHandler {
        if (state.relPath.isNotEmpty()) actions.onUp() else actions.onBack()
    }

    GlassTopBarScaffold(
        modifier = modifier,
        topBar = {
            GalleryTopAppBar(
                title = title,
                navigationIcon = {
                    HighlightedTopBarAction(
                        icon = COUIIcons.Light.Back,
                        contentDescription = stringResource(R.string.back),
                        onClick = actions.onBack,
                    )
                },
                actions = {
                    HighlightedTopBarAction(
                        icon = COUIIcons.Light.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = actions.onRefresh,
                    )
                    HighlightedTopBarAction(
                        icon = COUIIcons.Light.Settings,
                        contentDescription = stringResource(R.string.settings),
                        onClick = actions.onOpenSettings,
                    )
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            when {
                !state.isConfigured -> MessagePanel(
                    message = stringResource(R.string.autoworkflow_configure_first),
                    actionLabel = stringResource(R.string.autoworkflow_open_settings),
                    onAction = actions.onOpenSettings,
                )

                state.loading && state.folders.isEmpty() && state.images.isEmpty() ->
                    LoadingPanel()

                state.error != null && state.folders.isEmpty() && state.images.isEmpty() ->
                    MessagePanel(
                        message = state.error.orEmpty(),
                        actionLabel = stringResource(R.string.autoworkflow_retry),
                        onAction = actions.onRefresh,
                    )

                state.empty -> MessagePanel(
                    message = stringResource(R.string.autoworkflow_empty),
                    actionLabel = stringResource(R.string.autoworkflow_retry),
                    onAction = actions.onRefresh,
                )

                else -> {
                    state.error?.let { message ->
                        Text(
                            text = message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                            style = COUITheme.textStyles.footnote2,
                            color = COUITheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PathBar(state = state, actions = actions)
                    StatsLine(state = state)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 4.dp,
                            bottom = paddingValues.calculateBottomPadding() + 24.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.folders.isNotEmpty()) {
                            item(key = "header-folders", span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader(stringResource(R.string.autoworkflow_folders))
                            }
                            items(
                                items = state.folders,
                                key = { "folder:${it.relPath}" },
                            ) { folder ->
                                FolderTile(
                                    folder = folder,
                                    onClick = { actions.onOpenFolder(folder.relPath) },
                                )
                            }
                        }
                        if (state.images.isNotEmpty()) {
                            item(key = "header-images", span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader(
                                    stringResource(
                                        R.string.autoworkflow_images_progress,
                                        state.images.size,
                                        state.totalImages,
                                    ),
                                )
                            }
                            items(
                                items = state.images,
                                key = { "image:${it.relPath}" },
                            ) { image ->
                                ImageTile(
                                    image = image,
                                    onClick = { compareImage = image },
                                )
                            }
                        }
                        if (state.hasMore || state.loadingMore) {
                            item(key = "load-more", span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state.loadingMore) {
                                        CircularProgressIndicator()
                                    } else {
                                        TextButton(
                                            text = stringResource(R.string.autoworkflow_load_more),
                                            onClick = actions.onLoadMore,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    compareImage?.let { image ->
        CompareOverlay(image = image, onDismiss = { compareImage = null })
    }
}

@Composable
private fun PathBar(state: AutoWorkflowUiState, actions: AutoWorkflowActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(
            text = stringResource(R.string.autoworkflow_up),
            onClick = actions.onUp,
            enabled = state.relPath.isNotEmpty(),
        )
        Crumb(text = stringResource(R.string.autoworkflow_output_title), onClick = { actions.onOpenFolder("") })
        // The backend's first crumb is the output root itself, which the crumb above already is.
        state.breadcrumbs.drop(1).forEach { crumb ->
            Icon(
                imageVector = COUIIcons.Light.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            Crumb(text = crumb.name, onClick = { actions.onOpenFolder(crumb.relPath) })
        }
    }
}

@Composable
private fun Crumb(text: String, onClick: () -> Unit) {
    TextButton(text = text, onClick = onClick, minHeight = 32.dp)
}

@Composable
private fun StatsLine(state: AutoWorkflowUiState) {
    val parts = buildList {
        if (state.folderCount > 0) add(stringResource(R.string.autoworkflow_folders_count, state.folderCount))
        add(stringResource(R.string.autoworkflow_total_images, state.totalImages))
        if (state.linkedLoaded > 0) add(stringResource(R.string.autoworkflow_linked_count, state.linkedLoaded))
        if (state.taskCount > 0) add(stringResource(R.string.autoworkflow_dir_tasks, state.taskCount))
        if (state.serverUrl.isNotBlank()) add(state.serverUrl)
    }
    Text(
        text = parts.joinToString(" · "),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        style = COUITheme.textStyles.footnote2,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 8.dp),
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun FolderTile(folder: AutoWorkflowFolder, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = folder.name,
                style = COUITheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(stringResource(R.string.autoworkflow_folder_images, folder.directImageCount))
                    if (folder.subfolderCount > 0) {
                        append(" · ")
                        append(stringResource(R.string.autoworkflow_folder_subfolders, folder.subfolderCount))
                    }
                },
                modifier = Modifier.padding(top = 4.dp),
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folderTaskSummary(folder),
                modifier = Modifier.padding(top = 2.dp),
                style = COUITheme.textStyles.footnote2,
                color = if (folder.taskCount > 0) {
                    COUITheme.colorScheme.primary
                } else {
                    COUITheme.colorScheme.onSurfaceVariantSummary
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun folderTaskSummary(folder: AutoWorkflowFolder): String {
    if (folder.taskCount <= 0) return stringResource(R.string.autoworkflow_no_tasks)
    val tasks = stringResource(R.string.autoworkflow_folder_tasks, folder.taskCount)
    val first = folder.firstTaskId
    val last = folder.lastTaskId
    if (first == null || last == null) return tasks
    return "$tasks · " + stringResource(R.string.autoworkflow_folder_task_range, first, last)
}

@Composable
private fun ImageTile(image: AutoWorkflowImage, onClick: () -> Unit) {
    val context = LocalContext.current
    val task = image.task
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(PreviewTileCornerRadius))
                    .background(PreviewTilePlaceholder),
            ) {
                RemoteImage(
                    url = image.thumbUrl ?: image.imageUrl,
                    contentScale = ImageView.ScaleType.CENTER_CROP,
                    modifier = Modifier.fillMaxSize(),
                )
                Badge(
                    text = if (task != null) {
                        stringResource(R.string.autoworkflow_task_badge, task.id)
                    } else {
                        stringResource(R.string.autoworkflow_unlinked)
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    highlighted = task != null,
                )
                val sourceUrl = task?.sourceThumbUrl ?: task?.sourceImageUrl
                if (sourceUrl != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .size(52.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(PreviewTilePlaceholder)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(6.dp),
                            ),
                    ) {
                        RemoteImage(
                            url = sourceUrl,
                            contentScale = ImageView.ScaleType.CENTER_CROP,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = image.name,
                    style = COUITheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = task?.sourcePath?.let(::fileNameOrNull)
                        ?: stringResource(R.string.autoworkflow_no_source),
                    modifier = Modifier.padding(top = 2.dp),
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.autoworkflow_size_time,
                        Formatter.formatFileSize(context, image.size),
                        formatTime(image.mtime),
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (highlighted) Color(0xCC0A59F7) else Color(0x99000000),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = COUITheme.textStyles.footnote2,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun MessagePanel(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message,
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
private fun LoadingPanel() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.autoworkflow_loading),
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * Loads an http(s) asset through the app's Glide pipeline.
 *
 * [WebDavImageLoader.loadLocalImage] is used deliberately: it applies the gallery's size
 * limits and cross-fades, and the registered OkHttp integration is what makes a plain URL
 * work without WebDAV credentials.
 */
@Composable
private fun RemoteImage(
    url: String?,
    contentScale: ImageView.ScaleType,
    modifier: Modifier = Modifier,
) {
    if (url.isNullOrBlank()) return
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply { scaleType = contentScale }
        },
        update = { imageView ->
            imageView.scaleType = contentScale
            val bound = imageView.getTag(R.id.tag_media_bind_key) as? String
            if (bound == url && imageView.drawable != null) return@AndroidView
            imageView.setTag(R.id.tag_media_bind_key, url)
            WebDavImageLoader.loadLocalImage(
                context = imageView.context,
                imageUri = Uri.parse(url),
                imageView = imageView,
                isFolderPreview = true,
                limitSize = true,
            )
        },
    )
}

@Composable
private fun CompareOverlay(image: AutoWorkflowImage, onDismiss: () -> Unit) {
    var sideBySide by remember { mutableStateOf(false) }
    val task = image.task
    val generatedUrl = image.imageUrl ?: image.thumbUrl
    val sourceUrl = task?.sourceImageUrl ?: task?.sourceThumbUrl

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (task != null) {
                            stringResource(R.string.autoworkflow_task_badge, task.id) + " · " + image.name
                        } else {
                            image.name
                        },
                        style = COUITheme.textStyles.body2,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = buildList {
                        task?.sourcePath?.let(::fileNameOrNull)?.let {
                            add(stringResource(R.string.autoworkflow_source_prefix, it))
                        }
                        task?.workflowFile?.takeIf { it.isNotBlank() }?.let { add(it) }
                        task?.seed?.let { add(stringResource(R.string.autoworkflow_seed_prefix, it)) }
                        if (task == null) add(stringResource(R.string.autoworkflow_unlinked))
                    }.joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            modifier = Modifier.padding(top = 2.dp),
                            style = COUITheme.textStyles.footnote2,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = COUIIcons.Light.Close,
                        contentDescription = stringResource(R.string.autoworkflow_close),
                        tint = Color.White,
                    )
                }
            }

            if (sideBySide) {
                Row(modifier = Modifier.weight(1f)) {
                    ComparePane(
                        label = stringResource(R.string.autoworkflow_original),
                        url = sourceUrl,
                        modifier = Modifier.weight(1f),
                    )
                    ComparePane(
                        label = stringResource(R.string.autoworkflow_generated),
                        url = generatedUrl,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    ComparePane(
                        label = stringResource(R.string.autoworkflow_original),
                        url = sourceUrl,
                        modifier = Modifier.weight(1f),
                    )
                    ComparePane(
                        label = stringResource(R.string.autoworkflow_generated),
                        url = generatedUrl,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayButton(
                    label = stringResource(R.string.autoworkflow_toggle_layout),
                    onClick = { sideBySide = !sideBySide },
                    modifier = Modifier.weight(1f),
                )
                OverlayButton(
                    label = stringResource(R.string.autoworkflow_close),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OverlayButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.body2,
            color = Color.White,
        )
    }
}

@Composable
private fun ComparePane(label: String, url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141414)),
    ) {
        if (url != null) {
            RemoteImage(
                url = url,
                contentScale = ImageView.ScaleType.FIT_CENTER,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = stringResource(R.string.autoworkflow_no_source),
                modifier = Modifier.align(Alignment.Center),
                style = COUITheme.textStyles.footnote2,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Badge(
            text = label,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )
    }
}

/** Last path segment of a Windows or POSIX path. */
private fun fileNameOrNull(path: String): String? = autoWorkflowFileName(path)

/** `2026-11-16T20:15:33` -> `11-16 20:15`, without pulling in java.time on minSdk 24. */
private fun formatTime(raw: String): String {
    val cleaned = raw.replace(' ', 'T')
    if (cleaned.length >= 16 && cleaned.getOrNull(4) == '-' && cleaned.getOrNull(7) == '-') {
        return cleaned.substring(5, 16).replace('T', ' ')
    }
    return raw
}
