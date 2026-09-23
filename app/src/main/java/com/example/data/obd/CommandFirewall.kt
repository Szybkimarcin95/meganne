package com.example.data.obd

import java.util.Locale

/**
 * Result of CommandFirewall validation.
 */
sealed class CommandValidationResult {
    data class Allowed(val normalizedCommand: String, val category: String) : CommandValidationResult()
    data class Blocked(val rawCommand: String, val reason: String) : CommandValidationResult()
}

/**
 * Command Firewall for ELM327 / OBD-II / Renault UDS Diagnostic Transport.
 *
 * Enforces strict read-only policy:
 * - Allows only safe AT commands (setup, configuration, voltage)
 * - Allows standard OBD-II read modes (Mode 01, 02, 03, 07, 09)
 * - Allows verified Renault UDS Read services (0x22 ReadDataByIdentifier, 0x19 ReadDTCInformation)
 * - Strict prohibition of write (0x2E, 0x3D), actuator tests (0x2F, 0x30, 0x31),
 *   security access (0x27), and ECU resets (0x11).
 * - Mode 04 (Clear DTC) requires explicit authorization flag.
 */
object CommandFirewall {

    // Whitelist of supported AT command prefixes
    private val ALLOWED_AT_PREFIXES = setOf(
        "ATZ", "ATE0", "ATE1", "ATL0", "ATL1", "ATS0", "ATS1",
        "ATSP0", "ATSP6", "ATRV", "ATCRA", "ATSH", "ATST",
        "ATBD", "ATI", "ATDP", "ATDPN", "ATCAF0", "ATCAF1"
    )

    // Allowed Renault UDS Read DIDs (Service 0x22)
    // Confirmed in SID307 candidate profiles
    private val ALLOWED_UDS_READ_DIDS = setOf(
        "2001", // Coolant temperature
        "2002", // Engine RPM
        "2028"  // MIL warning state
    )

    fun validate(rawCommand: String, allowMode04Clear: Boolean = false): CommandValidationResult {
        val clean = rawCommand.trim().replace(" ", "").uppercase(Locale.ROOT)
        if (clean.isEmpty()) {
            return CommandValidationResult.Blocked(rawCommand, "Pusta komenda")
        }

        // 1. AT Commands
        if (clean.startsWith("AT")) {
            val matchedPrefix = ALLOWED_AT_PREFIXES.firstOrNull { clean.startsWith(it) }
            return if (matchedPrefix != null) {
                CommandValidationResult.Allowed(clean, "ELM327_AT_INIT")
            } else {
                CommandValidationResult.Blocked(rawCommand, "Niedozwolona lub nieznana komenda AT")
            }
        }

        // 2. Mode 04 Protected Clear
        if (clean == "04") {
            return if (allowMode04Clear) {
                CommandValidationResult.Allowed(clean, "OBD2_MODE04_CLEAR_AUTHORIZED")
            } else {
                CommandValidationResult.Blocked(rawCommand, "Mode 04 (Clear DTC) zablokowany: brak jawnej autoryzacji chronionej akcji")
            }
        }

        // 3. Prohibited Diagnostic Services (Write / Actuator / Security / Reset)
        if (clean.startsWith("11")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x11 ECUReset")
        }
        if (clean.startsWith("27")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x27 SecurityAccess")
        }
        if (clean.startsWith("2E")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x2E WriteDataByIdentifier")
        }
        if (clean.startsWith("2F") || clean.startsWith("30")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x2F/0x30 ActuatorTest / IOControl")
        }
        if (clean.startsWith("31")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x31 RoutineControl")
        }
        if (clean.startsWith("34") || clean.startsWith("36") || clean.startsWith("37")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x34/0x36/0x37 Flash / TransferData")
        }
        if (clean.startsWith("3D")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x3D WriteMemoryByAddress")
        }

        // 4. Standard OBD-II Mode 01 (Live Telemetry Read)
        if (clean.startsWith("01")) {
            if (clean.length == 4 && clean.all { it.isDigit() || it in 'A'..'F' }) {
                return CommandValidationResult.Allowed(clean, "OBD2_MODE01_LIVE_TELEMETRY")
            }
            return CommandValidationResult.Blocked(rawCommand, "Nieprawidłowy format PID dla Mode 01 (oczekiwano 4 znaków hex)")
        }

        // 5. Standard OBD-II Mode 02 (Freeze Frame Read)
        if (clean.startsWith("02")) {
            if (clean.length in 4..6 && clean.all { it.isDigit() || it in 'A'..'F' }) {
                return CommandValidationResult.Allowed(clean, "OBD2_MODE02_FREEZE_FRAME")
            }
            return CommandValidationResult.Blocked(rawCommand, "Nieprawidłowy format PID dla Mode 02")
        }

        // 6. Standard OBD-II Mode 03 (Stored DTC Read)
        if (clean == "03") {
            return CommandValidationResult.Allowed(clean, "OBD2_MODE03_STORED_DTC")
        }

        // 7. Standard OBD-II Mode 07 (Pending DTC Read)
        if (clean == "07") {
            return CommandValidationResult.Allowed(clean, "OBD2_MODE07_PENDING_DTC")
        }

        // 8. Standard OBD-II Mode 09 (Vehicle Info / Calibration Read)
        if (clean.startsWith("09")) {
            if (clean.length == 4 && clean.all { it.isDigit() || it in 'A'..'F' }) {
                return CommandValidationResult.Allowed(clean, "OBD2_MODE09_VEHICLE_INFO")
            }
            return CommandValidationResult.Blocked(rawCommand, "Nieprawidłowy format PID dla Mode 09")
        }

        // 9. Renault UDS Service 0x19 (Read DTC Information)
        if (clean.startsWith("19")) {
            if (clean == "1902FF" || clean == "190200" || clean == "1902") {
                return CommandValidationResult.Allowed(clean, "RENAULT_UDS_0x19_DTC_REPORT")
            }
            return CommandValidationResult.Blocked(rawCommand, "Nieobsługiwana sub-funkcja dla UDS 0x19")
        }

        // 10. Renault UDS Service 0x22 (ReadDataByIdentifier)
        if (clean.startsWith("22")) {
            val did = clean.removePrefix("22")
            if (ALLOWED_UDS_READ_DIDS.contains(did)) {
                return CommandValidationResult.Allowed(clean, "RENAULT_UDS_0x22_READ_DID")
            }
            return CommandValidationResult.Blocked(
                rawCommand,
                "DID $did nie znajduje się na białej liście zweryfikowanych odczytów SID307"
            )
        }

        return CommandValidationResult.Blocked(rawCommand, "Nieznana lub nieautoryzowana komenda diagnostyczna")
    }
}
