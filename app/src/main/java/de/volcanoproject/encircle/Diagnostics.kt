// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import java.text.DateFormat
import java.util.Date

/** Remembers what the last overlay session received, so problems are visible in the setup screen. */
object Diagnostics {
    private const val PREFS = "diagnostics"

    /** What is recorded, in display order. Stored under the enum name, shown with [label]. */
    enum class Key(@param:StringRes val label: Int) {
        Time(R.string.diag_time),
        Invocation(R.string.diag_invocation),
        Screenshot(R.string.diag_screenshot),
        TextRecognition(R.string.diag_ocr),
        Codes(R.string.diag_codes),
    }

    fun start(context: Context, invocation: String) {
        prefs(context).edit {
            clear()
            putString(Key.Time.name, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date()))
            putString(Key.Invocation.name, invocation)
        }
    }

    fun put(context: Context, key: Key, value: String) {
        prefs(context).edit { putString(key.name, value) }
    }

    fun read(context: Context): List<Pair<Key, String>> {
        val p = prefs(context)
        return Key.entries.mapNotNull { k -> p.getString(k.name, null)?.let { k to it } }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
