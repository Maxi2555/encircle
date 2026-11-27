// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.setup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import de.volcanoproject.encircle.assist.AssistService

/** How the user moves around the system: buttons or gestures. */
enum class Navigation { Buttons, Gestures }

/** Whose launcher handles the gestures; it decides where the assistant shortcut lives. */
enum class LauncherFamily { Pixel, Samsung, Aosp }

/** What the user has to do so that a system shortcut starts Encircle, and which settings page helps. */
data class ShortcutPlan(
    val title: String,
    val howTo: String,
    val buttonLabel: String,
    /** Settings actions to try in order; the first one the device knows is opened. */
    val settingsActions: List<String>,
    /** Offered on gesture navigation: switching to buttons gives the classic home long-press. */
    val offerButtonNavigation: Boolean,
)

data class SetupStatus(
    val assistant: Boolean,
    val disconnected: Boolean,
    val screenContent: Boolean,
    val shortcutUsed: Boolean,
    val plan: ShortcutPlan,
) {
    val stepsLeft: Int get() = listOf(assistant, screenContent, shortcutUsed).count { !it }
    val ready: Boolean get() = stepsLeft == 0
}

/**
 * Everything the setup screen needs to guide the user from "installed" to "starts from the shortcut".
 *
 * Android does not let apps read whether the home long-press or the corner swipe is enabled, so the
 * shortcut step is confirmed the reliable way: by the first session that the system starts through
 * an assist gesture ([markShortcutUsed], called from the overlay session).
 */
object AssistantSetup {
    private const val PREFS = "setup"
    private const val KEY_SHORTCUT_USED = "shortcut_used"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    const val ACTION_NAVIGATION_MODE = "com.android.settings.NAVIGATION_MODE_SETTINGS"
    const val ACTION_GESTURE_NAVIGATION = "com.android.settings.GESTURE_NAVIGATION_SETTINGS"
    const val ACTION_BUTTON_NAVIGATION = "com.android.settings.BUTTON_NAVIGATION_SETTINGS"

    private const val PIXEL_LAUNCHER = "com.google.android.apps.nexuslauncher"
    private const val SAMSUNG_LAUNCHER = "com.sec.android.app.launcher"

    fun status(context: Context): SetupStatus = SetupStatus(
        assistant = AssistService.isActive(context),
        disconnected = AssistService.isRoleHeldButDisconnected(context),
        screenContent = screenContentAllowed(context),
        shortcutUsed = shortcutUsed(context),
        plan = plan(navigation(context), launcherFamily(context)),
    )

    fun markShortcutUsed(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_SHORTCUT_USED, true) }
    }

    /** The first-run tutorial shows until it has been finished or postponed once. */
    fun onboardingDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDING_DONE, false)

    fun markOnboardingDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ONBOARDING_DONE, true) }
    }

    private fun shortcutUsed(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHORTCUT_USED, false)

    /** Both switches on the assistant settings page; Android treats a missing value as on. */
    private fun screenContentAllowed(context: Context): Boolean = try {
        val resolver = context.contentResolver
        Settings.Secure.getInt(resolver, "assist_structure_enabled", 1) == 1 &&
            Settings.Secure.getInt(resolver, "assist_screenshot_enabled", 1) == 1
    } catch (e: SecurityException) {
        true // Unknown on this system; the overlay explains it if no screenshot arrives.
    }

    /** Settings.Secure.NAVIGATION_MODE exists since Android 10 (the minimum) and is readable by apps. */
    private fun navigation(context: Context): Navigation = navigationFor(
        try {
            Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0)
        } catch (e: SecurityException) {
            0
        },
    )

    private fun launcherFamily(context: Context): LauncherFamily {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcher = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return launcherFamilyFor(launcher, Build.MANUFACTURER)
    }

    /** 0 = three buttons, 1 = two buttons, 2 = gestures (Settings.Secure.NAVIGATION_MODE). */
    internal fun navigationFor(mode: Int): Navigation = if (mode == 2) Navigation.Gestures else Navigation.Buttons

    internal fun launcherFamilyFor(launcherPackage: String?, manufacturer: String): LauncherFamily = when {
        launcherPackage == PIXEL_LAUNCHER -> LauncherFamily.Pixel
        launcherPackage == SAMSUNG_LAUNCHER || manufacturer.equals("samsung", ignoreCase = true) -> LauncherFamily.Samsung
        else -> LauncherFamily.Aosp
    }

    internal fun plan(navigation: Navigation, launcher: LauncherFamily): ShortcutPlan = when {
        navigation == Navigation.Buttons -> ShortcutPlan(
            title = "Home gedrückt halten",
            howTo = "Halte die Home-Taste kurz gedrückt, dann startet Encircle. Passiert nichts, schalte in den " +
                "Tasten-Einstellungen „Home gedrückt halten, um Assistenten aufzurufen“ ein.",
            buttonLabel = "Tasten-Einstellungen",
            settingsActions = listOf(ACTION_BUTTON_NAVIGATION, ACTION_NAVIGATION_MODE),
            offerButtonNavigation = false,
        )
        launcher == LauncherFamily.Pixel -> ShortcutPlan(
            title = "Navigationsleiste gedrückt halten",
            howTo = "Schalte unter Navigationsmodus „Circle to Search“ aus. Danach startet langes Drücken auf die " +
                "Leiste am unteren Rand Encircle. Alternativ wischst du schräg aus einer unteren Ecke nach oben.",
            buttonLabel = "Navigationsmodus öffnen",
            settingsActions = listOf(ACTION_NAVIGATION_MODE, ACTION_GESTURE_NAVIGATION),
            offerButtonNavigation = false,
        )
        launcher == LauncherFamily.Samsung -> ShortcutPlan(
            title = "Navigationsleiste gedrückt halten",
            howTo = "Schalte unter Einstellungen › Anzeige › Navigationsleiste „Circle to Search“ aus. Danach startet " +
                "langes Drücken auf die Leiste Encircle.",
            buttonLabel = "Anzeige-Einstellungen",
            settingsActions = listOf(Settings.ACTION_DISPLAY_SETTINGS),
            offerButtonNavigation = false,
        )
        else -> ShortcutPlan(
            title = "Aus der Ecke wischen",
            howTo = "Schalte in den Gesten-Einstellungen „Zum Aufrufen des Assistenten wischen“ ein. Danach startet ein " +
                "Wisch schräg aus einer unteren Ecke nach oben Encircle. Langes Drücken auf die Leiste ist auf " +
                "diesem System für die Google-Suche reserviert.",
            buttonLabel = "Gesten-Einstellungen",
            settingsActions = listOf(ACTION_GESTURE_NAVIGATION, ACTION_NAVIGATION_MODE),
            offerButtonNavigation = true,
        )
    }
}
