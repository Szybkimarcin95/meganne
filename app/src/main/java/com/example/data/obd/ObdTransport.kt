package com.example.data.obd

enum class ObdConnectionState(val label: String) {
    DISCONNECTED("Rozłączono"),
    CONNECTING("Łączenie z ELM327..."),
    CONNECTED("Połączono z ELM327"),
    READING("Odczyt parametrów OBD-II"),
    ERROR("Błąd połączenia OBD")
}

enum class DataVerificationStatus(val label: String) {
    VERIFIED("Zweryfikowane (OEM/Dokumentacja)"),
    USER_PROVIDED("Dane użytkownika"),
    MEASURED("Zmierzone (Live OBD-II)"),
    INFERRED("Wyliczone / Inferred"),
    SIMULATED("Symulacja / Demo"),
    UNVERIFIED("Niezweryfikowane (Wymaga potwierdzenia)")
}

data class ObdPidResult(
    val pidHex: String,
    val name: String,
    val value: Double,
    val unit: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "OK",
    val source: DataVerificationStatus = DataVerificationStatus.MEASURED
)

interface DiagnosticTransport {
    suspend fun open(): Boolean
    suspend fun close()
    suspend fun sendCommand(command: String, timeoutMs: Long = 1500): String
    fun isTransportOpen(): Boolean
}
