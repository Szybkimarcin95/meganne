package com.example.ui.viewmodel

import com.example.data.model.DtcCode
import com.example.data.model.DtcSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
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
}
