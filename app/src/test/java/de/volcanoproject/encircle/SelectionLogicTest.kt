// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import de.volcanoproject.encircle.analysis.CodeContent
import de.volcanoproject.encircle.analysis.CodeParser
import de.volcanoproject.encircle.analysis.TextBlock
import de.volcanoproject.encircle.analysis.TextLayout
import de.volcanoproject.encircle.analysis.TextLine
import de.volcanoproject.encircle.analysis.Word
import de.volcanoproject.encircle.ui.StrokeShape
import de.volcanoproject.encircle.ui.pointInPolygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

class SelectionLogicTest {

    /** Builds a layout from lines of text; each line is 40 px high, words 10 px per character. */
    private fun layoutOf(vararg paragraphs: List<String>): TextLayout {
        val words = ArrayList<Word>()
        val lines = ArrayList<TextLine>()
        val blocks = ArrayList<TextBlock>()
        var y = 0f
        for (para in paragraphs) {
            val firstWord = words.size
            val firstLine = lines.size
            for (lineText in para) {
                var x = 0f
                val lineFirst = words.size
                for (t in lineText.split(' ')) {
                    words += Word(words.size, t, Rect(x, y, x + t.length * 10f, y + 30f), lines.size, blocks.size)
                    x += t.length * 10f + 10f
                }
                lines += TextLine(lines.size, blocks.size, lineFirst, words.size - 1, Rect(0f, y, x - 10f, y + 30f))
                y += 40f
            }
            val right = lines.subList(firstLine, lines.size).maxOf { it.box.right }
            blocks += TextBlock(blocks.size, firstWord, words.size - 1, firstLine, lines.size - 1, Rect(0f, lines[firstLine].box.top, right, y - 10f))
            y += 60f
        }
        return TextLayout(words, lines, blocks)
    }

    @Test
    fun textOf_reflowsParagraphAndRemovesHyphenation() {
        val l = layoutOf(
            listOf("Das ist ein lan-", "ger Satz der weiter geht", "und hier endet."),
            listOf("Zweiter Absatz."),
        )
        assertEquals(
            "Das ist ein langer Satz der weiter geht und hier endet.\n\nZweiter Absatz.",
            l.textOf(l.allIndices),
        )
    }

    @Test
    fun textOf_keepsBreakForShortLinesAndLists() {
        val l = layoutOf(listOf("Einkaufsliste mit vielen Dingen", "- Milch", "- Brot und Butter"))
        assertEquals("Einkaufsliste mit vielen Dingen\n- Milch\n- Brot und Butter", l.textOf(l.allIndices))
    }

    @Test
    fun sentenceRange_respectsAbbreviationsAndOrdinals() {
        val l = layoutOf(listOf("Wir treffen uns z.B. am 25. November. Danach essen wir."))
        // "treffen" is word 1; the first sentence ends at "November."
        val r = l.sentenceRange(1)
        assertEquals(0, r.first)
        assertEquals("November.", l.words[r.last].text)
        val second = l.sentenceRange(r.last + 1)
        assertEquals("wir.", l.words[second.last].text)
    }

    @Test
    fun wordAt_findsWordWithTolerance() {
        val l = layoutOf(listOf("eins zwei drei"))
        assertEquals(1, l.wordAt(Offset(60f, 15f), 5f))
        assertEquals(1, l.wordAt(Offset(70f, 34f), 5f))
        assertEquals(null, l.wordAt(Offset(70f, 80f), 5f))
    }

    @Test
    fun blockContaining_picksParagraphForCornerToCornerSwipe() {
        val l = layoutOf(listOf("erste Zeile hier", "zweite Zeile da"), listOf("anderer Absatz"))
        val b = l.blockContaining(Offset(-5f, -5f), Offset(160f, 72f), 20f)
        assertEquals(0, b?.index)
    }

    @Test
    fun strokeShape_detectsCircleButNotSwipes() {
        val circle = (0..60).map { i ->
            val a = i / 60.0 * 2 * Math.PI
            Offset(300f + 200f * cos(a).toFloat(), 300f + 150f * sin(a).toFloat())
        }
        assertTrue(StrokeShape(circle, 3f).isLasso)

        // Three quarters of a circle still counts as encircling.
        val arc = circle.take(50)
        assertTrue(StrokeShape(arc, 3f).isLasso)

        val line = (0..40).map { Offset(it * 20f, 400f + it * 2f) }
        assertFalse(StrokeShape(line, 3f).isLasso)

        // Scribbling back and forth over one line of text is a highlight, not a lasso.
        val scribble = (0..60).map { Offset(if ((it / 10) % 2 == 0) (it % 10) * 60f else (10 - it % 10) * 60f, 500f + (it % 3)) }
        assertFalse(StrokeShape(scribble, 3f).isLasso)
    }

    @Test
    fun pointInPolygon_works() {
        val square = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 10f), Offset(0f, 10f))
        assertTrue(pointInPolygon(Offset(5f, 5f), square))
        assertFalse(pointInPolygon(Offset(15f, 5f), square))
    }

    @Test
    fun codeParser_wifi() {
        val c = CodeParser.parse("WIFI:T:WPA;S:Mein\\;Netz;P:geheim123;H:false;;", false)
        assertEquals(CodeContent.Wifi("Mein;Netz", "geheim123", "WPA", false), c)
    }

    @Test
    fun codeParser_mecardBecomesVcard() {
        val c = CodeParser.parse("MECARD:N:Muster,Erika;TEL:+4930123;EMAIL:erika@example.org;;", false)
        c as CodeContent.Contact
        assertEquals("Erika Muster", c.name)
        assertTrue(c.vcard.contains("TEL:+4930123"))
        assertTrue(c.vcard.startsWith("BEGIN:VCARD"))
    }

    @Test
    fun codeParser_mecardValuesAreEscaped() {
        val c = CodeParser.parse("MECARD:N:Muster,Erika;NOTE:a\nTEL:999\\;b,c;TEL:+4930123;;", false) as CodeContent.Contact
        assertTrue(c.vcard.contains("\r\nNOTE:a\\nTEL:999\\;b\\,c\r\n"))
        assertFalse(c.vcard.contains("\nTEL:999"))
        assertTrue(c.vcard.contains("\r\nTEL:+4930123\r\n"))
    }

    @Test
    fun codeParser_smstoKeepsColonsAndQuestionMarks() {
        assertEquals(CodeContent.Sms("+49123", "Wann? Um 8:30"), CodeParser.parse("SMSTO:+49123:Wann? Um 8:30", false))
        assertEquals(CodeContent.Sms("+49123", null), CodeParser.parse("SMSTO:+49123", false))
    }

    @Test
    fun codeParser_event() {
        val c = CodeParser.parse("BEGIN:VEVENT\nSUMMARY:Treffen\nDTSTART:20261201T100000Z\nLOCATION:Berlin\nEND:VEVENT", false)
        c as CodeContent.Event
        assertEquals("Treffen", c.title)
        assertEquals(1796119200000L, c.start)
        assertEquals("Berlin", c.location)
        assertFalse(c.allDay)
    }

    private fun event(body: String) = CodeParser.parse("BEGIN:VEVENT\r\n$body\r\nEND:VEVENT", false) as CodeContent.Event

    @Test
    fun codeParser_allDayEvent() {
        val c = event("DTSTART;VALUE=DATE:20261201\r\nDTEND;VALUE=DATE:20261202")
        assertTrue(c.allDay)
        assertEquals(LocalDate.of(2026, 12, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), c.start)
        assertEquals(LocalDate.of(2026, 12, 2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), c.end)
    }

    @Test
    fun codeParser_eventTimeZone() {
        assertEquals(1796119200000L, event("DTSTART;TZID=Europe/Berlin:20261201T110000").start)
        assertEquals(1796119200000L, event("DTSTART;TZID=\"Europe/Berlin\";X-FOO=1:20261201T110000").start)
        assertEquals(null, event("DTSTART;TZID=W. Europe Standard Time:20261201T110000").start)
    }

    @Test
    fun codeParser_eventFoldingAndEscapes() {
        val c = event("SUMMARY:Tre\r\n ffen\r\nLOCATION:A\\, B\\; C\\\\D\r\nDESCRIPTION:x\\Ny\\nz")
        assertEquals("Treffen", c.title)
        assertEquals("A, B; C\\D", c.location)
        assertEquals("x\ny\nz", c.description)
    }

    @Test
    fun codeParser_productAndPhone() {
        assertEquals(CodeContent.Product("4006381333931"), CodeParser.parse("4006381333931", true))
        assertEquals(CodeContent.Phone("+49301234"), CodeParser.parse("tel:+49301234", false))
    }
}
