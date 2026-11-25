// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.analysis

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One recognized text fragment, as the OCR engine reports it. */
class RawElement(val text: String, val box: Rect)

/** One line as the OCR engine reports it; [sourceBlock] is the engine's own paragraph guess. */
class RawLine(val elements: List<RawElement>, val sourceBlock: Int)

/**
 * Turns raw OCR lines into words, lines and paragraphs that match what a reader sees.
 *
 * OCR engines split visual lines into several pieces, break one paragraph into many blocks and cut
 * strings like links at punctuation. This builder rebuilds the structure from geometry instead:
 * pieces of one string (a link split at a dot) become one word, pieces on the same row become one line, and lines that
 * follow each other with normal line spacing, similar size and a shared left edge form a paragraph.
 */
object LayoutBuilder {

    private class Line(var elements: List<RawElement>, val source: Int) {
        val box: Rect get() = union(elements.map { it.box })
        val height: Float get() = median(elements.map { it.box.height })
    }

    fun build(raw: List<RawLine>): TextLayout {
        val lines = raw
            .map { l -> Line(l.elements.filter { it.text.isNotBlank() }.sortedBy { it.box.left }, l.sourceBlock) }
            .filter { it.elements.isNotEmpty() }
        val rows = mergeRows(lines)
        val paragraphs = groupParagraphs(rows)

        val words = ArrayList<Word>()
        val outLines = ArrayList<TextLine>()
        val blocks = ArrayList<TextBlock>()
        for (para in paragraphs) {
            val firstWord = words.size
            val firstLine = outLines.size
            for (line in para) {
                val lineFirst = words.size
                for ((text, box) in glue(line)) {
                    words += Word(words.size, text, box, outLines.size, blocks.size)
                }
                outLines += TextLine(outLines.size, blocks.size, lineFirst, words.size - 1, union(words.subList(lineFirst, words.size).map { it.box }))
            }
            blocks += TextBlock(blocks.size, firstWord, words.size - 1, firstLine, outLines.size - 1, union(words.subList(firstWord, words.size).map { it.box }))
        }
        return TextLayout(words, outLines, blocks)
    }

    /** Pieces of one visual row, reported as separate lines, become a single line. */
    private fun mergeRows(input: List<Line>): List<Line> {
        val lines = input.sortedBy { it.box.top }.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in lines.indices) {
                for (j in lines.indices) {
                    if (i == j) continue
                    val a = lines[i]
                    val b = lines[j]
                    val ab = a.box
                    val bb = b.box
                    val h = min(a.height, b.height)
                    val ratio = a.height / b.height
                    val vOverlap = min(ab.bottom, bb.bottom) - max(ab.top, bb.top)
                    val gap = bb.left - ab.right
                    if (ratio in 0.75f..1.33f && vOverlap >= 0.6f * h && gap >= -0.3f * h && gap <= 1.1f * h) {
                        a.elements = (a.elements + b.elements).sortedBy { it.box.left }
                        lines.removeAt(j)
                        merged = true
                        break@loop
                    }
                }
            }
        }
        return lines.sortedBy { it.box.top }
    }

    /** Lines that read as one paragraph are grouped; paragraphs come back in reading order. */
    private fun groupParagraphs(rows: List<Line>): List<List<Line>> {
        val paragraphs = ArrayList<MutableList<Line>>()
        for (line in rows) {
            val lb = line.box
            var best: MutableList<Line>? = null
            var bestGap = Float.MAX_VALUE
            for (p in paragraphs) {
                val last = p.last()
                val pb = last.box
                val h = max(last.height, line.height)
                val ratio = line.height / last.height
                if (ratio !in 0.72f..1.38f) continue
                val gap = lb.top - pb.bottom
                val maxGap = if (last.source == line.source) 1.1f * h else 0.8f * h
                if (gap < -0.3f * h || gap > maxGap) continue
                // Once a paragraph has a rhythm, a clearly larger gap starts the next paragraph.
                if (p.size >= 2) {
                    val rhythm = last.box.top - p[p.size - 2].box.bottom
                    if (gap > max(rhythm * 1.5f + 0.1f * h, 0.3f * h)) continue
                }
                val overlap = min(lb.right, pb.right) - max(lb.left, pb.left)
                val aligned = abs(lb.left - pb.left) <= 1.2f * h
                if (overlap < 0.3f * min(lb.width, pb.width) && !aligned) continue
                // The line must start inside the paragraph, not far to its left (that would be another column).
                if (lb.left < p.minOf { it.box.left } - 2f * h) continue
                if (gap < bestGap) {
                    bestGap = gap
                    best = p
                }
            }
            if (best != null) best += line else paragraphs += mutableListOf(line)
        }
        return paragraphs.sortedWith(compareBy({ it.first().box.top }, { it.first().box.left }))
    }

    /** Fragments that belong to one string are one word, e.g. a link split after a dot or slash. */
    private fun glue(line: Line): List<Pair<String, Rect>> {
        val h = line.height
        val out = ArrayList<Pair<String, Rect>>()
        var text = StringBuilder()
        var box: Rect? = null
        for (e in line.elements) {
            val b = box
            val gap = if (b == null) 0f else e.box.left - b.right
            if (b != null && gap < MAX_GLUE_GAP * h && belongTogether(text.toString(), e.text)) {
                text.append(e.text)
                box = union(listOf(b, e.box))
            } else {
                if (b != null) out += text.toString() to b
                text = StringBuilder(e.text)
                box = e.box
            }
        }
        box?.let { out += text.toString() to it }
        return out
    }

    /** Pieces further apart than this are never one string, whatever their text says. */
    private const val MAX_GLUE_GAP = 0.6f

    /**
     * OCR splits links and addresses after dots and slashes, and its box gaps are too noisy to tell
     * a real space from a split. The text decides instead: "https://example." + "org", "hallo@" +
     * "example.org" or "example.org" + "/pfad" are one string, "Ende." + "Neuer" is not.
     */
    internal fun belongTogether(prev: String, next: String): Boolean {
        if (prev.isEmpty() || next.isEmpty()) return false
        val last = prev.last()
        val first = next.first()
        if (last == '@' || last == '/' || first == '@' || first == '/') return true
        if (prev.endsWith("://") || prev.endsWith("www.", ignoreCase = true)) return true
        if (last != '.' || !(first.isLowerCase() || first.isDigit())) return false
        val linkish = prev.contains("://") || prev.contains('@') || prev.startsWith("www.", ignoreCase = true)
        if (linkish) return true
        // "example." + "org/pfad": a bare domain followed by a known ending.
        val tld = next.takeWhile { it.isLetter() }
        return DOMAIN_START.matches(prev) && tld.lowercase() in EntityFinder.TLDS && tld.all { it.isLowerCase() }
    }

    private val DOMAIN_START = Regex("""^[a-z0-9][a-z0-9-]*(\.[a-z0-9-]+)*\.$""")

    private fun union(boxes: List<Rect>): Rect {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (box in boxes) {
            if (box.left < l) l = box.left
            if (box.top < t) t = box.top
            if (box.right > r) r = box.right
            if (box.bottom > b) b = box.bottom
        }
        return Rect(l, t, r, b)
    }

    private fun median(v: List<Float>): Float {
        val s = v.sorted()
        return if (s.isEmpty()) 1f else s[s.size / 2].coerceAtLeast(1f)
    }
}
