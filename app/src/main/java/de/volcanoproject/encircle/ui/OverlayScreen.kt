// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.ui

import android.util.DisplayMetrics
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.volcanoproject.encircle.R
import de.volcanoproject.encircle.actions.SystemActions
import de.volcanoproject.encircle.analysis.CodeContent
import de.volcanoproject.encircle.analysis.DetectedCode
import de.volcanoproject.encircle.analysis.EntityKind
import de.volcanoproject.encircle.analysis.TextEntity
import de.volcanoproject.encircle.analysis.TextLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun OverlayScreen(c: OverlayController) {
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val img = c.image
    val active = img != null || c.screenshotMissing

    val displayWidth = remember(view, configuration) { realDisplayWidth(view) }
    SideEffect {
        c.haptics = { view.performHapticFeedback(it) }
        c.density = density.density
        if (img != null && displayWidth > 0f) c.scale = displayWidth / img.width
    }

    val appear = remember { Animatable(0f) }
    val shimmer = remember { Animatable(0f) }
    val barDrag = remember { mutableFloatStateOf(0f) }
    val exit = remember { Animatable(0f) }
    val barIn = remember { Animatable(0f) }
    val edge = remember { Animatable(0f) }
    LaunchedEffect(c.sessionId, active) {
        barDrag.floatValue = 0f
        if (active) appear.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) else appear.snapTo(0f)
    }
    LaunchedEffect(c.sessionId, active) {
        // The bar springs up with a little overshoot, as if thrown in from below.
        if (active) barIn.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 340f)) else barIn.snapTo(0f)
    }
    LaunchedEffect(c.sessionId, img, c.analyzing) {
        // The glowing frame stays while the screen is being read, then fades out.
        if (img == null) {
            edge.snapTo(0f)
        } else if (c.analyzing) {
            edge.animateTo(1f, tween(280))
        } else {
            delay(200)
            edge.animateTo(0f, tween(1100, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(c.sessionId, img) {
        if (img != null) {
            shimmer.snapTo(0f)
            shimmer.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing))
        }
    }
    LaunchedEffect(c.exiting) {
        if (c.exiting) {
            exit.animateTo(1f, tween(190, easing = FastOutLinearInEasing))
            c.finishNow()
        } else {
            exit.snapTo(0f)
        }
    }
    val dragFadePx = with(density) { 360.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val loc = IntArray(2)
                view.getLocationOnScreen(loc)
                c.origin = -(Offset(loc[0].toFloat(), loc[1].toFloat()) + coords.positionInRoot())
            }
            .graphicsLayer {
                // Fading reveals the live app underneath, which looks identical to the screenshot.
                alpha = (1f - exit.value) * (1f - (barDrag.floatValue / dragFadePx).coerceIn(0f, 0.75f))
            },
    ) {
        if (img != null) {
            ScreenLayer(c, img, appear::value, shimmer::value, edge::value)
            Box(Modifier.fillMaxSize().overlayGestures(c))
            CodeBadges(c)
        } else if (c.screenshotMissing) {
            MissingScreenshot(c, appear::value)
        }
        if (active) {
            BottomPanel(
                c = c,
                appear = barIn::value,
                exit = exit::value,
                barDrag = barDrag,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// -------------------------------------------------------------------- drawing

@Composable
private fun ScreenLayer(c: OverlayController, img: ImageBitmap, appear: () -> Float, shimmer: () -> Float, edge: () -> Float) {
    val glow = rememberGlowPalette()
    val selection = c.selection
    val layout = c.layout
    val scrim by animateFloatAsState(if (selection == Selection.None) 0.18f else 0.45f, tween(260), label = "scrim")

    // One slow rotation drives every flowing gradient: the frame and the code outlines.
    val spin = rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    // The main wave: once text and codes are both known, one wave of colour runs up from the bottom
    // edge across every word and through every code, so results appear together however fast each was.
    val wave = remember { Animatable(0f) }
    val done = !c.analyzing
    LaunchedEffect(c.sessionId, done) {
        if (done && (!layout.isEmpty || c.codes.isNotEmpty())) {
            wave.snapTo(0f)
            wave.animateTo(1f, tween(1050, easing = FastOutSlowInEasing))
        } else {
            wave.snapTo(0f)
        }
    }
    val entitiesIn = remember { Animatable(0f) }
    LaunchedEffect(c.entities) {
        entitiesIn.snapTo(0f)
        if (c.entities.isNotEmpty()) {
            delay(250)
            entitiesIn.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    val codesIn = remember { Animatable(0f) }
    LaunchedEffect(c.codes) {
        if (c.codes.isNotEmpty()) {
            codesIn.snapTo(0f)
            codesIn.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))
        }
    }
    val selectIn = remember { Animatable(1f) }
    val sheen = remember { Animatable(1f) }
    LaunchedEffect(selection) {
        if (selection != Selection.None) {
            selectIn.snapTo(0f)
            sheen.snapTo(0f)
            launch { sheen.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
            selectIn.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f))
        }
    }
    val trailAlpha = remember { Animatable(0f) }
    LaunchedEffect(c.strokeActive) {
        if (c.strokeActive) {
            trailAlpha.snapTo(1f)
        } else {
            trailAlpha.animateTo(0f, tween(380))
            if (!c.strokeActive) c.trail.clear()
        }
    }

    val selectionPath = remember(selection, layout) { selectionPath(c) }
    val codePaths = remember(c.codes, c.scale, c.density) {
        val k = c.density / c.scale
        c.codes.associate { it.id to roundedPolygon(expandPolygon(it.corners, 8f * k), 14f * k) }
    }
    val entitySegments = remember(c.entities, layout) { entitySegments(c.entities, layout) }

    Canvas(Modifier.fillMaxSize()) {
        val s = c.scale
        val o = c.origin
        val u = density / s // one dp in screenshot pixels
        val full = Size(img.width.toFloat(), img.height.toFloat())
        val deg = spin.value
        withTransform({
            translate(o.x, o.y)
            scale(s, s, Offset.Zero)
        }) {
            drawImage(img)
            drawRect(Color.Black, size = full, alpha = scrim * appear())

            val si = selectIn.value
            val sa = si.coerceIn(0f, 1f)
            when (selection) {
                is Selection.Words -> selectionPath?.let { p ->
                    val b = p.getBounds()
                    val brush = Brush.linearGradient(glow.colors, b.topLeft, b.bottomRight)
                    clipPath(p) { drawImage(img) }
                    withTransform({ scale(0.92f + 0.08f * si, 0.92f + 0.08f * si, b.center) }) {
                        drawPath(p, brush, alpha = 0.30f * sa)
                        drawPath(p, brush, alpha = 0.9f * sa, style = Stroke(width = 1.6f * u))
                    }
                    drawSheen(p, b, sheen.value)
                }
                is Selection.Code -> codePaths[selection.code.id]?.let { p ->
                    clipPath(p) { drawImage(img) }
                    drawSheen(p, p.getBounds(), sheen.value)
                }
                is Selection.Region -> {
                    val r = selection.rect
                    clipRect(r.left, r.top, r.right, r.bottom) { drawImage(img) }
                    drawBrackets(r, u, glow, si)
                }
                Selection.None -> Unit
            }

            // Links, addresses and numbers: a gradient underline that draws itself in.
            val ei = entitiesIn.value
            if (ei > 0f) {
                entitySegments.forEachIndexed { n, (entity, rects) ->
                    val t = (ei * 1.6f - (n % 10) * 0.06f).coerceIn(0f, 1f)
                    if (t <= 0f) return@forEachIndexed
                    for (r in rects) {
                        val y = r.bottom + 2f * u
                        val brush = Brush.horizontalGradient(glow.colors, r.left, r.right)
                        if (selection == Selection.None) {
                            drawRoundRect(
                                glow.at(entity.id * 0.29f),
                                Offset(r.left - 2f * u, r.top - 1.5f * u),
                                Size(r.width + 4f * u, r.height + 3f * u),
                                CornerRadius(4f * u),
                                alpha = 0.16f * t,
                            )
                        }
                        drawLine(brush, Offset(r.left, y), Offset(r.left + r.width * t, y), strokeWidth = 2.6f * u, cap = StrokeCap.Round)
                    }
                }
            }

            // Barcodes: a flowing gradient outline and corner brackets that snap into place. The code itself
            // takes on the colours of the main wave as it passes: its dark modules light up (screen blending
            // tints dark pixels, white stays white).
            val ca = codesIn.value
            val cv = ca.coerceIn(0f, 1f)
            val waveFrom = Offset(full.width / 2f, full.height)
            val waveReach = hypot(full.width / 2f, full.height)
            val prism = Brush.horizontalGradient(glow.loop, 0f, full.width)
            for (code in c.codes) {
                val p = codePaths[code.id] ?: continue
                val selected = (selection as? Selection.Code)?.code?.id == code.id
                val brush = spinBrush(glow.loop, code.center, deg + code.id * 47f)
                val ww = wave.value
                val d = (code.center - waveFrom).getDistance() / waveReach
                val lit = if (ww > 0f && ww < 1f) (1f - abs(d - (ww * 1.2f - 0.1f)) * 3.5f).coerceIn(0f, 1f) else 0f
                if (lit > 0f) {
                    clipPath(p) {
                        drawRect(prism, code.bounds.topLeft, code.bounds.size, alpha = lit * 0.95f, blendMode = BlendMode.Screen)
                    }
                }
                drawPath(p, brush, alpha = (if (selected) 0.16f else 0.10f) * cv)
                drawPath(p, brush, alpha = 0.22f * cv, style = Stroke(width = 16f * u, join = StrokeJoin.Round))
                drawPath(p, brush, alpha = 0.55f * cv, style = Stroke(width = 7f * u, join = StrokeJoin.Round))
                drawPath(p, Color.White, alpha = 0.95f * cv, style = Stroke(width = (if (selected) 3.5f else 2.2f) * u, join = StrokeJoin.Round))
                drawCodeBrackets(code, (1f - ca) * 22f * u + 12f * u, u, brush, cv)
            }

            // The main wave over the words.
            val w = wave.value
            if (w > 0f && w < 1f) {
                val from = Offset(full.width / 2f, full.height)
                val reach = hypot(full.width / 2f, full.height)
                val radius = CornerRadius(4f * u)
                for (word in layout.words) {
                    val d = (word.box.center - from).getDistance() / reach
                    val a = (1f - abs(d - (w * 1.2f - 0.1f)) * 6f).coerceIn(0f, 1f)
                    if (a <= 0f) continue
                    val color = glow.at(word.box.center.x / full.width * 0.8f + w * 0.4f)
                    val grow = a * 2f * u
                    drawRoundRect(
                        color,
                        Offset(word.box.left - grow, word.box.top - grow),
                        Size(word.box.width + 2 * grow, word.box.height + 2 * grow),
                        radius,
                        alpha = a * 0.55f,
                    )
                }
            }

            // The finger trail: coloured glow with a bright core.
            val ta = trailAlpha.value
            if (ta > 0f && c.trail.size > 1) {
                val path = smoothPath(c.trail)
                val b = path.getBounds()
                val brush = Brush.linearGradient(glow.colors, b.topLeft, b.bottomRight)
                drawPath(path, brush, alpha = 0.30f * ta, style = Stroke(width = 26f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, brush, alpha = 0.75f * ta, style = Stroke(width = 12f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, Color.White, alpha = 0.95f * ta, style = Stroke(width = 4.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        c.handleCenters()?.let { (start, end) ->
            drawHandle(start, true, glow.colors[0])
            drawHandle(end, false, glow.colors[2])
        }

        drawActivation(shimmer(), edge(), deg, glow)
    }
}

/** Height of the activation colour band, as a share of the screen height. */
private const val BAND_FRACTION = 0.42f

/** Line pieces of each entity, merged per text line, for underlining. */
private fun entitySegments(entities: List<TextEntity>, layout: TextLayout): List<Pair<TextEntity, List<Rect>>> =
    entities.map { e ->
        e to e.words.map { layout.words[it] }.groupBy { it.line }.values.map { ws ->
            Rect(ws.minOf { it.box.left }, ws.minOf { it.box.top }, ws.maxOf { it.box.right }, ws.maxOf { it.box.bottom })
        }
    }

/** A sweep gradient turned by [degrees] around [center]; animating the angle makes colours flow along a shape. */
private fun spinBrush(colors: List<Color>, center: Offset, degrees: Float): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val ints = IntArray(colors.size) { colors[it].toArgb() }
        return android.graphics.SweepGradient(center.x, center.y, ints, null).apply {
            setLocalMatrix(android.graphics.Matrix().apply { setRotate(degrees, center.x, center.y) })
        }
    }
}

/** A diagonal band of light that sweeps once across a fresh selection. */
private fun DrawScope.drawSheen(p: Path, b: Rect, t: Float) {
    if (t <= 0f || t >= 1f) return
    val band = max(b.height, 24f) * 1.2f
    val x = b.left - band + (b.width + 2 * band) * t
    clipPath(p) {
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.55f * (1f - t * 0.6f)), Color.Transparent),
                start = Offset(x - band / 2f, b.top),
                end = Offset(x + band / 2f, b.bottom),
            ),
            topLeft = b.topLeft,
            size = b.size,
        )
    }
}

/** L-shaped corner marks around a code, pushed out by [out] so they can spring inwards. */
private fun DrawScope.drawCodeBrackets(code: DetectedCode, out: Float, u: Float, brush: Brush, alpha: Float) {
    if (alpha <= 0f) return
    val pts = code.corners
    val n = pts.size
    val center = code.center
    for (i in 0 until n) {
        val c0 = pts[i]
        val dir = (c0 - center).let { v -> v / max(v.getDistance(), 1f) }
        val pos = c0 + dir * out
        val prev = pts[(i + n - 1) % n]
        val next = pts[(i + 1) % n]
        val toPrev = (prev - c0).let { v -> v / max(v.getDistance(), 1f) }
        val toNext = (next - c0).let { v -> v / max(v.getDistance(), 1f) }
        val len = minOf(18f * u, (prev - c0).getDistance() * 0.28f, (next - c0).getDistance() * 0.28f)
        val path = Path().apply {
            moveTo(pos.x + toPrev.x * len, pos.y + toPrev.y * len)
            lineTo(pos.x, pos.y)
            lineTo(pos.x + toNext.x * len, pos.y + toNext.y * len)
        }
        drawPath(path, brush, alpha = 0.45f * alpha, style = Stroke(width = 9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color.White, alpha = alpha, style = Stroke(width = 3.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun selectionPath(c: OverlayController): Path? {
    val sel = c.selection as? Selection.Words ?: return null
    val words = c.layout.words
    val path = Path()
    var i = 0
    val ids = sel.ids
    while (i < ids.size) {
        // Merge consecutive selected words of one line into a single rounded bar.
        val first = words[ids[i]]
        var j = i
        while (j + 1 < ids.size && words[ids[j + 1]].line == first.line && ids[j + 1] == ids[j] + 1) j++
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (k in i..j) {
            val box = words[ids[k]].box
            l = minOf(l, box.left); t = minOf(t, box.top); r = maxOf(r, box.right); b = maxOf(b, box.bottom)
        }
        val h = b - t
        val padX = h * 0.12f
        val padY = h * 0.14f
        path.addRoundRect(RoundRect(Rect(l - padX, t - padY, r + padX, b + padY), CornerRadius(h * 0.28f)))
        i = j + 1
    }
    return path
}

private fun DrawScope.drawBrackets(r: Rect, u: Float, glow: GlowPalette, t: Float) {
    val out = (1f - t) * 14f * u
    val len = minOf(28f * u, r.width / 3f, r.height / 3f)
    val brush = Brush.linearGradient(glow.colors, r.topLeft, r.bottomRight)
    val corners = listOf(
        Triple(r.topLeft, 1f, 1f), Triple(r.topRight, -1f, 1f),
        Triple(r.bottomLeft, 1f, -1f), Triple(r.bottomRight, -1f, -1f),
    )
    for ((p0, dx, dy) in corners) {
        val p = p0 + Offset(-dx * out, -dy * out)
        val path = Path().apply {
            moveTo(p.x + dx * len, p.y)
            lineTo(p.x, p.y)
            lineTo(p.x, p.y + dy * len)
        }
        drawPath(path, brush, alpha = 0.5f, style = Stroke(width = 10f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color.White, style = Stroke(width = 4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    drawRect(brush, r.topLeft, r.size, alpha = 0.8f, style = Stroke(width = 1.5f * u))
}

/** Teardrop selection handle; the pointed corner touches the text. */
private fun DrawScope.drawHandle(center: Offset, start: Boolean, color: Color) {
    val r = 9.dp.toPx()
    val corner = if (start) Rect(center.x, center.y - r, center.x + r, center.y) else Rect(center.x - r, center.y - r, center.x, center.y)
    drawCircle(Color.White, r + 1.5.dp.toPx(), center, alpha = 0.9f)
    drawRect(Color.White, corner.topLeft - Offset(if (start) 0f else 1.5.dp.toPx(), 1.5.dp.toPx()), Size(r + 1.5.dp.toPx(), r + 1.5.dp.toPx()), alpha = 0.9f)
    drawCircle(color, r, center)
    drawRect(color, corner.topLeft, corner.size)
}

/**
 * Activation light show in screen space: a bloom rising from the bottom, a band of prism colours
 * sweeping to the top, and a frame of flowing colour that stays while [edge] is up.
 */
private fun DrawScope.drawActivation(p: Float, edge: Float, degrees: Float, glow: GlowPalette) {
    val w = size.width
    val h = size.height
    if (p > 0f && p < 1f) {
        val intensity = sin(p * PI).toFloat()
        drawRect(
            Brush.radialGradient(
                listOf(glow.at(0.15f).copy(alpha = 0.45f * intensity), glow.at(0.55f).copy(alpha = 0.2f * intensity), Color.Transparent),
                center = Offset(w / 2f, h * 1.05f),
                radius = h * (0.35f + 0.45f * p),
            ),
        )
        // A colourful band: horizontal prism gradient, masked to a soft vertical stripe.
        val band = h * BAND_FRACTION
        val cy = h + band / 2f - p * (h + band)
        drawContext.canvas.saveLayer(Rect(0f, 0f, w, h), Paint())
        drawRect(Brush.horizontalGradient(glow.loop, 0f, w), topLeft = Offset(0f, cy - band / 2f), size = Size(w, band))
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.5f to Color.Black.copy(alpha = 0.6f * intensity),
                1f to Color.Transparent,
                startY = cy - band / 2f,
                endY = cy + band / 2f,
            ),
            topLeft = Offset(0f, cy - band / 2f),
            size = Size(w, band),
            blendMode = BlendMode.DstIn,
        )
        drawContext.canvas.restore()
    }
    if (edge > 0f) {
        val brush = spinBrush(glow.loop, center, degrees * 1.4f)
        val corner = CornerRadius(34.dp.toPx())
        for ((width, alpha) in listOf(22.dp.toPx() to 0.14f, 11.dp.toPx() to 0.32f, 4.dp.toPx() to 0.95f)) {
            drawRoundRect(
                brush,
                topLeft = Offset(width / 2f, width / 2f),
                size = Size(w - width, h - width),
                cornerRadius = corner,
                alpha = alpha * edge,
                style = Stroke(width = width),
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun realDisplayWidth(view: View): Float {
    val display = view.display ?: return 0f
    val m = DisplayMetrics()
    display.getRealMetrics(m)
    return m.widthPixels.toFloat()
}

// -------------------------------------------------------------------- gestures

/**
 * One detector for everything so gestures never fight each other:
 * tap, long-press, highlight stroke, lasso and handle drags.
 */
private fun Modifier.overlayGestures(c: OverlayController) = pointerInput(c) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val handle = c.handleAt(down.position)
        val slop = viewConfiguration.touchSlop
        var outcome = OUTCOME_LONG_PRESS
        val track: suspend AwaitPointerEventScope.() -> Unit = {
            while (true) {
                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (ch == null || !ch.pressed) { outcome = OUTCOME_TAP; break }
                if ((ch.position - down.position).getDistance() > slop) { outcome = OUTCOME_DRAG; break }
            }
        }
        if (handle != null) track() else withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { track() }

        when (outcome) {
            OUTCOME_TAP -> c.onTap(down.position)
            OUTCOME_LONG_PRESS -> {
                c.onLongPress(down.position)
                drain(down.id) {}
            }
            else -> if (handle != null) {
                c.onHandleDragStart(handle)
                drain(down.id) { c.onHandleDrag(it) }
                c.onHandleDragEnd()
            } else {
                c.onStrokeStart(down.position)
                drain(down.id) { c.onStrokeMove(it) }
                c.onStrokeEnd()
            }
        }
    }
}

/** Feeds every position of [id], including batched historical ones, until the finger lifts. */
private suspend fun AwaitPointerEventScope.drain(id: PointerId, onMove: (Offset) -> Unit) {
    while (true) {
        val ch = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: break
        ch.historical.forEach { onMove(it.position) }
        onMove(ch.position)
        ch.consume()
        if (!ch.pressed) break
    }
}

private const val OUTCOME_LONG_PRESS = 0
private const val OUTCOME_TAP = 1
private const val OUTCOME_DRAG = 2

// -------------------------------------------------------------------- barcode badges

@Composable
private fun CodeBadges(c: OverlayController) {
    val codes = c.codes
    if (codes.isEmpty()) return
    val density = LocalDensity.current
    val safeTop = WindowInsets.statusBars.getTop(density) + with(density) { 8.dp.roundToPx() }
    val gap = with(density) { 12.dp.roundToPx() }
    // Keep badges clear of the action row and bar at the bottom.
    val bottomReserve = WindowInsets.navigationBars.getBottom(density) + with(density) { 190.dp.roundToPx() }
    Layout(
        content = { codes.forEachIndexed { i, code -> CodeBadge(c, code, i) } },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val bottomLimit = height - bottomReserve
        // Code outlines in view space, padded like the drawn glow, so badges never cover another code.
        val outlines = codes.map { code ->
            val tl = c.toView(code.bounds.topLeft)
            val br = c.toView(code.bounds.bottomRight)
            Rect(tl, br).inflate(gap.toFloat())
        }
        val taken = ArrayList<Rect>()
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                val box = outlines[i]
                val x = (box.center.x.roundToInt() - p.width / 2).coerceIn(gap, (width - p.width - gap).coerceAtLeast(gap))
                val candidates = listOf(
                    box.top.roundToInt() - p.height - gap / 2, // above
                    box.bottom.roundToInt() + gap / 2, // below
                    box.top.roundToInt() + gap * 2, // on top of its own code
                )
                fun rectAt(y: Int) = Rect(x.toFloat(), y.toFloat(), (x + p.width).toFloat(), (y + p.height).toFloat())
                val y = candidates.firstOrNull { y ->
                    val r = rectAt(y)
                    y >= safeTop && y + p.height <= bottomLimit &&
                        outlines.indices.none { j -> j != i && outlines[j].overlaps(r) } &&
                        taken.none { it.overlaps(r) }
                } ?: candidates.last()
                taken += rectAt(y)
                p.place(x, y)
            }
        }
    }
}

@Composable
private fun CodeBadge(c: OverlayController, code: DetectedCode, index: Int) {
    val (icon, label) = badgeFor(code)
    val glow = rememberGlowPalette()
    val pop = remember(code.id, code.raw) { Animatable(0f) }
    LaunchedEffect(code.id, code.raw) {
        delay(180L + index * 70L)
        pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f))
    }
    Surface(
        onClick = { c.selectCode(code) },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 8.dp,
        border = BorderStroke(2.dp, Brush.linearGradient(glow.colors)),
        modifier = Modifier.graphicsLayer {
            val v = pop.value
            scaleX = 0.4f + 0.6f * v
            scaleY = 0.4f + 0.6f * v
            alpha = v.coerceIn(0f, 1f)
        },
    ) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

private fun badgeFor(code: DetectedCode): Pair<Int, String> = when (val k = code.content) {
    is CodeContent.Link -> R.drawable.ic_link to (android.net.Uri.parse(k.url).host?.removePrefix("www.") ?: k.url)
    is CodeContent.Wifi -> R.drawable.ic_wifi to k.ssid.ifEmpty { "WLAN" }
    is CodeContent.Contact -> R.drawable.ic_person_add to (k.name ?: "Kontakt")
    is CodeContent.Email -> R.drawable.ic_mail to k.to
    is CodeContent.Phone -> R.drawable.ic_call to k.number
    is CodeContent.Sms -> R.drawable.ic_sms to k.number
    is CodeContent.Geo -> R.drawable.ic_place to "Ort"
    is CodeContent.Event -> R.drawable.ic_event to (k.title ?: "Termin")
    is CodeContent.OtherUri -> R.drawable.ic_open to k.uri.substringBefore(':')
    is CodeContent.Product -> R.drawable.ic_qr to k.code
    is CodeContent.Text -> R.drawable.ic_qr to code.formatName
}

// -------------------------------------------------------------------- bottom bar

@Composable
private fun BottomPanel(
    c: OverlayController,
    appear: () -> Float,
    exit: () -> Float,
    barDrag: MutableFloatState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val dismissPx = with(density) { 56.dp.toPx() }
    val hidePx = with(density) { 240.dp.toPx() }
    val actions = remember(c.selection, c.layout, c.codes, c.image, c.entities) { c.actions() }
    val (title, preview) = barTexts(c)

    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            .graphicsLayer {
                translationY = (1f - appear()) * hidePx + barDrag.floatValue + exit() * hidePx
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { d ->
                    barDrag.floatValue = (barDrag.floatValue + d).coerceAtLeast(0f)
                },
                onDragStopped = { velocity ->
                    if (barDrag.floatValue > dismissPx || velocity > 900f) c.dismiss()
                    else animate(barDrag.floatValue, 0f, animationSpec = spring(dampingRatio = 0.7f)) { v, _ -> barDrag.floatValue = v }
                },
            ),
    ) {
        AnimatedContent(
            targetState = actions,
            contentKey = { list -> list.joinToString { it.label } },
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 2 }) togetherWith fadeOut(tween(90)) using
                    SizeTransform(clip = false)
            },
            label = "actions",
        ) { list ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = if (list.isEmpty()) 0.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                list.forEach { ActionButton(it) }
            }
        }

        Surface(
            shape = RoundedCornerShape(30.dp),
            color = scheme.surfaceContainerHigh,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(scheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 12.dp)
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LogoRing(Modifier.size(28.dp), busy = c.analyzing)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(targetState = title, label = "title") {
                            Text(it, style = MaterialTheme.typography.labelLarge, color = scheme.primary, maxLines = 1)
                        }
                        Text(
                            preview,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (c.selection != Selection.None) {
                        IconButton(onClick = { c.clearSelection() }) {
                            Icon(painterResource(R.drawable.ic_close), "Auswahl aufheben", tint = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(a: OverlayAction) {
    val content: @Composable () -> Unit = {
        Icon(painterResource(a.icon), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(a.label, maxLines = 1)
    }
    if (a.primary) {
        Button(
            onClick = a.run,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        ) { content() }
    } else {
        ElevatedButton(
            onClick = a.run,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp),
        ) { content() }
    }
}

private fun barTexts(c: OverlayController): Pair<String, String> {
    val pair = when {
        c.screenshotMissing -> "Kein Bildschirminhalt" to "Erlaube dem Assistenten, Screenshots zu verwenden."
        else -> when (val s = c.selection) {
            Selection.None -> "Encircle" to when {
                c.analyzing -> "Bildschirm wird gelesen …"
                c.codes.size == 1 -> "1 Code erkannt · Tippe ihn an oder markiere Text"
                c.codes.size > 1 -> "${c.codes.size} Codes erkannt · Tippe einen an oder markiere Text"
                c.entities.isNotEmpty() -> entitySummary(c.entities) + " erkannt · Unterstrichenes antippen"
                !c.layout.isEmpty -> "Tippe auf ein Wort, markiere Text oder kreise etwas ein"
                else -> "Kein Text erkannt · Kreise einen Bereich ein"
            }
            is Selection.Words -> c.selectedEntity()?.let { e -> entityTitle(e.kind) to e.value.removePrefix("https://") } ?: run {
                val n = s.ids.size
                (if (n == 1) "Text · 1 Wort" else "Text · $n Wörter") to
                    c.layout.textOf(s.ids).replace('\n', ' ').take(400)
            }
            is Selection.Code -> "${s.code.formatName} · ${typeName(s.code.content)}" to describe(s.code)
            is Selection.Region -> "Bereich" to "Ausschnitt als Bild teilen oder kopieren"
        }
    }
    return (c.feedback ?: pair.first) to pair.second
}

private fun entityTitle(k: EntityKind) = when (k) {
    EntityKind.Link -> "Link"
    EntityKind.Email -> "E-Mail-Adresse"
    EntityKind.Phone -> "Telefonnummer"
    EntityKind.Iban -> "IBAN"
}

private fun entitySummary(list: List<TextEntity>): String =
    EntityKind.entries.mapNotNull { k ->
        val n = list.count { it.kind == k }
        if (n == 0) {
            null
        } else {
            when (k) {
                EntityKind.Link -> if (n == 1) "1 Link" else "$n Links"
                EntityKind.Email -> if (n == 1) "1 Adresse" else "$n Adressen"
                EntityKind.Phone -> if (n == 1) "1 Nummer" else "$n Nummern"
                EntityKind.Iban -> if (n == 1) "1 IBAN" else "$n IBANs"
            }
        }
    }.joinToString(", ")

private fun typeName(k: CodeContent) = when (k) {
    is CodeContent.Link, is CodeContent.OtherUri -> "Link"
    is CodeContent.Wifi -> "WLAN"
    is CodeContent.Contact -> "Kontakt"
    is CodeContent.Email -> "E-Mail"
    is CodeContent.Phone -> "Telefon"
    is CodeContent.Sms -> "SMS"
    is CodeContent.Geo -> "Ort"
    is CodeContent.Event -> "Termin"
    is CodeContent.Product -> "Produktnummer"
    is CodeContent.Text -> "Text"
}

private fun describe(code: DetectedCode): String = when (val k = code.content) {
    is CodeContent.Link -> k.url
    is CodeContent.Wifi -> buildString {
        append(k.ssid)
        if (k.security != "NOPASS") append(" · ").append(k.security)
        if (k.password != null) append(" · Passwort vorhanden")
    }
    is CodeContent.Contact -> k.name ?: "Visitenkarte"
    is CodeContent.Email -> listOfNotNull(k.to, k.subject).joinToString(" · ")
    is CodeContent.Phone -> k.number
    is CodeContent.Sms -> listOfNotNull(k.number, k.body).joinToString(" · ")
    is CodeContent.Geo -> k.uri
    is CodeContent.Event -> listOfNotNull(
        k.title,
        k.start?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) },
        k.location,
    ).joinToString(" · ")
    is CodeContent.OtherUri -> k.uri
    is CodeContent.Product -> k.code
    is CodeContent.Text -> k.text.replace('\n', ' ')
}

// -------------------------------------------------------------------- no screenshot

@Composable
private fun MissingScreenshot(c: OverlayController, appear: () -> Float) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear() }
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { c.dismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(Modifier.padding(24.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_settings), null, tint = MaterialTheme.colorScheme.primary)
                Text("Kein Bildschirminhalt", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Encircle hat keinen Screenshot bekommen. Entweder erlaubt die geöffnete App keine Screenshots, " +
                        "oder der Zugriff ist aus. Aktiviere in den Einstellungen des digitalen Assistenten " +
                        "„Bildschirm- und App-Daten verwenden“, je nach System auch „Screenshot verwenden“.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { if (SystemActions.openAssistSettings(context)) c.finishNow() }) {
                    Text("Einstellungen öffnen")
                }
            }
        }
    }
}
