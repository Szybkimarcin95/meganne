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

    @Test
    fun testCommandFirewallAllowsSafeAtCommands() {
        val safeAtList = listOf(
            "ATZ", "ATE0", "ATL0", "ATS0", "ATSP0", "ATSP6",
            "ATRV", "ATCRA 7E8", "ATSH 7E0", "ATST 64", "ATI", "ATDP"
        )
        for (cmd in safeAtList) {
            val result = CommandFirewall.validate(cmd)
            assertTrue("Expected command '$cmd' to be allowed", result is CommandValidationResult.Allowed)
        }
    }

    @Test
    fun testCommandFirewallAllowsStandardObd2Reads() {
        val safeObd2Reads = listOf(
            "010C", "010D", "0105", "010B", "0104", "010F", "0110", "0111",
            "0202", "03", "07", "0902", "0900"
        )
        for (cmd in safeObd2Reads) {
            val result = CommandFirewall.validate(cmd)
            assertTrue("Expected command '$cmd' to be allowed", result is CommandValidationResult.Allowed)
        }
    }

    @Test
    fun testCommandFirewallAllowsVerifiedRenaultUdsReads() {
        val safeUds = listOf("222001", "222002", "222028", "1902FF")
        for (cmd in safeUds) {
            val result = CommandFirewall.validate(cmd)
            assertTrue("Expected Renault UDS read '$cmd' to be allowed", result is CommandValidationResult.Allowed)
        }
    }

    @Test
    fun testCommandFirewallBlocksForbiddenCommands() {
        val forbiddenList = listOf(
            "11 01",   // ECUReset
            "27 01",   // SecurityAccess
            "2E 20 01",// WriteDataByIdentifier
            "2F 01 02",// Actuator / IOControl
            "30 01",   // IOControl
            "31 01 AA",// RoutineControl
            "34 00",   // RequestDownload
            "36 01",   // TransferData
            "3D 00",   // WriteMemoryByAddress
            "22 9999", // Unverified / unwhitelisted DID
            "04"       // Mode 04 without explicit authorization flag
        )
        for (cmd in forbiddenList) {
            val result = CommandFirewall.validate(cmd, allowMode04Clear = false)
            assertTrue("Expected command '$cmd' to be blocked", result is CommandValidationResult.Blocked)
        }
    }

    @Test
    fun testMode04AllowedOnlyWhenExplicitlyAuthorized() {
        val blocked = CommandFirewall.validate("04", allowMode04Clear = false)
        assertTrue(blocked is CommandValidationResult.Blocked)

        val allowed = CommandFirewall.validate("04", allowMode04Clear = true)
        assertTrue(allowed is CommandValidationResult.Allowed)
    }

    @Test
    fun testSafeSessionBlocksForbiddenCommandWithoutTouchingTransport() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        val session = SafeDiagnosticSession(mockTransport)

        val result = session.executeCommand("2E2001") // Write attempt
        assertTrue(result is DiagnosticExecutionResult.BlockedByFirewall)
        assertEquals(0, mockTransport.sentCommands.size) // Physical transport never touched!
    }

    @Test
    fun testSafeSessionExecutesAllowedCommandsSequentially() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        mockTransport.mockResponses["010C"] = "41 0C 1A F8>"
        mockTransport.mockResponses["010D"] = "41 0D 32>"

        val session = SafeDiagnosticSession(mockTransport)

        val r1 = session.executeCommand("010C")
        val r2 = session.executeCommand("010D")

        assertTrue(r1 is DiagnosticExecutionResult.Success)
        assertTrue(r2 is DiagnosticExecutionResult.Success)
        assertEquals("410C1AF8", (r1 as DiagnosticExecutionResult.Success).cleanedResponse)
        assertEquals("410D32", (r2 as DiagnosticExecutionResult.Success).cleanedResponse)
        assertEquals(listOf("010C", "010D"), mockTransport.sentCommands)
    }

    @Test
    fun testSafeSessionMutualExclusionUnderConcurrency() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        val session = SafeDiagnosticSession(mockTransport)

        // Launch 10 concurrent requests
        val jobs = (1..10).map { i ->
            async {
                val pid = if (i % 2 == 0) "010C" else "010D"
                mockTransport.mockResponses[pid] = "41 ${pid.removePrefix("01")} 00>"
                session.executeCommand(pid)
            }
        }
        val results = jobs.awaitAll()
        assertEquals(10, results.size)
        assertTrue(results.all { it is DiagnosticExecutionResult.Success })
        assertEquals(10, mockTransport.sentCommands.size)
    }

    @Test
    fun testSafeSessionDetectsBufferFullAndCanError() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        val session = SafeDiagnosticSession(mockTransport)

        mockTransport.mockResponses["010C"] = "BUFFER FULL>"
        val r1 = session.executeCommand("010C")
        assertTrue(r1 is DiagnosticExecutionResult.TransportError)
        assertEquals("BUFFER_FULL", (r1 as DiagnosticExecutionResult.TransportError).errorType)

        mockTransport.mockResponses["010D"] = "CAN ERROR>"
        val r2 = session.executeCommand("010D")
        assertTrue(r2 is DiagnosticExecutionResult.TransportError)
        assertEquals("CAN_ERROR", (r2 as DiagnosticExecutionResult.TransportError).errorType)
    }

    @Test
    fun testSafeSessionAbortsWhenClosedDuringReset() = runBlocking {
        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        val session = SafeDiagnosticSession(mockTransport)

        val genBefore = session.sessionGeneration
        session.close()
        val genAfter = session.sessionGeneration

        assertTrue(genAfter > genBefore)
        assertFalse(session.isTransportOpen())

        val result = session.executeCommand("010C")
        assertTrue(result is DiagnosticExecutionResult.TransportError)
        assertEquals("TRANSPORT_CLOSED", (result as DiagnosticExecutionResult.TransportError).errorType)
    }
}
