package com.example.data.obd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockDiagnosticTransport : DiagnosticTransport {
    private var openState = false
    val sentCommands = mutableListOf<String>()
    var mockResponses = mutableMapOf<String, String>()

    override suspend fun open(): Boolean {
        openState = true
        return true
    }

    override suspend fun close() {
        openState = false
    }

    override fun isTransportOpen(): Boolean = openState

    override suspend fun sendCommand(command: String, timeoutMs: Long): String {
        sentCommands.add(command)
        return mockResponses[command] ?: "NO DATA"
    }
}

class DiagnosticTransportTest {

    @Test
    fun testMockTransportConnectionLifecycle() = runBlocking {
        val transport = MockDiagnosticTransport()
        assertFalse(transport.isTransportOpen())
        assertTrue(transport.open())
        assertTrue(transport.isTransportOpen())
        transport.close()
        assertFalse(transport.isTransportOpen())
    }

    @Test
    fun testMockTransportCommandResponses() = runBlocking {
        val transport = MockDiagnosticTransport()
        transport.open()
        transport.mockResponses["010C"] = "41 0C 1A F8>"
        transport.mockResponses["010D"] = "41 0D 32>" // 50 km/h
        transport.mockResponses["0105"] = "41 05 5A>"

        val rpmResp = transport.sendCommand("010C")
        val speedResp = transport.sendCommand("010D")
        val coolantResp = transport.sendCommand("0105")

        assertEquals("41 0C 1A F8>", rpmResp)
        assertEquals("41 0D 32>", speedResp)
        assertEquals("41 05 5A>", coolantResp)
        assertEquals(listOf("010C", "010D", "0105"), transport.sentCommands)
    }

    @Test
    fun testMockTransportDefaultNoData() = runBlocking {
        val transport = MockDiagnosticTransport()
        transport.open()
        val unknownResp = transport.sendCommand("015C")
        assertEquals("NO DATA", unknownResp)
    }
}

