// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package io.github.maxi2555.encircle

import android.content.Context
import java.text.DateFormat
import java.util.Date

/** Remembers what the last overlay session received, so problems are visible in the setup screen. */
object Diagnostics {
    private const val PREFS = "diagnostics"

    val keys = listOf(
        "Zeitpunkt",
        "Aufruf",
        "Screenshot",
        "Texterkennung",
        "Codes",
    )

    fun start(context: Context, flags: String) {
        prefs(context).edit()
            .clear()
            .putString("Zeitpunkt", DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date()))
            .putString("Aufruf", flags)
            .apply()
    }

    fun put(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun read(context: Context): List<Pair<String, String>> {
        val p = prefs(context)
        return keys.mapNotNull { k -> p.getString(k, null)?.let { k to it } }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
