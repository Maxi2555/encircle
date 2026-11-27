// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.ui

import android.util.DisplayMetrics
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
    // How far the bar has been pushed up from its resting place (0 or negative, in px), and
    // whether the overlay is leaving through the top because the bar was pushed out.
    val barOffset = remember { mutableFloatStateOf(0f) }
    val exitUp = remember { mutableStateOf(false) }
    val exit = remember { Animatable(0f) }
    val barIn = remember { Animatable(0f) }
    val shimmer = remember { Animatable(0f) }
    val finish = remember { Animatable(0f) }
    LaunchedEffect(c.sessionId, active) {
        barOffset.floatValue = 0f
        exitUp.value = false
        if (active) appear.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) else appear.snapTo(0f)
    }
    LaunchedEffect(c.sessionId, active) {
        // The bar springs up with a little overshoot, as if thrown in from below.
        if (active) barIn.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 340f)) else barIn.snapTo(0f)
    }
    LaunchedEffect(c.sessionId, img) {
        // Reset as soon as the screenshot is gone, so the next one never shows a frame of the last sweep.
        shimmer.snapTo(0f)
        if (img == null) return@LaunchedEffect
        // The sweep runs at its own pace while the screen is being read; once it has been read, whatever
        // is left of it hurries out of the way.
        snapshotFlow { c.analyzing }.collectLatest { reading ->
            val ms = (if (reading) SWEEP_MS else SWEEP_FINISH_MS) * (1f - shimmer.value)
            shimmer.animateTo(1f, tween(ms.toInt(), easing = LinearEasing))
        }
    }
    LaunchedEffect(c.sessionId, img, c.analyzing) {
        // The frame stays while the screen is being read; once it has been read, it flashes and winds back.
        if (img == null || c.analyzing) finish.snapTo(0f) else finish.animateTo(1f, tween(FINISH_MS, easing = LinearEasing))
    }
    LaunchedEffect(c.exiting) {
        if (c.exiting) {
            exit.animateTo(1f, tween(190, easing = FastOutLinearInEasing))
            c.finishNow()
        } else {
            exit.snapTo(0f)
        }
    }
    // Fades the overlay while the bar is pushed past the top or bottom edge, previewing that letting go closes it.
    val beyondEdge = remember { mutableFloatStateOf(0f) }

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
                alpha = (1f - exit.value) * (1f - 0.6f * beyondEdge.floatValue)
            },
    ) {
        if (img != null) {
            // The frozen screen and its badges swell a little and settle as the frame goes; the frame and
            // touch input stay put.
            val swell = Modifier.graphicsLayer {
                val k = 1f + SCREEN_POP * sin(PI.toFloat() * (finish.value / POP_SHARE).coerceIn(0f, 1f))
                scaleX = k
                scaleY = k
            }
            ScreenLayer(c, img, appear::value, shimmer::value, finish::value, swell)
            Box(Modifier.fillMaxSize().overlayGestures(c))
            CodeBadges(c, swell)
        } else if (c.screenshotMissing) {
            MissingScreenshot(c, appear::value)
        }
        if (active) {
            BottomPanel(
                c = c,
                appear = barIn::value,
                exit = exit::value,
                barOffset = barOffset,
                exitUp = exitUp,
                beyondEdge = beyondEdge,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// -------------------------------------------------------------------- drawing

@Composable
private fun ScreenLayer(
    c: OverlayController,
    img: ImageBitmap,
    appear: () -> Float,
    shimmer: () -> Float,
    finish: () -> Float,
    modifier: Modifier = Modifier,
) {
    val glow = rememberGlowPalette()
    val selection = c.selection
    val layout = c.layout
    val scrim by animateFloatAsState(if (selection == Selection.None) 0.18f else 0.45f, tween(260), label = "scrim")

    // Once text and codes are both known, a wave runs up from the bottom edge across every word, so
    // results appear together however fast each was.
    val wave = remember { Animatable(0f) }
    val done = !c.analyzing
    LaunchedEffect(c.sessionId, done) {
        wave.snapTo(0f)
        if (done && (!layout.isEmpty || c.codes.isNotEmpty())) wave.animateTo(1f, tween(1050, easing = FastOutSlowInEasing))
    }
    // As the frame dissolves, a quiet aurora takes its place for as long as Encircle is open, so it is
    // always clear that the screen is frozen and being looked at.
    val ambientIn = remember { Animatable(0f) }
    LaunchedEffect(c.sessionId, done) {
        if (done) ambientIn.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) else ambientIn.snapTo(0f)
    }
    val drift = rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(AURORA_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val entitiesIn = remember { Animatable(0f) }
    LaunchedEffect(c.entities) {
        entitiesIn.snapTo(0f)
        if (c.entities.isNotEmpty()) {
            delay(250)
            entitiesIn.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    val codesIn = remember { Animatable(0f) }
    // Codes are often found long before the text; their layer waits for the rest, like the badges.
    LaunchedEffect(c.codes, done) {
        codesIn.snapTo(0f)
        if (c.codes.isNotEmpty() && done) codesIn.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))
    }
    val selectIn = remember { Animatable(1f) }
    // Selecting a code: a ripple spreads from the touch while the focus ring springs in and the corners round out.
    val focus = remember { Animatable(0f) }
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(selection) {
        if (selection is Selection.Code) {
            launch {
                ripple.snapTo(0f)
                ripple.animateTo(1f, tween(600, easing = LinearOutSlowInEasing))
            }
            launch {
                focus.snapTo(0f)
                focus.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
            }
        }
        if (selection != Selection.None) {
            selectIn.snapTo(0f)
            selectIn.animateTo(1f, tween(150))
        }
    }
    val trailAlpha = remember { Animatable(0f) }
    LaunchedEffect(c.strokeActive) {
        if (c.strokeActive) {
            trailAlpha.snapTo(1f)
        } else {
            trailAlpha.animateTo(0f, tween(150))
            if (!c.strokeActive) c.trail.clear()
        }
    }

    val selectionPath = remember(selection, layout) { selectionPath(c) }
    // Marks take the darker of the two primary tones on light surroundings, the lighter on dark ones.
    val scheme = MaterialTheme.colorScheme
    val (darkTone, lightTone) = remember(scheme) { listOf(scheme.primary, scheme.inversePrimary).sortedBy { it.luminance() } }
    // Between the two tones, so it shows on light and dark screens alike; marks take it too until the
    // screenshot's pixels are ready to tell which tone they need.
    val sweepTone = remember(scheme) { lerp(darkTone, lightTone, 0.5f) }
    fun toneAround(outline: List<Offset>, margin: Float) = when (c.isOnLight(outline, margin)) {
        true -> darkTone
        false -> lightTone
        null -> sweepTone
    }
    val codeTone = remember(c.codes, scheme) { c.codes.associate { it.id to toneAround(it.corners, CODE_PAD) } }
    val entityTone = remember(c.entities, scheme) { c.entities.map { toneAround(it.bounds.outline(), MARK_MARGIN) } }
    val selectionTone = remember(selectionPath, scheme) { selectionPath?.let { toneAround(it.getBounds().outline(), MARK_MARGIN) } ?: darkTone }
    // Derived, so the layer recomposes when a stroke starts, not on every point of it.
    val trailStart by remember { derivedStateOf { c.trail.firstOrNull() } }
    val trailTone = remember(trailStart, scheme) { trailStart?.let { toneAround(Rect(it, 1f).outline(), TRAIL_MARGIN) } ?: darkTone }
    val entitySegments = remember(c.entities, layout) { entitySegments(c.entities, layout) }

    Canvas(modifier.fillMaxSize()) {
        val s = c.scale
        val o = c.origin
        val u = density / s // one dp in screenshot pixels
        val full = Size(img.width.toFloat(), img.height.toFloat())
        withTransform({
            translate(o.x, o.y)
            scale(s, s, Offset.Zero)
        }) {
            drawImage(img)
            drawRect(Color.Black, size = full, alpha = scrim * appear())
            drawAurora(drift.value, ambientIn.value * appear(), glow, full)

            val sa = selectIn.value
            when (selection) {
                is Selection.Words -> selectionPath?.let { p ->
                    clipPath(p) { drawImage(img) }
                    drawPath(p, selectionTone, alpha = 0.3f * sa)
                }
                is Selection.Code -> clipPath(codeLayer(selection.code, u, CODE_PAD, CODE_RADIUS + CODE_RADIUS_GROW * focus.value)) { drawImage(img) }
                Selection.None -> Unit
            }

            // Links, addresses and numbers: an underline that draws itself in.
            val ei = entitiesIn.value
            if (ei > 0f) {
                entitySegments.forEachIndexed { n, (_, rects) ->
                    val t = (ei * 1.6f - (n % 10) * 0.06f).coerceIn(0f, 1f)
                    if (t <= 0f) return@forEachIndexed
                    for (r in rects) {
                        val y = r.bottom + 2f * u
                        if (selection == Selection.None) {
                            drawRoundRect(
                                entityTone[n],
                                Offset(r.left - 2f * u, r.top - 1.5f * u),
                                Size(r.width + 4f * u, r.height + 3f * u),
                                CornerRadius(4f * u),
                                alpha = 0.16f * t,
                            )
                        }
                        drawLine(entityTone[n], Offset(r.left, y), Offset(r.left + r.width * t, y), strokeWidth = 2f * u, cap = StrokeCap.Round)
                    }
                }
            }

            // Barcodes: a Material state layer. Selecting one spreads a ripple from the touch, springs the
            // corners rounder and adds a focus ring.
            val ca = codesIn.value
            val a = ca.coerceIn(0f, 1f)
            if (a > 0f) for (code in c.codes) {
                val selected = (selection as? Selection.Code)?.takeIf { it.code.id == code.id }
                val f = if (selected != null) focus.value else 0f
                val tone = codeTone.getValue(code.id)
                val radius = CODE_RADIUS + CODE_RADIUS_GROW * f
                val layer = codeLayer(code, u, CODE_PAD, radius)
                withTransform({ scale(0.96f + 0.04f * ca, 0.96f + 0.04f * ca, code.center) }) {
                    drawPath(layer, tone, alpha = (0.10f + 0.05f * f.coerceIn(0f, 1f)) * a)
                    if (selected != null) {
                        val r = ripple.value
                        if (r < 1f) {
                            val from = selected.touch ?: code.center
                            val reach = code.corners.maxOf { (it - from).getDistance() } + CODE_PAD * u
                            clipPath(layer) { drawCircle(tone, reach * r, from, alpha = 0.14f * (1f - r)) }
                        }
                        drawPath(
                            codeLayer(code, u, CODE_PAD + FOCUS_OFFSET, radius + FOCUS_GAP),
                            tone,
                            alpha = f.coerceIn(0f, 1f),
                            style = Stroke(width = FOCUS_WIDTH * u),
                        )
                    }
                }
            }

            // The wave over the words.
            val w = wave.value
            if (w > 0f && w < 1f) {
                val from = Offset(full.width / 2f, full.height)
                val reach = hypot(full.width / 2f, full.height)
                val radius = CornerRadius(4f * u)
                for (word in layout.words) {
                    val d = (word.box.center - from).getDistance() / reach
                    val lit = (1f - abs(d - (w * 1.2f - 0.1f)) * 6f).coerceIn(0f, 1f)
                    if (lit <= 0f) continue
                    val color = glow.flow(0.04f + 0.14f * word.box.center.x / full.width + 0.1f * w)
                    val grow = lit * 2f * u
                    drawRoundRect(
                        color,
                        Offset(word.box.left - grow, word.box.top - grow),
                        Size(word.box.width + 2 * grow, word.box.height + 2 * grow),
                        radius,
                        alpha = lit * 0.45f,
                    )
                }
            }

            val ta = trailAlpha.value
            if (ta > 0f && c.trail.size > 1) {
                drawPath(smoothPath(c.trail), trailTone, alpha = 0.6f * ta, style = Stroke(width = 4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        c.handleCenters()?.let { (start, end) ->
            drawHandle(start, true, selectionTone)
            drawHandle(end, false, selectionTone)
        }
    }
    Spacer(
        Modifier.fillMaxSize().drawWithCache {
            val edge = screenEdge(size)
            onDrawBehind {
                drawSweep(sweepTone, shimmer())
                drawScreenFrame(edge, glow, shimmer(), finish(), drift.value)
            }
        },
    )
}

/** One full drift cycle of the aurora behind the frozen screen. */
private const val AURORA_PERIOD_MS = 16_000

/**
 * Three large, soft clouds in neighbouring hues of the glow palette drifting slowly over the frozen
 * screen, plus a gentle vignette. Kept at low alpha so text stays readable; drawn below selections,
 * codes and underlines.
 */
private fun DrawScope.drawAurora(t: Float, strength: Float, glow: GlowPalette, full: Size) {
    if (strength <= 0f) return
    val w = full.width
    val h = full.height
    val turn = 2f * PI.toFloat()
    val clouds = listOf(
        Triple(Offset(w * (0.18f + 0.14f * sin(turn * t)), h * (0.84f + 0.07f * cos(turn * t))), 0.62f, glow.flow(0.02f)),
        Triple(Offset(w * (0.82f + 0.12f * cos(turn * (t + 0.33f))), h * (0.18f + 0.08f * sin(turn * (t + 0.33f)))), 0.58f, glow.flow(0.12f)),
        Triple(Offset(w * (0.5f + 0.32f * sin(turn * (t + 0.6f))), h * (0.5f + 0.22f * cos(turn * 2f * t))), 0.48f, glow.flow(0.22f)),
    )
    for ((centre, size, color) in clouds) {
        val radius = h * size
        drawCircle(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.16f * strength), color.copy(alpha = 0.06f * strength), Color.Transparent),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
    // A soft darkening towards the corners frames the content like a stage.
    drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.22f * strength),
            center = Offset(w / 2f, h / 2f),
            radius = hypot(w / 2f, h / 2f),
        ),
        size = full,
    )
}

/** How long the frame takes to flash and go once the screen has been read. */
private const val FINISH_MS = 700

/** How much the frozen screen swells as the frame goes, and the share of [FINISH_MS] the swell takes. */
private const val SCREEN_POP = 0.012f
private const val POP_SHARE = 0.5f

/** How far around a mark the screen is sampled to pick its tone, in dp; around the start of a stroke, wider. */
private const val MARK_MARGIN = 4f
private const val TRAIL_MARGIN = 16f

private fun Rect.outline() = listOf(topLeft, topRight, bottomRight, bottomLeft)

/** Line pieces of each entity, merged per text line, for underlining. */
private fun entitySegments(entities: List<TextEntity>, layout: TextLayout): List<Pair<TextEntity, List<Rect>>> =
    entities.map { e ->
        e to e.words.map { layout.words[it] }.groupBy { it.line }.values.map { ws ->
            Rect(ws.minOf { it.box.left }, ws.minOf { it.box.top }, ws.maxOf { it.box.right }, ws.maxOf { it.box.bottom })
        }
    }

/** How far a code's state layer reaches beyond the code's corners, along the diagonal, in dp. */
private const val CODE_PAD = 14f

/** Corner radius of a code's state layer, in dp, and how much rounder it gets while selected. */
private const val CODE_RADIUS = 16f
private const val CODE_RADIUS_GROW = 8f

/** The focus ring: its width and the gap to the layer in dp, and how far its corners sit outside the layer's. */
private const val FOCUS_WIDTH = 2f
private const val FOCUS_GAP = 3f
private const val FOCUS_OFFSET = 4.2f

/** The area a code's state layer covers: its corners pushed out by [pad] dp and rounded by [radius] dp; [u] is pixels per dp. */
private fun codeLayer(code: DetectedCode, u: Float, pad: Float, radius: Float): Path =
    roundedPolygon(expandPolygon(code.corners, pad * u), radius * u)

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

/** Teardrop selection handle; the pointed corner touches the text. */
private fun DrawScope.drawHandle(center: Offset, start: Boolean, color: Color) {
    val r = 9.dp.toPx()
    val corner = if (start) Rect(center.x, center.y - r, center.x + r, center.y) else Rect(center.x - r, center.y - r, center.x, center.y)
    drawCircle(color, r, center)
    drawRect(color, corner.topLeft, corner.size)
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
private fun CodeBadges(c: OverlayController, modifier: Modifier = Modifier) {
    val codes = c.codes
    // Badges appear with the main wave, once text is known too, so they can be placed clear of it.
    if (codes.isEmpty() || c.analyzing) return
    val words = c.layout.words
    val density = LocalDensity.current
    val safeTop = WindowInsets.statusBars.getTop(density) + with(density) { 8.dp.roundToPx() }
    val gap = with(density) { 12.dp.roundToPx() }
    // Keep badges clear of the action row and bar at the bottom.
    val bottomReserve = WindowInsets.navigationBars.getBottom(density) + with(density) { 190.dp.roundToPx() }
    // A badge that sits on its code moves off it while the code is selected, so the whole code shows.
    val selectedId = (c.selection as? Selection.Code)?.code?.id
    val moved = remember(codes) { codes.map { Animatable(0f) } }
    LaunchedEffect(moved, selectedId) {
        codes.forEachIndexed { i, code ->
            launch { moved[i].animateTo(if (code.id == selectedId) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
        }
    }
    Layout(
        content = { codes.forEachIndexed { i, code -> CodeBadge(c, code, i) } },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val bottomLimit = height - bottomReserve
        // Code outlines in view space, padded like the drawn state layer, so badges never cover another code.
        val outlines = codes.map { code ->
            val tl = c.toView(code.bounds.topLeft)
            val br = c.toView(code.bounds.bottomRight)
            Rect(tl, br).inflate(gap.toFloat())
        }
        // Recognized text in view space: a badge above or below a code must not hide words.
        val text = words.map { w -> Rect(c.toView(w.box.topLeft), c.toView(w.box.bottomRight)) }
        layout(width, height) {
            // Filled anew on every placement: the badges' moves re-run only this block, not the measuring above.
            val taken = ArrayList<Rect>()
            placeables.forEachIndexed { i, p ->
                val box = outlines[i]
                val x = (box.center.x.roundToInt() - p.width / 2).coerceIn(gap, (width - p.width - gap).coerceAtLeast(gap))
                val onCode = box.bottom.roundToInt() - p.height - gap * 2 // inside its own code, near the bottom
                val candidates = listOf(
                    box.top.roundToInt() - p.height - gap / 2, // above
                    box.bottom.roundToInt() + gap / 2, // below
                )
                fun rectAt(y: Int) = Rect(x.toFloat(), y.toFloat(), (x + p.width).toFloat(), (y + p.height).toFloat())
                fun fits(y: Int, avoidText: Boolean): Boolean {
                    val r = rectAt(y)
                    return y >= safeTop && y + p.height <= bottomLimit &&
                        outlines.indices.none { j -> j != i && outlines[j].overlaps(r) } &&
                        taken.none { it.overlaps(r) } &&
                        (!avoidText || text.none { it.overlaps(r) })
                }
                val y = candidates.firstOrNull { fits(it, avoidText = true) }
                    ?: onCode.takeIf { fits(it, avoidText = false) }
                    ?: candidates.firstOrNull { fits(it, avoidText = false) }
                    ?: onCode
                val off = if (y == onCode) candidates.firstOrNull { fits(it, avoidText = false) } ?: y else y
                taken += rectAt(y)
                p.place(x, y + ((off - y) * moved[i].value).roundToInt())
            }
        }
    }
}

@Composable
private fun CodeBadge(c: OverlayController, code: DetectedCode, index: Int) {
    val (icon, label) = badgeFor(code)
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
    barOffset: MutableFloatState,
    exitUp: MutableState<Boolean>,
    beyondEdge: MutableFloatState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val scheme = MaterialTheme.colorScheme
    val hidePx = with(density) { 240.dp.toPx() }
    val homeSnapPx = with(density) { 40.dp.toPx() }
    val closeMarginPx = with(density) { 24.dp.toPx() }
    val safeTopPx = WindowInsets.statusBars.getTop(density) + with(density) { 8.dp.toPx() }
    // Distance from the bar's resting place to the top of the screen, measured from its layout position.
    var restTop by remember { mutableFloatStateOf(0f) }
    var panelHeight by remember { mutableFloatStateOf(0f) }
    // Pushing the bar this far up (to the top edge) closes Encircle when released.
    val closeAt = { (restTop - safeTopPx - closeMarginPx).coerceAtLeast(hidePx) }
    var armed by remember { mutableStateOf(false) }
    val actions = remember(c.selection, c.layout, c.codes, c.image, c.entities, c.analyzing) { c.actions() }
    val (title, preview) = barTexts(c)
    // Long text shows two lines; tapping it expands the bar to the full text, scrollable.
    var expanded by remember(c.selection) { mutableStateOf(false) }
    var cut by remember(preview) { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            .onGloballyPositioned { coords ->
                // Layout position ignores the graphicsLayer translation, so this is the resting place.
                restTop = coords.positionInRoot().y
                panelHeight = coords.size.height.toFloat()
            }
            .graphicsLayer {
                val leaving = if (exitUp.value) -(restTop + panelHeight) else hidePx
                translationY = (1f - appear()) * hidePx + barOffset.floatValue + exit() * leaving
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { d ->
                    // Up moves the bar out of the way; down only as far as off the screen, to close.
                    val next = (barOffset.floatValue + d).coerceIn(-(restTop + panelHeight), panelHeight)
                    barOffset.floatValue = next
                    val past = max(-next - closeAt(), next - panelHeight * 0.5f)
                    beyondEdge.floatValue = (past / panelHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val nowArmed = past > 0f
                    if (nowArmed != armed) {
                        armed = nowArmed
                        view.performHapticFeedback(
                            if (nowArmed) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK,
                        )
                    }
                },
                onDragStopped = { velocity ->
                    val up = -barOffset.floatValue
                    val flungOut = velocity < -2600f && up > restTop * 0.35f
                    val flungDown = velocity > 2600f && -up > panelHeight * 0.2f
                    if (armed || flungOut || flungDown) {
                        // Pushed out at the top or the bottom: leave that way.
                        exitUp.value = up > 0f
                        c.dismiss()
                    } else if (up < homeSnapPx) {
                        animate(barOffset.floatValue, 0f, animationSpec = spring(dampingRatio = 0.75f)) { v, _ ->
                            barOffset.floatValue = v
                        }
                    }
                    // Anywhere else above its resting place the bar simply stays where it was put.
                    armed = false
                    beyondEdge.floatValue = 0f
                },
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = scheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)
                        .heightIn(min = 48.dp),
                    verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically,
                ) {
                    LogoRing(Modifier.size(28.dp), busy = c.analyzing)
                    Spacer(Modifier.width(14.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .animateContentSize()
                            .then(
                                if (cut || expanded) {
                                    Modifier.clickable(
                                        interactionSource = null,
                                        indication = null,
                                        onClickLabel = if (expanded) "Weniger anzeigen" else "Ganzen Text anzeigen",
                                    ) { expanded = !expanded }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        AnimatedContent(targetState = title, label = "title") {
                            Text(it, style = MaterialTheme.typography.labelLarge, color = scheme.primary, maxLines = 1)
                        }
                        if (expanded) {
                            Text(
                                preview,
                                Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurface,
                            )
                        } else {
                            Text(
                                preview,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { cut = it.hasVisualOverflow },
                            )
                        }
                    }
                    if (cut || expanded) {
                        val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "expand")
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                painterResource(R.drawable.ic_expand_more),
                                if (expanded) "Weniger anzeigen" else "Ganzen Text anzeigen",
                                Modifier.graphicsLayer { rotationZ = turn },
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (c.selection != Selection.None) {
                        IconButton(onClick = { c.clearSelection() }) {
                            Icon(painterResource(R.drawable.ic_close), "Auswahl aufheben", tint = scheme.onSurfaceVariant)
                        }
                    }
                }
                AnimatedContent(
                    targetState = actions,
                    contentKey = { list -> list.joinToString { it.label } },
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 3 }) togetherWith fadeOut(tween(90)) using
                            SizeTransform(clip = false)
                    },
                    label = "actions",
                ) { list ->
                    if (list.isNotEmpty()) ActionRow(list)
                }
            }
        }
    }
}

/** The actions for the current selection; the row fades out towards an edge it can still be scrolled past. */
@Composable
private fun ActionRow(actions: List<OverlayAction>) {
    val scroll = rememberScrollState()
    val fade = with(LocalDensity.current) { 24.dp.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (scroll.canScrollBackward) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, fade), blendMode = BlendMode.DstIn)
                }
                if (scroll.canScrollForward) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - fade, size.width), blendMode = BlendMode.DstIn)
                }
            }
            .horizontalScroll(scroll)
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { ActionButton(it) }
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
        Button(onClick = a.run, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) { content() }
    } else {
        FilledTonalButton(onClick = a.run, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) { content() }
    }
}

private fun barTexts(c: OverlayController): Pair<String, String> {
    val pair = when {
        c.screenshotMissing -> "Kein Bildschirminhalt" to "Erlaube dem Assistenten, Screenshots zu verwenden."
        else -> when (val s = c.selection) {
            Selection.None -> when {
                c.analyzing -> "Bildschirm wird gelesen" to "Einkreisen geht schon jetzt"
                c.codes.size == 1 -> "1 Code erkannt" to "Antippen oder Text markieren"
                c.codes.size > 1 -> "${c.codes.size} Codes erkannt" to "Einen antippen oder Text markieren"
                c.entities.isNotEmpty() -> entitySummary(c.entities) + " erkannt" to "Unterstrichenes antippen"
                !c.layout.isEmpty -> "Text erkannt" to "Antippen, markieren oder einkreisen"
                else -> "Kein Text erkannt" to "Der Screenshot lässt sich trotzdem teilen"
            }
            is Selection.Words -> c.selectedEntity()?.let { e -> entityTitle(e.kind) to entityDisplay(e) } ?: run {
                val n = s.ids.size
                (if (n == 1) "Text · 1 Wort" else "Text · $n Wörter") to
                    c.layout.textOf(s.ids).replace('\n', ' ')
            }
            is Selection.Code -> "${s.code.formatName} · ${typeName(s.code.content)}" to describe(s.code)
        }
    }
    return (c.feedback ?: pair.first) to pair.second
}

/** Readable form for the bar: links without the scheme, IBANs in groups of four. */
private fun entityDisplay(e: TextEntity): String = when (e.kind) {
    EntityKind.Link -> e.value.removePrefix("https://")
    EntityKind.Iban -> e.value.chunked(4).joinToString(" ")
    else -> e.value
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
        k.start?.let {
            if (k.allDay) {
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
            } else {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
            }
        },
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
