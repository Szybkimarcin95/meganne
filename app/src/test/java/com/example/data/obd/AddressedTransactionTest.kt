package com.example.data.obd

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class AddressedTransactionTest {
    private class FixtureTransport : DiagnosticTransport {
        val sent = mutableListOf<String>()
        override suspend fun open() = true
        override suspend fun close() {}
        override fun isTransportOpen() = true
        override suspend fun sendCommand(command: String, timeoutMs: Long): String {
            sent += command
            return "41 0C 00 00>"
        }
    }

    @Test fun addressedPreflightBlocksWithoutTouchingTransportOrDeadlocking() = runBlocking {
        val transport = FixtureTransport()
        val session = SafeDiagnosticSession(transport)
        val result = withTimeout(2000) { session.executeAddressedTransaction(
            "123", "456", "6", listOf("22FFFF"), listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATSP0")) }
        assertTrue(result.failure is DiagnosticExecutionResult.BlockedByFirewall)
        assertTrue(result.setupResults.isEmpty())
        assertTrue(result.readResults.isEmpty())
        assertTrue(transport.sent.isEmpty())
        assertTrue(session.executeCommand("010C") is DiagnosticExecutionResult.Success)
        assertEquals(listOf("010C"), transport.sent)
    }

    @Test fun cannotInjectCommandsThroughAddressOrOmitRestorePlan() = runBlocking {
        val transport = FixtureTransport()
        val session = SafeDiagnosticSession(transport)
        for ((tx, restore) in listOf("123\r04" to listOf("ATSP0"), "123" to emptyList<String>())) {
            val result = session.executeAddressedTransaction(tx, "456", null, listOf("010C"), restore)
            assertTrue(result.failure is DiagnosticExecutionResult.BlockedByFirewall)
        }
        assertTrue(transport.sent.isEmpty())
    }
}
