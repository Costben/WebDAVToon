// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.autoworkflow

import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import erl.webdavtoon.AutoWorkflowFolder
import erl.webdavtoon.AutoWorkflowImage
import erl.webdavtoon.Folder
import erl.webdavtoon.Photo
import erl.webdavtoon.R
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.WebDavImageLoader
import erl.webdavtoon.autoWorkflowFileName
import erl.webdavtoon.ui.component.GalleryTopAppBar
import erl.webdavtoon.ui.component.GlassTopBarScaffold
import erl.webdavtoon.ui.component.HighlightedTopBarAction
import erl.webdavtoon.ui.component.PreviewTilePlaceholder
import erl.webdavtoon.ui.screen.waterfall.FolderCardExtraHeight
import erl.webdavtoon.ui.screen.waterfall.FollowZoomWaterfallLayout
import erl.webdavtoon.ui.screen.waterfall.MediaCardExtraHeight
import erl.webdavtoon.ui.screen.waterfall.MediaCardImageMargin
import erl.webdavtoon.ui.screen.waterfall.MediaCardMiuix
import erl.webdavtoon.ui.screen.waterfall.MediaCardOverlay
import erl.webdavtoon.ui.screen.waterfall.MixedWaterfallItemUi
import erl.webdavtoon.ui.screen.waterfall.rememberFollowZoomGridState
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
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
 * The page is the folder page's grid, one level of detail deeper: folder and image tiles are the
 * app's own waterfall cards, so the only AutoWorkflow-specific chrome is the task badge and the
 * original-image inset on an image tile, plus the comparison overlay behind a tap.
 */
@Composable
fun AutoWorkflowScreen(
    state: AutoWorkflowUiState,
    actions: AutoWorkflowActions,
    modifier: Modifier = Modifier,
) {
    var compareImage by remember { mutableStateOf<AutoWorkflowImage?>(null) }
    val context = LocalContext.current
    val settings = remember { SettingsManager(context) }
    var columns by remember { mutableIntStateOf(settings.getGridColumns()) }
    val onColumnsChanged: (Int) -> Unit = { newColumns ->
        settings.setGridColumns(newColumns)
        columns = newColumns
    }

    val rootTitle = stringResource(R.string.autoworkflow_output_title)
    // Derived from the target path, not from `breadcrumbs`: those still describe the previous
    // level until the request returns, which made the title flash the parent's name mid-load.
    val title = state.relPath.substringAfterLast('/').takeIf { it.isNotBlank() } ?: rootTitle

    val goUpOrBack = {
        if (state.relPath.isNotEmpty()) actions.onUp() else actions.onBack()
    }
    BackHandler { goUpOrBack() }

    GlassTopBarScaffold(
        modifier = modifier,
        topBar = {
            GalleryTopAppBar(
                title = title,
                navigationIcon = {
                    HighlightedTopBarAction(
                        icon = COUIIcons.Light.Back,
                        contentDescription = stringResource(R.string.back),
                        onClick = goUpOrBack,
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
        AutoWorkflowLevels(
            state = state,
            columns = columns,
            onColumnsChanged = onColumnsChanged,
            topInset = paddingValues.calculateTopPadding(),
            actions = actions,
            onOpenImage = { compareImage = it },
        )
    }

    compareImage?.let { image ->
        CompareOverlay(image = image, onDismiss = { compareImage = null })
    }
}

/**
 * Slides one directory level out and the next one in.
 *
 * The level being left is drawn from [AutoWorkflowUiState.previousLevel] — the state snapshot
 * taken when navigation started — so the outgoing page keeps its own grid and does not flash the
 * new directory's empty loading state.
 */
@Composable
private fun AutoWorkflowLevels(
    state: AutoWorkflowUiState,
    columns: Int,
    onColumnsChanged: (Int) -> Unit,
    topInset: Dp,
    actions: AutoWorkflowActions,
    onOpenImage: (AutoWorkflowImage) -> Unit,
) {
    AnimatedContent(
        targetState = state.relPath,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val forward = levelDepth(targetState) > levelDepth(initialState)
            val slide = tween<IntOffset>(durationMillis = 280, easing = FastOutSlowInEasing)
            val fade = tween<Float>(durationMillis = 220)
            if (forward) {
                (slideInHorizontally(slide) { it / 4 } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(slide) { -it / 4 } + fadeOut(fade))
            } else {
                (slideInHorizontally(slide) { -it / 4 } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(slide) { it / 4 } + fadeOut(fade))
            }
        },
        label = "autoworkflow-level",
    ) { relPath ->
        val isActive = relPath == state.relPath
        val level = when {
            isActive -> state.levelState()
            else -> state.previousLevel?.takeIf { it.relPath == relPath }
                ?: AutoWorkflowLevelState(relPath = relPath, loading = true)
        }
        AutoWorkflowLevel(
            level = level,
            isActive = isActive,
            columns = columns,
            onColumnsChanged = onColumnsChanged,
            topInset = topInset,
            actions = actions,
            onOpenImage = onOpenImage,
        )
    }
}

private fun levelDepth(relPath: String): Int =
    if (relPath.isBlank()) 0 else relPath.count { it == '/' } + 1

@Composable
private fun AutoWorkflowLevel(
    level: AutoWorkflowLevelState,
    isActive: Boolean,
    columns: Int,
    onColumnsChanged: (Int) -> Unit,
    topInset: Dp,
    actions: AutoWorkflowActions,
    onOpenImage: (AutoWorkflowImage) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val cells = remember(level.folders, level.images) { level.toCells() }
        when {
            !level.isConfigured -> MessagePanel(
                message = stringResource(R.string.autoworkflow_configure_first),
                actionLabel = stringResource(R.string.autoworkflow_open_settings),
                onAction = actions.onOpenSettings,
            )

            level.loading && cells.isEmpty() -> CenteredProgress()

            level.error != null && cells.isEmpty() -> MessagePanel(
                message = level.error.orEmpty(),
                actionLabel = stringResource(R.string.autoworkflow_retry),
                onAction = actions.onRefresh,
            )

            cells.isEmpty() -> MessagePanel(
                message = stringResource(R.string.autoworkflow_empty),
                actionLabel = stringResource(R.string.autoworkflow_retry),
                onAction = actions.onRefresh,
            )

            else -> key(level.relPath) {
                AutoWorkflowGrid(
                    cells = cells,
                    columns = columns,
                    onColumnsChanged = onColumnsChanged,
                    isRefreshing = level.loading,
                    isActive = isActive,
                    topInset = topInset,
                    onRefresh = actions.onRefresh,
                    onLoadMore = actions.onLoadMore,
                    onOpenFolder = actions.onOpenFolder,
                    onOpenImage = onOpenImage,
                )
            }
        }
    }
}

/**
 * The output grid.
 *
 * Rebuilt per directory ([key] on the relative path) so entering a folder starts at the top, the
 * same way opening a folder does today.
 */
@Composable
private fun AutoWorkflowGrid(
    cells: List<AutoWorkflowCell>,
    columns: Int,
    onColumnsChanged: (Int) -> Unit,
    isRefreshing: Boolean,
    isActive: Boolean,
    topInset: Dp,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenImage: (AutoWorkflowImage) -> Unit,
) {
    val zoomState = rememberFollowZoomGridState(
        columns = columns,
        minColumns = 1,
        maxColumns = 4,
        onColumnsChanged = onColumnsChanged,
    )
    val aspectRatios = remember(cells) { cells.map { it.item.aspectRatio } }
    val extraHeights = remember(cells) {
        cells.map { cell ->
            if (cell is AutoWorkflowCell.FolderCell) FolderCardExtraHeight else MediaCardExtraHeight
        }
    }
    LaunchedEffect(zoomState, cells.size, isActive) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow { zoomState.scrollOffset }.collect {
            val total = cells.size
            if (total > 0 && zoomState.lastVisibleIndex >= total - 16) onLoadMore()
        }
    }

    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset),
    ) {
        FollowZoomWaterfallLayout(
            itemCount = cells.size,
            aspectRatios = aspectRatios,
            columns = columns,
            minColumns = 1,
            maxColumns = 4,
            onColumnsChanged = onColumnsChanged,
            spacing = 12.dp,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = topInset + 12.dp,
                end = 16.dp,
                bottom = 12.dp,
            ),
            state = zoomState,
            itemExtraHeights = extraHeights,
            itemHorizontalPadding = MediaCardImageMargin,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) { index, widthPx, heightPx ->
            val cell = cells.getOrNull(index) ?: return@FollowZoomWaterfallLayout
            when (cell) {
                is AutoWorkflowCell.FolderCell -> MediaCardMiuix(
                    item = cell.item,
                    showFilename = true,
                    isSelectionMode = false,
                    onClick = { onOpenFolder(cell.source.relPath) },
                    onLongClick = {},
                    targetWidthPx = widthPx,
                    targetHeightPx = heightPx,
                    fillHeight = true,
                    isZooming = zoomState.isZoomActive,
                    folderSubtitle = folderSummary(cell.source),
                )

                is AutoWorkflowCell.ImageCell -> MediaCardMiuix(
                    item = cell.item,
                    showFilename = true,
                    isSelectionMode = false,
                    onClick = { onOpenImage(cell.source) },
                    onLongClick = {},
                    targetWidthPx = widthPx,
                    targetHeightPx = heightPx,
                    fillHeight = true,
                    isZooming = zoomState.isZoomActive,
                    overlay = imageOverlay(cell.source),
                )
            }
        }
    }
}

/** One cell of the output grid: an output folder or one generated image. */
private sealed interface AutoWorkflowCell {
    val item: MixedWaterfallItemUi

    data class FolderCell(
        val source: AutoWorkflowFolder,
        override val item: MixedWaterfallItemUi.FolderItem,
    ) : AutoWorkflowCell

    data class ImageCell(
        val source: AutoWorkflowImage,
        override val item: MixedWaterfallItemUi.MediaItem,
    ) : AutoWorkflowCell
}

private const val IMAGE_TILE_ASPECT = 0.75f

private fun AutoWorkflowLevelState.toCells(): List<AutoWorkflowCell> =
    folders.map { folder ->
        val path = folder.relPath
        AutoWorkflowCell.FolderCell(
            source = folder,
            item = MixedWaterfallItemUi.FolderItem(
                folder = Folder(
                    path = path,
                    name = folder.name,
                    isLocal = true,
                    photoCount = folder.directImageCount,
                    hasSubFolders = folder.subfolderCount > 0,
                ),
                key = "folder:$path",
                name = folder.name,
                path = path,
                isLocal = true,
                photoCount = folder.directImageCount,
                previewUris = folder.previewUrls,
                aspectRatio = 1f,
            ),
        )
    } + images.mapNotNull { image ->
        val url = image.thumbUrl ?: image.imageUrl ?: return@mapNotNull null
        val uri = Uri.parse(url)
        AutoWorkflowCell.ImageCell(
            source = image,
            item = MixedWaterfallItemUi.MediaItem(
                photo = Photo(
                    id = image.relPath,
                    imageUri = uri,
                    title = image.name,
                    isLocal = true,
                    size = image.size,
                ),
                key = "image:${image.relPath}",
                id = image.relPath,
                uri = uri,
                title = image.name,
                isLocal = true,
                isVideo = false,
                durationText = "",
                aspectRatio = IMAGE_TILE_ASPECT,
                isFavorite = false,
            ),
        )
    }

@Composable
private fun folderSummary(folder: AutoWorkflowFolder): String = when {
    folder.taskCount <= 0 && folder.directImageCount > 0 ->
        stringResource(R.string.autoworkflow_folder_images_no_tasks, folder.directImageCount)

    folder.taskCount <= 0 && folder.subfolderCount > 0 ->
        stringResource(R.string.autoworkflow_folder_subfolders_no_tasks, folder.subfolderCount)

    folder.directImageCount > 0 ->
        stringResource(
            R.string.autoworkflow_folder_images_tasks,
            folder.directImageCount,
            folder.taskCount,
        )

    folder.subfolderCount > 0 ->
        stringResource(
            R.string.autoworkflow_folder_subfolders_tasks,
            folder.subfolderCount,
            folder.taskCount,
        )

    else -> stringResource(R.string.autoworkflow_folder_images, folder.directImageCount)
}

@Composable
private fun imageOverlay(image: AutoWorkflowImage): MediaCardOverlay {
    val task = image.task
    return MediaCardOverlay(
        badgeText = if (task != null) {
            stringResource(R.string.autoworkflow_task_badge, task.id)
        } else {
            stringResource(R.string.autoworkflow_unlinked)
        },
        badgeHighlighted = task != null,
        sourceThumbUrl = task?.sourceThumbUrl ?: task?.sourceImageUrl,
    )
}

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
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
                        task?.sourcePath?.let(::autoWorkflowFileName)?.let {
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
