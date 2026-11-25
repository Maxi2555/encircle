package io.github.maxi2555.encircle.ui

import android.util.DisplayMetrics
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import io.github.maxi2555.encircle.R
import io.github.maxi2555.encircle.actions.SystemActions
import io.github.maxi2555.encircle.analysis.CodeContent
import io.github.maxi2555.encircle.analysis.DetectedCode
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.abs
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
    LaunchedEffect(c.sessionId, active) {
        barDrag.floatValue = 0f
        if (active) appear.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) else appear.snapTo(0f)
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
            ScreenLayer(c, img, appear::value, shimmer::value)
            Box(Modifier.fillMaxSize().overlayGestures(c))
            CodeBadges(c)
        } else if (c.screenshotMissing) {
            MissingScreenshot(c, appear::value)
        }
        if (active) {
            BottomPanel(
                c = c,
                appear = appear::value,
                exit = exit::value,
                barDrag = barDrag,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// -------------------------------------------------------------------- drawing

@Composable
private fun ScreenLayer(c: OverlayController, img: ImageBitmap, appear: () -> Float, shimmer: () -> Float) {
    val scheme = MaterialTheme.colorScheme
    val selection = c.selection
    val layout = c.layout
    val scrim by animateFloatAsState(if (selection == Selection.None) 0.16f else 0.42f, tween(260), label = "scrim")

    val wave = remember { Animatable(0f) }
    LaunchedEffect(layout) {
        if (!layout.isEmpty) {
            wave.snapTo(0f)
            wave.animateTo(1f, tween(650, easing = LinearOutSlowInEasing))
        }
    }
    val codesIn = remember { Animatable(0f) }
    LaunchedEffect(c.codes) {
        if (c.codes.isNotEmpty()) {
            codesIn.snapTo(0f)
            codesIn.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
        }
    }
    val pulse = if (c.codes.isNotEmpty()) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
            label = "pulse",
        )
    } else {
        null
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

    Canvas(Modifier.fillMaxSize()) {
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

            when (selection) {
                is Selection.Words -> selectionPath?.let { p ->
                    clipPath(p) { drawImage(img) }
                    drawPath(p, scheme.primary, alpha = 0.30f)
                }
                is Selection.Code -> codePaths[selection.code.id]?.let { p -> clipPath(p) { drawImage(img) } }
                is Selection.Region -> {
                    val r = selection.rect
                    clipRect(r.left, r.top, r.right, r.bottom) { drawImage(img) }
                    drawBrackets(r, u)
                }
                Selection.None -> Unit
            }

            // Barcode outlines with a soft breathing glow.
            val ca = codesIn.value.coerceIn(0f, 1.2f)
            val glow = pulse?.value ?: 0f
            for (code in c.codes) {
                val p = codePaths[code.id] ?: continue
                val selected = (selection as? Selection.Code)?.code?.id == code.id
                drawPath(p, scheme.primary, alpha = (if (selected) 0.18f else 0.10f) * ca.coerceAtMost(1f))
                drawPath(
                    p, scheme.primary, alpha = ((0.30f + 0.30f * glow) * ca).coerceIn(0f, 1f),
                    style = Stroke(width = 11f * u, join = StrokeJoin.Round),
                )
                drawPath(
                    p, Color.White, alpha = (0.95f * ca).coerceIn(0f, 1f),
                    style = Stroke(width = (if (selected) 4f else 2.5f) * u, join = StrokeJoin.Round),
                )
            }

            // A light wave over the words once text has been found.
            val w = wave.value
            if (w > 0f && w < 1f) {
                val radius = CornerRadius(4f * u)
                for (word in layout.words) {
                    val y = word.box.center.y / full.height
                    val a = (1f - abs(y - w) * 7f).coerceIn(0f, 1f)
                    if (a > 0f) drawRoundRect(scheme.primary, word.box.topLeft, word.box.size, radius, alpha = a * 0.4f)
                }
            }

            // The finger trail: coloured glow with a bright core.
            val ta = trailAlpha.value
            if (ta > 0f && c.trail.size > 1) {
                val path = smoothPath(c.trail)
                val b = path.getBounds()
                drawPath(
                    path,
                    Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary), b.topLeft, b.bottomRight),
                    alpha = 0.55f * ta,
                    style = Stroke(width = 16f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(path, Color.White, alpha = 0.95f * ta, style = Stroke(width = 5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        c.handleCenters()?.let { (start, end) ->
            drawHandle(start, true, scheme.primary)
            drawHandle(end, false, scheme.primary)
        }

        drawShimmer(shimmer(), scheme.primary, scheme.tertiary, scheme.secondary)
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

private fun DrawScope.drawBrackets(r: Rect, u: Float) {
    val len = minOf(28f * u, r.width / 3f, r.height / 3f)
    val stroke = Stroke(width = 4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val corners = listOf(
        Triple(r.topLeft, 1f, 1f), Triple(r.topRight, -1f, 1f),
        Triple(r.bottomLeft, 1f, -1f), Triple(r.bottomRight, -1f, -1f),
    )
    for ((p, dx, dy) in corners) {
        val path = Path().apply {
            moveTo(p.x + dx * len, p.y)
            lineTo(p.x, p.y)
            lineTo(p.x, p.y + dy * len)
        }
        drawPath(path, Color.White, style = stroke)
    }
    drawRect(Color.White, r.topLeft, r.size, alpha = 0.6f, style = Stroke(width = 1f * u))
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

/** One sweep of accent-coloured light from the bottom to the top, plus a fading edge glow. */
private fun DrawScope.drawShimmer(p: Float, a: Color, b: Color, c: Color) {
    if (p <= 0f || p >= 1f) return
    val h = size.height
    val band = h * 0.5f
    val cy = h + band / 2f - p * (h + band)
    val intensity = sin(p * PI).toFloat()
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.3f to b.copy(alpha = 0.32f * intensity),
            0.5f to a.copy(alpha = 0.42f * intensity),
            0.7f to c.copy(alpha = 0.32f * intensity),
            1f to Color.Transparent,
            startY = cy - band / 2f,
            endY = cy + band / 2f,
        ),
    )
    val edge = 6.dp.toPx()
    drawRoundRect(
        Brush.sweepGradient(listOf(a, b, c, a), center),
        topLeft = Offset(edge / 2, edge / 2),
        size = Size(size.width - edge, size.height - edge),
        cornerRadius = CornerRadius(32.dp.toPx()),
        alpha = (1f - p) * 0.9f,
        style = Stroke(width = edge),
    )
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
        content = { codes.forEach { CodeBadge(c, it) } },
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
private fun CodeBadge(c: OverlayController, code: DetectedCode) {
    val (icon, label) = badgeFor(code)
    Surface(
        onClick = { c.selectCode(code) },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
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
    val actions = remember(c.selection, c.layout, c.codes, c.image) { c.actions() }
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
                !c.layout.isEmpty -> "Tippe auf ein Wort, markiere Text oder kreise etwas ein"
                else -> "Kein Text erkannt · Kreise einen Bereich ein"
            }
            is Selection.Words -> {
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
