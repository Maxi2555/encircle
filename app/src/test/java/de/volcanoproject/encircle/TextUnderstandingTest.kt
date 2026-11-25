// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle

import androidx.compose.ui.geometry.Rect
import de.volcanoproject.encircle.analysis.EntityFinder
import de.volcanoproject.encircle.analysis.EntityKind
import de.volcanoproject.encircle.analysis.LayoutBuilder
import de.volcanoproject.encircle.analysis.RawElement
import de.volcanoproject.encircle.analysis.RawLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUnderstandingTest {

    private fun found(text: String) = EntityFinder.findIn(text).map { it.kind to it.value }

    @Test
    fun linksInSentencesLoseTrailingPunctuation() {
        assertEquals(listOf(EntityKind.Link to "https://example.org/a"), found("Mehr unter https://example.org/a."))
        assertEquals(listOf(EntityKind.Link to "https://www.example.de"), found("(siehe www.example.de)"))
        assertEquals(listOf(EntityKind.Link to "https://de.wikipedia.org/wiki/Foo_(Bar)"), found("Quelle: de.wikipedia.org/wiki/Foo_(Bar), abgerufen"))
    }

    @Test
    fun bareDomainsNeedAKnownEnding() {
        assertEquals(listOf(EntityKind.Link to "https://grapheneos.org"), found("Installiere es über grapheneos.org heute"))
        assertTrue(found("Das ist z.B. so, siehe bericht.pdf und Ende.Es geht weiter").isEmpty())
    }

    @Test
    fun emailsIncludingSpelledOutForms() {
        assertEquals(listOf(EntityKind.Email to "hallo@example.org"), found("Schreib an hallo@example.org. Danke"))
        assertEquals(listOf(EntityKind.Email to "info@firma.de"), found("Kontakt: info (at) firma (dot) de"))
        assertEquals(listOf(EntityKind.Email to "max.muster@sub.firma.co.uk"), found("max.muster@sub.firma.co.uk"))
    }

    @Test
    fun phonesButNotDatesOrAmounts() {
        assertEquals(listOf(EntityKind.Phone to "+4971112345678"), found("Tel. +49 711 1234 5678"))
        assertEquals(listOf(EntityKind.Phone to "0711123456"), found("Ruf an: 0711/123456!"))
        assertTrue(found("Am 01.02.2024 um 10:30 kostet es 1.299,00 €").isEmpty())
    }

    @Test
    fun ibanWithChecksum() {
        assertEquals(listOf(EntityKind.Iban to "DE89370400440532013000"), found("IBAN: DE89 3704 0044 0532 0130 00"))
        assertTrue(found("DE89 3704 0044 0532 0130 01").none { it.first == EntityKind.Iban })
        // Wrapped over two lines, as text reflows on a phone screen.
        assertEquals(listOf(EntityKind.Iban to "DE89370400440532013000"), found("an DE89 3704\n0044 0532 0130 00."))
    }

    @Test
    fun wrappedLinkContinuesOnNextLine() {
        assertTrue(EntityFinder.continuesUrl("https://example.org/very/", "long-path"))
        assertTrue(!EntityFinder.continuesUrl("Hallo", "Welt"))
    }

    // ------------------------------------------------------------------ layout

    private fun el(text: String, l: Float, t: Float, r: Float, h: Float = 40f) = RawElement(text, Rect(l, t, r, t + h))

    @Test
    fun touchingFragmentsBecomeOneWord() {
        val layout = LayoutBuilder.build(
            listOf(RawLine(listOf(el("Siehe", 0f, 0f, 100f), el("example.org", 112f, 0f, 300f), el("/pfad", 302f, 0f, 380f)), 0)),
        )
        assertEquals(listOf("Siehe", "example.org/pfad"), layout.words.map { it.text })
        assertEquals("https://example.org/pfad", EntityFinder.find(layout).single().value)
    }

    @Test
    fun ocrSplitsOfLinksAndAddressesAreRejoined() {
        // Exactly what ML Kit reported on a Pixel 10 Pro: the box gaps say nothing, the text does.
        val layout = LayoutBuilder.build(
            listOf(
                RawLine(listOf(el("https://example.", 120f, 0f, 437f, 47f), el("org", 448f, 0f, 513f, 47f), el("und", 513f, 0f, 580f, 47f), el("Adressen", 597f, 0f, 777f, 47f)), 0),
                RawLine(listOf(el("hallo@example.", 121f, 60f, 429f, 45f), el("org", 437f, 60f, 499f, 45f), el("werden", 500f, 60f, 639f, 45f)), 0),
            ),
        )
        assertEquals(listOf("https://example.org", "und", "Adressen", "hallo@example.org", "werden"), layout.words.map { it.text })
        assertEquals(listOf("https://example.org", "hallo@example.org"), EntityFinder.find(layout).map { it.value })
        assertTrue(!LayoutBuilder.belongTogether("Ende.", "Neuer"))
        assertTrue(!LayoutBuilder.belongTogether("Ende.", "und"))
        assertTrue(LayoutBuilder.belongTogether("example.", "org/pfad"))
    }

    @Test
    fun splitBlocksOfOneParagraphAreJoined() {
        // The OCR engine put each line into its own block; spacing and alignment say it is one paragraph.
        val lines = listOf(
            RawLine(listOf(el("Erste", 0f, 0f, 100f), el("Zeile", 112f, 0f, 200f)), 0),
            RawLine(listOf(el("zweite", 0f, 50f, 110f), el("Zeile", 122f, 50f, 210f)), 1),
            RawLine(listOf(el("dritte.", 0f, 100f, 120f)), 2),
            // A heading-sized line far below is a new paragraph.
            RawLine(listOf(el("Neu", 0f, 220f, 140f, h = 70f)), 3),
        )
        val layout = LayoutBuilder.build(lines)
        assertEquals(2, layout.blocks.size)
        assertEquals("Erste Zeile zweite Zeile dritte.", layout.textOf(IntArray(layout.blocks[0].last + 1) { it }))
    }

    @Test
    fun lineOfDigitsStaysInItsParagraph() {
        // Digits have no ascenders or descenders, so their boxes are shorter and the box gap looks larger.
        // Box positions as ML Kit reported them on the store demo screen: 37, 40 and 28 px tall.
        val lines = listOf(
            RawLine(listOf(el("Fragen", 72f, 1044f, 200f, 37f), el("+49", 760f, 1044f, 830f, 37f)), 5),
            RawLine(listOf(el("Vorbestellungen", 72f, 1099f, 350f, 40f), el("DE89", 810f, 1099f, 900f, 40f), el("3704", 910f, 1099f, 990f, 40f)), 5),
            RawLine(listOf(el("0044", 72f, 1162f, 160f, 28f), el("0532", 170f, 1162f, 258f, 28f), el("0130", 268f, 1162f, 356f, 28f), el("00.", 366f, 1162f, 410f, 28f)), 6),
        )
        val layout = LayoutBuilder.build(lines)
        assertEquals(1, layout.blocks.size)
        assertEquals(listOf(EntityKind.Iban), EntityFinder.find(layout).map { it.kind })
    }

    /** Two lines of one paragraph, 40 px tall, the first running to the right edge at x = 1000. */
    private fun twoLines(first: List<Pair<String, Float>>, second: List<Pair<String, Float>>): List<RawLine> {
        fun line(words: List<Pair<String, Float>>, top: Float, endAtEdge: Boolean): RawLine {
            var x = 0f
            val els = words.mapIndexed { i, (t, w) ->
                val right = if (endAtEdge && i == words.lastIndex) 1000f else x + w
                el(t, x, top, right).also { x = right + 12f }
            }
            return RawLine(els, 0)
        }
        return listOf(line(first, 0f, endAtEdge = true), line(second, 52f, endAtEdge = false))
    }

    @Test
    fun entitiesAcrossLineBreaks() {
        // E-mail wrapped after the @.
        var layout = LayoutBuilder.build(twoLines(listOf("Schreib" to 160f, "an" to 50f, "hallo@" to 300f), listOf("kraterrand.example.org" to 420f, "heute." to 120f)))
        assertEquals(listOf("hallo@kraterrand.example.org"), EntityFinder.find(layout).map { it.value })

        // Domain wrapped after a dot.
        layout = LayoutBuilder.build(twoLines(listOf("Siehe" to 120f, "https://kraterrand.example." to 600f), listOf("org/markt" to 200f, "für" to 60f)))
        assertEquals(listOf("https://kraterrand.example.org/markt"), EntityFinder.find(layout).map { it.value })

        // A long link broken mid-word because it fills the whole line.
        layout = LayoutBuilder.build(twoLines(listOf("https://kraterrand.example.org/veranstaltungen/herbstm" to 900f), listOf("arkt-2026" to 200f, "lesen" to 100f)))
        assertEquals(listOf("https://kraterrand.example.org/veranstaltungen/herbstmarkt-2026"), EntityFinder.find(layout).map { it.value })

        // A short link at the line end is not glued to the next word.
        layout = LayoutBuilder.build(twoLines(listOf("Mehr" to 100f, "Infos" to 100f, "auf" to 60f, "kraterrand.example.org" to 400f), listOf("und" to 70f, "vor" to 60f, "Ort." to 80f)))
        assertEquals(listOf("https://kraterrand.example.org"), EntityFinder.find(layout).map { it.value })

        // A phone number wrapped between digit groups.
        layout = LayoutBuilder.build(twoLines(listOf("Ruf" to 70f, "an:" to 60f, "+49" to 70f, "30" to 50f), listOf("1234" to 90f, "5678." to 100f)))
        assertEquals(listOf("+493012345678"), EntityFinder.find(layout).map { it.value })
    }

    @Test
    fun piecesOfOneRowBecomeOneLine() {
        val lines = listOf(
            RawLine(listOf(el("Links", 0f, 0f, 100f)), 0),
            RawLine(listOf(el("rechts", 112f, 2f, 220f)), 1),
        )
        val layout = LayoutBuilder.build(lines)
        assertEquals(1, layout.lines.size)
        assertEquals(1, layout.blocks.size)
    }

    @Test
    fun columnsStaySeparate() {
        val lines = listOf(
            RawLine(listOf(el("Spalte", 0f, 0f, 200f)), 0),
            RawLine(listOf(el("eins", 0f, 50f, 200f)), 0),
            RawLine(listOf(el("Spalte", 600f, 0f, 800f)), 1),
            RawLine(listOf(el("zwei", 600f, 50f, 800f)), 1),
        )
        val layout = LayoutBuilder.build(lines)
        assertEquals(2, layout.blocks.size)
        assertEquals("Spalte eins", layout.textOf(IntArray(layout.blocks[0].last - layout.blocks[0].first + 1) { layout.blocks[0].first + it }))
    }
}
