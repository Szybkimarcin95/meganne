package com.example.data.obd.ddt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DdtReadOnlyCommandPolicyTest {

    @Test
    fun allowsOnlyReadCommandPresentInExactMatchedDefinition() {
        val policy = DdtReadOnlyCommandPolicy(
            descriptor = targetDescriptor(),
            physicalIdentity = MeganeSid307Target.physicalIdentity
        )

        val result = policy.validate("22 F1 90")

        assertTrue(result is DdtReadValidationResult.Allowed)
        result as DdtReadValidationResult.Allowed
        assertEquals("22F190", result.normalizedCommand)
        assertEquals("Read identification", result.requestName)
    }

    @Test
    fun blocksUnknownReadEvenWhenServiceLooksReadOnly() {
        val policy = DdtReadOnlyCommandPolicy(
            descriptor = targetDescriptor(),
            physicalIdentity = MeganeSid307Target.physicalIdentity
        )

        val result = policy.validate("22ABCD")

        assertTrue(result is DdtReadValidationResult.Blocked)
    }

    @Test
    fun blocksWriteEvenIfDefinitionContainsIt() {
        val policy = DdtReadOnlyCommandPolicy(
            descriptor = targetDescriptor(),
            physicalIdentity = MeganeSid307Target.physicalIdentity
        )

        val result = policy.validate("2E1234")

        assertTrue(result is DdtReadValidationResult.Blocked)
    }

    @Test
    fun blocksReadWhenPhysicalTransportDoesNotMatchDefinition() {
        val wrongTransport = MeganeSid307Target.physicalIdentity.copy(
            receiveCanId = "7E9"
        )

        val policy = DdtReadOnlyCommandPolicy(
            descriptor = targetDescriptor(),
            physicalIdentity = wrongTransport
        )

        val result = policy.validate("22F190")

        assertTrue(result is DdtReadValidationResult.Blocked)
    }

    private fun targetDescriptor() = DdtEcuDescriptor(
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
                requestName = "Read identification",
                sentBytes = "22F190",
                replyBytes = "62F190",
                operationClass = DdtOperationClass.READ,
                manualSend = false,
                inputDataNames = emptyList(),
                outputDataNames = listOf("IDENT")
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
}
