package com.example.ui.viewmodel

import com.example.data.model.DtcCode
import com.example.data.model.DtcSeverity
import com.example.data.obd.DataVerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcCatalogMatchingTest {
    private val df025 = DtcCode(
        code = "DF025 / P0380",
        system = "Układ żarzenia",
        title = "Połączenie modułu diagnostycznego świec żarowych",
        severity = DtcSeverity.MEDIUM,
        symptoms = emptyList(),
        rootCauses = emptyList(),
        diagnosticSteps = emptyList(),
        urgencyScore = 5
    )

    private val df297 = DtcCode(
        code = "DF297 / P242F",
        system = "Układ oczyszczania spalin DPF/FAP",
        title = "Filtr cząstek stałych zablokowany",
        severity = DtcSeverity.CRITICAL,
        symptoms = emptyList(),
        rootCauses = emptyList(),
        diagnosticSteps = emptyList(),
        urgencyScore = 9
    )

    private val catalog = listOf(df025, df297)

    @Test
    fun slashSeparatedAliasMatchesP0380() {
        val result = resolveStoredDtc(catalog, "P0380")

        assertSame(df025, result)
    }

    @Test
    fun slashSeparatedAliasMatchesP242F() {
        val result = resolveStoredDtc(catalog, "P242F")

        assertSame(df297, result)
    }

    @Test
    fun aliasesAreTrimmedAndCaseInsensitive() {
        val result = resolveStoredDtc(catalog, " df025 ")

        assertSame(df025, result)
    }

    @Test
    fun unknownPCodeFallsBackToGenericStandardObdObject() {
        val result = resolveStoredDtc(catalog, "P9999")

        assertEquals("P9999", result.code)
        assertEquals("Standard OBD-II", result.system)
        assertFalse(result.isRenaultSpecific)
    }

    @Test
    fun simulatedProvenanceSelectedForSimulationScan() {
        val status = resolveDtcHistoryStatus(isSimulatedScan = true)
        val storedSource = resolveStoredDtcSource(isSimulatedScan = true)
        val pendingSource = resolvePendingDtcSource(isSimulatedScan = true)

        assertEquals(DataVerificationStatus.SIMULATED, status)
        assertEquals("OBD-II Mode 03 (Symulacja)", storedSource)
        assertEquals("OBD-II Mode 07 (Pending, Symulacja)", pendingSource)
        assertFalse(storedSource.contains("SID307", ignoreCase = true))
        assertFalse(pendingSource.contains("SID307", ignoreCase = true))
        assertFalse(storedSource.contains("Renault", ignoreCase = true))
    }

    @Test
    fun measuredProvenanceSelectedForPhysicalScan() {
        val status = resolveDtcHistoryStatus(isSimulatedScan = false)
        val storedSource = resolveStoredDtcSource(isSimulatedScan = false)
        val pendingSource = resolvePendingDtcSource(isSimulatedScan = false)

        assertEquals(DataVerificationStatus.MEASURED, status)
        assertEquals("OBD-II Mode 03", storedSource)
        assertEquals("OBD-II Mode 07 (Pending)", pendingSource)
        assertFalse(storedSource.contains("Symulacja", ignoreCase = true))
        assertFalse(pendingSource.contains("Symulacja", ignoreCase = true))
        assertFalse(storedSource.contains("SID307", ignoreCase = true))
        assertFalse(pendingSource.contains("SID307", ignoreCase = true))
    }
}
