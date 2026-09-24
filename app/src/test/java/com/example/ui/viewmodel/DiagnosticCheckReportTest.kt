package com.example.ui.viewmodel

import com.example.data.model.DiagnosticFaultHistoryEntry
import com.example.data.model.DtcCode
import com.example.data.model.DtcSeverity
import com.example.data.model.HealthCheckStatus
import com.example.data.model.LiveTelemetry
import com.example.data.model.TelemetryField
import com.example.data.obd.ObdConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticCheckReportTest {

    private val nominalTelemetry = LiveTelemetry(
        rpm = TelemetryField.measured(850),
        speedKmH = TelemetryField.measured(0),
        boostBar = TelemetryField.measured(0.05f),
        railPressureBar = TelemetryField.measured(280),
        coolantTempC = TelemetryField.measured(88),
        batteryVoltage = TelemetryField.measured(14.2f),
        dpfSootGrams = TelemetryField.measured(12.0f),
        injector1Correction = TelemetryField.measured(-0.1f),
        injector2Correction = TelemetryField.measured(0.1f),
        injector3Correction = TelemetryField.measured(-0.05f),
        injector4Correction = TelemetryField.measured(0.05f),
        mapPressureKpa = TelemetryField.measured(101),
        isConnected = true,
        isSimulated = false
    )

    @Test
    fun allNominal_returnsPassStatusAndAllChecks() {
        val report = buildDiagnosticCheckReport(
            telemetry = nominalTelemetry,
            connectionState = ObdConnectionState.CONNECTED,
            activeDtc = emptyList(),
            pendingDtc = emptyList(),
            historyFaults = emptyList()
        )

        assertEquals(HealthCheckStatus.PASS, report.overallStatus)
        assertEquals(0, report.activeDtcCount)
        assertEquals(0, report.pendingDtcCount)
        assertFalse(report.isSimulated)
        assertTrue(report.isConnected)
        assertEquals(7, report.checks.size)

        // Subsystems must all be verified
        val checkIds = report.checks.map { it.id }
        assertTrue(checkIds.contains("obd_link"))
        assertTrue(checkIds.contains("ecu_dtc"))
        assertTrue(checkIds.contains("electrical_battery"))
        assertTrue(checkIds.contains("thermal_cooling"))
        assertTrue(checkIds.contains("fuel_injection"))
        assertTrue(checkIds.contains("dpf_fap"))
        assertTrue(checkIds.contains("turbo_map"))
    }

    @Test
    fun withCriticalDtc_overallStatusBecomesAlert() {
        val criticalDtc = DtcCode(
            code = "DF297 / P242F",
            system = "DPF",
            title = "Filtr zablokowany",
            severity = DtcSeverity.CRITICAL,
            symptoms = emptyList(),
            rootCauses = emptyList(),
            diagnosticSteps = emptyList(),
            urgencyScore = 9
        )

        val report = buildDiagnosticCheckReport(
            telemetry = nominalTelemetry,
            connectionState = ObdConnectionState.CONNECTED,
            activeDtc = listOf(criticalDtc),
            pendingDtc = emptyList(),
            historyFaults = emptyList()
        )

        assertEquals(HealthCheckStatus.ALERT, report.overallStatus)
        assertEquals(1, report.activeDtcCount)

        val dtcCheck = report.checks.first { it.id == "ecu_dtc" }
        assertEquals(HealthCheckStatus.ALERT, dtcCheck.status)
        assertTrue(dtcCheck.message.contains("KRYTYCZNYM"))
    }

    @Test
    fun withLowBattery_overallStatusBecomesAlert() {
        val lowBattTelemetry = nominalTelemetry.copy(
            batteryVoltage = TelemetryField.measured(11.2f)
        )

        val report = buildDiagnosticCheckReport(
            telemetry = lowBattTelemetry,
            connectionState = ObdConnectionState.CONNECTED,
            activeDtc = emptyList(),
            pendingDtc = emptyList(),
            historyFaults = emptyList()
        )

        assertEquals(HealthCheckStatus.ALERT, report.overallStatus)
        val battCheck = report.checks.first { it.id == "electrical_battery" }
        assertEquals(HealthCheckStatus.ALERT, battCheck.status)
        assertTrue(battCheck.message.contains("Niski poziom"))
    }

    @Test
    fun withEngineOverheating_coolingStatusBecomesAlert() {
        val hotTelemetry = nominalTelemetry.copy(coolantTempC = TelemetryField.measured(112))

        val report = buildDiagnosticCheckReport(
            telemetry = hotTelemetry,
            connectionState = ObdConnectionState.CONNECTED,
            activeDtc = emptyList(),
            pendingDtc = emptyList(),
            historyFaults = emptyList()
        )

        assertEquals(HealthCheckStatus.ALERT, report.overallStatus)
        val coolCheck = report.checks.first { it.id == "thermal_cooling" }
        assertEquals(HealthCheckStatus.ALERT, coolCheck.status)
        assertTrue(coolCheck.message.contains("przegrzania"))
    }

    @Test
    fun withExcessiveInjectorCorrection_injectionStatusBecomesAlert() {
        val badInjTelemetry = nominalTelemetry.copy(injector2Correction = TelemetryField.measured(3.2f))

        val report = buildDiagnosticCheckReport(
            telemetry = badInjTelemetry,
            connectionState = ObdConnectionState.CONNECTED,
            activeDtc = emptyList(),
            pendingDtc = emptyList(),
            historyFaults = emptyList()
        )

        val injCheck = report.checks.first { it.id == "fuel_injection" }
        assertEquals(HealthCheckStatus.ALERT, injCheck.status)
        assertTrue(injCheck.message.contains("przelewowa"))
    }

    @Test
    fun simulationModeReflectedInProvenance() {
        val simTelemetry = nominalTelemetry.copy(
            isConnected = true,
            isSimulated = true
        )

        val report = buildDiagnosticCheckReport(
            telemetry = simTelemetry,
            connectionState = ObdConnectionState.DISCONNECTED,
            activeDtc = emptyList(),
            pendingDtc = emptyList(),
            historyFaults = emptyList()
        )

        assertTrue(report.isSimulated)
        val linkCheck = report.checks.first { it.id == "obd_link" }
        assertEquals(HealthCheckStatus.INFO, linkCheck.status)
        assertTrue(linkCheck.message.contains("symulacji"))
    }
}
