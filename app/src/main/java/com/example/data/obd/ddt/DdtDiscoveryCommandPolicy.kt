package com.example.data.obd.ddt

/** Proposed scoped identification-read policy. Not wired to transport or the global firewall. */
class DdtDiscoveryCommandPolicy(
    private val descriptor: DdtEcuDescriptor,
    private val physicalIdentity: PhysicalEcuIdentity?
) {
    fun validate(rawCommand: String): DdtReadValidationResult {
        fun blocked(reason: String) = DdtReadValidationResult.Blocked(rawCommand, reason)
        val command = rawCommand.filterNot(Char::isWhitespace).uppercase()
        if (!Regex("22[0-9A-F]{4}").matches(command)) return blocked("Discovery permits one explicit identification DID read only")
        val identity = physicalIdentity ?: return blocked("PHYSICAL_IDENTITY_NOT_VERIFIED")
        if (descriptor.sourceFile.isBlank()) return blocked("SOURCE_NOT_FOUND")
        val matches = descriptor.capabilities.filter { it.sentBytes.filterNot(Char::isWhitespace).uppercase() == command }
        if (matches.isEmpty() || matches.any { it.operationClass != DdtOperationClass.READ })
            return blocked("READ_REQUEST_NOT_UNAMBIGUOUS")
        if (matches.any { it.inputDataNames.isNotEmpty() || it.replyBytes.isNullOrBlank() })
            return blocked("IDENTIFICATION_RESPONSE_CONTRACT_MISSING")
        return DdtReadOnlyCommandPolicy(descriptor, identity).validate(command)
    }
}
