package com.example.data.obd

object ObdParser {

    /**
     * Cleans raw ELM327 response:
     * - Removes echoing
     * - Strips prompt character '>'
     * - Removes carriage returns and spaces
     */
    fun cleanResponse(raw: String): String {
        return raw.replace(">", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .trim()
            .uppercase()
    }

    fun isErrorOrNoData(response: String): Boolean {
        val upper = response.uppercase()
        return upper.contains("NO DATA") ||
                upper.contains("ERROR") ||
                upper.contains("UNABLE TO CONNECT") ||
                upper.contains("BUS INIT") ||
                upper.contains("STOPPED") ||
                upper.contains("CAN ERROR") ||
                upper.isBlank()
    }

    /**
     * Parses standard OBD-II Mode 01 response (e.g. 41 0C 1A F8)
     */
    fun parseMode01(pid: String, rawResponse: String): ObdPidResult? {
        val clean = cleanResponse(rawResponse)
        if (isErrorOrNoData(clean)) return null

        val expectedPrefix = "41" + pid.uppercase().removePrefix("01")
        val index = clean.indexOf(expectedPrefix)
        if (index == -1) return null

        val payload = clean.substring(index + expectedPrefix.length)

        return try {
            when (pid.uppercase()) {
                "010C" -> { // Engine RPM: ((A * 256) + B) / 4
                    if (payload.length < 4) return null
                    val a = payload.substring(0, 2).toInt(16)
                    val b = payload.substring(2, 4).toInt(16)
                    val rpm = ((a * 256) + b) / 4.0
                    ObdPidResult(
                        pidHex = "010C",
                        name = "Prędkość obrotowa (RPM)",
                        value = rpm,
                        unit = "obr/min"
                    )
                }
                "010D" -> { // Vehicle Speed: A
                    if (payload.length < 2) return null
                    val a = payload.substring(0, 2).toInt(16)
                    ObdPidResult(
                        pidHex = "010D",
                        name = "Prędkość pojazdu",
                        value = a.toDouble(),
                        unit = "km/h"
                    )
                }
                "0105" -> { // Coolant Temperature: A - 40
                    if (payload.length < 2) return null
                    val a = payload.substring(0, 2).toInt(16)
                    ObdPidResult(
                        pidHex = "0105",
                        name = "Temperatura płynu chłodzącego",
                        value = (a - 40).toDouble(),
                        unit = "°C"
                    )
                }
                "010F" -> { // Intake Air Temperature: A - 40
                    if (payload.length < 2) return null
                    val a = payload.substring(0, 2).toInt(16)
                    ObdPidResult(
                        pidHex = "010F",
                        name = "Temperatura powietrza w dolocie",
                        value = (a - 40).toDouble(),
                        unit = "°C"
                    )
                }
                "0110" -> { // MAF air flow rate: ((A * 256) + B) / 100
                    if (payload.length < 4) return null
                    val a = payload.substring(0, 2).toInt(16)
                    val b = payload.substring(2, 4).toInt(16)
                    val maf = ((a * 256) + b) / 100.0
                    ObdPidResult(
                        pidHex = "0110",
                        name = "Przepływomierz MAF",
                        value = maf,
                        unit = "g/s"
                    )
                }
                "0104" -> { // Calculated Engine Load: (A * 100) / 255
                    if (payload.length < 2) return null
                    val a = payload.substring(0, 2).toInt(16)
                    val load = (a * 100.0) / 255.0
                    ObdPidResult(
                        pidHex = "0104",
                        name = "Obciążenie silnika",
                        value = (Math.round(load * 10) / 10.0),
                        unit = "%"
                    )
                }
                "0111" -> { // Throttle Position: (A * 100) / 255
                    if (payload.length < 2) return null
                    val a = payload.substring(0, 2).toInt(16)
                    val throttle = (a * 100.0) / 255.0
                    ObdPidResult(
                        pidHex = "0111",
                        name = "Pozycja przepustnicy",
                        value = (Math.round(throttle * 10) / 10.0),
                        unit = "%"
                    )
                }
                "010B" -> { // Intake Manifold Absolute Pressure (MAP): A kPa
                    if (payload.length < 2) return null
                    val a = payload.substring(0, 2).toInt(16)
                    ObdPidResult(
                        pidHex = "010B",
                        name = "Ciśnienie w kolektorze (MAP)",
                        value = a.toDouble(),
                        unit = "kPa"
                    )
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses standard OBD-II DTC response for Mode 03 (Stored) or Mode 07 (Pending).
     * Response format typically: 43 01 04 20 00 00 (Mode 03 -> 43, Mode 07 -> 47)
     */
    fun parseDtcResponse(rawResponse: String, modePrefix: String = "43"): List<String> {
        val clean = cleanResponse(rawResponse)
        if (isErrorOrNoData(clean)) return emptyList()

        val dtcCodes = mutableListOf<String>()
        val index = clean.indexOf(modePrefix)
        if (index == -1) return emptyList()

        val payload = clean.substring(index + modePrefix.length)
        var i = 0
        while (i + 4 <= payload.length) {
            val byte1Hex = payload.substring(i, i + 2)
            val byte2Hex = payload.substring(i + 2, i + 4)
            i += 4

            if (byte1Hex == "00" && byte2Hex == "00") continue

            try {
                val b1 = byte1Hex.toInt(16)
                val type = when ((b1 and 0xC0) shr 6) {
                    0 -> "P"
                    1 -> "C"
                    2 -> "B"
                    3 -> "U"
                    else -> "P"
                }
                val digit2 = (b1 and 0x30) shr 4
                val digit3 = (b1 and 0x0F).toString(16).uppercase()
                val code = "$type$digit2$digit3$byte2Hex"
                if (!dtcCodes.contains(code)) {
                    dtcCodes.add(code)
                }
            } catch (_: Exception) {}
        }
        return dtcCodes
    }
}
