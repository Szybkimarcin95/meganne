package com.example.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiagnosticFloatingActionMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun floatingActionMenu_initiallyDisplaysMainButtonOnly() {
        composeTestRule.setContent {
            MyApplicationTheme {
                DiagnosticFloatingActionMenu(
                    onVerifyEcu = {},
                    onScanSensors = {},
                    onRefreshConfig = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("fab_diagnostic_menu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fab_action_verify_ecu").assertDoesNotExist()
        composeTestRule.onNodeWithTag("fab_action_scan_sensors").assertDoesNotExist()
        composeTestRule.onNodeWithTag("fab_action_refresh_config").assertDoesNotExist()
    }

    @Test
    fun floatingActionMenu_expandsOnClick_displaysAllQuickActions() {
        composeTestRule.setContent {
            MyApplicationTheme {
                DiagnosticFloatingActionMenu(
                    onVerifyEcu = {},
                    onScanSensors = {},
                    onRefreshConfig = {}
                )
            }
        }

        // Click to expand
        composeTestRule.onNodeWithTag("fab_diagnostic_menu").performClick()

        // Verify all 3 quick actions appear
        composeTestRule.onNodeWithTag("fab_action_verify_ecu").assertIsDisplayed()
        composeTestRule.onNodeWithText("Verify ECU").assertIsDisplayed()

        composeTestRule.onNodeWithTag("fab_action_scan_sensors").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan Sensors").assertIsDisplayed()

        composeTestRule.onNodeWithTag("fab_action_refresh_config").assertIsDisplayed()
        composeTestRule.onNodeWithText("Refresh Config").assertIsDisplayed()
    }

    @Test
    fun floatingActionMenu_actionClicksTriggerCallbacks() {
        var verifyEcuClicked = false
        var scanSensorsClicked = false
        var refreshConfigClicked = false

        composeTestRule.setContent {
            MyApplicationTheme {
                DiagnosticFloatingActionMenu(
                    onVerifyEcu = { verifyEcuClicked = true },
                    onScanSensors = { scanSensorsClicked = true },
                    onRefreshConfig = { refreshConfigClicked = true }
                )
            }
        }

        // Expand menu
        composeTestRule.onNodeWithTag("fab_diagnostic_menu").performClick()

        // Click Verify ECU
        composeTestRule.onNodeWithTag("fab_action_verify_ecu").performClick()
        assertTrue("Verify ECU callback should be called", verifyEcuClicked)

        // Expand menu again
        composeTestRule.onNodeWithTag("fab_diagnostic_menu").performClick()

        // Click Scan Sensors
        composeTestRule.onNodeWithTag("fab_action_scan_sensors").performClick()
        assertTrue("Scan Sensors callback should be called", scanSensorsClicked)

        // Expand menu again
        composeTestRule.onNodeWithTag("fab_diagnostic_menu").performClick()

        // Click Refresh Config
        composeTestRule.onNodeWithTag("fab_action_refresh_config").performClick()
        assertTrue("Refresh Config callback should be called", refreshConfigClicked)
    }
}
