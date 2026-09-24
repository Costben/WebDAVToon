// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic

/** Light tick for confirming an ordinary selection action. */
@Composable
fun rememberTapHaptic(): () -> Unit {
    val haptic = rememberCouiHaptic()
    return remember(haptic) { { haptic(CouiHapticEffect.Switch) } }
}

/** Heavier tick for entering selection mode by long press and for destructive actions. */
@Composable
fun rememberStrongHaptic(): () -> Unit {
    val haptic = rememberCouiHaptic()
    return remember(haptic) { { haptic(CouiHapticEffect.Strength) } }
}
