package com.example.ui.eculab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcuLabModelsTest {

    @Test
    fun counters_match_984_mixed_results() {
        val results = buildList {
            repeat(720) { add(result("22${(0x2000 + it).toString(16)}", EcuLabStatus.SUPPORTED, value = it.toDouble())) }
            repeat(140) { add(result("22${(0x3000 + it).toString(16)}", EcuLabStatus.NRC, nrc = 0x31)) }
            repeat(80) { add(result("22${(0x4000 + it).toString(16)}", EcuLabStatus.TIMEOUT)) }
            repeat(44) { add(result("22${(0x5000 + it).toString(16)}", EcuLabStatus.SESSION_REQUIRED)) }
        }

        val counters = EcuLabUiState(totalRequests = 984, results = results).counters

        assertEquals(720, counters.supported)
        assertEquals(140, counters.nrc)
        assertEquals(80, counters.timeout)
        assertEquals(44, counters.sessionRequired)
        assertEquals(0, counters.notTested)
        assertEquals(0, counters.cancelled)
    }

    @Test
    fun cancellation_after_200_marks_remaining_784_cancelled() {
        val completed = (0 until 200).map {
            result("22${(0x2000 + it).toString(16)}", EcuLabStatus.SUPPORTED)
        }
        val pending = (0 until 784).map {
            result("22${(0x4000 + it).toString(16)}", EcuLabStatus.NOT_TESTED)
        }
        val initial = EcuLabUiState(
            totalRequests = 984,
            results = completed,
            isScanning = true,
            startedAtMillis = 0,
            updatedAtMillis = 10_000
        )

        val cancelled = reduceEcuLabEvent(
            initial,
            EcuLabEvent.ScanCancelled(pending, atMillis = 11_000)
        )

        assertEquals(200, cancelled.counters.supported)
        assertEquals(784, cancelled.counters.cancelled)
        assertEquals(0, cancelled.counters.notTested)
        assertEquals(984, cancelled.completedCount)
    }

    @Test
    fun filter_by_status_keeps_only_nrc() {
        val state = EcuLabUiState(
            results = listOf(
                result("222401", EcuLabStatus.SUPPORTED),
                result("222496", EcuLabStatus.NRC, nrc = 0x31),
                result("222801", EcuLabStatus.TIMEOUT)
            ),
            statusFilter = EcuLabStatus.NRC
        )

        assertEquals(listOf("222496"), state.filteredResults.map { it.requestHex })
    }

    @Test
    fun query_by_request_prefix_filters_hex_live() {
        val state = EcuLabUiState(
            results = listOf(
                result("222401", EcuLabStatus.SUPPORTED),
                result("222496", EcuLabStatus.SUPPORTED),
                result("222801", EcuLabStatus.SUPPORTED)
            ),
            query = "0x2224"
        )

        assertEquals(listOf("222401", "222496"), state.filteredResults.map { it.requestHex })
    }

    @Test
    fun value_sort_orders_numeric_values_both_directions() {
        val rows = listOf(
            result("222401", EcuLabStatus.SUPPORTED, value = 1000.0),
            result("222496", EcuLabStatus.SUPPORTED, value = 12.34),
            result("222801", EcuLabStatus.SUPPORTED, value = 500.0)
        )

        val asc = EcuLabUiState(results = rows, sort = EcuLabSort.VALUE_ASC).filteredResults
        val desc = EcuLabUiState(results = rows, sort = EcuLabSort.VALUE_DESC).filteredResults

        assertEquals(listOf("222496", "222801", "222401"), asc.map { it.requestHex })
        assertEquals(listOf("222401", "222801", "222496"), desc.map { it.requestHex })
    }

    @Test
    fun eta_uses_live_completed_rate() {
        val rows = (0 until 100).map { result("22${(0x2000 + it).toString(16)}", EcuLabStatus.SUPPORTED) }
        val state = EcuLabUiState(
            totalRequests = 200,
            results = rows,
            startedAtMillis = 1_000,
            updatedAtMillis = 11_000
        )

        assertEquals(10L, state.etaSeconds)
        assertTrue(state.progressFraction == 0.5f)
    }

    private fun result(
        request: String,
        status: EcuLabStatus,
        value: Double? = null,
        nrc: Int? = null
    ) = EcuReadResult(
        requestHex = request.uppercase(),
        name = "Test ${request}",
        service = 0x22,
        status = status,
        nrc = nrc,
        nrcMeaning = if (nrc != null) "requestOutOfRange" else null,
        physicalValue = value,
        unit = if (value != null) "u" else null,
        sourceLine = 1
    )
}
