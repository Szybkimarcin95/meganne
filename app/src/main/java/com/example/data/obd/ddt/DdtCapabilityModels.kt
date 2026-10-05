package com.example.data.obd.ddt

/**
 * Database-derived capability metadata.
 *
 * IMPORTANT: A capability discovered in a DDT database is not automatically executable.
 * Execution stays behind the existing CommandFirewall / SafeDiagnosticSession gates.
 */
enum class DdtOperationClass {
    READ,
    WRITE,
    ACTUATOR_TEST,
    RESET,
    CONFIGURATION,
    SECURITY_ACCESS,
    SESSION_CONTROL,
    UNKNOWN
}

data class DdtCapability(
    val requestName: String,
    val sentBytes: String,
    val replyBytes: String?,
    val operationClass: DdtOperationClass,
    val manualSend: Boolean,
    val inputDataNames: List<String>,
    val outputDataNames: List<String>,
    val source: String = "DDT_DATABASE",
    val executable: Boolean = false
)

data class DdtAutoIdent(
    val diagnosticVersion: String?,
    val supplier: String?,
    val software: String?,
    val version: String?
)

data class DdtEcuDescriptor(
    val ecuName: String?,
    val protocol: String?,
    val sendId: String?,
    val receiveId: String?,
    val functionalAddress: String?,
    val baudRate: Int?,
    val endianness: String?,
    val autoIdents: List<DdtAutoIdent>,
    val capabilities: List<DdtCapability>,
    val sourceFile: String,
    val vehicleMatchConfirmed: Boolean = false
) {
    val autoIdentCount: Int
        get() = autoIdents.size
}

data class PhysicalEcuIdentity(
    val diagnosticVersion: String?,
    val supplier: String?,
    val software: String?,
    val version: String?,
    val receiveCanId: String? = null,
    val protocol: String? = null,
    val baudRate: Int? = null
)

enum class DdtVehicleMatchStatus {
    EXACT_AUTOIDENT_MATCH,
    AUTOIDENT_MISMATCH,
    TRANSPORT_MISMATCH,
    INSUFFICIENT_DATA
}

data class DdtVehicleMatchResult(
    val status: DdtVehicleMatchStatus,
    val matchedAutoIdent: DdtAutoIdent? = null,
    val matchedFields: Set<String> = emptySet(),
    val mismatchedFields: Set<String> = emptySet()
)
