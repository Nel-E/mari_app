package com.mari.app.ui.accessibility

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.mari.app.ui.screens.main.AddTaskFab
import com.mari.app.ui.screens.main.AllTasksIconButton
import com.mari.app.ui.screens.main.SettingsIconButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that interactive elements carry a non-empty contentDescription for screen-reader
 * accessibility. Tests run on the JVM via Robolectric — no emulator required.
 *
 * These tests exercise the actual production composables from MainScreenActions so that any
 * change to a contentDescription string will immediately fail the corresponding test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilitySemanticTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `FAB add button has contentDescription`() {
        composeRule.setContent { AddTaskFab(onClick = {}) }

        composeRule
            .onNodeWithContentDescription("Add Task")
            .assertIsDisplayed()
    }

    @Test
    fun `tasks list icon button has contentDescription`() {
        composeRule.setContent { AllTasksIconButton(onClick = {}) }

        composeRule
            .onNodeWithContentDescription("All Tasks")
            .assertIsDisplayed()
    }

    @Test
    fun `settings icon button has contentDescription`() {
        composeRule.setContent { SettingsIconButton(onClick = {}) }

        composeRule
            .onNodeWithContentDescription("Settings")
            .assertIsDisplayed()
    }
}
