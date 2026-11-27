// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.setup

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.edit
import de.volcanoproject.encircle.R
import de.volcanoproject.encircle.assist.AssistService
import de.volcanoproject.encircle.assist.StubRecognitionService

/** How the user moves around the system: buttons or gestures. */
enum class Navigation { Buttons, Gestures }

/** Whose system launcher handles navigation; it decides where the assistant shortcut lives. */
enum class LauncherFamily { Pixel, Samsung, Aosp }

/** What the user has to do so that a system shortcut starts Encircle, and which settings page helps. */
data class ShortcutPlan(
    @param:StringRes val title: Int,
    @param:StringRes val howTo: Int,
    @param:StringRes val buttonLabel: Int,
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
    /** adb command that keeps Encircle connected across restarts, see [AssistantSetup.recognizerCommand]. */
    val recognizerCommand: String = "",
) {
    val steps: List<Boolean> get() = listOf(assistant, screenContent, shortcutUsed)
    val stepsLeft: Int get() = steps.count { !it }
    val ready: Boolean get() = stepsLeft == 0
}

/**
 * Everything the setup screen needs to guide the user from "installed" to "starts from the shortcut".
 *
 * Android does not let apps read whether the home long-press or the corner swipe is enabled, so the
 * shortcut step is confirmed the reliable way: by the first session that the system starts through
 * an assist gesture ([markShortcutUsed], called from the overlay session), per navigation mode.
 */
object AssistantSetup {
    private const val PREFS = "setup"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    const val ACTION_NAVIGATION_MODE = "com.android.settings.NAVIGATION_MODE_SETTINGS"
    const val ACTION_GESTURE_NAVIGATION = "com.android.settings.GESTURE_NAVIGATION_SETTINGS"
    const val ACTION_BUTTON_NAVIGATION = "com.android.settings.BUTTON_NAVIGATION_SETTINGS"

    internal const val PIXEL_LAUNCHER = "com.google.android.apps.nexuslauncher"
    internal const val SAMSUNG_LAUNCHER = "com.sec.android.app.launcher"

    fun status(context: Context): SetupStatus {
        val navigation = navigation(context)
        return SetupStatus(
            assistant = AssistService.isActive(context),
            disconnected = AssistService.isRoleHeldButDisconnected(context),
            screenContent = screenContentAllowed(context),
            shortcutUsed = prefs(context).getBoolean(shortcutKey(navigation), false),
            plan = plan(navigation, launcherFamily(context)),
            recognizerCommand = recognizerCommand(context),
        )
    }

    fun markShortcutUsed(context: Context) {
        prefs(context).edit { putBoolean(shortcutKey(navigation(context)), true) }
    }

    /** The first-run tutorial shows until it has been finished or postponed once. */
    fun onboardingDone(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun markOnboardingDone(context: Context) {
        prefs(context).edit { putBoolean(KEY_ONBOARDING_DONE, true) }
    }

    /**
     * AOSP unsets a non-system assistant whenever a user starts without a default speech recognizer, and
     * since Android 17 it never picks a third-party recognizer by itself. On systems without Google's
     * recognizer (GrapheneOS, secondary users) Encircle is therefore signed out at every boot and, for
     * secondary users, at every switch. Only Settings or adb may set the recognizer, so the app offers
     * this command; the setting is per user.
     */
    fun recognizerCommand(context: Context): String = recognizerCommand(
        // UserHandle.getIdentifier() is hidden; hashCode() returns the same user id.
        Process.myUserHandle().hashCode(),
        ComponentName(context, StubRecognitionService::class.java).flattenToShortString(),
    )

    internal fun recognizerCommand(userId: Int, recognizer: String) =
        "adb shell settings --user $userId put secure voice_recognition_service $recognizer"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun shortcutKey(navigation: Navigation) = "shortcut_used_${navigation.name.lowercase()}"

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

    /**
     * Gestures and the home long-press are handled by the system launcher, which stays installed even
     * when another app is the default home screen.
     */
    private fun launcherFamily(context: Context): LauncherFamily {
        val pm = context.packageManager
        return launcherFamilyFor { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    /** 0 = three buttons, 1 = two buttons, 2 = gestures (Settings.Secure.NAVIGATION_MODE). */
    internal fun navigationFor(mode: Int): Navigation = if (mode == 2) Navigation.Gestures else Navigation.Buttons

    internal fun launcherFamilyFor(isInstalled: (String) -> Boolean): LauncherFamily = when {
        isInstalled(PIXEL_LAUNCHER) -> LauncherFamily.Pixel
        isInstalled(SAMSUNG_LAUNCHER) -> LauncherFamily.Samsung
        else -> LauncherFamily.Aosp
    }

    internal fun plan(navigation: Navigation, launcher: LauncherFamily): ShortcutPlan = when (navigation) {
        Navigation.Buttons -> when (launcher) {
            LauncherFamily.Pixel -> ShortcutPlan(
                title = R.string.plan_buttons_title,
                howTo = R.string.plan_buttons_pixel_how,
                buttonLabel = R.string.plan_pixel_button,
                settingsActions = listOf(ACTION_NAVIGATION_MODE, ACTION_BUTTON_NAVIGATION),
                offerButtonNavigation = false,
            )
            LauncherFamily.Samsung -> ShortcutPlan(
                title = R.string.plan_buttons_title,
                howTo = R.string.plan_buttons_samsung_how,
                buttonLabel = R.string.plan_samsung_button,
                settingsActions = listOf(Settings.ACTION_DISPLAY_SETTINGS),
                offerButtonNavigation = false,
            )
            LauncherFamily.Aosp -> ShortcutPlan(
                title = R.string.plan_buttons_title,
                howTo = R.string.plan_buttons_how,
                buttonLabel = R.string.plan_buttons_button,
                settingsActions = listOf(ACTION_BUTTON_NAVIGATION, ACTION_NAVIGATION_MODE),
                offerButtonNavigation = false,
            )
        }
        Navigation.Gestures -> when (launcher) {
            LauncherFamily.Pixel -> ShortcutPlan(
                title = R.string.plan_handle_title,
                howTo = R.string.plan_pixel_how,
                buttonLabel = R.string.plan_pixel_button,
                settingsActions = listOf(ACTION_NAVIGATION_MODE, ACTION_GESTURE_NAVIGATION),
                offerButtonNavigation = false,
            )
            LauncherFamily.Samsung -> ShortcutPlan(
                title = R.string.plan_handle_title,
                howTo = R.string.plan_samsung_how,
                buttonLabel = R.string.plan_samsung_button,
                settingsActions = listOf(Settings.ACTION_DISPLAY_SETTINGS),
                offerButtonNavigation = false,
            )
            LauncherFamily.Aosp -> ShortcutPlan(
                title = R.string.plan_corner_title,
                howTo = R.string.plan_corner_how,
                buttonLabel = R.string.plan_corner_button,
                settingsActions = listOf(ACTION_GESTURE_NAVIGATION, ACTION_NAVIGATION_MODE),
                offerButtonNavigation = true,
            )
        }
    }
}
