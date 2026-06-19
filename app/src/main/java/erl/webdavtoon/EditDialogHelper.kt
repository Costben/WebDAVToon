package erl.webdavtoon

import android.content.Context
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import erl.webdavtoon.databinding.DialogEditBinding
import kotlinx.coroutines.launch

object EditDialogHelper {
    private const val TAG = "EditDialogHelper"

    fun show(
        activity: AppCompatActivity,
        selectedPhotos: List<Photo>,
        settingsManager: SettingsManager,
        onSubmitted: () -> Unit
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

        Toast.makeText(activity, R.string.comfyui_loading_config, Toast.LENGTH_SHORT).show()
        val service = EditService(activity, baseUrl, settingsManager)
        activity.lifecycleScope.launch {
            val config = service.fetchConfig().getOrElse { error ->
                Log.e(TAG, "fetchConfig failed", error)
                showConnectionFailureDialog(activity, baseUrl, error)
                return@launch
            }
            if (config.workflows.isEmpty()) {
                Log.e(TAG, "fetchConfig returned no workflows")
                showConnectionFailureDialog(
                    activity,
                    baseUrl,
                    IllegalStateException("No workflow JSON files returned by /api/uploads/config")
                )
                return@launch
            }
            Log.d(TAG, "fetchConfig success workflows=${config.workflows.size} presets=${config.promptPresets.size}")
            showConfigDialog(activity, imagePhotos, config, service, onSubmitted)
        }
    }

    private fun showConfigDialog(
        activity: AppCompatActivity,
        photos: List<Photo>,
        config: EditConfig,
        service: EditService,
        onSubmitted: () -> Unit
    ) {
        val binding = DialogEditBinding.inflate(activity.layoutInflater)
        val workflowAdapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, config.workflows)
        binding.workflowEdit.setAdapter(workflowAdapter)
        binding.workflowEdit.setText(config.defaultWorkflow.ifBlank { config.workflows.first() }, false)

        val presetLabels = listOf(activity.getString(R.string.comfyui_no_preset)) + config.promptPresets.map { it.name }
        val presetAdapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, presetLabels)
        binding.presetEdit.setAdapter(presetAdapter)
        binding.presetEdit.setText(presetLabels.first(), false)
        binding.presetEdit.setOnItemClickListener { _, _, position, _ ->
            val preset = config.promptPresets.getOrNull(position - 1) ?: return@setOnItemClickListener
            binding.promptEdit.setText(preset.content)
            binding.promptEdit.setSelection(binding.promptEdit.text?.length ?: 0)
            binding.promptLayout.error = null
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.comfyui_edit_title)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.comfyui_submit, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val workflow = binding.workflowEdit.text?.toString().orEmpty().trim()
                val prompt = binding.promptEdit.text?.toString().orEmpty().trim()
                if (prompt.isBlank()) {
                    binding.promptLayout.error = activity.getString(R.string.comfyui_prompt_required)
                    return@setOnClickListener
                }
                binding.promptLayout.error = null
                dialog.dismiss()
                submitPhotos(activity, photos, service, workflow, prompt, onSubmitted)
            }
        }

        dialog.show()
    }

    private fun showConnectionFailureDialog(
        activity: AppCompatActivity,
        baseUrl: String,
        error: Throwable
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val message = error.message
            ?.takeIf { it.isNotBlank() }
            ?: error.javaClass.simpleName
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.comfyui_connect_failed)
            .setMessage(activity.getString(R.string.comfyui_connect_failed_detail, baseUrl, message))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun submitPhotos(
        activity: AppCompatActivity,
        photos: List<Photo>,
        service: EditService,
        workflow: String,
        prompt: String,
        onSubmitted: () -> Unit
    ) {
        activity.lifecycleScope.launch {
            var successCount = 0
            var firstError: String? = null
            photos.forEach { photo ->
                val result = service.submitEdit(photo, workflow, prompt)
                result.onSuccess { submitResult ->
                    successCount += 1
                    Log.d(TAG, "submitEdit accepted taskId=${submitResult.taskId} filename=${submitResult.filename}")
                }.onFailure { error ->
                    val message = error.message ?: error.javaClass.simpleName
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

            showSubmitResult(activity, successCount, photos.size, firstError)
            if (successCount > 0) {
                onSubmitted()
            }
        }
    }

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
