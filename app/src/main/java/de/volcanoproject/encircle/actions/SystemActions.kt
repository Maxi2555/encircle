// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.actions

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import de.volcanoproject.encircle.analysis.CodeContent
import java.io.File

/** Thin wrappers around the system intents the overlay can fire. Each returns false if nothing handled it. */
object SystemActions {
    private const val TAG = "SystemActions"

    fun copyText(context: Context, text: String, label: String = "Text", sensitive: Boolean = false) {
        val clip = ClipData.newPlainText(label, text)
        if (sensitive && Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity for $intent", e); false
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to start $intent", e); false
    }

    fun shareText(context: Context, text: String): Boolean =
        launch(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))

    fun openUri(context: Context, uri: String): Boolean =
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(uri)))

    fun email(context: Context, to: String, subject: String? = null, body: String? = null): Boolean =
        launch(
            context,
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to, "@.+-_")))
                .apply { subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }; body?.let { putExtra(Intent.EXTRA_TEXT, it) } },
        )

    fun dial(context: Context, number: String): Boolean =
        launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }))))

    fun sms(context: Context, number: String, body: String?): Boolean =
        launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).apply { body?.let { putExtra("sms_body", it) } })

    fun addContact(context: Context, vcard: String): Boolean {
        val uri = writeShared(context, "contact.vcf") { it.writeText(vcard) } ?: return false
        return launch(
            context,
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/x-vcard").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    fun addEvent(context: Context, e: CodeContent.Event): Boolean = launch(
        context,
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            e.title?.let { putExtra(CalendarContract.Events.TITLE, it) }
            e.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            e.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            e.start?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            e.end?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
        },
    )

    /** Hands the network to the system "add Wi-Fi" sheet (Android 11+). */
    fun connectWifi(context: Context, w: CodeContent.Wifi): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val builder = WifiNetworkSuggestion.Builder().setSsid(w.ssid)
        val pw = w.password
        when {
            pw == null || w.security == "NOPASS" -> Unit
            w.security.contains("SAE") || w.security.contains("WPA3") -> builder.setWpa3Passphrase(pw)
            w.security.startsWith("WPA") || w.security.isEmpty() -> builder.setWpa2Passphrase(pw)
            else -> return false // WEP and enterprise networks cannot be suggested.
        }
        if (w.hidden) builder.setIsHiddenSsid(true)
        val suggestion = try { builder.build() } catch (e: IllegalArgumentException) { return false }
        return launch(
            context,
            Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion)),
        )
    }

    fun openWifiSettings(context: Context): Boolean = launch(context, Intent(Settings.ACTION_WIFI_SETTINGS))

    fun openAssistSettings(context: Context): Boolean =
        launch(context, Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) ||
            launch(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))

    /** Writes [bitmap] as PNG into the shared cache. Call off the main thread. */
    fun saveImage(context: Context, bitmap: Bitmap): Uri? =
        writeShared(context, "encircle-${System.currentTimeMillis()}.png") { f ->
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

    fun shareImage(context: Context, uri: Uri): Boolean {
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri(null, uri) }
        return launch(context, Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun copyImage(context: Context, uri: Uri) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newUri(context.contentResolver, "Bild", uri))
    }

    private fun writeShared(context: Context, name: String, write: (File) -> Unit): Uri? = try {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Keep the share folder small: only the latest few files are needed.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
        val file = File(dir, name)
        write(file)
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    } catch (e: Exception) {
        Log.w(TAG, "Could not write $name", e); null
    }
}
