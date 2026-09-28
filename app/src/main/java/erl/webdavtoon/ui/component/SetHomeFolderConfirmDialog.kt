// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import erl.webdavtoon.R
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog

@Composable
fun SetHomeFolderConfirmDialog(
    folderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tapHaptic = rememberTapHaptic()

    OverlayDialog(
        show = true,
        title = stringResource(R.string.set_as_home_folder),
        summary = stringResource(R.string.set_as_home_folder_confirm_message, folderName),
        onDismissRequest = onDismiss,
        content = {
            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                ),
                positive = DialogButtonBarAction(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        tapHaptic()
                        onConfirm()
                    },
                ),
                hasContentAbove = true,
            )
        },
    )
}
