// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.analysis

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.sqrt

/** One recognized word. All coordinates are screenshot pixels. */
class Word(val index: Int, val text: String, val box: Rect, val line: Int, val block: Int)

class TextLine(val index: Int, val block: Int, val first: Int, val last: Int, val box: Rect)

/** A paragraph-like group of lines as found by the recognizer. */
class TextBlock(val index: Int, val first: Int, val last: Int, val firstLine: Int, val lastLine: Int, val box: Rect)

/**
 * Words in reading order plus their line and paragraph structure.
 * Selections are expressed as sorted word indices into [words].
 */
class TextLayout(val words: List<Word>, val lines: List<TextLine>, val blocks: List<TextBlock>) {

    val isEmpty: Boolean get() = words.isEmpty()
    val allIndices: IntArray by lazy { IntArray(words.size) { it } }

    /** The word under [p], allowing [slop] pixels of tolerance. Prefers the closest, then the smallest box. */
    fun wordAt(p: Offset, slop: Float): Int? {
        var best = -1
        var bestDist = Float.MAX_VALUE
        var bestArea = Float.MAX_VALUE
        for (w in words) {
            val d = w.box.distanceTo(p)
            if (d > slop) continue
            val area = w.box.width * w.box.height
            if (d < bestDist - 0.5f || (d <= bestDist + 0.5f && area < bestArea)) {
                best = w.index; bestDist = d; bestArea = area
            }
        }
        return best.takeIf { it >= 0 }
    }

    /** Closest word to [p] within [maxDist] pixels. Vertical distance counts double so lines stay sticky. */
    fun nearestWord(p: Offset, maxDist: Float): Int? {
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (w in words) {
            val dx = max(0f, max(w.box.left - p.x, p.x - w.box.right))
            val dy = max(0f, max(w.box.top - p.y, p.y - w.box.bottom)) * 2f
            val d = sqrt(dx * dx + dy * dy)
            if (d < bestDist) { bestDist = d; best = w.index }
        }
        return best.takeIf { it >= 0 && bestDist <= maxDist }
    }

    fun blockRange(i: Int): IntRange = blocks[words[i].block].let { it.first..it.last }

    fun sentenceRange(i: Int): IntRange {
        val b = blocks[words[i].block]
        var s = i
        while (s > b.first && !endsSentence(words[s - 1].text)) s--
        var e = i
        while (e < b.last && !endsSentence(words[e].text)) e++
        return s..e
    }

    /** Smallest block whose padded box contains both points. */
    fun blockContaining(a: Offset, b: Offset, pad: Float): TextBlock? =
        blocks.filter { it.box.inflate(pad).let { r -> r.contains(a) && r.contains(b) } }
            .minByOrNull { it.box.width * it.box.height }

    /**
     * Joins the selected words into clean text: spaces within a line, reflowed lines inside a paragraph,
     * real line breaks where a line clearly ends early or a list starts, blank lines between paragraphs,
     * and hyphens at line ends removed when the word continues on the next line.
     */
    fun textOf(indices: IntArray): String {
        val sb = StringBuilder()
        var prev: Word? = null
        for (i in indices) {
            val w = words[i]
            val p = prev
            if (p != null) {
                when {
                    p.line == w.line -> sb.append(' ')
                    p.block != w.block -> sb.append("\n\n")
                    endsWithHyphen(p.text) && w.text.firstOrNull()?.isLowerCase() == true -> sb.setLength(sb.length - 1)
                    isHardBreak(p, w) -> sb.append('\n')
                    else -> sb.append(' ')
                }
            }
            sb.append(w.text)
            prev = w
        }
        return sb.toString()
    }

    private fun isHardBreak(a: Word, b: Word): Boolean {
        val line = lines[a.line]
        val block = blocks[a.block]
        val endsEarly = line.box.right < block.box.right - block.box.width * 0.18f
        val nextIsList = LIST_START.matches(b.text)
        val lineGap = lines[b.line].box.top - line.box.bottom > line.box.height * 0.9f
        return endsEarly || nextIsList || lineGap
    }

    companion object {
        val EMPTY = TextLayout(emptyList(), emptyList(), emptyList())

        private val LIST_START = Regex("""^([•·▪◦‣–—*-]|\d{1,3}[.)]|[a-zA-Z][.)])$""")
        private val ABBREVIATIONS = setOf(
            "z.b.", "bzw.", "ca.", "nr.", "dr.", "prof.", "u.a.", "etc.", "e.g.", "i.e.", "mr.", "mrs.", "ms.",
            "vs.", "usw.", "d.h.", "evtl.", "ggf.", "inkl.", "exkl.", "st.", "str.", "abs.", "s.", "vgl.", "bspw.",
            "zzgl.", "max.", "min.", "tel.", "jan.", "feb.", "mär.", "apr.", "jun.", "jul.", "aug.", "sep.",
            "sept.", "okt.", "nov.", "dez.", "no.", "approx.", "dept.", "inc.", "ltd.", "co.",
        )
        private val ORDINAL = Regex("""^\d{1,4}\.$""")
        private val INITIAL = Regex("""^\p{L}\.$""")
        private val CLOSERS = charArrayOf('"', '”', '“', '\'', '’', '»', '«', ')', ']')

        fun endsSentence(raw: String): Boolean {
            val t = raw.trimEnd(*CLOSERS)
            if (t.isEmpty()) return false
            val last = t.last()
            if (last == '!' || last == '?' || last == '…') return true
            if (last != '.') return false
            val lower = t.lowercase()
            return lower !in ABBREVIATIONS && !ORDINAL.matches(t) && !INITIAL.matches(t)
        }

        private fun endsWithHyphen(t: String) =
            t.length > 2 && (t.endsWith('-') || t.endsWith('­') || t.endsWith('‐'))
    }
}

internal fun Rect.distanceTo(p: Offset): Float {
    val dx = max(0f, max(left - p.x, p.x - right))
    val dy = max(0f, max(top - p.y, p.y - bottom))
    return sqrt(dx * dx + dy * dy)
}
