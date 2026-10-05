package com.example.data.obd.ddt

import java.util.Locale

/**
 * Matches a physical ECU identity against DDT AutoIdent metadata only.
 *
 * No request is sent to the vehicle here. The caller is responsible for obtaining
 * the physical identity from a trusted diagnostic read and for preserving provenance.
 */
class DdtVehicleMatcher {

    fun match(
        descriptor: DdtEcuDescriptor,
        physical: PhysicalEcuIdentity
    ): DdtVehicleMatchResult {
        val physicalFields = normalizedFields(physical)
        if (physicalFields.size < REQUIRED_FIELDS.size) {
            return DdtVehicleMatchResult(
                status = DdtVehicleMatchStatus.INSUFFICIENT_DATA
            )
        }

        descriptor.autoIdents.forEach { candidate ->
            val candidateFields = normalizedFields(candidate)
            val matched = mutableSetOf<String>()
            val mismatched = mutableSetOf<String>()

            REQUIRED_FIELDS.forEach { field ->
                if (candidateFields[field] == physicalFields[field]) {
                    matched += field
                } else {
                    mismatched += field
                }
            }

            if (mismatched.isEmpty()) {
                return DdtVehicleMatchResult(
                    status = DdtVehicleMatchStatus.EXACT_AUTOIDENT_MATCH,
                    matchedAutoIdent = candidate,
                    matchedFields = matched
                )
            }
        }

        return DdtVehicleMatchResult(
            status = DdtVehicleMatchStatus.AUTOIDENT_MISMATCH,
            mismatchedFields = REQUIRED_FIELDS
        )
    }

    fun confirmVehicleMatch(
        descriptor: DdtEcuDescriptor,
        physical: PhysicalEcuIdentity
    ): DdtEcuDescriptor {
        val result = match(descriptor, physical)
        return descriptor.copy(
            vehicleMatchConfirmed = result.status == DdtVehicleMatchStatus.EXACT_AUTOIDENT_MATCH
        )
    }

    private fun normalizedFields(identity: PhysicalEcuIdentity): Map<String, String> =
        mapOfNotNull(
            "diagversion" to normalize(identity.diagnosticVersion),
            "supplier" to normalize(identity.supplier),
            "soft" to normalize(identity.software),
            "version" to normalize(identity.version)
        )

    private fun normalizedFields(identity: DdtAutoIdent): Map<String, String> =
        mapOfNotNull(
            "diagversion" to normalize(identity.diagnosticVersion),
            "supplier" to normalize(identity.supplier),
            "soft" to normalize(identity.software),
            "version" to normalize(identity.version)
        )

    private fun normalize(value: String?): String? =
        value
            ?.trim()
            ?.replace(" ", "")
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }

    private fun mapOfNotNull(vararg pairs: Pair<String, String?>): Map<String, String> =
        pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()

    private companion object {
        val REQUIRED_FIELDS = setOf("diagversion", "supplier", "soft", "version")
    }
}
