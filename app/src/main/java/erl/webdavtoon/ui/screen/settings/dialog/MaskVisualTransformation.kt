// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.settings.dialog

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Masks every typed character with [mask] while staying deliberately distinct from
 * [androidx.compose.ui.text.input.PasswordVisualTransformation].
 *
 * Compose tags any field whose visual transformation is a `PasswordVisualTransformation` with the
 * semantics `password()` flag, which Android autofill and password managers read as "this is a
 * credential field" and answer with their save/autofill sheet on top of the dialog. This
 * transformation keeps the masked look (one bullet per character, so offsets map 1:1) without
 * advertising the field as a password.
 */
class MaskVisualTransformation(private val mask: Char = '•') : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val masked = AnnotatedString(mask.toString().repeat(text.text.length))
        return TransformedText(masked, OffsetMapping.Identity)
    }
}
