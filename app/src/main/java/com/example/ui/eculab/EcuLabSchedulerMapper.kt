package com.example.ui.eculab

/**
 * Normalizes the Python READ-only scheduler result schema into the Android ECU Lab UI model.
 *
 * The scheduler result is deliberately accepted as a generic map so this layer stays
 * transport-agnostic: JSON/Moshi, a mock file, or a future local bridge can all feed it.
 */
fun mapSchedulerResult(
    schedulerResult: Map<String, Any?>,
    manifestRequest: Map<String, Any?>
): EcuReadResult {
    val requestHex = (schedulerResult["request"] ?: manifestRequest["sentBytes"])
        ?.toString()
        ?.replace(" ", "")
        ?.uppercase()
        ?: error("request/sentBytes is required")

    val kind = schedulerResult["kind"]?.toString()
    val uiStatus = schedulerResult["uiStatus"]?.toString()
    val status = when {
        kind == "positive" || uiStatus == "SUPPORTED" -> EcuLabStatus.SUPPORTED
        kind == "nrc" -> EcuLabStatus.NRC
        uiStatus == "TIMEOUT" || uiStatus == "NRC_TIMEOUT" || kind == "timeout" -> EcuLabStatus.TIMEOUT
        uiStatus == "SESSION_REQUIRED" -> EcuLabStatus.SESSION_REQUIRED
        uiStatus == "CANCELLED" || kind == "cancelled" -> EcuLabStatus.CANCELLED
        else -> EcuLabStatus.NOT_TESTED
    }

    val decodedValues = (schedulerResult["values"] as? List<*>)
        .orEmpty()
        .mapNotNull { raw ->
            val value = raw as? Map<*, *> ?: return@mapNotNull null
            EcuDecodedValue(
                name = value["name"]?.toString() ?: "value",
                value = value["value"],
                unit = value["unit"]?.toString(),
                rawHex = value["rawHex"]?.toString()
            )
        }

    val firstDecoded = decodedValues.firstOrNull()
    val nrc = parseHexByte(schedulerResult["code"])

    return EcuReadResult(
        requestHex = requestHex,
        name = manifestRequest["name"]?.toString() ?: requestHex,
        service = parseHexByte(manifestRequest["service"]) ?: requestHex.take(2).toInt(16),
        status = status,
        nrc = nrc,
        nrcMeaning = schedulerResult["meaning"]?.toString(),
        raw = schedulerResult["response"]?.toString() ?: schedulerResult["rawHex"]?.toString(),
        physicalValue = firstDecoded?.value,
        unit = firstDecoded?.unit,
        decodedValues = decodedValues,
        sourceFile = manifestRequest["sourceFile"]?.toString()
            ?: "SID307_00F7_550_V05_20130313T104520.xml",
        sourceLine = (schedulerResult["sourceLine"] as? Number)?.toInt()
            ?: (manifestRequest["sourceLine"] as? Number)?.toInt()
            ?: error("sourceLine is required"),
        requiredSession = manifestRequest["requiredSession"]?.toString()
    )
}

private fun parseHexByte(value: Any?): Int? {
    if (value == null) return null
    if (value is Number) return value.toInt()
    val text = value.toString().trim().removePrefix("0x").removePrefix("0X")
    return text.toIntOrNull(16)
}
