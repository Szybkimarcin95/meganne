package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.eculab.EcuLabStatus
import com.example.ui.eculab.EcuLabUiState
import com.example.ui.eculab.EcuReadResult
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EcuLabScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renders_live_summary_counters_and_result_row() {
        val state = EcuLabUiState(
            totalRequests = 984,
            results = listOf(
                result("222496", EcuLabStatus.SUPPORTED, 12.34),
                result("222401", EcuLabStatus.NRC, nrc = 0x31),
                result("222801", EcuLabStatus.TIMEOUT),
                result("21F0", EcuLabStatus.SESSION_REQUIRED)
            ),
            isScanning = true,
            startedAtMillis = 1_000,
            updatedAtMillis = 5_000
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                EcuLabScreen(
                    state = state,
                    onQueryChange = {},
                    onStatusFilterChange = {},
                    onServiceFilterChange = {},
                    onGroupBySessionChange = {},
                    onSortChange = {},
                    onCancelScan = {},
                    onExportFinished = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("count_supported_value").assertTextContains("1")
        composeTestRule.onNodeWithTag("count_nrc_value").assertTextContains("1")
        composeTestRule.onNodeWithTag("count_timeout_value").assertTextContains("1")
        composeTestRule.onNodeWithTag("count_session_value").assertTextContains("1")
        composeTestRule.onNodeWithTag("count_not_tested_value").assertTextContains("980")
        composeTestRule.onNodeWithTag("count_cancelled_value").assertTextContains("0")
        composeTestRule.onNodeWithTag("ecu_lab_progress").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ecu_row_222496").assertIsDisplayed()
    }

    private fun result(
        request: String,
        status: EcuLabStatus,
        value: Double? = null,
        nrc: Int? = null
    ) = EcuReadResult(
        requestHex = request,
        name = "Mock ${request}",
        service = if (request.startsWith("21")) 0x21 else 0x22,
        status = status,
        nrc = nrc,
        nrcMeaning = if (nrc != null) "requestOutOfRange" else null,
        raw = if (status == EcuLabStatus.SUPPORTED) "62 24 96 04 D2" else null,
        physicalValue = value,
        unit = if (value != null) "g" else null,
        sourceLine = 1
    )
}
