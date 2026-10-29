// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.MetaBallParticle
import io.github.suqi8.coui.kmp.basic.MetaBallSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor

/**
 * A ColorOS 17 floating ball, built the way the library builds its accessory ball.
 *
 * COUI's `BottomFloatingAccessoryNavigationBar` dresses each side accessory as
 * `dropShadow(CircleShape, BarShadow)` with a single circular [MetaBallParticle] painted over it by
 * [MetaBallSurface]. That surface is what carries the material's light: `MaterialCausticShadow`
 * lays the caustic glow under the lower edge, and `nativeMaterialEdge` traces the thin rim line —
 * bright and complete with `rimBottomScale = 1f`, the way `TYPE_FRAMEWORK_CAPSULE_3` ramps it.
 *
 * Drawing the halo by hand instead (an outside band plus a spot light plus a material edge) is what
 * made the ball read wrong on a dark page: the band is painted *outside* the silhouette, so it
 * shows up as a grey smudge on the background instead of light sitting on the material.
 *
 * Pressing springs the ball to [FloatingBallDefaults.PressedScale]. COUI's own touch motion would
 * also sweep a spot light across the accessory, but `Modifier.couiTouchMotion` is internal to the
 * library and cannot be called from the app.
 *
 * @param onClick the click action.
 * @param size the diameter of the ball.
 * @param containerColor the ball's body colour.
 * @param contentColor the colour of the [content] glyph.
 * @param content the glyph, drawn centred in the ball.
 */
@Composable
fun CouiFloatingBall(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    containerColor: Color = COUITheme.colorScheme.primary,
    contentColor: Color = COUITheme.colorScheme.onPrimary,
    content: @Composable () -> Unit,
) {
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        // The COUI press spring: bounce 0, response 0.3s.
        animationSpec = spring(dampingRatio = 1f, stiffness = 438.65f),
        label = "floatingBallPress",
    )
    val scale = 1f - (1f - FloatingBallDefaults.PressedScale) * pressProgress

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The device lifts the ball with COUI's SHADOW_LV_B spot shadow — the same elevation the
        // bar and its accessories cast — and paints the blob over it.
        Box(
            modifier = Modifier
                .matchParentSize()
                .dropShadow(
                    shape = CircleShape,
                    shadow = BottomFloatingAccessoryNavigationBarDefaults.BarShadow,
                ),
        )
        MetaBallSurface(
            particles = listOf(MetaBallParticle.circle(size / 2, size / 2, size)),
            modifier = Modifier.matchParentSize(),
            color = containerColor,
            // Nothing to refract: the page is recorded into `GlassTopBarScaffold`'s backdrop layer
            // and this ball is drawn *into* that same layer, so sampling it would feed the ball
            // back into its own blur. With no backdrop the surface paints the flat body plus the
            // rim and the caustic glow, which is the library's own path when nothing is behind it.
            backdrop = null,
            rimColor = FloatingBallDefaults.rimColor(isDark),
            bottomGlowColor = FloatingBallDefaults.bottomGlowColor,
            rimBottomScale = 1f,
        )
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/** Defaults for [CouiFloatingBall], taken from COUI's accessory ball. */
object FloatingBallDefaults {

    /** The scale the ball springs to while pressed. */
    val PressedScale = 0.92f

    /**
     * The rim line's colour: COUI asks both the bar and the accessory for
     * `TYPE_FRAMEWORK_CAPSULE_3`, and a white bloom reads far stronger over a dark body than a
     * light one, so the device thins it in the light theme.
     */
    fun rimColor(isDark: Boolean): Color = Color.White.copy(alpha = if (isDark) 0.8f else 0.25f)

    /** The caustic glow under the ball, `coui_caustic_color_1` at the alpha the device uses. */
    val bottomGlowColor: Color = Color.White.copy(alpha = 66f / 255f)
}
