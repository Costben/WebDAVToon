package erl.webdavtoon.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import erl.webdavtoon.R
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog

/**
 * Shared COUI delete-confirmation dialog. [message] carries the per-screen count
 * text so each host keeps its own wording.
 */
@Composable
fun DeleteConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.confirm_delete),
        summary = message,
        onDismissRequest = onDismiss,
        content = {
            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                ),
                positive = DialogButtonBarAction(
                    text = stringResource(R.string.delete),
                    onClick = onConfirm,
                ),
                hasContentAbove = true,
            )
        },
    )
}
