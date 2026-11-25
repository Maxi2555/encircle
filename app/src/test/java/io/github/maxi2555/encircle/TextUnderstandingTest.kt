// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package io.github.maxi2555.encircle

import androidx.compose.ui.geometry.Rect
import io.github.maxi2555.encircle.analysis.EntityFinder
import io.github.maxi2555.encircle.analysis.EntityKind
import io.github.maxi2555.encircle.analysis.LayoutBuilder
import io.github.maxi2555.encircle.analysis.RawElement
import io.github.maxi2555.encircle.analysis.RawLine
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
