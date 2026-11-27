// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle

import de.volcanoproject.encircle.setup.AssistantSetup
import de.volcanoproject.encircle.setup.LauncherFamily
import de.volcanoproject.encircle.setup.Navigation
import de.volcanoproject.encircle.setup.SetupStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSetupTest {

    @Test
    fun navigationModes() {
        assertEquals(Navigation.Buttons, AssistantSetup.navigationFor(0)) // three buttons
        assertEquals(Navigation.Buttons, AssistantSetup.navigationFor(1)) // two buttons
        assertEquals(Navigation.Gestures, AssistantSetup.navigationFor(2))
    }

    @Test
    fun recognizerCommandTargetsTheUser() {
        assertEquals(
            "adb shell settings --user 12 put secure voice_recognition_service " +
                "de.volcanoproject.encircle/.assist.StubRecognitionService",
            AssistantSetup.recognizerCommand(12, "de.volcanoproject.encircle/.assist.StubRecognitionService"),
        )
    }

    @Test
    fun launcherFamilies() {
        assertEquals(LauncherFamily.Pixel, AssistantSetup.launcherFamilyFor(setOf(AssistantSetup.PIXEL_LAUNCHER)::contains))
        assertEquals(LauncherFamily.Samsung, AssistantSetup.launcherFamilyFor(setOf(AssistantSetup.SAMSUNG_LAUNCHER)::contains))
        // GrapheneOS and other AOSP-based systems ship Launcher3.
        assertEquals(LauncherFamily.Aosp, AssistantSetup.launcherFamilyFor(setOf("com.android.launcher3")::contains))
    }

    @Test
    fun buttonsMeanHomeLongPress() {
        for (launcher in LauncherFamily.entries) {
            val plan = AssistantSetup.plan(Navigation.Buttons, launcher)
            assertEquals(R.string.plan_buttons_title, plan.title)
            assertFalse(plan.offerButtonNavigation)
        }
        assertEquals(
            AssistantSetup.ACTION_BUTTON_NAVIGATION,
            AssistantSetup.plan(Navigation.Buttons, LauncherFamily.Aosp).settingsActions.first(),
        )
        // Circle to Search takes the home long-press on Pixel and Samsung until it is switched off.
        assertEquals(R.string.plan_buttons_pixel_how, AssistantSetup.plan(Navigation.Buttons, LauncherFamily.Pixel).howTo)
        assertEquals(R.string.plan_buttons_samsung_how, AssistantSetup.plan(Navigation.Buttons, LauncherFamily.Samsung).howTo)
    }

    @Test
    fun gesturesDependOnTheLauncher() {
        val pixel = AssistantSetup.plan(Navigation.Gestures, LauncherFamily.Pixel)
        assertEquals(AssistantSetup.ACTION_NAVIGATION_MODE, pixel.settingsActions.first())
        assertEquals(R.string.plan_pixel_how, pixel.howTo)

        // Launcher3 sends the handle long-press to contextual search only, so AOSP gets the corner swipe.
        val aosp = AssistantSetup.plan(Navigation.Gestures, LauncherFamily.Aosp)
        assertEquals(AssistantSetup.ACTION_GESTURE_NAVIGATION, aosp.settingsActions.first())
        assertEquals(R.string.plan_corner_title, aosp.title)
        assertTrue(aosp.offerButtonNavigation)
    }

    @Test
    fun stepsLeftCountsOpenSteps() {
        val plan = AssistantSetup.plan(Navigation.Buttons, LauncherFamily.Aosp)
        assertEquals(3, SetupStatus(false, false, false, false, plan).stepsLeft)
        assertEquals(1, SetupStatus(true, false, true, false, plan).stepsLeft)
        assertEquals(listOf(true, true, false), SetupStatus(true, false, true, false, plan).steps)
        assertTrue(SetupStatus(true, false, true, true, plan).ready)
    }
}
