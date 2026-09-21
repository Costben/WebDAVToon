package erl.webdavtoon.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import erl.webdavtoon.R
import io.github.suqi8.coui.kmp.basic.Icon as MiuixIcon
import io.github.suqi8.coui.kmp.basic.IconButton as MiuixIconButton
import io.github.suqi8.coui.kmp.basic.TextField as MiuixTextField
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close

private val SearchFieldCornerRadius = 18.dp

/**
 * Search field shared by every top bar.
 *
 * The field expands down from the top bar instead of popping into place, using
 * the COUI rounded search-field chrome so it reads like a native ColorOS search bar.
 */
@Composable
fun AnimatedSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        modifier = modifier,
    ) {
        MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            cornerRadius = SearchFieldCornerRadius,
            label = placeholder,
            useLabelAsPlaceholder = true,
            singleLine = true,
            leadingIcon = {
                MiuixIcon(
                    FunnelIcon,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp, end = 6.dp),
                )
            },
            trailingIcon = {
                MiuixIconButton(
                    onClick = onClose,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    MiuixIcon(COUIIcons.Light.Close, contentDescription = stringResource(R.string.cancel))
                }
            },
        )
    }
}
