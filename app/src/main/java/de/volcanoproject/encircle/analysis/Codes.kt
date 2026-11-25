// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.analysis

import android.net.Uri
import android.util.Patterns
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** A barcode found on screen. Corners are screenshot pixels, clockwise from top-left. */
class DetectedCode(
    val id: Int,
    val formatName: String,
    val isMatrix: Boolean,
    val raw: String,
    val corners: List<Offset>,
    val content: CodeContent,
) {
    val bounds: Rect = Rect(
        corners.minOf { it.x }, corners.minOf { it.y },
        corners.maxOf { it.x }, corners.maxOf { it.y },
    )
    val center: Offset get() = bounds.center
}

sealed interface CodeContent {
    data class Link(val url: String) : CodeContent
    data class Wifi(val ssid: String, val password: String?, val security: String, val hidden: Boolean) : CodeContent
    data class Contact(val vcard: String, val name: String?) : CodeContent
    data class Email(val to: String, val subject: String?, val body: String?) : CodeContent
    data class Phone(val number: String) : CodeContent
    data class Sms(val number: String, val body: String?) : CodeContent
    data class Geo(val uri: String) : CodeContent
    data class Event(val title: String?, val start: Long?, val end: Long?, val location: String?, val description: String?) : CodeContent
    data class OtherUri(val uri: String) : CodeContent
    data class Product(val code: String) : CodeContent
    data class Text(val text: String) : CodeContent
}

object CodeParser {

    private val SCHEME_URI = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]{1,30}:[^\s]+$""")
    private val LOCAL_SCHEMES = setOf("file", "content", "android.resource", "intent", "android-app", "javascript", "data")

    fun parse(raw: String, isProductCode: Boolean): CodeContent {
        val t = raw.trim()
        val upper = t.uppercase(Locale.ROOT)
        return when {
            isProductCode -> CodeContent.Product(t)
            upper.startsWith("WIFI:") -> parseWifi(t)
            upper.startsWith("BEGIN:VCARD") -> CodeContent.Contact(t, vcardName(t))
            upper.startsWith("MECARD:") -> mecardToVcard(t)
            upper.startsWith("BEGIN:VEVENT") || upper.startsWith("BEGIN:VCALENDAR") -> parseEvent(t)
            upper.startsWith("MATMSG:") -> parseMatmsg(t)
            upper.startsWith("MAILTO:") -> parseMailto(t)
            upper.startsWith("TEL:") -> CodeContent.Phone(t.substring(4))
            upper.startsWith("SMSTO:") || upper.startsWith("SMS:") -> parseSms(t)
            upper.startsWith("GEO:") -> CodeContent.Geo(t)
            upper.startsWith("HTTP://") || upper.startsWith("HTTPS://") -> CodeContent.Link(t)
            !t.contains(' ') && upper.startsWith("WWW.") -> CodeContent.Link("https://$t")
            Patterns.EMAIL_ADDRESS.matcher(t).matches() -> CodeContent.Email(t, null, null)
            SCHEME_URI.matches(t) && t.substringBefore(':').lowercase(Locale.ROOT) in LOCAL_SCHEMES -> CodeContent.OtherUri(t)
            !t.contains(' ') && t.contains('.') && Patterns.WEB_URL.matcher(t).matches() -> CodeContent.Link("https://$t")
            else -> CodeContent.Text(t)
        }
    }

    /** Splits `KEY:value;KEY:value;;` while honouring backslash escapes. */
    private fun fields(body: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val cur = StringBuilder()
        var key: String? = null
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> { cur.append(body[i + 1]); i++ }
                c == ':' && key == null -> { key = cur.toString().uppercase(Locale.ROOT); cur.clear() }
                c == ';' -> {
                    key?.let { k -> if (k !in out) out[k] = cur.toString() }
                    key = null; cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        key?.let { k -> if (k !in out) out[k] = cur.toString() }
        return out
    }

    private fun parseWifi(t: String): CodeContent {
        val f = fields(t.substringAfter(':'))
        return CodeContent.Wifi(
            ssid = f["S"].orEmpty(),
            password = f["P"]?.takeIf { it.isNotEmpty() },
            security = f["T"]?.uppercase(Locale.ROOT)?.ifEmpty { "NOPASS" } ?: "NOPASS",
            hidden = f["H"].equals("true", ignoreCase = true),
        )
    }

    private fun parseMatmsg(t: String): CodeContent {
        val f = fields(t.substringAfter(':'))
        return CodeContent.Email(f["TO"].orEmpty(), f["SUB"], f["BODY"])
    }

    private fun parseMailto(t: String): CodeContent {
        val rest = t.substring(7)
        val to = Uri.decode(rest.substringBefore('?'))
        val query = rest.substringAfter('?', "")
        fun param(name: String) = query.split('&')
            .firstOrNull { it.substringBefore('=').equals(name, ignoreCase = true) }
            ?.substringAfter('=')?.let(Uri::decode)
        return CodeContent.Email(to, param("subject"), param("body"))
    }

    private fun parseSms(t: String): CodeContent {
        val rest = t.substringAfter(':')
        val number = rest.substringBefore(':').substringBefore('?')
        val body = when {
            rest.contains(':') -> rest.substringAfter(':')
            rest.contains("body=") -> Uri.decode(rest.substringAfter("body="))
            else -> null
        }
        return CodeContent.Sms(number, body)
    }

    private fun vcardName(v: String): String? {
        val lines = v.lines()
        lines.firstOrNull { it.uppercase(Locale.ROOT).startsWith("FN") }
            ?.substringAfter(':')?.trim()?.let { if (it.isNotEmpty()) return it }
        return lines.firstOrNull { it.uppercase(Locale.ROOT).let { u -> u.startsWith("N:") || u.startsWith("N;") } }
            ?.substringAfter(':')?.split(';')?.take(2)?.filter { it.isNotBlank() }?.reversed()?.joinToString(" ")
    }

    private fun mecardToVcard(t: String): CodeContent {
        val f = fields(t.substringAfter(':'))
        val nameParts = f["N"].orEmpty().split(',').map { it.trim() }
        val display = nameParts.reversed().filter { it.isNotEmpty() }.joinToString(" ")
        val sb = StringBuilder("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        sb.append("N:").append(nameParts.getOrElse(0) { "" }).append(';').append(nameParts.getOrElse(1) { "" }).append("\r\n")
        if (display.isNotEmpty()) sb.append("FN:").append(display).append("\r\n")
        f["TEL"]?.let { sb.append("TEL:").append(it).append("\r\n") }
        f["EMAIL"]?.let { sb.append("EMAIL:").append(it).append("\r\n") }
        f["ADR"]?.let { sb.append("ADR:;;").append(it).append("\r\n") }
        f["URL"]?.let { sb.append("URL:").append(it).append("\r\n") }
        f["ORG"]?.let { sb.append("ORG:").append(it).append("\r\n") }
        f["NOTE"]?.let { sb.append("NOTE:").append(it).append("\r\n") }
        sb.append("END:VCARD\r\n")
        return CodeContent.Contact(sb.toString(), display.ifEmpty { null })
    }

    private fun parseEvent(t: String): CodeContent {
        fun prop(name: String): Pair<String, String>? = t.lines().firstOrNull {
            val u = it.uppercase(Locale.ROOT)
            u.startsWith("$name:") || u.startsWith("$name;")
        }?.let { line -> line.substringBefore(':') to line.substringAfter(':').trim() }
        return CodeContent.Event(
            title = prop("SUMMARY")?.second,
            start = prop("DTSTART")?.let { parseIcsDate(it.first, it.second) },
            end = prop("DTEND")?.let { parseIcsDate(it.first, it.second) },
            location = prop("LOCATION")?.second?.replace("\\,", ","),
            description = prop("DESCRIPTION")?.second?.replace("\\n", "\n")?.replace("\\,", ","),
        )
    }

    private fun parseIcsDate(params: String, value: String): Long? = try {
        when {
            value.length == 8 -> SimpleDateFormat("yyyyMMdd", Locale.ROOT).parse(value)?.time
            value.endsWith("Z") -> SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)?.time
            else -> SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ROOT).apply {
                params.substringAfter("TZID=", "").takeIf { it.isNotEmpty() }
                    ?.let { timeZone = TimeZone.getTimeZone(it) }
            }.parse(value)?.time
        }
    } catch (_: Exception) {
        null
    }
}
