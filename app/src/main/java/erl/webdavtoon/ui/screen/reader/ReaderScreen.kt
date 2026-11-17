// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.webdavtoon.AutoWorkflowClient
import erl.webdavtoon.AutoWorkflowCompareStore
import erl.webdavtoon.FileUtils
import erl.webdavtoon.GestureType
import erl.webdavtoon.MediaShareHelper
import erl.webdavtoon.R
import erl.webdavtoon.SettingsManager
import erl.webdavtoon.ui.screen.autoworkflow.AutoWorkflowPendingAction
import erl.webdavtoon.ui.screen.autoworkflow.ConfirmActionDialog
import erl.webdavtoon.ui.screen.autoworkflow.ReeditPromptDialog
import erl.webdavtoon.ui.screen.autoworkflow.ResubmitDialog
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import kotlinx.coroutines.launch

/**
 * Modern Compose-based ReaderScreen coordinating:
 * 1. Dual reader engines (CardReader & WebtoonReader)
 * 2. Immersive full-screen system bars control via WindowInsetsControllerCompat
 * 3. Dual-track floating overlay (Miuix & Material 3)
 * 4. Image actions: save, share, favorite, mode toggle, slideshow, orientation lock
 */
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val window = activity?.window

    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    DisposableEffect(uiState.isImmersive, insetsController) {
        if (insetsController != null) {
            if (uiState.isImmersive) {
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val currentPhoto = uiState.currentPhoto
    val currentPhotoTitle = currentPhoto?.title ?: ""
    val compareInfo = currentPhoto?.let { AutoWorkflowCompareStore.get(it.id) }
    var swappedPhotoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val displayPhotos = remember(uiState.photos, swappedPhotoIds) {
        if (swappedPhotoIds.isEmpty()) {
            uiState.photos
        } else {
            uiState.photos.map { photo ->
                if (photo.id in swappedPhotoIds) {
                    val sourceUrl = AutoWorkflowCompareStore.get(photo.id)?.sourceUrl
                    if (sourceUrl != null) photo.copy(imageUri = Uri.parse(sourceUrl)) else photo
                } else {
                    photo
                }
            }
        }
    }
    val settingsManager = remember(context) { SettingsManager(context) }
    val downloadSuccessMessage = stringResource(R.string.download_success)
    val downloadFailedMessage = stringResource(R.string.download_failed)

    val onSaveImage: () -> Unit = remember(
        context,
        currentPhoto,
        downloadSuccessMessage,
        downloadFailedMessage
    ) {
        {
            if (currentPhoto != null) {
                coroutineScope.launch {
                    val success = FileUtils.downloadImage(context, currentPhoto)
                    val message = if (success) downloadSuccessMessage else downloadFailedMessage
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val onShare: () -> Unit = remember(context, coroutineScope, settingsManager, currentPhoto) {
        {
            if (currentPhoto != null) {
                MediaShareHelper.sharePhotos(
                    context = context,
                    scope = coroutineScope,
                    settingsManager = settingsManager,
                    photos = listOf(currentPhoto)
                )
            }
        }
    }

    val modalOverlayVisible = uiState.isGesturePanelVisible || uiState.isPhotoInfoVisible

    // Resubmit and re-edit only exist for output images that carry a generation task.
    val resubmitTaskId = compareInfo?.taskId
    var reeditDialogTaskId by remember { mutableStateOf<Long?>(null) }
    var resubmitDialogTaskId by remember { mutableStateOf<Long?>(null) }
    var pendingResubmit by remember { mutableStateOf<AutoWorkflowPendingAction?>(null) }
    var resubmitRunning by remember { mutableStateOf(false) }
    val autoWorkflowUrl = remember(context) { SettingsManager(context).getAutoWorkflowUrl() }
    val resubmitConfirmText = stringResource(R.string.autoworkflow_resubmit_confirm)
    val reeditConfirmText = stringResource(R.string.autoworkflow_reedit_confirm)
    val resubmitDeleteOldText = stringResource(R.string.autoworkflow_resubmit_delete_old_confirm)
    val resubmitEditLabel = stringResource(R.string.autoworkflow_resubmit_edit)
    val resubmitUpscaleLabel = stringResource(R.string.autoworkflow_resubmit_upscale)
    val resubmitDoneText = stringResource(R.string.autoworkflow_resubmit_done)
    val resubmitFailedText = stringResource(R.string.autoworkflow_action_failed_unknown)
    val notConfiguredText = stringResource(R.string.autoworkflow_configure_first)

    // The reader is shared by every album, so the mutation path stays local to it: the same
    // two-step request the output browser uses (preview, then confirm) runs against the saved
    // AutoWorkflow address, and nothing is written before the second step.
    fun runResubmit(
        taskId: Long,
        flow: String,
        deleteOldOutput: Boolean,
        confirm: Boolean,
        promptText: String? = null,
    ) {
        if (resubmitRunning) return
        if (autoWorkflowUrl.isBlank()) {
            Toast.makeText(context, notConfiguredText, Toast.LENGTH_SHORT).show()
            return
        }
        resubmitRunning = true
        coroutineScope.launch {
            AutoWorkflowClient(autoWorkflowUrl)
                .resubmit(listOf(taskId), flow, deleteOldOutput, confirm, promptText)
                .fold(
                    onSuccess = { result ->
                        resubmitRunning = false
                        when {
                            result.isBlocked -> Toast.makeText(
                                context,
                                result.message.ifBlank { resubmitFailedText },
                                Toast.LENGTH_SHORT,
                            ).show()

                            result.isPreview -> {
                                val flowLabel = if (flow == AutoWorkflowClient.FLOW_UPSCALE) {
                                    resubmitUpscaleLabel
                                } else {
                                    resubmitEditLabel
                                }
                                val baseMessage = if (!promptText.isNullOrBlank()) {
                                    reeditConfirmText
                                } else {
                                    resubmitConfirmText.format(1, flowLabel)
                                }
                                pendingResubmit = AutoWorkflowPendingAction(
                                    action = AutoWorkflowClient.ACTION_RESUBMIT,
                                    taskIds = listOf(taskId),
                                    flow = flow,
                                    deleteOldOutput = deleteOldOutput,
                                    message = baseMessage +
                                        if (deleteOldOutput) "\n$resubmitDeleteOldText" else "",
                                    promptText = promptText,
                                )
                            }

                            else -> {
                                val count = result.affectedTaskIds.size.takeIf { it > 0 } ?: 1
                                if (flow == AutoWorkflowClient.FLOW_EDIT && deleteOldOutput) {
                                    val removed = result.affectedTaskIds.ifEmpty { listOf(taskId) }
                                    erl.webdavtoon.AutoWorkflowCompareStore.markDeletedTasks(removed)
                                }
                                Toast.makeText(
                                    context,
                                    resubmitDoneText.format(count),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onFailure = {
                        resubmitRunning = false
                        Toast.makeText(context, resubmitFailedText, Toast.LENGTH_SHORT).show()
                    },
                )
        }
    }

    // ColorOS 17's glass header samples a recorded layer of what sits under it. Without a
    // backdrop the bar falls back to an opaque surface and reads as a flat band over the photo.
    // Recording is demand-driven, so an immersive reader never pays for it while the bars hide.
    val backdrop = rememberLayerBackdrop {
        drawRect(Color.Black)
        drawContent()
    }

    // `edgeToEdge` is what lets the app bar draw its material instead of falling back to a flat
    // fill: its blur, gradient ramp and light rim are only painted when the page reaches the
    // window edges, which the reader does by design.
    CompositionLocalProvider(
        LocalBackdrop provides backdrop,
        LocalEdgeToEdge provides true,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Exactly one node records this backdrop: the container wrapping the pager, never a
            // single page. The bars stay siblings of it so they can sample it without blurring
            // themselves.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                when (uiState.readingMode) {
                    ReadingMode.CARD -> {
                        CardReader(
                            photos = displayPhotos,
                            currentIndex = uiState.currentIndex,
                            onPageChanged = { viewModel.updateCurrentIndex(it) },
                            // Gesture zones only exist in card mode, matching the pre-Compose reader.
                            // An unconfigured zone falls through to the default tap behaviour.
                            // A modal overlay swallows the gesture entirely: its scrim already
                            // handled the tap by closing itself, and forwarding it would also
                            // toggle immersive mode behind the panel.
                            onTapAt = { xFraction, yFraction ->
                                if (!modalOverlayVisible) {
                                    val handled = viewModel.dispatchGesture(
                                        GestureType.SINGLE_TAP,
                                        xFraction,
                                        yFraction
                                    )
                                    if (!handled) viewModel.toggleImmersive()
                                }
                            },
                            onLongPressAt = { xFraction, yFraction ->
                                if (!modalOverlayVisible) {
                                    viewModel.dispatchGesture(GestureType.LONG_PRESS, xFraction, yFraction)
                                }
                            }
                        )
                    }
                    ReadingMode.WEBTOON -> {
                        WebtoonReader(
                            photos = displayPhotos,
                            currentIndex = uiState.currentIndex,
                            onIndexChanged = { viewModel.updateCurrentIndex(it) },
                            onSingleTap = { viewModel.toggleImmersive() }
                        )
                    }
                }
            }


            val visible = !uiState.isImmersive

            ReaderOverlayMiuix(
                visible = visible,
                title = uiState.sessionTitle.ifBlank { currentPhotoTitle },
                currentIndex = uiState.currentIndex,
                totalCount = uiState.totalCount,
                readingMode = uiState.readingMode,
                isSlideshowPlaying = uiState.isSlideshowPlaying,
                isOrientationLocked = uiState.isOrientationLocked,
                isFavorite = uiState.isFavorite,
                canResubmit = resubmitTaskId != null,
                onBack = onBack,
                onSeek = { viewModel.updateCurrentIndex(it) },
                onToggleMode = { viewModel.toggleReadingMode() },
                onToggleSlideshow = { viewModel.toggleSlideshow() },
                onToggleOrientationLock = { viewModel.toggleOrientationLock() },
                onToggleFavorite = { viewModel.toggleFavorite() },
                onSaveImage = onSaveImage,
                onOpenGestureControl = { viewModel.setGesturePanelVisible(true) },
                onShare = onShare,
                onReedit = { resubmitTaskId?.let { reeditDialogTaskId = it } },
                onResubmit = { resubmitTaskId?.let { resubmitDialogTaskId = it } },
            )

            // The AutoWorkflow output browser's compare inset. Only images that were opened from the
            // output tree have an entry in the store, so every other album renders exactly as before.
            AutoWorkflowCompareInset(
                info = compareInfo,
                visible = visible,
                isSwapped = currentPhoto?.id?.let { it in swappedPhotoIds } == true,
                fallbackGeneratedUrl = currentPhoto?.imageUri?.toString(),
                onToggleSwap = {
                    val id = currentPhoto?.id ?: return@AutoWorkflowCompareInset
                    if (compareInfo?.sourceUrl == null) return@AutoWorkflowCompareInset
                    swappedPhotoIds = if (id in swappedPhotoIds) {
                        swappedPhotoIds - id
                    } else {
                        swappedPhotoIds + id
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = 16.dp,
                        bottom = BottomFloatingNavigationBarDefaults.navigationBarPadding() +
                            ReaderBottomCapsuleClearance,
                    ),
            )

            if (uiState.isGesturePanelVisible) {
                GestureControlOverlay(
                    config = uiState.gestureControl,
                    onEnabledChange = { viewModel.setGestureControlEnabled(it) },
                    onActionChange = { zone, type, action ->
                        viewModel.updateGestureAction(zone, type, action)
                    },
                    onDismiss = { viewModel.setGesturePanelVisible(false) },
                )
            }

            if (uiState.isPhotoInfoVisible) {
                currentPhoto?.let { photo ->
                    PhotoInfoOverlay(
                        photo = photo,
                        onDismiss = { viewModel.setPhotoInfoVisible(false) },
                    )
                }
            }

            // Re-edit and resubmit run against the output browser's backend, so their dialogs live
            // here rather than in the toolbar: the pill only raises the request.
            reeditDialogTaskId?.let { taskId ->
                ReeditPromptDialog(
                    onDismiss = { reeditDialogTaskId = null },
                    onConfirm = { promptText, deleteOld ->
                        reeditDialogTaskId = null
                        runResubmit(
                            taskId = taskId,
                            flow = AutoWorkflowClient.FLOW_EDIT,
                            deleteOldOutput = deleteOld,
                            confirm = false,
                            promptText = promptText,
                        )
                    },
                )
            }

            resubmitDialogTaskId?.let { taskId ->
                ResubmitDialog(
                    onDismiss = { resubmitDialogTaskId = null },
                    onConfirm = { flow, deleteOld ->
                        resubmitDialogTaskId = null
                        runResubmit(taskId, flow, deleteOld, confirm = false)
                    },
                )
            }

            pendingResubmit?.let { pending ->
                ConfirmActionDialog(
                    pending = pending,
                    running = resubmitRunning,
                    onConfirm = {
                        pendingResubmit = null
                        runResubmit(
                            taskId = pending.taskIds.first(),
                            flow = pending.flow,
                            deleteOldOutput = pending.deleteOldOutput,
                            confirm = true,
                            promptText = pending.promptText,
                        )
                    },
                    onDismiss = { if (!resubmitRunning) pendingResubmit = null },
                )
            }

            // Hosts COUI's popup surfaces. The reader's chrome no longer has an overflow menu, but
            // the gesture panel and shared COUI components still render their menus here.
            io.github.suqi8.coui.kmp.utils.COUIPopupUtils.COUIPopupHost()
        }
    }
}

/**
 * Distance from the screen bottom that clears the reader's bottom capsule (slider row, action row,
 * the capsule's own 16dp margin and the navigation bar padding it carries).
 */
private val ReaderBottomCapsuleClearance = 140.dp

private fun Context.findActivity(): Activity? {    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}
