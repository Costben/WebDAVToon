package erl.webdavtoon

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * ComfyUI edit flow.
 *
 * The dialogs are Compose/COUI now (the old `dialog_edit.xml` +
 * `MaterialAlertDialogBuilder` path is gone), so this object only owns the
 * asynchronous work - config fetch and batch submit - and reports state to the
 * host Activity, which renders it with
 * [erl.webdavtoon.ui.component.ComfyUiEditDialog].
 */
object EditDialogHelper {
    private const val TAG = "EditDialogHelper"

    /** Rendered by `ComfyUiEditDialog`. */
    sealed interface DialogState {
        /** Fetching `/api/uploads/config`. */
        data object Loading : DialogState

        /** Config fetched; the form is shown. */
        data class Form(
            val config: EditConfig,
            val service: EditService,
            val photos: List<Photo>,
        ) : DialogState

        /** Config fetch failed. */
        data class Failure(val baseUrl: String, val message: String) : DialogState
    }

    /**
     * Starts the flow. Returns without emitting state (after an explanatory Toast)
     * when there is nothing to edit or the AutoWorkflow URL is not configured.
     */
    fun show(
        activity: ComponentActivity,
        selectedPhotos: List<Photo>,
        settingsManager: SettingsManager,
        onStateChange: (DialogState?) -> Unit,
        onSubmitted: () -> Unit,
    ) {
        val imagePhotos = selectedPhotos.filter { it.mediaType == MediaType.IMAGE }
        if (imagePhotos.isEmpty()) {
            Toast.makeText(activity, R.string.favorite_requires_selection, Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = settingsManager.getAutoWorkflowUrl()
        if (!EditService.isValidUrl(baseUrl)) {
            Toast.makeText(activity, R.string.comfyui_configure_first, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "show aborted: AutoWorkflow URL is not configured")
            return
        }

        onStateChange(DialogState.Loading)
        val service = EditService(activity, baseUrl, settingsManager)
        activity.lifecycleScope.launch {
            val config = service.fetchConfig().getOrElse { error ->
                Log.e(TAG, "fetchConfig failed", error)
                onStateChange(DialogState.Failure(baseUrl, describe(error)))
                return@launch
            }
            if (config.workflows.isEmpty()) {
                Log.e(TAG, "fetchConfig returned no workflows")
                onStateChange(
                    DialogState.Failure(
                        baseUrl,
                        "No workflow JSON files returned by /api/uploads/config",
                    )
                )
                return@launch
            }
            Log.d(TAG, "fetchConfig success workflows=${config.workflows.size} presets=${config.promptPresets.size}")
            onStateChange(DialogState.Form(config, service, imagePhotos))
        }
    }

    /** Submits the edit for every photo in the selection, then reports a summary Toast. */
    fun submit(
        activity: ComponentActivity,
        form: DialogState.Form,
        workflow: String,
        prompt: String,
        onSubmitted: () -> Unit,
    ) {
        activity.lifecycleScope.launch {
            var successCount = 0
            var firstError: String? = null
            form.photos.forEach { photo ->
                form.service.submitEdit(photo, workflow, prompt)
                    .onSuccess { submitResult ->
                        successCount += 1
                        Log.d(TAG, "submitEdit accepted taskId=${submitResult.taskId} filename=${submitResult.filename}")
                    }
                    .onFailure { error ->
                        val message = describe(error)
                        if (firstError == null) {
                            firstError = message
                        }
                        Log.e(TAG, "submitEdit failed photo=${photo.title}", error)
                        if (!photo.isLocal) {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.comfyui_download_failed, photo.title),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            }

            showSubmitResult(activity, successCount, form.photos.size, firstError)
            if (successCount > 0) {
                onSubmitted()
            }
        }
    }

    private fun describe(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

    private fun showSubmitResult(context: Context, successCount: Int, total: Int, firstError: String?) {
        val message = when {
            successCount == total ->
                context.getString(R.string.comfyui_submit_success, successCount)
            successCount > 0 ->
                context.getString(R.string.comfyui_submit_partial, successCount, total, total - successCount)
            else ->
                context.getString(R.string.comfyui_submit_failed, firstError.orEmpty())
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        Log.d(TAG, "submit summary success=$successCount total=$total error=$firstError")
    }
}
