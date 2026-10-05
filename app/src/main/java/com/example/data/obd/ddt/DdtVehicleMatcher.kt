package com.example.data.obd.ddt

import java.util.Locale

/**
 * Matches a physical ECU identity against DDT AutoIdent metadata and,
 * when available, transport metadata observed from the physical vehicle.
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
                val transportMismatches = transportMismatches(descriptor, physical)
                if (transportMismatches.isNotEmpty()) {
                    return DdtVehicleMatchResult(
                        status = DdtVehicleMatchStatus.TRANSPORT_MISMATCH,
                        matchedAutoIdent = candidate,
                        matchedFields = matched,
                        mismatchedFields = transportMismatches
                    )
                }

                return DdtVehicleMatchResult(
                    status = DdtVehicleMatchStatus.EXACT_AUTOIDENT_MATCH,
                    matchedAutoIdent = candidate,
                    matchedFields = matched + transportMatchedFields(descriptor, physical)
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

    private fun transportMismatches(
        descriptor: DdtEcuDescriptor,
        physical: PhysicalEcuIdentity
    ): Set<String> {
        val mismatches = mutableSetOf<String>()

        if (
            physical.receiveCanId != null &&
            descriptor.receiveId != null &&
            normalizeCanId(physical.receiveCanId) != normalizeCanId(descriptor.receiveId)
        ) {
            mismatches += "recv_id"
        }

        if (
            physical.protocol != null &&
            descriptor.protocol != null &&
            normalizeProtocol(physical.protocol) != normalizeProtocol(descriptor.protocol)
        ) {
            mismatches += "protocol"
        }

        if (
            physical.baudRate != null &&
            descriptor.baudRate != null &&
            physical.baudRate != descriptor.baudRate
        ) {
            mismatches += "baudrate"
        }

        return mismatches
    }

    private fun transportMatchedFields(
        descriptor: DdtEcuDescriptor,
        physical: PhysicalEcuIdentity
    ): Set<String> {
        val matched = mutableSetOf<String>()

        if (
            physical.receiveCanId != null &&
            descriptor.receiveId != null &&
            normalizeCanId(physical.receiveCanId) == normalizeCanId(descriptor.receiveId)
        ) {
            matched += "recv_id"
        }

        if (
            physical.protocol != null &&
            descriptor.protocol != null &&
            normalizeProtocol(physical.protocol) == normalizeProtocol(descriptor.protocol)
        ) {
            matched += "protocol"
        }

        if (
            physical.baudRate != null &&
            descriptor.baudRate != null &&
            physical.baudRate == descriptor.baudRate
        ) {
            matched += "baudrate"
        }

        return matched
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

    private fun normalizeCanId(value: String): String =
        value.trim()
            .removePrefix("0x")
            .removePrefix("0X")
            .replace(" ", "")
            .uppercase(Locale.ROOT)
            .trimStart('0')
            .ifEmpty { "0" }

    private fun normalizeProtocol(value: String): String =
        value.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .uppercase(Locale.ROOT)

    private fun mapOfNotNull(vararg pairs: Pair<String, String?>): Map<String, String> =
        pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()

    private companion object {
        val REQUIRED_FIELDS = setOf("diagversion", "supplier", "soft", "version")
    }
}
