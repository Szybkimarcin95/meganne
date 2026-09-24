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
 * Command Firewall for ELM327 / OBD-II Diagnostic Transport.
 *
 * Enforces strict READ_ONLY policy under CP3A (Least Privilege):
 * - Allows ONLY AT commands present in current production code (ATZ, ATE0, ATL0, ATS0, ATSP0)
 * - Allows ONLY standard OBD-II Mode 01 PIDs used by current production telemetry
 * - Allows ONLY standard OBD-II DTC Read commands (Mode 03, Mode 07)
 * - Blocks Mode 04 (Clear DTC) under READ_ONLY policy
 * - Explicitly blocks dangerous state-changing services (0x11, 0x27, 0x2E, 0x2F, 0x30, 0x31, 0x34, 0x36, 0x37, 0x3D)
 * - Blocks unverified candidate commands (ATSP6, ATSH, ATCRA, UDS 0x19, UDS 0x22) as BLOCKED_PENDING_SOURCE_VERIFICATION
 */
object CommandFirewall {

    // Whitelist of AT commands strictly required by current production code:
    // Verified in ObdManager.kt initialization sequence.
    val ALLOWED_AT_COMMANDS = setOf(
        "ATZ",
        "ATE0",
        "ATL0",
        "ATS0",
        "ATSP0"
    )

    // Whitelist of standard OBD-II Mode 01 PIDs strictly used by current production:
    // Verified in ObdManager.kt startLiveObdPolling():
    // 010C: RPM, 010D: Speed, 0105: Coolant Temp, 010F: Intake Air Temp,
    // 0110: MAF, 0104: Engine Load, 0111: Throttle Position, 010B: MAP
    val ALLOWED_MODE01_PIDS = setOf(
        "010C",
        "010D",
        "0105",
        "010F",
        "0110",
        "0104",
        "0111",
        "010B"
    )

    // Whitelist of standard OBD-II DTC Read commands:
    // 03: Mode 03 (Stored DTCs), 07: Mode 07 (Pending DTCs)
    val ALLOWED_DTC_READ_COMMANDS = setOf(
        "03",
        "07"
    )

    fun validate(rawCommand: String): CommandValidationResult {
        val clean = rawCommand.trim().replace(" ", "").uppercase(Locale.ROOT)
        if (clean.isEmpty()) {
            return CommandValidationResult.Blocked(rawCommand, "Pusta komenda")
        }

        // 1. Dangerous State-Changing Services (Hard Blocked)
        if (clean.startsWith("11")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x11 ECUReset (modyfikacja stanu ECU)")
        }
        if (clean.startsWith("27")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x27 SecurityAccess (dostęp bezpieczeństwa)")
        }
        if (clean.startsWith("2E")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x2E WriteDataByIdentifier (zapis parametrów)")
        }
        if (clean.startsWith("2F") || clean.startsWith("30")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x2F/0x30 InputOutputControlByIdentifier (test elementów wykonawczych)")
        }
        if (clean.startsWith("31")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x31 RoutineControl (uruchomienie procedury serwisowej)")
        }
        if (clean.startsWith("34") || clean.startsWith("36") || clean.startsWith("37")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x34/0x36/0x37 Flash / TransferData (programowanie ECU)")
        }
        if (clean.startsWith("3D")) {
            return CommandValidationResult.Blocked(rawCommand, "ZABLOKOWANO: Serwis 0x3D WriteMemoryByAddress (zapis pamięci)")
        }

        // 2. Mode 04 (Clear DTC) - State modifying command, unconditionally blocked under READ_ONLY policy
        if (clean == "04") {
            return CommandValidationResult.Blocked(
                rawCommand,
                "Mode 04 (Clear DTC) jest zablokowany: polityka READ_ONLY zabrania modyfikacji stanu diagnostycznego ECU"
            )
        }

        // 3. Proprietary Renault / SID307 UDS & CAN addressing - BLOCKED PENDING SOURCE VERIFICATION
        if (clean.startsWith("ATSH") || clean.startsWith("ATCRA") || clean == "ATSP6") {
            return CommandValidationResult.Blocked(
                rawCommand,
                "BLOCKED_PENDING_SOURCE_VERIFICATION: Adresowanie magistrali i protokoły Renault zablokowane do czasu CP2"
            )
        }
        if (clean.startsWith("19")) {
            return CommandValidationResult.Blocked(
                rawCommand,
                "BLOCKED_PENDING_SOURCE_VERIFICATION: Serwis 0x19 UDS ReadDTCInformation zablokowany do czasu weryfikacji bazy w CP2"
            )
        }
        if (clean.startsWith("22")) {
            return CommandValidationResult.Blocked(
                rawCommand,
                "BLOCKED_PENDING_SOURCE_VERIFICATION: Serwis 0x22 UDS ReadDataByIdentifier zablokowany do czasu weryfikacji bazy w CP2"
            )
        }

        // 4. AT Commands (Minimal production allowlist: exact match only)
        if (clean.startsWith("AT")) {
            return if (ALLOWED_AT_COMMANDS.contains(clean)) {
                CommandValidationResult.Allowed(clean, "ELM327_AT_INIT")
            } else {
                CommandValidationResult.Blocked(
                    rawCommand,
                    "Komenda AT '$clean' nie znajduje się na minimalnej liście produkcyjnej (Least Privilege)"
                )
            }
        }

        // 5. Standard OBD-II Mode 01 Live Telemetry (Minimal production allowlist)
        if (clean.startsWith("01")) {
            return if (ALLOWED_MODE01_PIDS.contains(clean)) {
                CommandValidationResult.Allowed(clean, "OBD2_MODE01_LIVE_TELEMETRY")
            } else {
                CommandValidationResult.Blocked(
                    rawCommand,
                    "PID '$clean' nie jest używany przez bieżący tor produkcyjny (Least Privilege)"
                )
            }
        }

        // 6. Standard OBD-II DTC Read (Mode 03 / Mode 07)
        if (ALLOWED_DTC_READ_COMMANDS.contains(clean)) {
            val category = if (clean == "03") "OBD2_MODE03_STORED_DTC" else "OBD2_MODE07_PENDING_DTC"
            return CommandValidationResult.Allowed(clean, category)
        }

        // 7. All other uncalled standard services (e.g. Mode 02, Mode 09) or unknown commands
        return CommandValidationResult.Blocked(
            rawCommand,
            "Komenda '$clean' nie jest używana przez bieżący tor produkcyjny i pozostaje zablokowana (Least Privilege)"
        )
    }
}
