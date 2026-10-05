package com.example.data.obd.ddt

/**
 * Read-only summary of capabilities declared by a matched DDT definition.
 *
 * This object is metadata only. It does not make any request executable and
 * does not communicate with the vehicle.
 */
data class DdtCapabilityAudit(
    val totalRequests: Int,
    val operationCounts: Map<DdtOperationClass, Int>,
    val manualSendRequests: Int,
    val requestsWithInputs: Int,
    val requestsWithOutputs: Int,
    val readCandidates: Int,
    val guardedMutatingRequests: Int,
    val unknownRequests: Int
)

object DdtCapabilityAuditor {

    fun audit(descriptor: DdtEcuDescriptor): DdtCapabilityAudit {
        val counts = DdtOperationClass.entries.associateWith { operation ->
            descriptor.capabilities.count { it.operationClass == operation }
        }

        val guardedMutating = descriptor.capabilities.count {
            it.operationClass in setOf(
                DdtOperationClass.WRITE,
                DdtOperationClass.ACTUATOR_TEST,
                DdtOperationClass.RESET,
                DdtOperationClass.CONFIGURATION,
                DdtOperationClass.SECURITY_ACCESS,
                DdtOperationClass.SESSION_CONTROL
            )
        }

        return DdtCapabilityAudit(
            totalRequests = descriptor.capabilities.size,
            operationCounts = counts,
            manualSendRequests = descriptor.capabilities.count { it.manualSend },
            requestsWithInputs = descriptor.capabilities.count { it.inputDataNames.isNotEmpty() },
            requestsWithOutputs = descriptor.capabilities.count { it.outputDataNames.isNotEmpty() },
            readCandidates = counts[DdtOperationClass.READ] ?: 0,
            guardedMutatingRequests = guardedMutating,
            unknownRequests = counts[DdtOperationClass.UNKNOWN] ?: 0
        )
    }
}
