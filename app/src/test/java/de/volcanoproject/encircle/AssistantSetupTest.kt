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
    fun launcherFamilies() {
        assertEquals(LauncherFamily.Pixel, AssistantSetup.launcherFamilyFor("com.google.android.apps.nexuslauncher", "Google"))
        assertEquals(LauncherFamily.Samsung, AssistantSetup.launcherFamilyFor("com.sec.android.app.launcher", "samsung"))
        assertEquals(LauncherFamily.Samsung, AssistantSetup.launcherFamilyFor("com.teslacoilsw.launcher", "samsung"))
        // GrapheneOS and other AOSP-based systems ship Launcher3.
        assertEquals(LauncherFamily.Aosp, AssistantSetup.launcherFamilyFor("com.android.launcher3", "Google"))
        assertEquals(LauncherFamily.Aosp, AssistantSetup.launcherFamilyFor(null, "Fairphone"))
    }

    @Test
    fun buttonsAlwaysMeanHomeLongPress() {
        for (launcher in LauncherFamily.entries) {
            val plan = AssistantSetup.plan(Navigation.Buttons, launcher)
            assertEquals("Home gedrückt halten", plan.title)
            assertEquals(AssistantSetup.ACTION_BUTTON_NAVIGATION, plan.settingsActions.first())
            assertFalse(plan.offerButtonNavigation)
        }
    }

    @Test
    fun gesturesDependOnTheLauncher() {
        val pixel = AssistantSetup.plan(Navigation.Gestures, LauncherFamily.Pixel)
        assertEquals(AssistantSetup.ACTION_NAVIGATION_MODE, pixel.settingsActions.first())
        assertTrue(pixel.howTo.contains("Circle to Search"))

        // Launcher3 sends the handle long-press to contextual search only, so AOSP gets the corner swipe.
        val aosp = AssistantSetup.plan(Navigation.Gestures, LauncherFamily.Aosp)
        assertEquals(AssistantSetup.ACTION_GESTURE_NAVIGATION, aosp.settingsActions.first())
        assertTrue(aosp.offerButtonNavigation)
    }

    @Test
    fun stepsLeftCountsOpenSteps() {
        val plan = AssistantSetup.plan(Navigation.Buttons, LauncherFamily.Aosp)
        assertEquals(3, SetupStatus(false, false, false, false, plan).stepsLeft)
        assertEquals(1, SetupStatus(true, false, true, false, plan).stepsLeft)
        assertTrue(SetupStatus(true, false, true, true, plan).ready)
    }
}
