package com.example.data.obd.ddt

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.Locale

/**
 * Read-only DDT JSON indexer based on the JSON format emitted by DDT4All Ecu_file.dumpJson().
 *
 * Expected top-level fields include:
 * - autoidents
 * - ecuname
 * - obd { protocol, send_id, recv_id, baudrate, funcaddr, ... }
 * - data
 * - requests[]
 * - devices[]
 *
 * This class only indexes and classifies capabilities. It never talks to the vehicle.
 */
class DdtCapabilityIndexer(
    moshi: Moshi = Moshi.Builder().build()
) {
    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        Any::class.java
    )
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    fun indexJson(
        json: String,
        sourceFile: String
    ): DdtEcuDescriptor {
        val root = requireNotNull(mapAdapter.fromJson(json)) {
            "DDT JSON is empty or invalid"
        }

        val obd = root["obd"].asStringMap()
        val requests = root["requests"] as? List<*> ?: emptyList<Any?>()
        val autoidents = root["autoidents"] as? List<*> ?: emptyList<Any?>()

        val capabilities = requests.mapNotNull { raw ->
            val req = raw.asStringMap() ?: return@mapNotNull null
            val name = req["name"] as? String ?: return@mapNotNull null
            val sentBytes = normalizeHex(req["sentbytes"] as? String ?: "")
            val sendItems = req["sendbyte_dataitems"].asStringMap()?.keys?.sorted().orEmpty()
            val receiveItems = req["receivebyte_dataitems"].asStringMap()?.keys?.sorted().orEmpty()

            DdtCapability(
                requestName = name,
                sentBytes = sentBytes,
                replyBytes = (req["replybytes"] as? String)?.let(::normalizeHex)?.takeIf { it.isNotEmpty() },
                operationClass = classifyOperation(name, sentBytes),
                manualSend = req["manualsend"] as? Boolean ?: false,
                inputDataNames = sendItems,
                outputDataNames = receiveItems,
                executable = false
            )
        }.sortedWith(compareBy({ it.operationClass.ordinal }, { it.requestName.lowercase(Locale.ROOT) }))

        return DdtEcuDescriptor(
            ecuName = root["ecuname"] as? String,
            protocol = obd?.get("protocol") as? String,
            sendId = obd?.get("send_id") as? String,
            receiveId = obd?.get("recv_id") as? String,
            functionalAddress = obd?.get("funcaddr") as? String,
            baudRate = (obd?.get("baudrate") as? Number)?.toInt(),
            endianness = root["endian"] as? String,
            autoIdentCount = autoidents.size,
            capabilities = capabilities,
            sourceFile = sourceFile,
            vehicleMatchConfirmed = false
        )
    }

    internal fun classifyOperation(
        requestName: String,
        sentBytes: String
    ): DdtOperationClass {
        val service = normalizeHex(sentBytes).take(2)
        val upperName = requestName.uppercase(Locale.ROOT)

        return when (service) {
            // Diagnostic/session state, but not persistent configuration.
            "10", "3E" -> DdtOperationClass.SESSION_CONTROL

            // Security access must never be treated as an ordinary read.
            "27" -> DdtOperationClass.SECURITY_ACCESS

            // Reset.
            "11" -> DdtOperationClass.RESET

            // Input/output control / actuator routines.
            "2F", "30", "31" -> DdtOperationClass.ACTUATOR_TEST

            // Persistent writes / programming.
            "2E" -> {
                if (
                    upperName.contains("CONFIG") ||
                    upperName.contains("CODING") ||
                    upperName.contains("PROGRAM") ||
                    upperName.contains("OPTION")
                ) DdtOperationClass.CONFIGURATION else DdtOperationClass.WRITE
            }
            "3D", "34", "36", "37" -> DdtOperationClass.WRITE

            // Read-oriented services seen in OBD/UDS/KWP contexts.
            "01", "02", "03", "07", "09", "19", "1A", "21", "22", "23" -> DdtOperationClass.READ

            else -> {
                when {
                    upperName.contains("READ") ||
                        upperName.contains("STATUS") ||
                        upperName.contains("IDENT") ||
                        upperName.contains("MEASURE") -> DdtOperationClass.READ

                    upperName.contains("ACTUATOR") ||
                        upperName.contains("OUTPUT CONTROL") ||
                        upperName.contains("TEST ") -> DdtOperationClass.ACTUATOR_TEST

                    upperName.contains("CONFIG") ||
                        upperName.contains("CODING") ||
                        upperName.contains("OPTION") -> DdtOperationClass.CONFIGURATION

                    upperName.contains("WRITE") ||
                        upperName.contains("ERASE") -> DdtOperationClass.WRITE

                    upperName.contains("RESET") -> DdtOperationClass.RESET

                    else -> DdtOperationClass.UNKNOWN
                }
            }
        }
    }

    private fun normalizeHex(value: String): String =
        value.replace(" ", "")
            .replace("\n", "")
            .replace("\r", "")
            .uppercase(Locale.ROOT)

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asStringMap(): Map<String, Any?>? =
        this as? Map<String, Any?>
}
