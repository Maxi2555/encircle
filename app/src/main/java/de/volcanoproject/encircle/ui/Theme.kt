// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2

@Composable
fun EncircleTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * Vivid light colours for glows, outlines and sweeps. Material You's own accents are deliberately
 * muted; these keep the wallpaper's hue but add saturation and spread to neighbouring hues, so the
 * effects look colourful while still belonging to the user's theme.
 */
@Immutable
class GlowPalette(val colors: List<Color>) {
    /**
     * The colours as a closed loop for sweep gradients, sampled densely from [flow] so the gradient
     * never cuts across the hue wheel where it wraps around.
     */
    val loop: List<Color> = List(LOOP_SAMPLES + 1) { flow(it / LOOP_SAMPLES.toFloat()) }

    /** Colour at position [t] on a seamless loop that runs from the first colour to the last and back. */
    fun flow(t: Float): Color {
        val steps = 2 * (colors.size - 1)
        val x = ((t % 1f) + 1f) % 1f * steps
        val i = x.toInt().coerceAtMost(steps - 1)
        val f = x - i
        return lerp(bounce(i), bounce(i + 1), f * f * (3f - 2f * f))
    }

    private fun bounce(k: Int) = colors[if (k < colors.size) k else 2 * (colors.size - 1) - k]
}

private const val LOOP_SAMPLES = 24

/** Resting angle of the logo ring, in degrees: its gap then sits top right, as in the launcher icon. */
private const val LOGO_REST = -36f

/** Duration of one turn of the logo ring while it is busy. */
private const val LOGO_TURN_MS = 900

@Composable
fun rememberGlowPalette(): GlowPalette {
    val primary = MaterialTheme.colorScheme.primary
    val dark = isSystemInDarkTheme()
    return remember(primary, dark) { glowPaletteFor(primary.toArgb(), dark) }
}

fun glowPaletteFor(argb: Int, dark: Boolean): GlowPalette {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    val base = hsv[0]
    // Analogous neighbours plus one warm counterpoint, like light through a prism.
    val offsets = floatArrayOf(-32f, 18f, 74f, 150f)
    return GlowPalette(
        offsets.map { off ->
            val h = ((base + off) % 360f + 360f) % 360f
            // Yellow-green hues look much brighter than blue at equal value; tone them down a little.
            val yellowish = h in 45f..110f
            val s = if (yellowish) 0.62f else 0.72f
            val v = (if (dark) 1f else 0.9f) * (if (yellowish) 0.92f else 1f)
            Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
        },
    )
}

/**
 * The app mark: an open ring in the theme's accent colours with the star of the launcher icon in its gap.
 * Spins while [busy] with the star hidden, then eases back to rest and the star fades in.
 */
@Composable
fun LogoRing(modifier: Modifier = Modifier, busy: Boolean = false) {
    val glow = rememberGlowPalette()
    val angle = remember { Animatable(LOGO_REST) }
    LaunchedEffect(busy) {
        if (busy) {
            while (true) angle.animateTo(angle.value + 360f, tween(LOGO_TURN_MS, easing = LinearEasing))
        } else {
            val ahead = ((LOGO_REST - angle.value) % 360f + 360f) % 360f
            if (ahead > 0.5f) {
                angle.animateTo(
                    angle.value + ahead,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                    initialVelocity = 360f * 1000f / LOGO_TURN_MS,
                )
            }
        }
    }
    val sweep by animateFloatAsState(if (busy) 260f else 310f, spring(stiffness = Spring.StiffnessLow), label = "sweep")
    val star by animateFloatAsState(
        if (busy) 0f else 1f,
        tween(400, delayMillis = if (busy) 0 else 200, easing = FastOutSlowInEasing),
        label = "star",
    )
    Canvas(modifier) {
        val stroke = size.minDimension * 0.13f
        val brush = Brush.sweepGradient(glow.loop)
        if (star > 0f) {
            val k = (size.minDimension - 2 * stroke) / 2f / ICON_RING
            val at = center + Offset(STAR_X * k, STAR_Y * k)
            val turn = ((STAR_ANGLE - angle.value) % 360f + 360f) % 360f / 360f
            scale(0.6f + 0.4f * star, at) {
                drawPath(starPath(at, STAR_SIZE * k), glow.flow(turn), alpha = star)
            }
        }
        rotate(angle.value) {
            drawArc(
                brush = brush,
                startAngle = 20f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(stroke, stroke),
                size = Size(size.width - 2 * stroke, size.height - 2 * stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * The logo drawing itself: the ring grows from nothing into the open circle while turning into place,
 * then the star flashes up in its gap with a burst of light. It ends exactly like a resting [LogoRing].
 * [onRevealed] fires when the star has appeared, so text can follow it.
 */
@Composable
fun LogoReveal(modifier: Modifier = Modifier, onRevealed: () -> Unit = {}) {
    val glow = rememberGlowPalette()
    val ring = remember { Animatable(0f) }
    val star = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(REVEAL_DELAY_MS)
        ring.animateTo(1f, tween(REVEAL_RING_MS, easing = FastOutSlowInEasing))
        launch { burst.animateTo(1f, tween(REVEAL_BURST_MS, easing = LinearOutSlowInEasing)) }
        launch {
            delay(REVEAL_BURST_MS / 3L)
            onRevealed()
        }
        star.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
    }
    Canvas(modifier) {
        val stroke = size.minDimension * 0.13f
        val r = ring.value
        val k = (size.minDimension - 2 * stroke) / 2f / ICON_RING
        val at = center + Offset(STAR_X * k, STAR_Y * k)
        val b = burst.value
        if (b > 0f && b < 1f) {
            // A soft flash of light around the star as it appears.
            val radius = STAR_SIZE * k * (1.2f + 3.2f * b)
            drawCircle(
                Brush.radialGradient(
                    listOf(glow.flow(0.1f).copy(alpha = 0.7f * (1f - b)), glow.flow(0.4f).copy(alpha = 0.25f * (1f - b)), Color.Transparent),
                    center = at,
                    radius = radius,
                ),
                radius = radius,
                center = at,
            )
        }
        val s = star.value
        if (s > 0f) {
            val turn = ((STAR_ANGLE - LOGO_REST) % 360f + 360f) % 360f / 360f
            scale(s.coerceAtLeast(0f), at) {
                drawPath(starPath(at, STAR_SIZE * k), glow.flow(turn), alpha = s.coerceIn(0f, 1f))
            }
        }
        if (r > 0f) {
            rotate(LOGO_REST - 110f * (1f - r)) {
                drawArc(
                    brush = Brush.sweepGradient(glow.loop),
                    startAngle = 20f,
                    sweepAngle = 310f * r,
                    useCenter = false,
                    topLeft = Offset(stroke, stroke),
                    size = Size(size.width - 2 * stroke, size.height - 2 * stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

private const val REVEAL_DELAY_MS = 250L
private const val REVEAL_RING_MS = 1100
private const val REVEAL_BURST_MS = 700

/**
 * The star as in the launcher icon, whose ring has a radius of [ICON_RING]: its offset from the ring's
 * centre, the distance from its centre to each point, and its direction from the ring's centre in degrees.
 */
private const val ICON_RING = 23f
private const val STAR_X = 22f
private const val STAR_Y = -21f
private const val STAR_SIZE = 7f
private val STAR_ANGLE = Math.toDegrees(atan2(STAR_Y, STAR_X).toDouble()).toFloat()

/** The launcher icon's four-pointed star around [c], [h] from the centre to each point. */
private fun starPath(c: Offset, h: Float): Path = Path().apply {
    val a = 0.129f * h
    val b = 0.371f * h
    moveTo(c.x, c.y - h)
    cubicTo(c.x + a, c.y - b, c.x + b, c.y - a, c.x + h, c.y)
    cubicTo(c.x + b, c.y + a, c.x + a, c.y + b, c.x, c.y + h)
    cubicTo(c.x - a, c.y + b, c.x - b, c.y + a, c.x - h, c.y)
    cubicTo(c.x - b, c.y - a, c.x - a, c.y - b, c.x, c.y - h)
    close()
}
