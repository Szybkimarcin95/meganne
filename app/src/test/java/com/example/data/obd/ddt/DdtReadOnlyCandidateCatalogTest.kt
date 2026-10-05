package com.example.data.obd.ddt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DdtReadOnlyCandidateCatalogTest {

    @Test
    fun exposesOnlyReadCandidatesAfterExactTargetMatch() {
        val descriptor = DdtEcuDescriptor(
            ecuName = "SID307_00F7_550_V05_20130313T104520",
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
                    software = "00F7",
                    version = "5500"
                )
            ),
            capabilities = listOf(
                DdtCapability(
                    requestName = "Read state",
                    sentBytes = "22F190",
                    replyBytes = null,
                    operationClass = DdtOperationClass.READ,
                    manualSend = false,
                    inputDataNames = emptyList(),
                    outputDataNames = listOf("STATE")
                ),
                DdtCapability(
                    requestName = "Configuration write",
                    sentBytes = "2E1234",
                    replyBytes = null,
                    operationClass = DdtOperationClass.CONFIGURATION,
                    manualSend = false,
                    inputDataNames = listOf("VALUE"),
                    outputDataNames = emptyList()
                )
            ),
            sourceFile = MeganeSid307Target.expectedDefinitionFile
        )

        val result = DdtReadOnlyCandidateCatalog().build(
            descriptor,
            MeganeSid307Target.physicalIdentity
        )

        assertEquals(1, result.size)
        assertEquals(DdtOperationClass.READ, result.single().operationClass)
        assertFalse(result.single().executable)
    }

    @Test
    fun returnsNothingWhenAutoIdentDoesNotMatch() {
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
            capabilities = listOf(
                DdtCapability(
                    requestName = "Read state",
                    sentBytes = "22F190",
                    replyBytes = null,
                    operationClass = DdtOperationClass.READ,
                    manualSend = false,
                    inputDataNames = emptyList(),
                    outputDataNames = emptyList()
                )
            ),
            sourceFile = "SID307_OTHER.json"
        )

        val result = DdtReadOnlyCandidateCatalog().build(
            descriptor,
            MeganeSid307Target.physicalIdentity
        )

        assertTrue(result.isEmpty())
    }
}
