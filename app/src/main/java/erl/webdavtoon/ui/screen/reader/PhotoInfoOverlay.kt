// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.reader

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import erl.webdavtoon.Photo
import erl.webdavtoon.R
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * Photo metadata sheet for the reader, used by the `PHOTO_INFO` gesture action.
 */
@Composable
fun PhotoInfoOverlay(
    photo: Photo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val noRipple = remember { MutableInteractionSource() }
    val sizeText = remember(photo.id, photo.size) {
        Formatter.formatFileSize(context, photo.size)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = noRipple,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(COUITheme.colorScheme.surface)
                .clickable(
                    interactionSource = noRipple,
                    indication = null,
                    onClick = {},
                )
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.photo_details),
                style = COUITheme.textStyles.title3,
                color = COUITheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(10.dp))

            InfoRow(stringResource(R.string.file_name_prefix, photo.title))
            InfoRow(stringResource(R.string.file_size_prefix, sizeText))
            if (photo.width > 0 && photo.height > 0) {
                InfoRow(stringResource(R.string.file_dimension_prefix, photo.width, photo.height))
            }
            InfoRow(
                stringResource(
                    R.string.local_prefix,
                    photo.isLocal
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                text = stringResource(R.string.ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InfoRow(text: String) {
    Text(
        text = text,
        style = COUITheme.textStyles.body2,
        color = COUITheme.colorScheme.onSurfaceSecondary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
