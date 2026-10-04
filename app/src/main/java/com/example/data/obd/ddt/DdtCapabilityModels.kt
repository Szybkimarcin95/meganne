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

data class DdtEcuDescriptor(
    val ecuName: String?,
    val protocol: String?,
    val sendId: String?,
    val receiveId: String?,
    val functionalAddress: String?,
    val baudRate: Int?,
    val endianness: String?,
    val autoIdentCount: Int,
    val capabilities: List<DdtCapability>,
    val sourceFile: String,
    val vehicleMatchConfirmed: Boolean = false
)
