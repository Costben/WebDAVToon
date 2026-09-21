package erl.webdavtoon.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import erl.webdavtoon.EditDialogHelper
import erl.webdavtoon.R
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * ComfyUI edit dialog (COUI).
 *
 * Replaces the former View/XML `dialog_edit.xml` + `MaterialAlertDialogBuilder`
 * flow. [state] is produced by [EditDialogHelper]; the form keeps its own
 * workflow / preset / prompt state so the host Activity stays stateless.
 */
@Composable
fun ComfyUiEditDialog(
    state: EditDialogHelper.DialogState,
    onSubmit: (workflow: String, prompt: String) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        EditDialogHelper.DialogState.Loading -> OverlayDialog(
            show = true,
            title = stringResource(R.string.comfyui_edit_title),
            onDismissRequest = onDismiss,
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(size = 20.dp)
                    Text(
                        text = stringResource(R.string.comfyui_loading_config),
                        style = COUITheme.textStyles.body2,
                    )
                }
            },
        )

        is EditDialogHelper.DialogState.Failure -> OverlayDialog(
            show = true,
            title = stringResource(R.string.comfyui_connect_failed),
            onDismissRequest = onDismiss,
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.comfyui_connect_failed_detail,
                            state.baseUrl,
                            state.message,
                        ),
                        style = COUITheme.textStyles.body2,
                    )
                    TextButton(
                        text = stringResource(R.string.ok),
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            },
        )

        is EditDialogHelper.DialogState.Form -> ComfyUiEditForm(state, onSubmit, onDismiss)
    }
}

@Composable
private fun ComfyUiEditForm(
    state: EditDialogHelper.DialogState.Form,
    onSubmit: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val workflows = state.config.workflows
    val presets = state.config.promptPresets
    val noPreset = stringResource(R.string.comfyui_no_preset)

    var workflow by remember(workflows) {
        mutableStateOf(state.config.defaultWorkflow.ifBlank { workflows.first() })
    }
    var presetLabel by remember { mutableStateOf(noPreset) }
    var prompt by remember { mutableStateOf("") }
    var promptError by remember { mutableStateOf(false) }
    var workflowExpanded by remember { mutableStateOf(false) }
    var presetExpanded by remember { mutableStateOf(false) }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.comfyui_edit_title),
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Workflow picker (was an ExposedDropdownMenu + AutoCompleteTextView).
                DropdownField(
                    value = workflow,
                    label = stringResource(R.string.comfyui_workflow),
                    expanded = workflowExpanded,
                    onExpand = { workflowExpanded = true },
                    onDismissRequest = { workflowExpanded = false },
                    items = workflows.map { name ->
                        DropdownItem(
                            text = name,
                            selected = name == workflow,
                            onClick = {
                                workflow = name
                                workflowExpanded = false
                            },
                        )
                    },
                )

                // Preset picker: choosing one fills the prompt field.
                DropdownField(
                    value = presetLabel,
                    label = stringResource(R.string.comfyui_preset),
                    expanded = presetExpanded,
                    onExpand = { presetExpanded = true },
                    onDismissRequest = { presetExpanded = false },
                    items = buildList {
                        add(
                            DropdownItem(
                                text = noPreset,
                                selected = presetLabel == noPreset,
                                onClick = {
                                    presetLabel = noPreset
                                    presetExpanded = false
                                },
                            )
                        )
                        presets.forEach { preset ->
                            add(
                                DropdownItem(
                                    text = preset.name,
                                    selected = presetLabel == preset.name,
                                    onClick = {
                                        presetLabel = preset.name
                                        prompt = preset.content
                                        promptError = false
                                        presetExpanded = false
                                    },
                                )
                            )
                        }
                    },
                )

                TextField(
                    value = prompt,
                    onValueChange = { value ->
                        prompt = value
                        if (value.isNotBlank()) promptError = false
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    label = stringResource(R.string.comfyui_prompt_hint),
                    singleLine = false,
                )
                if (promptError) {
                    Text(
                        text = stringResource(R.string.comfyui_prompt_required),
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.error,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(text = stringResource(R.string.cancel), onClick = onDismiss)
                    TextButton(
                        text = stringResource(R.string.comfyui_submit),
                        onClick = {
                            if (prompt.isBlank()) {
                                promptError = true
                            } else {
                                onSubmit(workflow, prompt)
                            }
                        },
                    )
                }
            }
        },
    )
}

/**
 * A read-only text field that opens a COUI flat menu. COUI has no exposed-dropdown
 * field, so this pairs `TextField` with [CouiFlatMenu] the same way the server-config
 * dialog does.
 */
@Composable
private fun DropdownField(
    value: String,
    label: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismissRequest: () -> Unit,
    items: List<DropdownItem>,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = label,
            singleLine = true,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(role = Role.Button) { onExpand() }
        ) {
            Text(
                text = "\u25BE",
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            )
        }
        CouiFlatMenu(
            expanded = expanded,
            entries = listOf(DropdownEntry(items = items)),
            onDismissRequest = onDismissRequest,
        )
    }
}
