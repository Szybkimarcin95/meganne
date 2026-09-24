package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.obd.ObdConnectionState
import com.example.ui.components.DiagnosticSourceType
import com.example.ui.components.DiagnosticStatusBar
import com.example.ui.components.HealthStatusCode
import com.example.ui.components.HealthStateRow
import com.example.ui.components.ParameterRow
import com.example.ui.components.SourceBadge
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SystemHealthScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sourceBadge_displaysExplicitTextLabel_neverColorAlone() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SourceBadge(source = DiagnosticSourceType.LIVE, detail = "CAN")
            }
        }

        composeTestRule.onNodeWithTag("source_badge_live").assertIsDisplayed()
        composeTestRule.onNodeWithText("LIVE • CAN").assertIsDisplayed()
    }

    @Test
    fun sourceBadge_simulatedAndUnknown_displayCorrectTextLabels() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SourceBadge(source = DiagnosticSourceType.SIMULATED)
                SourceBadge(source = DiagnosticSourceType.UNKNOWN)
                SourceBadge(source = DiagnosticSourceType.UNAVAILABLE)
                SourceBadge(source = DiagnosticSourceType.CANDIDATE)
            }
        }

        composeTestRule.onNodeWithTag("source_badge_simulated").assertIsDisplayed()
        composeTestRule.onNodeWithText("SYMULACJA").assertIsDisplayed()
        composeTestRule.onNodeWithTag("source_badge_unknown").assertIsDisplayed()
        composeTestRule.onNodeWithText("BRAK DANYCH").assertIsDisplayed()
        composeTestRule.onNodeWithTag("source_badge_unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText("NIEDOSTĘPNE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("source_badge_candidate").assertIsDisplayed()
        composeTestRule.onNodeWithText("KANDYDAT").assertIsDisplayed()
    }

    @Test
    fun diagnosticStatusBar_displaysProtocolAndState_honoringCandidateProvenance() {
        composeTestRule.setContent {
            MyApplicationTheme {
                DiagnosticStatusBar(
                    connectionState = ObdConnectionState.CONNECTED,
                    connectionStatusText = "Połączono z ELM327",
                    isSimulated = false
                )
            }
        }

        composeTestRule.onNodeWithTag("diagnostic_status_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("ECU: PROFIL SID307 [CANDIDATE]").assertIsDisplayed()
        composeTestRule.onNodeWithText("ELM327 OK").assertIsDisplayed()
    }

    @Test
    fun healthStateRow_whenScanNotRun_displaysNotScannedEvidence() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HealthStateRow(
                    title = "Kody usterek zapisane (Mode 03)",
                    statusText = "NOT SCANNED",
                    statusCode = HealthStatusCode.NOT_SCANNED,
                    source = DiagnosticSourceType.UNKNOWN,
                    measuredValue = "Skan niewykonany",
                    nominalCondition = "0 zarejestrowanych usterek",
                    detailMessage = "Odczyt pamięci błędów nie został jeszcze wywołany."
                )
            }
        }

        composeTestRule.onNodeWithTag("health_row_kody_usterek_zapisane_(mode_03)").assertIsDisplayed()
        composeTestRule.onNodeWithText("NOT SCANNED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Odczyt: Skan niewykonany").assertIsDisplayed()
        composeTestRule.onNodeWithText("BRAK DANYCH").assertIsDisplayed()
    }

    @Test
    fun parameterRow_displaysMonospaceValuesAndProvenance() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ParameterRow(
                    parameterName = "Prędkość obrotowa silnika (RPM)",
                    pidHex = "010C",
                    valueString = "850",
                    unit = "obr/min",
                    source = DiagnosticSourceType.LIVE,
                    freshness = "Świeże (CAN)"
                )
            }
        }

        composeTestRule.onNodeWithTag("param_row_010C").assertIsDisplayed()
        composeTestRule.onNodeWithText("Prędkość obrotowa silnika (RPM)").assertIsDisplayed()
        composeTestRule.onNodeWithText("850").assertIsDisplayed()
        composeTestRule.onNodeWithText("obr/min").assertIsDisplayed()
        composeTestRule.onNodeWithText("LIVE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Świeże (CAN)").assertIsDisplayed()
    }

    @Test
    fun healthStateRow_whenScanFailed_displaysErrorAndNeverClaimsNoFaults() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HealthStateRow(
                    title = "Kody usterek zapisane (Mode 03)",
                    statusText = "BŁĄD SKANU",
                    statusCode = HealthStatusCode.ALERT,
                    source = DiagnosticSourceType.UNAVAILABLE,
                    measuredValue = "Błąd odczytu pamięci usterek",
                    nominalCondition = "Poprawna ramka odpowiedzi 43 xx",
                    detailMessage = "Nie udało się odpytać sterownika. Stan błędów NIEZNANY (nie traktować jako brak usterek!)."
                )
            }
        }

        composeTestRule.onNodeWithTag("health_row_kody_usterek_zapisane_(mode_03)").assertIsDisplayed()
        composeTestRule.onNodeWithText("BŁĄD SKANU").assertIsDisplayed()
        composeTestRule.onNodeWithText("NIEDOSTĘPNE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Odczyt: Błąd odczytu pamięci usterek").assertIsDisplayed()
    }
}
