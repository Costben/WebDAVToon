package erl.webdavtoon.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import erl.webdavtoon.R
import erl.webdavtoon.ui.UiMode
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close

private val SearchFieldShape = RoundedCornerShape(18.dp)
private val SearchFieldCornerRadius = 18.dp

/**
 * Search field shared by every top bar.
 *
 * The field expands down from the top bar instead of popping into place, and
 * uses the Miuix rounded search-field chrome in Miuix mode so it reads like a
 * native MIUIX search bar. Both tracks share the same enter/exit motion so the
 * interaction feels identical regardless of [uiMode].
 */
@Composable
fun AnimatedSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    uiMode: UiMode,
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
        when (uiMode) {
            UiMode.Miuix -> MiuixSearchField(value, onValueChange, placeholder, onClose)
            UiMode.Material -> MaterialSearchField(value, onValueChange, placeholder, onClose)
        }
    }
}

@Composable
private fun MiuixSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onClose: () -> Unit,
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
                MiuixIcon(MiuixIcons.Light.Close, contentDescription = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun MaterialSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onClose: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        singleLine = true,
        shape = SearchFieldShape,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(
                FunnelIcon,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp, end = 6.dp),
            )
        },
        trailingIcon = {
            IconButton(
                onClick = onClose,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(MiuixIcons.Light.Close, contentDescription = stringResource(R.string.cancel))
            }
        },
    )
}
