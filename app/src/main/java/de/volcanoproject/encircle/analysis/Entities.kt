// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.analysis

import androidx.compose.ui.geometry.Rect
import java.math.BigInteger
import java.util.Locale

enum class EntityKind { Link, Email, Phone, Iban }

/** A match inside a plain string. [value] is the cleaned, usable form (URL, address, number). */
class EntityMatch(val kind: EntityKind, val value: String, val start: Int, val end: Int)

/** A match on screen, tied to the words it covers. */
class TextEntity(val id: Int, val kind: EntityKind, val value: String, val words: IntArray, val bounds: Rect) {
    val first get() = words.first()
    val last get() = words.last()
    operator fun contains(word: Int) = word in first..last
}

/**
 * Finds links, e-mail addresses, phone numbers and IBANs in recognized text.
 * Tuned for OCR output: tolerates trailing punctuation, links wrapped over two lines,
 * spelled-out addresses like "name (at) example (dot) org" and bare domains like "example.org".
 */
object EntityFinder {

    private const val URL_CHAR = """[^\s<>"'„“”«»‚‘’\[\]{}|\\^`]"""

    private val EMAIL = Regex(
        """(?i)(?<![\w.%+-])[a-z0-9][a-z0-9._%+-]{0,63}(?:@| ?\(at\) ?| ?\[at\] ?)""" +
            """(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.| ?\(dot\) ?| ?\[dot\] ?))+[a-z]{2,24}\b""",
    )
    private val SCHEME_URL = Regex("""(?i)\b(?:https?|ftp)://$URL_CHAR+""")
    private val WWW_URL = Regex("""(?i)(?<![\w.@/-])www\d{0,3}\.$URL_CHAR+""")
    private val BARE_URL = Regex(
        """(?i)(?<![\w.@/:%+-])((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+)([a-z]{2,24})\b(?::\d{2,5})?(?:/$URL_CHAR*)?""",
    )
    // A line break may stand where a space would, when a number wraps.
    private val PHONE = Regex("""(?<![\w+/.,])(?:\+|\(\+?|0)[\d \n()/.\-–]{5,24}\d(?![\d,.]?\d)""")
    // Groups are separated by a space, or by a line break where the IBAN wraps.
    private val IBAN = Regex("""\b[A-Z]{2}\d{2}(?:[ \n]?[A-Z0-9]{4}){2,7}(?:[ \n]?[A-Z0-9]{1,3})?\b""")
    private val DATE = Regex("""\d{1,2}\.\d{1,2}\.\d{2,4}""")

    /** Top-level domains that are common enough to accept without "www." or "https://". */
    internal val TLDS = setOf(
        "com", "net", "org", "de", "at", "ch", "eu", "io", "dev", "app", "info", "biz", "me", "co", "uk", "us",
        "fr", "it", "es", "nl", "be", "pl", "se", "no", "dk", "fi", "cz", "sk", "hu", "lu", "li", "is", "ie",
        "pt", "gr", "ro", "bg", "hr", "si", "rs", "tr", "ru", "ua", "jp", "cn", "kr", "in", "br", "au", "ca",
        "nz", "za", "mx", "ar", "edu", "gov", "int", "tv", "fm", "ai", "gg", "xyz", "ly", "to", "cc", "ws",
        "page", "link", "online", "shop", "site", "store", "tech", "cloud", "blog", "news", "email", "digital",
        "media", "studio", "design", "berlin", "hamburg", "koeln", "bayern", "nrw", "wien", "swiss", "social",
        "zone", "club", "live", "world", "travel", "health", "bank", "art", "one", "team", "network", "systems",
    )

    /** Every entity in [text], without overlaps, in text order. */
    fun findIn(text: String): List<EntityMatch> {
        val out = ArrayList<EntityMatch>()
        fun free(s: Int, e: Int) = out.none { s < it.end && e > it.start }

        for (m in EMAIL.findAll(text)) {
            val raw = m.value
            val address = raw.replace(" ", "")
                .replace(Regex("""(?i)\(at\)|\[at\]"""), "@")
                .replace(Regex("""(?i)\(dot\)|\[dot\]"""), ".")
            if (address.count { it == '@' } == 1) out += EntityMatch(EntityKind.Email, address, m.range.first, m.range.last + 1)
        }
        for (rx in listOf(SCHEME_URL, WWW_URL)) {
            for (m in rx.findAll(text)) {
                val (s, e) = trimUrl(text, m.range.first, m.range.last + 1)
                val url = text.substring(s, e)
                if (e - s > 6 && free(s, e)) out += EntityMatch(EntityKind.Link, withScheme(url), s, e)
            }
        }
        for (m in BARE_URL.findAll(text)) {
            val tld = m.groupValues[2].lowercase(Locale.ROOT)
            val labels = m.groupValues[1]
            if (tld !in TLDS || labels.none { it.isLetter() }) continue
            // "Ende.Es" is a sentence with a lost space, not a link; real domains are written in lower case.
            val hasPath = m.value.contains('/')
            if (!hasPath && m.groupValues[2].any { it.isUpperCase() }) continue
            val (s, e) = trimUrl(text, m.range.first, m.range.last + 1)
            if (free(s, e)) out += EntityMatch(EntityKind.Link, withScheme(text.substring(s, e)), s, e)
        }
        for (m in IBAN.findAll(text)) {
            val compact = m.value.filterNot { it.isWhitespace() }
            if (compact.length in 15..34 && ibanValid(compact) && free(m.range.first, m.range.last + 1)) {
                out += EntityMatch(EntityKind.Iban, compact, m.range.first, m.range.last + 1)
            }
        }
        for (m in PHONE.findAll(text)) {
            var s = m.range.first
            val e = m.range.last + 1
            val raw = m.value
            val digits = raw.count { it.isDigit() }
            if (digits !in 7..15 || DATE.containsMatchIn(raw)) continue
            if (raw.startsWith("(") && !raw.contains(')')) s++
            if (!free(s, e)) continue
            val number = buildString {
                if (raw.trimStart('(').startsWith("+")) append('+')
                raw.filter { it.isDigit() }.let(::append)
            }
            out += EntityMatch(EntityKind.Phone, number, s, e)
        }
        return out.sortedBy { it.start }
    }

    /** Entities on screen. Lines of one paragraph are searched together so wrapped links are found too. */
    fun find(layout: TextLayout): List<TextEntity> {
        val result = ArrayList<TextEntity>()
        for (block in layout.blocks) {
            val sb = StringBuilder()
            val starts = IntArray(block.last - block.first + 1)
            val ends = IntArray(starts.size)
            for (i in block.first..block.last) {
                val w = layout.words[i]
                if (i > block.first) {
                    val prev = layout.words[i - 1]
                    sb.append(
                        when {
                            prev.line == w.line -> " "
                            continuesUrl(prev.text, w.text) -> ""
                            LayoutBuilder.belongTogether(prev.text, w.text) -> ""
                            isForcedBreak(layout, prev, w) -> ""
                            else -> "\n"
                        },
                    )
                }
                starts[i - block.first] = sb.length
                sb.append(w.text)
                ends[i - block.first] = sb.length
            }
            for (m in findIn(sb.toString())) {
                val ids = (block.first..block.last).filter { i ->
                    starts[i - block.first] < m.end && ends[i - block.first] > m.start
                }
                if (ids.isEmpty()) continue
                val boxes = ids.map { layout.words[it].box }
                val bounds = Rect(boxes.minOf { it.left }, boxes.minOf { it.top }, boxes.maxOf { it.right }, boxes.maxOf { it.bottom })
                result += TextEntity(result.size, m.kind, m.value, ids.toIntArray(), bounds)
            }
        }
        return result
    }

    /**
     * A link too long for its line is broken anywhere, even mid-word ("kraterrand.exam" / "ple.org/markt").
     * That is the case when the link fills most of a line that runs to the paragraph's right edge, and the
     * next line goes on without a capital letter.
     */
    private fun isForcedBreak(layout: TextLayout, prev: Word, next: Word): Boolean {
        val t = prev.text
        val linkish = t.contains("://") || t.startsWith("www.", ignoreCase = true) || t.contains('@') ||
            (t.contains('.') && t.contains('/'))
        if (!linkish || next.text.firstOrNull()?.let { it.isLowerCase() || it.isDigit() || it in "./-_" } != true) return false
        val line = layout.lines[prev.line]
        val block = layout.blocks[prev.block]
        val reachesEdge = line.box.right >= block.box.right - block.box.width * 0.05f
        val fillsLine = prev.box.width >= line.box.width * 0.6f
        return reachesEdge && fillsLine
    }

    /** True when a link at the end of one line obviously continues at the start of the next. */
    internal fun continuesUrl(end: String, next: String): Boolean {
        val urlish = end.contains("://") || end.startsWith("www.", ignoreCase = true) ||
            (end.contains('/') && end.contains('.'))
        if (!urlish || next.isEmpty()) return false
        return end.last() in "/-_=&?#%~" || next.first() == '/'
    }

    private fun trimUrl(text: String, start: Int, end: Int): Pair<Int, Int> {
        var e = end
        while (e > start) {
            val ch = text[e - 1]
            val drop = when (ch) {
                '.', ',', ';', ':', '!', '?', '"', '\'', '»', '“', '”', '’', '>', ']', '}' -> true
                ')' -> text.substring(start, e).count { it == '(' } < text.substring(start, e).count { it == ')' }
                else -> false
            }
            if (!drop) break
            e--
        }
        return start to e
    }

    private fun withScheme(url: String): String {
        val lower = url.lowercase(Locale.ROOT)
        return if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("ftp://")) url else "https://$url"
    }

    private fun ibanValid(iban: String): Boolean {
        val rearranged = iban.substring(4) + iban.substring(0, 4)
        val digits = buildString {
            for (ch in rearranged) {
                if (ch.isDigit()) append(ch) else append(ch - 'A' + 10)
            }
        }
        return runCatching { BigInteger(digits).mod(BigInteger.valueOf(97)) == BigInteger.ONE }.getOrDefault(false)
    }
}
