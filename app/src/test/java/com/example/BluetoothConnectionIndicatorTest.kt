package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.obd.ObdConnectionState
import com.example.ui.components.BluetoothConnectionIndicator
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BluetoothConnectionIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testBluetoothIndicator_disconnected() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BluetoothConnectionIndicator(
                    connectionState = ObdConnectionState.DISCONNECTED,
                    connectionStatusText = "Rozłączono z ELM327",
                    isSimulated = false
                )
            }
        }

        composeTestRule.onNodeWithTag("bluetooth_connection_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("ROZŁĄCZONO").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rozłączono z ELM327").assertIsDisplayed()
    }

    @Test
    fun testBluetoothIndicator_connected() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BluetoothConnectionIndicator(
                    connectionState = ObdConnectionState.CONNECTED,
                    connectionStatusText = "Połączono z OBDII (00:1D:A5:00:11:22)",
                    isSimulated = false
                )
            }
        }

        composeTestRule.onNodeWithTag("bluetooth_connection_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("POŁĄCZONO ELM").assertIsDisplayed()
        composeTestRule.onNodeWithText("Połączono z OBDII (00:1D:A5:00:11:22)").assertIsDisplayed()
    }

    @Test
    fun testBluetoothIndicator_simulated() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BluetoothConnectionIndicator(
                    connectionState = ObdConnectionState.CONNECTED,
                    connectionStatusText = "Symulacja telemetryczna (K9K 636)",
                    isSimulated = true
                )
            }
        }

        composeTestRule.onNodeWithTag("bluetooth_connection_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("SYMULACJA 4Hz").assertIsDisplayed()
        composeTestRule.onNodeWithText("Symulacja telemetryczna (K9K 636)").assertIsDisplayed()
    }
}
