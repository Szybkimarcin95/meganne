package com.example.data.obd.ddt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals("SID307_TEST", result.ecuName)
        assertEquals("CAN", result.protocol)
        assertEquals("7E0", result.sendId)
        assertEquals("7E8", result.receiveId)
        assertEquals(500000, result.baudRate)
        assertEquals(1, result.autoIdentCount)
        assertFalse(result.vehicleMatchConfirmed)

        assertEquals(DdtOperationClass.READ, result.capabilities.first { it.requestName == "Read engine state" }.operationClass)
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
}
