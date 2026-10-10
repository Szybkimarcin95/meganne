package com.example.data.model

import com.example.data.obd.ddt.DdtEcuDescriptor
import org.junit.Assert.*
import org.junit.Test

class K95InventoryCatalogTest {
    @Test fun databaseMatchDoesNotProvePresenceOrInventResponseAddress() {
        val descriptor = DdtEcuDescriptor(
            ecuName = "fixture", protocol = "CAN", sendId = "123", receiveId = "456",
            functionalAddress = null, baudRate = 250000, endianness = null,
            autoIdents = emptyList(), capabilities = emptyList(), sourceFile = "fixture.json",
            vehicleMatchConfirmed = true
        )
        val ecu = K95InventoryCatalog.fromDdt(listOf(descriptor, descriptor)).single()
        assertEquals(K95EcuStatus.DATABASE_CANDIDATE, ecu.status)
        assertEquals("456", ecu.responseAddress)
        assertNull(ecu.lastSeen)
    }

    @Test fun passiveSubwooferAndCameraConnectorsAreNotEcus() {
        val map = K95ServiceMap("fixture", "DATABASE_VERIFIED", listOf(
            K95ServiceComponent("1917", "amp", "location", "DATABASE_VERIFIED", ""),
            K95ServiceComponent("1987", "woofer", "floor", "DATABASE_VERIFIED", "")
        ), emptyList(), emptyList())
        val ecu = K95InventoryCatalog.fromServiceMap(map).single()
        assertEquals(listOf("1917"), ecu.serviceMapNodeIds)
        assertEquals(K95EcuStatus.UNVERIFIED, ecu.status)
        assertNull(ecu.diagnosticAddress)
        assertTrue(K95InventoryCatalog.fromServiceMap(null).isEmpty())
    }
}
