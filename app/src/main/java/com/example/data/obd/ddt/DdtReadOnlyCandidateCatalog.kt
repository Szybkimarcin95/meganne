package com.example.data.obd.ddt

/**
 * Produces a candidate READ catalog only after an exact AutoIdent match.
 *
 * The returned capabilities remain executable=false. This class does not bypass
 * CommandFirewall and does not send commands to the vehicle.
 */
class DdtReadOnlyCandidateCatalog(
    private val matcher: DdtVehicleMatcher = DdtVehicleMatcher()
) {
    fun build(
        descriptor: DdtEcuDescriptor,
        physical: PhysicalEcuIdentity
    ): List<DdtCapability> {
        val match = matcher.match(descriptor, physical)
        if (match.status != DdtVehicleMatchStatus.EXACT_AUTOIDENT_MATCH) {
            return emptyList()
        }

        return descriptor.capabilities
            .asSequence()
            .filter { it.operationClass == DdtOperationClass.READ }
            .filter { it.sentBytes.isNotBlank() }
            .map { it.copy(executable = false) }
            .toList()
    }
}
