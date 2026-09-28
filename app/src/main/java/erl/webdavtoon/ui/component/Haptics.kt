// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/** Light tick for confirming an ordinary selection action. */
@Composable
fun rememberTapHaptic(): () -> Unit {
    val view = LocalView.current
    val context = LocalContext.current.applicationContext
    return remember(view, context) {
        {
            performFeedback(view, context, isStrong = false)
        }
    }
}

/** Heavier tick for entering selection mode by long press and for destructive actions. */
@Composable
fun rememberStrongHaptic(): () -> Unit {
    val view = LocalView.current
    val context = LocalContext.current.applicationContext
    return remember(view, context) {
        {
            performFeedback(view, context, isStrong = true)
        }
    }
}

private fun performFeedback(view: View, context: Context, isStrong: Boolean) {
    if (vibratePredefined(context, isStrong)) {
        return
    }

    val constant = if (isStrong) {
        HapticFeedbackConstants.LONG_PRESS
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }

    view.performHapticFeedback(
        constant,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
    )
}

@Suppress("DEPRECATION")
private fun vibratePredefined(context: Context, isStrong: Boolean): Boolean {
    return runCatching {
        val effectId = if (isStrong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val vm = context.getSystemService(VibratorManager::class.java) ?: return false
                val vibrator = vm.defaultVibrator
                if (!vibrator.hasVibrator()) return false
                val effect = VibrationEffect.createPredefined(effectId)
                val attributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build()
                vibrator.vibrate(effect, attributes)
                true
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val vm = context.getSystemService(VibratorManager::class.java) ?: return false
                if (!vm.defaultVibrator.hasVibrator()) return false
                val effect = VibrationEffect.createPredefined(effectId)
                val attributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build()
                vm.vibrate(CombinedVibration.createParallel(effect), attributes)
                true
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val vibrator = context.getSystemService(Vibrator::class.java) ?: return false
                if (!vibrator.hasVibrator()) return false
                val effect = VibrationEffect.createPredefined(effectId)
                vibrator.vibrate(effect)
                true
            }
            else -> {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return false
                if (!vibrator.hasVibrator()) return false
                val duration = if (isStrong) 35L else 12L
                vibrator.vibrate(duration)
                true
            }
        }
    }.getOrDefault(false)
}
