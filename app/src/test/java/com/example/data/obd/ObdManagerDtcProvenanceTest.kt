package com.example.data.obd

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ObdManagerDtcProvenanceTest {

    @Test
    fun testExplicitSimulationReturnsSampleDtcs() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ObdManager(context)
        manager.startSimulation()

        val (stored, pending) = manager.readTroubleCodes()

        assertEquals(listOf("P0380", "P242F"), stored)
        assertEquals(listOf("P0101"), pending)
        assertEquals(listOf("P0380", "P242F"), manager.activeTroubleCodes.value)
        assertEquals(listOf("P0101"), manager.pendingTroubleCodes.value)
    }

    @Test
    fun testNoTransportAndNonSimulatedReturnsEmptyListsWithoutFakeDtcs() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ObdManager(context)
        manager.disconnect()

        val (stored, pending) = manager.readTroubleCodes()

        assertTrue("Stored DTCs must be empty when disconnected", stored.isEmpty())
        assertTrue("Pending DTCs must be empty when disconnected", pending.isEmpty())
        assertTrue("Active DTC state must be empty", manager.activeTroubleCodes.value.isEmpty())
        assertTrue("Pending DTC state must be empty", manager.pendingTroubleCodes.value.isEmpty())
    }

    @Test
    fun testRealTransportReturnsParsedDtcs() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ObdManager(context)
        manager.disconnect()

        val mockTransport = MockDiagnosticTransport()
        mockTransport.open()
        mockTransport.mockResponses["03"] = "43 01 03 80 00 00 00 00>" // P0380
        mockTransport.mockResponses["07"] = "47 01 01 01 00 00 00 00>" // P0101

        manager.transport = mockTransport

        val (stored, pending) = manager.readTroubleCodes()

        assertEquals(listOf("P0380"), stored)
        assertEquals(listOf("P0101"), pending)
        assertEquals(listOf("P0380"), manager.activeTroubleCodes.value)
        assertEquals(listOf("P0101"), manager.pendingTroubleCodes.value)
    }
}
