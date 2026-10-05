package com.example.data.obd.ddt

import org.junit.Assert.assertEquals
import org.junit.Test

class DdtCapabilityAuditTest {

    @Test
    fun summarizesReadAndGuardedOperationsWithoutUnlockingAnything() {
        val descriptor = DdtEcuDescriptor(
            ecuName = "SID307_TEST",
            protocol = "CAN",
            sendId = "7E0",
            receiveId = "7E8",
            functionalAddress = "10",
            baudRate = 500000,
            endianness = "Big",
            autoIdents = emptyList(),
            capabilities = listOf(
                capability("Read state", "22F190", DdtOperationClass.READ, outputs = listOf("STATE")),
                capability("Read pressure", "221234", DdtOperationClass.READ, outputs = listOf("PRESSURE")),
                capability("Write config", "2E1234", DdtOperationClass.CONFIGURATION, inputs = listOf("VALUE")),
                capability("Actuator", "2F0101", DdtOperationClass.ACTUATOR_TEST),
                capability("Reset", "1101", DdtOperationClass.RESET),
                capability("Security", "2701", DdtOperationClass.SECURITY_ACCESS),
                capability("Tester present", "3E00", DdtOperationClass.SESSION_CONTROL, manual = true),
                capability("Unknown", "AA55", DdtOperationClass.UNKNOWN)
            ),
            sourceFile = "SID307_TEST.json"
        )

        val result = DdtCapabilityAuditor.audit(descriptor)

        assertEquals(8, result.totalRequests)
        assertEquals(2, result.readCandidates)
        assertEquals(5, result.guardedMutatingRequests)
        assertEquals(1, result.unknownRequests)
        assertEquals(1, result.manualSendRequests)
        assertEquals(1, result.requestsWithInputs)
        assertEquals(2, result.requestsWithOutputs)
        assertEquals(2, result.operationCounts[DdtOperationClass.READ])
        assertEquals(1, result.operationCounts[DdtOperationClass.CONFIGURATION])
    }

    private fun capability(
        name: String,
        bytes: String,
        operation: DdtOperationClass,
        inputs: List<String> = emptyList(),
        outputs: List<String> = emptyList(),
        manual: Boolean = false
    ) = DdtCapability(
        requestName = name,
        sentBytes = bytes,
        replyBytes = null,
        operationClass = operation,
        manualSend = manual,
        inputDataNames = inputs,
        outputDataNames = outputs
    )
}
