// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/** Duration of the sweep that runs while the screen is being read, from the bottom edge to above the top. */
internal const val SWEEP_MS = 1200

/** Time the rest of a full sweep takes once the screen has been read; a partly run sweep takes its share. */
internal const val SWEEP_FINISH_MS = 400

private const val SWEEP_SECONDS = SWEEP_MS / 1000f
private const val DRAW_IN_SECONDS = 0.8f
private const val DOT_LEVELS = 12
private const val LIGHT_STOPS = 17
private const val CORNER_SAMPLES = 12

/**
 * A fine grid of dots lit by a band of light sweeping up the screen like a scanner, at progress [p]
 * (0..1 over [SWEEP_MS]), with a soft light running along. It starts on the bottom edge at full speed
 * so it shows at once, and slows a little towards the top. The dots keep their size; each is brightest
 * while the middle of the band is on it and dims again behind it.
 */
internal fun DrawScope.drawSweep(color: Color, p: Float) {
    if (p <= 0f || p >= 1f) return
    val reach = 140.dp.toPx()
    val band = size.height - (size.height + reach) * LinearOutSlowInEasing.transform(p)
    val spread = 70.dp.toPx()
    val light = 110.dp.toPx()

    val stops = Array(LIGHT_STOPS) { k ->
        val f = k / (LIGHT_STOPS - 1f)
        val d = 4f * f - 2f
        f to color.copy(alpha = 0.14f * exp(-d * d))
    }
    drawRect(Brush.verticalGradient(*stops, startY = band - 2f * light, endY = band + 2f * light), Offset(0f, band - 2f * light), Size(size.width, 4f * light))

    val gap = 8.dp.toPx()
    val firstRow = floor((band - 3f * spread) / gap).toInt().coerceAtLeast(0)
    val lastRow = ceil((band + 3f * spread) / gap).toInt().coerceAtMost((size.height / gap).toInt())
    val cols = (size.width / gap).toInt() + 1
    if (lastRow < firstRow) return
    // Each dot's brightness level (or -1 when too dim to draw), then all dots sorted by level into one
    // array, so every level is drawn in a single call.
    val level = IntArray((lastRow - firstRow + 1) * cols)
    val counts = IntArray(DOT_LEVELS)
    for (row in firstRow..lastRow) {
        val y = (row + 0.5f) * gap
        for (col in 0 until cols) {
            val d = (y - band) / spread + (hash(row, col) - 0.5f) * 0.5f
            val b = exp(-d * d) * (0.75f + 0.25f * hash(col, row + 91))
            val k = if (b < 0.02f) -1 else (b * DOT_LEVELS).toInt().coerceAtMost(DOT_LEVELS - 1)
            level[(row - firstRow) * cols + col] = k
            if (k >= 0) counts[k]++
        }
    }
    val start = IntArray(DOT_LEVELS)
    for (k in 1 until DOT_LEVELS) start[k] = start[k - 1] + counts[k - 1]
    val points = FloatArray(2 * (start[DOT_LEVELS - 1] + counts[DOT_LEVELS - 1]))
    val next = start.copyOf()
    level.forEachIndexed { i, k ->
        if (k < 0) return@forEachIndexed
        points[2 * next[k]] = (i % cols + 0.5f) * gap
        points[2 * next[k] + 1] = (i / cols + firstRow + 0.5f) * gap
        next[k]++
    }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.8f.dp.toPx()
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    val argb = color.toArgb()
    drawIntoCanvas { canvas ->
        for (k in 0 until DOT_LEVELS) {
            if (counts[k] == 0) continue
            paint.color = alpha(argb, (k + 0.5f) / DOT_LEVELS)
            canvas.nativeCanvas.drawPoints(points, 2 * start[k], 2 * counts[k], paint)
        }
    }
}

/** A fixed pseudo-random value in 0..1 for grid cell ([a], [b]), so each dot keeps its own variation. */
private fun hash(a: Int, b: Int): Float {
    var h = a * 73856093 xor b * 19349663
    h = h xor (h ushr 13)
    h *= 0x5bd1e995
    h = h xor (h ushr 15)
    return (h and 0xFFFF) / 65535f
}

/**
 * The screen frame while the screen is being read, along [edge]: colour from [glow] flowing in
 * a thin line, with a soft glow that falls off into the screen and breathes slowly. At the start of the
 * sweep ([p] as in [drawSweep]) it draws itself in from the bottom middle, both ways round to the top.
 * Once reading is done, [finish] runs 0..1: the frame flashes, then winds back the way it came and
 * disappears into the bottom middle. [cycle] runs 0..1 per aurora cycle; every motion repeats a whole
 * number of times per cycle, so the frame never jumps when the cycle starts over.
 */
internal fun DrawScope.drawScreenFrame(edge: ScreenEdge, glow: GlowPalette, p: Float, finish: Float, cycle: Float) {
    if (finish >= 1f) return
    val drawn = (p * SWEEP_SECONDS / DRAW_IN_SECONDS).coerceIn(0f, 1f)
    val back = 1f - smoothstep(RETRACT_FROM, 1f, finish)
    val reach = 0.5f * (1f - (1f - drawn).pow(3)) * back
    val cover = FloatArray(edge.count) { k ->
        val u = abs(edge.s[k] - edge.bottomMiddle).let { minOf(it, 1f - it) }
        ((reach - u) / DRAW_IN_SOFTNESS + 1f).coerceIn(0f, 1f)
    }
    val phase = 3f * cycle + 0.06f * sin(cycle * 8f * PI.toFloat())
    val color = IntArray(edge.count) { glow.flow(edge.s[it] - phase).toArgb() }
    val breath = 0.8f + 0.2f * (0.5f + 0.5f * sin(4f * cycle * 2f * PI.toFloat()))
    val flash = when {
        finish <= 0f -> 0f
        finish < FLASH_PEAK -> FastOutSlowInEasing.transform(finish / FLASH_PEAK)
        else -> exp(-(finish - FLASH_PEAK) / 0.12f)
    }
    val inner = 0.26f * breath + 0.15f * flash
    val line = 0.6f + 0.35f * flash
    val white = Color.White.toArgb()
    val width = 1.5f.dp.toPx()
    val feather = 1.dp.toPx()
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint()
        fun strip(from: Float, to: Float, colorAt: (Int) -> Int, outer: Float, inner: Float) =
            edge.drawStrip(canvas.nativeCanvas, paint, { from }, { to }, { alpha(colorAt(it), outer * cover[it]) }, { alpha(colorAt(it), inner * cover[it]) })
        for (b in 0 until GLOW_STOPS.lastIndex) {
            val from = GLOW_STOPS[b]
            val to = GLOW_STOPS[b + 1]
            val end = if (b + 1 == GLOW_STOPS.lastIndex) 0f else inner * exp(-to / GLOW_FALLOFF)
            strip(from.dp.toPx(), to.dp.toPx(), { color[it] }, inner * exp(-from / GLOW_FALLOFF), end)
        }
        strip(0f, width, { color[it] }, line, line)
        strip(width, width + feather, { color[it] }, line, 0f)
        if (flash > 0f) {
            strip(0f, width, { white }, 0.6f * flash, 0.6f * flash)
            strip(width, width + feather, { white }, 0.6f * flash, 0f)
        }
    }
}

/** Share of the finish at which the frame's flash is brightest, and from which it winds back. */
private const val FLASH_PEAK = 0.15f
private const val RETRACT_FROM = 0.1f

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Length of the soft end of the drawn-in part of the frame, as a share of the whole edge. */
private const val DRAW_IN_SOFTNESS = 0.02f

/** Where the frame's glow is sampled, in dp into the screen, and how quickly it falls off; it ends at the last stop. */
private val GLOW_STOPS = floatArrayOf(0f, 6f, 14f, 24f, 38f, 56f, 80f, 110f)
private const val GLOW_FALLOFF = 22f

private fun alpha(argb: Int, a: Float): Int = ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (argb and 0xFFFFFF)

/** The rounded edge of a screen of [size], as [drawScreenFrame] follows it. */
internal fun Density.screenEdge(size: Size) = ScreenEdge(size.width, size.height, 34.dp.toPx(), 24.dp.toPx())

/**
 * Points along the rounded screen edge with normals pointing into the screen, about [step] apart on the
 * straight sides and denser on the corners so they stay round. [s] is each point's position along the
 * edge, 0..1 clockwise from the top-left.
 */
internal class ScreenEdge(w: Float, h: Float, r: Float, step: Float) {
    val count: Int
    val s: FloatArray

    /** Position of the middle of the bottom edge along the edge. */
    val bottomMiddle: Float
    private val x: FloatArray
    private val y: FloatArray
    private val nx: FloatArray
    private val ny: FloatArray

    init {
        val arc = PI.toFloat() / 2f * r
        val parts = floatArrayOf(w - 2 * r, arc, h - 2 * r, arc, w - 2 * r, arc, h - 2 * r, arc)
        val samples = IntArray(parts.size) { if (it % 2 == 1) CORNER_SAMPLES else ceil(parts[it] / step).toInt().coerceAtLeast(1) }
        count = samples.sum()
        s = FloatArray(count)
        x = FloatArray(count)
        y = FloatArray(count)
        nx = FloatArray(count)
        ny = FloatArray(count)
        val total = parts.sum()
        bottomMiddle = (parts.sliceArray(0..3).sum() + parts[4] / 2f) / total
        var k = 0
        var travelled = 0f
        for (part in parts.indices) {
            for (i in 0 until samples[part]) {
                val d = parts[part] * i / samples[part]
                s[k] = (travelled + d) / total
                when (part) {
                    0 -> set(k, r + d, 0f, 0f, 1f)
                    2 -> set(k, w, r + d, -1f, 0f)
                    4 -> set(k, w - r - d, h, 0f, -1f)
                    6 -> set(k, 0f, h - r - d, 1f, 0f)
                    else -> {
                        val cx = if (part == 1 || part == 3) w - r else r
                        val cy = if (part == 1 || part == 7) r else h - r
                        val a = PI.toFloat() / 2f * (part / 2 - 1) + d / r
                        set(k, cx + r * cos(a), cy + r * sin(a), -cos(a), -sin(a))
                    }
                }
                k++
            }
            travelled += parts[part]
        }
    }

    private fun set(k: Int, px: Float, py: Float, dx: Float, dy: Float) {
        x[k] = px
        y[k] = py
        nx[k] = dx
        ny[k] = dy
    }

    /**
     * Draws a closed band between [from] and [to] (distances into the screen) as a triangle strip, coloured
     * per point: [outer] on the side towards the edge, [inner] on the side towards the middle.
     */
    fun drawStrip(
        canvas: android.graphics.Canvas,
        paint: android.graphics.Paint,
        from: (Int) -> Float,
        to: (Int) -> Float,
        outer: (Int) -> Int,
        inner: (Int) -> Int,
    ) {
        val n = count + 1
        val verts = FloatArray(n * 4)
        val colors = IntArray(n * 2)
        for (j in 0 until n) {
            val k = j % count
            val a = from(k)
            val b = to(k)
            verts[j * 4] = x[k] + nx[k] * a
            verts[j * 4 + 1] = y[k] + ny[k] * a
            verts[j * 4 + 2] = x[k] + nx[k] * b
            verts[j * 4 + 3] = y[k] + ny[k] * b
            colors[j * 2] = outer(k)
            colors[j * 2 + 1] = inner(k)
        }
        canvas.drawVertices(android.graphics.Canvas.VertexMode.TRIANGLE_STRIP, verts.size, verts, 0, null, 0, colors, 0, null, 0, 0, paint)
    }
}
