package com.example.data.obd

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandFirewallAndSessionTest {

    // 1. Allowed current Mode 01 request reaches fake transport
    @Test
    fun test01_AllowedMode01RequestsReachFakeTransport() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        val session = SafeDiagnosticSession(mockTransport)

        val allowedMode01 = listOf(
            "010C", "010D", "0105", "010F", "0110", "0104", "0111", "010B"
        )
        for (pid in allowedMode01) {
            mockTransport.mockResponses[pid] = "41 ${pid.removePrefix("01")} 00>"
            val result = session.executeCommand(pid)
            assertTrue("Expected PID $pid to succeed", result is DiagnosticExecutionResult.Success)
        }
        assertEquals(allowedMode01, mockTransport.sentCommands)
    }

    // 2. Mode 03 reaches fake transport
    @Test
    fun test02_Mode03ReachesFakeTransport() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        mockTransport.mockResponses["03"] = "43 01 04 00 00 00 00>"
        val session = SafeDiagnosticSession(mockTransport)

        val result = session.executeCommand("03")
        assertTrue(result is DiagnosticExecutionResult.Success)
        assertEquals("43010400000000", (result as DiagnosticExecutionResult.Success).cleanedResponse)
        assertEquals(listOf("03"), mockTransport.sentCommands)
    }

    // 3. Mode 07 reaches fake transport
    @Test
    fun test03_Mode07ReachesFakeTransport() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        mockTransport.mockResponses["07"] = "47 01 01 00 00 00 00>"
        val session = SafeDiagnosticSession(mockTransport)

        val result = session.executeCommand("07")
        assertTrue(result is DiagnosticExecutionResult.Success)
        assertEquals("47010100000000", (result as DiagnosticExecutionResult.Success).cleanedResponse)
        assertEquals(listOf("07"), mockTransport.sentCommands)
    }

    // 4. Mode 04 blocked under READ_ONLY
    @Test
    fun test04_Mode04BlockedUnderReadOnlyPolicy() = runBlocking {
        val validation = CommandFirewall.validate("04")
        assertTrue(validation is CommandValidationResult.Blocked)

        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        val session = SafeDiagnosticSession(mockTransport)

        val result = session.executeCommand("04")
        assertTrue(result is DiagnosticExecutionResult.BlockedByFirewall)
        assertEquals(0, mockTransport.sentCommands.size) // 0 calls to transport!
    }

    // 5. Service 11 blocked
    @Test
    fun test05_Service11EcuResetBlocked() {
        val result = CommandFirewall.validate("11 01")
        assertTrue(result is CommandValidationResult.Blocked)
        assertTrue((result as CommandValidationResult.Blocked).reason.contains("0x11"))
    }

    // 6. Service 27 blocked
    @Test
    fun test06_Service27SecurityAccessBlocked() {
        val result = CommandFirewall.validate("27 01")
        assertTrue(result is CommandValidationResult.Blocked)
        assertTrue((result as CommandValidationResult.Blocked).reason.contains("0x27"))
    }

    // 7. Service 2E blocked
    @Test
    fun test07_Service2EWriteDataByIdentifierBlocked() {
        val result = CommandFirewall.validate("2E 20 01 00")
        assertTrue(result is CommandValidationResult.Blocked)
        assertTrue((result as CommandValidationResult.Blocked).reason.contains("0x2E"))
    }

    // 8. Service 2F blocked
    @Test
    fun test08_Service2FInputOutputControlBlocked() {
        val result = CommandFirewall.validate("2F 01 02 03")
        assertTrue(result is CommandValidationResult.Blocked)
        assertTrue((result as CommandValidationResult.Blocked).reason.contains("0x2F"))
    }

    // 9. Service 31 blocked
    @Test
    fun test09_Service31RoutineControlBlocked() {
        val result = CommandFirewall.validate("31 01 AA")
        assertTrue(result is CommandValidationResult.Blocked)
        assertTrue((result as CommandValidationResult.Blocked).reason.contains("0x31"))
    }

    // 10. Service 34 blocked
    @Test
    fun test10_Service34RequestDownloadBlocked() {
        val result = CommandFirewall.validate("34 00 44")
        assertTrue(result is CommandValidationResult.Blocked)
        assertTrue((result as CommandValidationResult.Blocked).reason.contains("0x34"))
    }

    // 11. Service 36 blocked
    @Test
    fun test11_Service36TransferDataBlocked() {
        val result = CommandFirewall.validate("36 01 FF")
        assertTrue(result is CommandValidationResult.Blocked)
        assertTrue((result as CommandValidationResult.Blocked).reason.contains("0x36"))
    }

    // 12. Service 3D blocked
    @Test
    fun test12_Service3DWriteMemoryByAddressBlocked() {
        val result = CommandFirewall.validate("3D 00 10 20")
        assertTrue(result is CommandValidationResult.Blocked)
        assertTrue((result as CommandValidationResult.Blocked).reason.contains("0x3D"))
    }

    // 13. Service 222001 blocked while CP2 source unavailable
    @Test
    fun test13_Service22BlockedPendingSourceVerification() {
        val candidateCommands = listOf("222001", "222002", "222028", "22F190")
        for (cmd in candidateCommands) {
            val result = CommandFirewall.validate(cmd)
            assertTrue(result is CommandValidationResult.Blocked)
            assertTrue((result as CommandValidationResult.Blocked).reason.contains("BLOCKED_PENDING_SOURCE_VERIFICATION"))
        }
    }

    // 14. ATSH7E0 blocked
    @Test
    fun test14_Atsh7E0BlockedPendingSourceVerification() {
        val result = CommandFirewall.validate("ATSH 7E0")
        assertTrue(result is CommandValidationResult.Blocked)
        assertTrue((result as CommandValidationResult.Blocked).reason.contains("BLOCKED_PENDING_SOURCE_VERIFICATION"))
    }

    // 15. ATCRA7E8 blocked
    @Test
    fun test15_Atcra7E8BlockedPendingSourceVerification() {
        val result = CommandFirewall.validate("ATCRA 7E8")
        assertTrue(result is CommandValidationResult.Blocked)
        assertTrue((result as CommandValidationResult.Blocked).reason.contains("BLOCKED_PENDING_SOURCE_VERIFICATION"))
    }

    // 16. Blocked command -> transport call count == 0
    @Test
    fun test16_BlockedCommandsProduceZeroTransportCalls() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        val session = SafeDiagnosticSession(mockTransport)

        val forbiddenCommands = listOf(
            "1101", "2701", "2E2001", "2F01", "3101", "3400", "3601", "3D00",
            "222001", "ATSH7E0", "ATCRA7E8", "ATSP6", "04", "0202", "0902"
        )

        for (cmd in forbiddenCommands) {
            val result = session.executeCommand(cmd)
            assertTrue("Expected command '$cmd' to be blocked", result is DiagnosticExecutionResult.BlockedByFirewall)
        }
        assertEquals("Blocked commands must produce ZERO transport calls", 0, mockTransport.sentCommands.size)
    }

    // 17. Concurrent requests do not interleave (One transaction at a time)
    @Test
    fun test17_ConcurrentRequestsDoNotInterleave() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        mockTransport.mockResponses["010C"] = "41 0C 1A F8>"
        mockTransport.mockResponses["010D"] = "41 0D 32>"
        val session = SafeDiagnosticSession(mockTransport)

        // Launch 20 concurrent telemetry requests
        val jobs = (1..20).map { i ->
            async {
                val pid = if (i % 2 == 0) "010C" else "010D"
                session.executeCommand(pid)
            }
        }
        val results = jobs.awaitAll()

        assertEquals(20, results.size)
        assertTrue(results.all { it is DiagnosticExecutionResult.Success })
        assertEquals(20, mockTransport.sentCommands.size)
        // All commands in sentCommands must be atomic, unfragmented PIDs
        assertTrue(mockTransport.sentCommands.all { it == "010C" || it == "010D" })
    }

    // 18. Timeout releases transaction ownership (Mutex released properly)
    @Test
    fun test18_TimeoutReleasesTransactionOwnership() = runBlocking {
        val slowTransport = object : DiagnosticTransport {
            private var openState = false
            override suspend fun open(): Boolean { openState = true; return true }
            override suspend fun close() { openState = false }
            override fun isTransportOpen(): Boolean = openState
            override suspend fun sendCommand(command: String, timeoutMs: Long): String {
                delay(300) // Deliberate delay exceeding timeout
                return "41 0C 00>"
            }
        }
        slowTransport.open()
        val session = SafeDiagnosticSession(slowTransport)

        // Command with very small timeout (50ms) to trigger timeout
        val timeoutResult = session.executeCommand("010C", timeoutMs = 50L)
        assertTrue(timeoutResult is DiagnosticExecutionResult.TransportError)
        assertEquals("TIMEOUT", (timeoutResult as DiagnosticExecutionResult.TransportError).errorType)

        // Next command must successfully acquire lock and execute without being blocked
        val secondResult = session.executeCommand("010D", timeoutMs = 500L)
        assertTrue("Subsequent command after timeout must acquire lock", secondResult is DiagnosticExecutionResult.Success)
    }

    // 19. Session generation rejects stale result
    @Test
    fun test19_SessionGenerationRejectsStaleResult() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        val session = SafeDiagnosticSession(mockTransport)

        val genBefore = session.sessionGeneration
        session.close() // Invalides session generation
        val genAfter = session.sessionGeneration

        assertTrue(genAfter > genBefore)
        assertFalse(session.isTransportOpen())

        val result = session.executeCommand("010C")
        assertTrue(result is DiagnosticExecutionResult.TransportError)
        assertEquals("TRANSPORT_CLOSED", (result as DiagnosticExecutionResult.TransportError).errorType)
    }

    // 20. Disconnected/no transport does not fabricate DTC
    @Test
    fun test20_DisconnectedTransportDoesNotFabricateDtc() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        // Transport NOT opened
        val session = SafeDiagnosticSession(mockTransport)

        val resultStored = session.executeCommand("03")
        val resultPending = session.executeCommand("07")

        assertTrue(resultStored is DiagnosticExecutionResult.TransportError)
        assertTrue(resultPending is DiagnosticExecutionResult.TransportError)
        assertEquals(0, mockTransport.sentCommands.size)
    }

    // 21. Minimal AT commands allowlist verification
    @Test
    fun test21_MinimalAtCommandsAllowlist() {
        val allowedAt = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATSP0")
        for (cmd in allowedAt) {
            val result = CommandFirewall.validate(cmd)
            assertTrue("Expected AT command '$cmd' to be allowed", result is CommandValidationResult.Allowed)
        }

        val blockedAt = listOf("ATSP6", "ATRV", "ATI", "ATDP", "ATST64", "ATBD", "ATCAF1")
        for (cmd in blockedAt) {
            val result = CommandFirewall.validate(cmd)
            assertTrue("Expected AT command '$cmd' to be blocked under least privilege", result is CommandValidationResult.Blocked)
        }
    }

    // 22. Adapter error response classification (NO DATA, BUFFER FULL, CAN ERROR, UNABLE TO CONNECT, STOPPED, ERROR)
    @Test
    fun test22_AdapterErrorClassification() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        val session = SafeDiagnosticSession(mockTransport)

        mockTransport.mockResponses["010C"] = "BUFFER FULL>"
        val r1 = session.executeCommand("010C")
        assertTrue(r1 is DiagnosticExecutionResult.AdapterError)
        assertEquals("BUFFER_FULL", (r1 as DiagnosticExecutionResult.AdapterError).errorType)

        mockTransport.mockResponses["010D"] = "CAN ERROR>"
        val r2 = session.executeCommand("010D")
        assertTrue(r2 is DiagnosticExecutionResult.AdapterError)
        assertEquals("CAN_ERROR", (r2 as DiagnosticExecutionResult.AdapterError).errorType)

        mockTransport.mockResponses["0105"] = "UNABLE TO CONNECT>"
        val r3 = session.executeCommand("0105")
        assertTrue(r3 is DiagnosticExecutionResult.AdapterError)
        assertEquals("UNABLE_TO_CONNECT", (r3 as DiagnosticExecutionResult.AdapterError).errorType)

        mockTransport.mockResponses["010F"] = "STOPPED>"
        val r4 = session.executeCommand("010F")
        assertTrue(r4 is DiagnosticExecutionResult.AdapterError)
        assertEquals("STOPPED", (r4 as DiagnosticExecutionResult.AdapterError).errorType)

        mockTransport.mockResponses["0110"] = "NO DATA>"
        val r5 = session.executeCommand("0110")
        assertTrue(r5 is DiagnosticExecutionResult.AdapterError)
        assertEquals("NO_DATA", (r5 as DiagnosticExecutionResult.AdapterError).errorType)

        mockTransport.mockResponses["0104"] = "ERROR>"
        val r6 = session.executeCommand("0104")
        assertTrue(r6 is DiagnosticExecutionResult.AdapterError)
        assertEquals("ERROR", (r6 as DiagnosticExecutionResult.AdapterError).errorType)
    }
}
