package com.example.data.obd.ddt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DdtCapabilityIndexerTest {

    private val indexer = DdtCapabilityIndexer()

    @Test
    fun indexesDdt4AllJsonWithoutMakingCapabilitiesExecutable() {
        val json = """
            {
              "autoidents": [{"diagversion":"00FD","supplier":"550","soft":"A00","version":"01"}],
              "ecuname": "SID307_TEST",
              "obd": {
                "protocol": "CAN",
                "send_id": "7E0",
                "recv_id": "7E8",
                "baudrate": 500000,
                "funcaddr": "10"
              },
              "endian": "Big",
              "data": {},
              "devices": [],
              "requests": [
                {
                  "name": "Read engine state",
                  "sentbytes": "22 F1 90",
                  "replybytes": "62F190",
                  "minbytes": 8,
                  "shiftbytescount": 1,
                  "deny_sds": ["plant", "supplier"],
                  "receivebyte_dataitems": {
                    "ENGINE_STATE": {"firstbyte": 4}
                  }
                },
                {
                  "name": "Configuration write",
                  "sentbytes": "2E1234",
                  "sendbyte_dataitems": {
                    "OPTION_VALUE": {"firstbyte": 4}
                  }
                },
                {
                  "name": "Actuator test",
                  "sentbytes": "2F0101"
                }
              ]
            }
        """.trimIndent()

        val result = indexer.indexJson(json, "SID307_TEST.json")
        val readState = result.capabilities.first { it.requestName == "Read engine state" }

        assertEquals("SID307_TEST", result.ecuName)
        assertEquals("CAN", result.protocol)
        assertEquals("7E0", result.sendId)
        assertEquals("7E8", result.receiveId)
        assertEquals(500000, result.baudRate)
        assertEquals(1, result.autoIdentCount)
        assertEquals("00FD", result.autoIdents.single().diagnosticVersion)
        assertEquals("550", result.autoIdents.single().supplier)
        assertEquals("A00", result.autoIdents.single().software)
        assertEquals("01", result.autoIdents.single().version)
        assertFalse(result.vehicleMatchConfirmed)

        assertEquals(DdtOperationClass.READ, readState.operationClass)
        assertEquals(8, readState.minimumResponseBytes)
        assertEquals(1, readState.shiftBytesCount)
        assertEquals(listOf("plant", "supplier"), readState.deniedSessionNames)
        assertEquals(DdtOperationClass.CONFIGURATION, result.capabilities.first { it.requestName == "Configuration write" }.operationClass)
        assertEquals(DdtOperationClass.ACTUATOR_TEST, result.capabilities.first { it.requestName == "Actuator test" }.operationClass)
        assertFalse(result.capabilities.any { it.executable })
    }

    @Test
    fun classifiesSecurityResetAndUnknownConservatively() {
        assertEquals(DdtOperationClass.SECURITY_ACCESS, indexer.classifyOperation("Security", "27 01"))
        assertEquals(DdtOperationClass.RESET, indexer.classifyOperation("ECU reset", "11 01"))
        assertEquals(DdtOperationClass.UNKNOWN, indexer.classifyOperation("Mystery", "AA55"))
    }

    @Test
    fun exactMeganeSid307AutoIdentAndTransportCanBeConfirmedWithoutUnlockingCommands() {
        val json = """
            {
              "autoidents": [
                {"diagversion":"129","supplier":"4BE","soft":"00F7","version":"5500"}
              ],
              "ecuname": "SID307_00F7_550_V05_20130313T104520",
              "obd": {
                "protocol": "CAN",
                "send_id": "7E0",
                "recv_id": "7E8",
                "baudrate": 500000,
                "funcaddr": "10"
              },
              "requests": [
                {"name":"Read identification","sentbytes":"22F190"}
              ]
            }
        """.trimIndent()

        val descriptor = indexer.indexJson(
            json,
            "SID307_00F7_550_V05_20130313T104520.json"
        )

        val matcher = DdtVehicleMatcher()
        val match = matcher.match(descriptor, MeganeSid307Target.physicalIdentity)
        val confirmed = matcher.confirmVehicleMatch(descriptor, MeganeSid307Target.physicalIdentity)

        assertEquals(DdtVehicleMatchStatus.EXACT_AUTOIDENT_MATCH, match.status)
        assertEquals(
            setOf("diagversion", "supplier", "soft", "version", "recv_id", "protocol", "baudrate"),
            match.matchedFields
        )
        assertTrue(confirmed.vehicleMatchConfirmed)
        assertFalse(confirmed.capabilities.any { it.executable })
    }

    @Test
    fun exactAutoIdentWithWrongCanTransportIsRejected() {
        val descriptor = DdtEcuDescriptor(
            ecuName = "SID307_WRONG_TRANSPORT",
            protocol = "CAN",
            sendId = "7E0",
            receiveId = "7E9",
            functionalAddress = "10",
            baudRate = 250000,
            endianness = "Big",
            autoIdents = listOf(
                DdtAutoIdent(
                    diagnosticVersion = "129",
                    supplier = "4BE",
                    software = "00F7",
                    version = "5500"
                )
            ),
            capabilities = emptyList(),
            sourceFile = "SID307_WRONG_TRANSPORT.json"
        )

        val match = DdtVehicleMatcher().match(
            descriptor,
            MeganeSid307Target.physicalIdentity
        )

        assertEquals(DdtVehicleMatchStatus.TRANSPORT_MISMATCH, match.status)
        assertEquals(setOf("recv_id", "baudrate"), match.mismatchedFields)
    }

    @Test
    fun mismatchedSoftwareDoesNotConfirmVehicle() {
        val descriptor = DdtEcuDescriptor(
            ecuName = "SID307_OTHER",
            protocol = "CAN",
            sendId = "7E0",
            receiveId = "7E8",
            functionalAddress = "10",
            baudRate = 500000,
            endianness = "Big",
            autoIdents = listOf(
                DdtAutoIdent(
                    diagnosticVersion = "129",
                    supplier = "4BE",
                    software = "00FD",
                    version = "A00"
                )
            ),
            capabilities = emptyList(),
            sourceFile = "SID307_OTHER.json"
        )

        val physical = PhysicalEcuIdentity(
            diagnosticVersion = "129",
            supplier = "4BE",
            software = "00F7",
            version = "5500"
        )

        val match = DdtVehicleMatcher().match(descriptor, physical)

        assertEquals(DdtVehicleMatchStatus.AUTOIDENT_MISMATCH, match.status)
        assertFalse(descriptor.vehicleMatchConfirmed)
    }
}
