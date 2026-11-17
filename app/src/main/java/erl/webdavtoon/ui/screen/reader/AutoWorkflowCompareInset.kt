// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.reader

import android.net.Uri
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import erl.webdavtoon.AutoWorkflowCompareInfo
import erl.webdavtoon.R
import erl.webdavtoon.WebDavImageLoader
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

private val InsetWidth = 104.dp
private val InsetCorner = RoundedCornerShape(10.dp)

/**
 * The original-image counterpart of the output file currently shown in the reader.
 *
 * Ids on the inset name the generation task so the picture on screen can be traced back to its row
 * in the AutoWorkflow index. The inset only appears while the reader chrome is visible, mirroring
 * how the top and bottom bars fade out in immersive mode.
 */
@Composable
internal fun AutoWorkflowCompareInset(
    info: AutoWorkflowCompareInfo?,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val url = info?.sourceUrl ?: return
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { -it / 3 },
        exit = fadeOut() + slideOutHorizontally { -it / 3 },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .width(InsetWidth)
                .aspectRatio(0.75f)
                .shadow(8.dp, InsetCorner)
                .clip(InsetCorner)
                .background(Color(0xFF141414))
                .border(1.dp, Color.White.copy(alpha = 0.75f), InsetCorner),
        ) {
            RemoteInsetImage(url)
            InsetBadge(
                text = buildString {
                    append(stringResource(R.string.autoworkflow_original))
                    info.taskId?.let {
                        append(" · ")
                        append(stringResource(R.string.autoworkflow_task_badge, it))
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun RemoteInsetImage(url: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        },
        update = { imageView ->
            val bound = imageView.getTag(R.id.tag_media_bind_key) as? String
            if (bound == url && imageView.drawable != null) return@AndroidView
            imageView.setTag(R.id.tag_media_bind_key, url)
            WebDavImageLoader.loadLocalImage(
                context = imageView.context,
                imageUri = Uri.parse(url),
                imageView = imageView,
                limitSize = false,
            )
        },
    )
}

@Composable
private fun InsetBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x99000000))
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
