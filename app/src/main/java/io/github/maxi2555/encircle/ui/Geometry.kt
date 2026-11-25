package io.github.maxi2555.encircle.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Shape analysis of a finger stroke, used to tell a lasso from a highlight swipe. */
internal class StrokeShape(points: List<Offset>, unit: Float) {
    val bounds: Rect
    val length: Float
    val isLasso: Boolean

    init {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        var len = 0f
        for (i in points.indices) {
            val p = points[i]
            l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y)
            if (i > 0) len += (p - points[i - 1]).getDistance()
        }
        bounds = if (points.isEmpty()) Rect.Zero else Rect(l, t, r, b)
        length = len

        val w = bounds.width
        val h = bounds.height
        val diag = hypot(w, h)
        val thin = min(w, h) < 0.2f * max(w, h) && min(w, h) < 40f * unit
        isLasso = if (points.size < 6 || diag < 44f * unit || thin) {
            false
        } else {
            val close = (points.first() - points.last()).getDistance()
            val closed = close < max(56f * unit, 0.33f * diag) && len > 1.7f * diag
            closed || abs(turning(resample(points, 6f * unit))) > 1.4 * PI
        }
    }

    /** Total signed turning angle along the stroke, in radians. A full circle is about 2π. */
    private fun turning(pts: List<Offset>): Double {
        var sum = 0.0
        for (i in 2 until pts.size) {
            val a1 = atan2((pts[i - 1].y - pts[i - 2].y).toDouble(), (pts[i - 1].x - pts[i - 2].x).toDouble())
            val a2 = atan2((pts[i].y - pts[i - 1].y).toDouble(), (pts[i].x - pts[i - 1].x).toDouble())
            var d = a2 - a1
            while (d > PI) d -= 2 * PI
            while (d < -PI) d += 2 * PI
            sum += d
        }
        return sum
    }
}

/** Evenly spaced points along a polyline. */
internal fun resample(points: List<Offset>, spacing: Float): List<Offset> {
    if (points.size < 2) return points
    val out = ArrayList<Offset>()
    out += points[0]
    var carry = 0f
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val seg = (b - a).getDistance()
        if (seg == 0f) continue
        var d = spacing - carry
        while (d <= seg) {
            out += a + (b - a) * (d / seg)
            d += spacing
        }
        carry = seg - (d - spacing)
    }
    if (out.last() != points.last()) out += points.last()
    return out
}

internal fun pointInPolygon(p: Offset, poly: List<Offset>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** Smooth path through finger samples using midpoint quadratic segments. */
internal fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size < 3) {
        points.drop(1).forEach { path.lineTo(it.x, it.y) }
        return path
    }
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        path.quadraticTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
    }
    points.last().let { path.lineTo(it.x, it.y) }
    return path
}

/** Polygon with rounded corners, used for barcode outlines. */
internal fun roundedPolygon(points: List<Offset>, radius: Float): Path {
    val path = Path()
    val n = points.size
    if (n < 3) return path
    for (i in 0 until n) {
        val prev = points[(i - 1 + n) % n]
        val cur = points[i]
        val next = points[(i + 1) % n]
        val toPrev = prev - cur
        val toNext = next - cur
        val r = min(radius, min(toPrev.getDistance(), toNext.getDistance()) / 2f)
        val a = cur + toPrev * (r / toPrev.getDistance())
        val b = cur + toNext * (r / toNext.getDistance())
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadraticTo(cur.x, cur.y, b.x, b.y)
    }
    path.close()
    return path
}

/** Moves every corner away from the centroid by [amount] pixels. */
internal fun expandPolygon(points: List<Offset>, amount: Float): List<Offset> {
    val c = Offset(points.sumOf { it.x.toDouble() }.toFloat() / points.size, points.sumOf { it.y.toDouble() }.toFloat() / points.size)
    return points.map { p ->
        val d = p - c
        val len = d.getDistance()
        if (len == 0f) p else p + d * (amount / len)
    }
}
