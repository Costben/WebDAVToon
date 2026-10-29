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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.GradientStrokeStyle
import io.github.suqi8.coui.kmp.basic.InnerShadowStyle
import io.github.suqi8.coui.kmp.basic.MaterialShadowEdgeDefaults
import io.github.suqi8.coui.kmp.basic.MaterialStrokeParams
import io.github.suqi8.coui.kmp.basic.materialShadowEdge
import io.github.suqi8.coui.kmp.basic.spotLight
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor

/**
 * The ColorOS 17 floating ball: a shaded circle with the material rim, an outside halo and a shadow
 * it casts on the page.
 *
 * COUI's `FloatingActionButton` is a plain surface with a drop shadow, and the library states it
 * did not port `COUIFABShadowUtils.setOuterShadowEffect` — the caustic light the device draws around
 * a floating button. The ball is therefore composed here from the same public primitives
 * `HighlightedIconButton` uses for the toolbar halo, in the order the device paints them:
 *
 * - [dropShadow] for the shadow the ball lifts off the page with,
 * - a radial fill that is darker in the middle and brighter at the rim, the material's own shading,
 * - [spotLight] with `PLUS` blending for the specular that reads as light landing on the surface,
 * - [materialShadowEdge] with `TYPE_FRAMEWORK_CIRCLE_1` for the rim and its inner shadow,
 * - a halo outside the edge, drawn after the shadow so the shadow never dims it: white caustic light
 *   in the dark theme, a soft drop halo in the light theme.
 *
 * Pressing shrinks the ball to 0.92, sinks the highlight toward the centre and fades the halo, so the
 * light travels across the surface as the ball gives under the finger.
 *
 * @param onClick the click action.
 * @param size the diameter of the ball.
 * @param containerColor the fill colour.
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
    val lightPosition = Offset(
        x = FloatingBallDefaults.LightX,
        y = FloatingBallDefaults.LightY + FloatingBallDefaults.PressedLightDropY * pressProgress,
    )
    val lightIntensity =
        FloatingBallDefaults.LightIntensity + FloatingBallDefaults.PressedLightGain * pressProgress

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // The halo is painted after the content, so the drop shadow underneath never dims it;
            // the difference clip keeps it outside the ball, over the page only.
            .drawWithContent {
                drawContent()
                val radius = this.size.minDimension / 2f
                val ball = Path().apply { addOval(Rect(center, radius)) }
                clipPath(ball, ClipOp.Difference) {
                    drawBallHalo(
                        center = center,
                        radius = radius,
                        isDark = isDark,
                        alpha = (1f - pressProgress).coerceIn(0f, 1f),
                    )
                }
            }
            .dropShadow(shape = CircleShape, shadow = FloatingBallDefaults.shadow(isDark))
            .clip(CircleShape)
            .drawBehind {
                val radius = this.size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to FloatingBallDefaults.fillCenter(containerColor),
                            FloatingBallDefaults.FillEdgeStop to FloatingBallDefaults.fillEdge(containerColor),
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
            .spotLight(
                position = lightPosition,
                radius = size * FloatingBallDefaults.LightRadiusRatio,
                intensity = lightIntensity,
                lightHeight = size * FloatingBallDefaults.LightHeightRatio,
                ambientStrength = FloatingBallDefaults.LightAmbient,
                diffuseStrength = FloatingBallDefaults.LightDiffuse,
                blendMode = BlendMode.Plus,
                clipShape = CircleShape,
            )
            .materialShadowEdge(
                stroke = MaterialStrokeParams.frameworkCircle1(isDark),
                cornerWeight = MaterialShadowEdgeDefaults.SingleItemCornerWeight,
                cornerRadius = size / 2,
                edgeStyle = GradientStrokeStyle.EDGE_STYLE_1,
                shadowStyle = InnerShadowStyle.SHADOW_STYLE_1,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * The light hugging the ball's edge: the dark theme's white caustic band, or the light theme's soft
 * drop halo. Both start at the edge and fade out linearly over their extent.
 */
private fun DrawScope.drawBallHalo(center: Offset, radius: Float, isDark: Boolean, alpha: Float) {
    if (alpha <= 0.01f) return
    val haloCenter = center + Offset(0f, FloatingBallDefaults.haloOffsetY(isDark).toPx())
    val outerRadius = radius + FloatingBallDefaults.haloExtent(isDark).toPx()
    val color = FloatingBallDefaults.haloColor(isDark).copy(
        alpha = FloatingBallDefaults.haloColor(isDark).alpha * alpha,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to color,
                (radius / outerRadius) to color,
                1f to Color.Transparent,
            ),
            center = haloCenter,
            radius = outerRadius,
        ),
        radius = outerRadius,
        center = haloCenter,
    )
}

/** Defaults for [CouiFloatingBall], measured against ColorOS 17's floating button. */
object FloatingBallDefaults {

    /** The scale the ball springs to while pressed. */
    val PressedScale = 0.92f

    /** The highlight's horizontal position, as a fraction of the ball's width. */
    val LightX = 0.30f

    /** The highlight's resting vertical position, as a fraction of the ball's height. */
    val LightY = 0.24f

    /** How far the highlight sinks while pressed. */
    val PressedLightDropY = 0.16f

    /** The resting specular intensity. */
    val LightIntensity = 0.22f

    /** How much the specular brightens while pressed. */
    val PressedLightGain = 0.14f

    /** The light's radius as a multiple of the ball's diameter. */
    val LightRadiusRatio = 0.9f

    /** The light source's height as a multiple of the ball's diameter; close, so the falloff shows. */
    val LightHeightRatio = 0.55f

    /** The spotlight's ambient term, `u_lightColorAndAmbient.w`. */
    val LightAmbient = 0.08f

    /** The spotlight's diffuse term, `u_lightingParams.x`. */
    val LightDiffuse = 0.9f

    /** The radial-fill stop at which the edge colour is reached, as a fraction of the radius. */
    val FillEdgeStop = 0.89f

    /** How far the fill's centre is pulled toward black, the material's "darker in the middle". */
    val FillCenterDarken = 0.12f

    /** How far the fill's edge is pulled toward white. */
    val FillEdgeBrighten = 0.10f

    /** The fill's centre colour. */
    fun fillCenter(containerColor: Color): Color = lerp(containerColor, Color.Black, FillCenterDarken)

    /** The fill's edge colour. */
    fun fillEdge(containerColor: Color): Color = lerp(containerColor, Color.White, FillEdgeBrighten)

    /**
     * The halo colour: `coui_caustic_color_1` (`#33FFFFFF`) at night, where the device draws the
     * floating button's own light; a soft drop halo in the light theme, where the button casts a
     * shadow instead.
     */
    fun haloColor(isDark: Boolean): Color =
        if (isDark) Color.White.copy(alpha = 51f / 255f) else Color.Black.copy(alpha = 0.07f)

    /** How far the halo reaches past the edge: the native 4.9dp caustic band, 10dp for a shadow. */
    fun haloExtent(isDark: Boolean): Dp = if (isDark) 4.899994.dp else 10.dp

    /** The halo's vertical offset; the light theme's drop halo is bottom-weighted. */
    fun haloOffsetY(isDark: Boolean): Dp = if (isDark) 0.dp else 1.dp

    /** The shadow under the ball: a soft, slightly offset drop, deeper in the dark theme. */
    fun shadow(isDark: Boolean): Shadow = Shadow(
        radius = if (isDark) 16.dp else 14.dp,
        color = Color.Black.copy(alpha = if (isDark) 0x73 / 255f else 0x40 / 255f),
        spread = 0.dp,
        offset = DpOffset(0.dp, 5.dp),
    )
}
