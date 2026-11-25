package io.github.maxi2555.encircle.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext

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
    /** The colours as a closed loop, for sweep gradients. */
    val loop: List<Color> = colors + colors.first()

    /** Colour at position [t] along the palette, wrapping around. */
    fun at(t: Float): Color {
        val n = colors.size
        val x = ((t % 1f) + 1f) % 1f * n
        val i = x.toInt() % n
        return lerp(colors[i], colors[(i + 1) % n], x - x.toInt())
    }
}

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

/** The app mark: an open ring in the theme's accent colours. Spins while [busy]. */
@Composable
fun LogoRing(modifier: Modifier = Modifier, busy: Boolean = false) {
    val glow = rememberGlowPalette()
    val spin by rememberInfiniteTransition(label = "logo").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    Canvas(modifier) {
        val stroke = size.minDimension * 0.13f
        val brush = Brush.sweepGradient(glow.loop)
        rotate(if (busy) spin else -60f) {
            drawArc(
                brush = brush,
                startAngle = 20f,
                sweepAngle = if (busy) 260f else 310f,
                useCenter = false,
                topLeft = Offset(stroke, stroke),
                size = Size(size.width - 2 * stroke, size.height - 2 * stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
