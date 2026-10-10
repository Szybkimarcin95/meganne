package com.example.data.obd

import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** All addresses and responses in this class are TEST FIXTURE, never a vehicle catalog. */
class SafeDiagnosticSessionAddressedTest {
    private val fixtureResponses = mapOf(
        "ATSH123" to "OK", "ATCRA456" to "OK",
        "ATSH124" to "OK", "ATCRA457" to "OK",
        "ATSH7DF" to "OK", "ATD" to "OK",
        "0100" to "41 00 BE 3F A8 13", "010C" to "41 0C 00 00"
    )
    private val fixtureValidator: (String) -> CommandValidationResult = { raw ->
        val command = raw.replace(" ", "").uppercase(Locale.ROOT)
        if (command in fixtureResponses) CommandValidationResult.Allowed(command, "TEST_FIXTURE")
        else CommandValidationResult.Blocked(raw, "Not in TEST_FIXTURE")
    }

    private class FakeTransport(private val response: suspend (String) -> String) : DiagnosticTransport {
        private val sent = Collections.synchronizedList(mutableListOf<String>())
        @Volatile private var open = true
        val closeCount = AtomicInteger()
        fun snapshot(): List<String> = synchronized(sent) { sent.toList() }
        override suspend fun open(): Boolean { open = true; return true }
        override suspend fun close() { closeCount.incrementAndGet(); open = false }
        override fun isTransportOpen(): Boolean = open
        override suspend fun sendCommand(command: String, timeoutMs: Long): String {
            // Record exactly what session sent. Do not normalize away session defects.
            sent += command
            return response(command)
        }
    }
    private fun fake(responses: Map<String, String> = fixtureResponses) = FakeTransport { responses.getValue(it) }
    private fun session(transport: DiagnosticTransport) = SafeDiagnosticSession(transport, fixtureValidator)
    private suspend fun transact(session: SafeDiagnosticSession, second: Boolean = false,
        restores: List<String> = listOf("ATSH7DF")): AddressedTransactionResult =
        session.executeAddressedTransaction(
            txAddress = if (second) "124" else "123",
            rxAddress = if (second) "457" else "456", protocol = null,
            commands = listOf("01 00"), restoreCommands = restores, timeoutMs = 5000L
        )
    private val firstOrder = listOf("ATSH123", "ATCRA456", "0100", "ATSH7DF")
    private val secondOrder = listOf("ATSH124", "ATCRA457", "0100", "ATSH7DF")

    @Test(timeout = 10000) fun fullSequenceAndFixtureReadAreDistinctFromAdapterAcknowledgements() = runBlocking {
        val transport = fake()
        val result = transact(session(transport), restores = listOf("ATSH 7DF", "ATD"))
        assertEquals(firstOrder + "ATD", transport.snapshot())
        assertNull(result.failure)
        assertEquals(2, result.setupResults.size)
        assertEquals(2, result.restoreResults.size)
        assertEquals("4100BE3FA813", (result.readResults.single() as DiagnosticExecutionResult.Success).cleanedResponse)
        assertTrue(result.setupResults.all { (it as DiagnosticExecutionResult.Success).cleanedResponse == "OK" })
    }

    @Test(timeout = 10000) fun restoreRunsAfterNoDataAndFailureContainsNoData() = runBlocking {
        val transport = fake(fixtureResponses + ("0100" to "NO DATA"))
        val result = transact(session(transport))
        assertEquals(firstOrder, transport.snapshot())
        assertEquals("NO_DATA", (result.readResults.single() as DiagnosticExecutionResult.AdapterError).errorType)
        assertEquals(result.readResults.single(), result.failure)
        assertEquals("OK", (result.restoreResults.single() as DiagnosticExecutionResult.Success).cleanedResponse)
    }

    @Test(timeout = 10000) fun cancellationRestoresBeforeReleasingMutexAndSameSessionWorksAgain() = runBlocking {
        val readEntered = CompletableDeferred<Unit>()
        val neverFinishRead = CompletableDeferred<Unit>()
        var firstRead = true
        val transport = FakeTransport { command ->
            if (command == "0100" && firstRead) {
                firstRead = false
                readEntered.complete(Unit)
                neverFinishRead.await()
            }
            fixtureResponses.getValue(command)
        }
        val session = session(transport)
        val before = session.sessionGeneration
        val job = launch { transact(session) }
        readEntered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(firstOrder, transport.snapshot())
        val next = transact(session, second = true)
        assertNull(next.failure)
        assertEquals(firstOrder + secondOrder, transport.snapshot())
        assertEquals(before, session.sessionGeneration)
    }

    @Test(timeout = 10000) fun twoAddressedTransactionsOverlapButNeverInterleave() = runBlocking {
        val firstReadEntered = CompletableDeferred<Unit>()
        val releaseFirstRead = CompletableDeferred<Unit>()
        val secondValidated = CompletableDeferred<Unit>()
        var firstRead = true
        val transport = FakeTransport { command ->
            if (command == "0100" && firstRead) {
                firstRead = false
                firstReadEntered.complete(Unit)
                releaseFirstRead.await()
            }
            fixtureResponses.getValue(command)
        }
        val validator: (String) -> CommandValidationResult = { raw ->
            fixtureValidator(raw).also {
                if (raw == "ATCRA457") secondValidated.complete(Unit)
            }
        }
        val session = SafeDiagnosticSession(transport, validator)
        val first = async { transact(session) }
        firstReadEntered.await()
        val second = async { transact(session, second = true) }
        try {
            secondValidated.await()
            assertEquals(firstOrder.take(3), transport.snapshot())
        } finally { releaseFirstRead.complete(Unit) }
        assertNull(first.await().failure)
        assertNull(second.await().failure)
        assertEquals(firstOrder + secondOrder, transport.snapshot())
    }

    @Test(timeout = 10000) fun addressedTransactionDoesNotInterleaveWithLivePolling() = runBlocking {
        val readEntered = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val pollingValidated = CompletableDeferred<Unit>()
        val transport = FakeTransport { command ->
            if (command == "0100") { readEntered.complete(Unit); releaseRead.await() }
            fixtureResponses.getValue(command)
        }
        val validator: (String) -> CommandValidationResult = { raw ->
            fixtureValidator(raw).also { if (raw == "010C") pollingValidated.complete(Unit) }
        }
        val session = SafeDiagnosticSession(transport, validator)
        val addressed = async { transact(session) }
        readEntered.await()
        val polling = async { session.executeCommand("010C") }
        try {
            pollingValidated.await()
            assertEquals(firstOrder.take(3), transport.snapshot())
        } finally { releaseRead.complete(Unit) }
        assertNull(addressed.await().failure)
        assertTrue(polling.await() is DiagnosticExecutionResult.Success)
        assertEquals(firstOrder + "010C", transport.snapshot())
    }

    @Test(timeout = 10000) fun normalTransactionDoesNotChangeGeneration() = runBlocking {
        val transport = fake()
        val session = session(transport)
        val before = session.sessionGeneration
        assertNull(transact(session).failure)
        assertEquals(firstOrder, transport.snapshot())
        assertEquals(before, session.sessionGeneration)
    }

    @Test(timeout = 10000) fun entirePlanIsValidatedBeforeAnyAdapterMutation() = runBlocking {
        val transport = fake()
        val result = transact(session(transport), restores = listOf("ATSH7DF", "ATUNKNOWN"))
        assertTrue(result.failure is DiagnosticExecutionResult.BlockedByFirewall)
        assertTrue(transport.snapshot().isEmpty())
        assertTrue(result.setupResults.isEmpty())
        assertTrue(result.readResults.isEmpty())
        assertTrue(result.restoreResults.isEmpty())
    }

    @Test(timeout = 10000) fun setupRejectionSkipsReadButStillRestores() = runBlocking {
        val transport = fake(fixtureResponses + ("ATCRA456" to "?"))
        val result = transact(session(transport))
        assertEquals(listOf("ATSH123", "ATCRA456", "ATSH7DF"), transport.snapshot())
        assertEquals("SETUP_REJECTED", (result.failure as DiagnosticExecutionResult.AdapterError).errorType)
        assertTrue(result.readResults.isEmpty())
        assertEquals(1, result.restoreResults.size)
    }

    @Test(timeout = 10000) fun restoreFailureInvalidatesSessionAndClosesTransport() = runBlocking {
        val transport = fake(fixtureResponses + ("ATSH7DF" to "ERROR"))
        val session = session(transport)
        val before = session.sessionGeneration
        val result = transact(session)
        assertEquals(firstOrder, transport.snapshot())
        assertEquals("RESTORE_FAILED", (result.failure as DiagnosticExecutionResult.AdapterError).errorType)
        assertEquals(before + 1, session.sessionGeneration)
        assertFalse(transport.isTransportOpen())
        assertEquals(1, transport.closeCount.get())
        assertTrue(session.executeCommand("010C") is DiagnosticExecutionResult.TransportError)
        assertEquals(firstOrder, transport.snapshot())
    }

    @Test(timeout = 10000) fun publicConstructorStillBlocksAddressedFixtureWithoutTransmission() = runBlocking {
        val transport = fake()
        val result = transact(SafeDiagnosticSession(transport))
        assertTrue(result.failure is DiagnosticExecutionResult.BlockedByFirewall)
        assertTrue(transport.snapshot().isEmpty())
    }
}
