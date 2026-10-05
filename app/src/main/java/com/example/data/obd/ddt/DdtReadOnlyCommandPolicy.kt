package com.example.data.obd.ddt

import java.util.Locale

sealed class DdtReadValidationResult {
    data class Allowed(
        val normalizedCommand: String,
        val requestName: String,
        val category: String = "RENAULT_DDT_READ"
    ) : DdtReadValidationResult()

    data class Blocked(
        val rawCommand: String,
        val reason: String
    ) : DdtReadValidationResult()
}

/**
 * Dynamic allowlist for Renault/DDT reads.
 *
 * A command is allowed only when:
 * 1. the supplied DDT definition exactly matches the physical ECU,
 * 2. the command exists in that definition,
 * 3. that request is classified as READ,
 * 4. it is not a known state-changing service.
 *
 * This class does not send anything to the vehicle.
 */
class DdtReadOnlyCommandPolicy(
    private val descriptor: DdtEcuDescriptor,
    private val physicalIdentity: PhysicalEcuIdentity,
    private val matcher: DdtVehicleMatcher = DdtVehicleMatcher()
) {

    fun validate(rawCommand: String): DdtReadValidationResult {
        val normalized = normalizeHex(rawCommand)
        if (normalized.isEmpty()) {
            return DdtReadValidationResult.Blocked(rawCommand, "Pusta komenda DDT")
        }

        if (isStateChangingService(normalized)) {
            return DdtReadValidationResult.Blocked(
                rawCommand,
                "ZABLOKOWANO: komenda należy do usługi zmieniającej stan ECU"
            )
        }

        val match = matcher.match(descriptor, physicalIdentity)
        if (match.status != DdtVehicleMatchStatus.EXACT_AUTOIDENT_MATCH) {
            return DdtReadValidationResult.Blocked(
                rawCommand,
                "DDT definition nie jest dokładnie dopasowana do fizycznego ECU"
            )
        }

        val candidates = descriptor.capabilities.filter {
            normalizeHex(it.sentBytes) == normalized
        }

        if (candidates.isEmpty()) {
            return DdtReadValidationResult.Blocked(
                rawCommand,
                "Komenda nie występuje w dopasowanej definicji DDT"
            )
        }

        val readCandidate = candidates.firstOrNull {
            it.operationClass == DdtOperationClass.READ
        }

        if (readCandidate == null) {
            return DdtReadValidationResult.Blocked(
                rawCommand,
                "Request istnieje w DDT, ale nie jest sklasyfikowany jako READ"
            )
        }

        return DdtReadValidationResult.Allowed(
            normalizedCommand = normalized,
            requestName = readCandidate.requestName
        )
    }

    private fun isStateChangingService(command: String): Boolean {
        val service = command.take(2)
        return service in HARD_BLOCKED_SERVICES
    }

    private fun normalizeHex(value: String): String =
        value.trim()
            .replace(" ", "")
            .replace("\n", "")
            .replace("\r", "")
            .uppercase(Locale.ROOT)

    private companion object {
        val HARD_BLOCKED_SERVICES = setOf(
            "11", // ECUReset
            "14", // ClearDiagnosticInformation
            "27", // SecurityAccess
            "2E", // WriteDataByIdentifier
            "2F", // InputOutputControlByIdentifier
            "30", // legacy IO/control family used by source DBs
            "31", // RoutineControl start
            "32", // RoutineControl stop
            "34", // RequestDownload
            "36", // TransferData
            "37", // RequestTransferExit
            "3D"  // WriteMemoryByAddress
        )
    }
}
