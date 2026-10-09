package com.example.data.obd.ddt

import org.junit.Assert.*
import org.junit.Test

class DdtDiscoveryCommandPolicyTest {
    private val request = DdtCapability("fixture", "22FFFF", "62FFFF", DdtOperationClass.READ,
        false, emptyList(), listOf("fixture"))
    private val descriptor = DdtEcuDescriptor("fixture", null, null, null, null, null, null,
        listOf(DdtAutoIdent("1", "2", "3", "4")), listOf(request), "TEST_FIXTURE.json")
    private val identity = PhysicalEcuIdentity("1", "2", "3", "4")

    @Test fun exactMatchedSingleSourceReadCanBeProposedButWritesCannot() {
        val policy = DdtDiscoveryCommandPolicy(descriptor, identity)
        assertTrue(policy.validate("22 FF FF") is DdtReadValidationResult.Allowed)
        assertTrue(policy.validate("2EFFFF00") is DdtReadValidationResult.Blocked)
        assertTrue(policy.validate("0100") is DdtReadValidationResult.Blocked)
        assertTrue(policy.validate("22FFFE") is DdtReadValidationResult.Blocked)
        assertTrue(DdtDiscoveryCommandPolicy(descriptor, null).validate("22FFFF") is DdtReadValidationResult.Blocked)
    }

    @Test fun duplicateReadWriteClassificationAndIdentityMismatchAreRejected() {
        val ambiguous = descriptor.copy(capabilities = listOf(request, request.copy(operationClass = DdtOperationClass.WRITE)))
        assertTrue(DdtDiscoveryCommandPolicy(ambiguous, identity).validate("22FFFF") is DdtReadValidationResult.Blocked)
        assertTrue(DdtDiscoveryCommandPolicy(descriptor, identity.copy(software = "different")).validate("22FFFF") is DdtReadValidationResult.Blocked)
    }
}
