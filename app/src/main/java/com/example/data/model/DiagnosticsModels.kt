package com.example.data.model

import com.example.data.obd.DataVerificationStatus

data class FuseItem(
    val id: String, // np. F1, F2, F3
    val name: String,
    val location: String, // "UPC - Skrzynka w komorze silnika" lub "BSI - Skrzynka w kabinie"
    val ratingAmps: Int,
    val colorHex: Long,
    val protectedCircuit: String,
    val wireCode: String,
    val oemNumber: String,
    val replacementGuide: String,
    val failureSymptoms: String,
    val verification: DataVerificationStatus = DataVerificationStatus.VERIFIED
)

data class EngineComponent(
    val id: String,
    val name: String,
    val polishName: String,
    val category: String,
    val xRatio: Float, // 0.0f .. 1.0f on visual layout
    val yRatio: Float,
    val functionDescription: String,
    val failureSymptoms: String,
    val diagnosticsProcedure: String,
    val nominalParameters: String,
    val oemNumber: String,
    val aftermarketOptions: String,
    val verification: DataVerificationStatus = DataVerificationStatus.VERIFIED
)

data class DtcCode(
    val code: String, // np. DF1012, DF297, P0420
    val system: String, // Wtrysk K9K, DPF/FAP, Doładowanie, Świece żarowe, BCM
    val title: String,
    val severity: DtcSeverity,
    val symptoms: List<String>,
    val rootCauses: List<String>,
    val diagnosticSteps: List<String>,
    val urgencyScore: Int, // 1-10
    val isRenaultSpecific: Boolean = code.startsWith("DF"),
    val verification: DataVerificationStatus = DataVerificationStatus.VERIFIED
)

enum class DtcSeverity(val label: String, val colorHex: Long) {
    CRITICAL("KRYTYCZNY", 0xFFFF1744),
    HIGH("WYSOKI", 0xFFFF9100),
    MEDIUM("ŚREDNI", 0xFFFFEA00),
    INFO("INFORMACYJNY", 0xFF00E5FF)
}

data class TorqueSpec(
    val id: String,
    val category: String,
    val component: String,
    val torqueNm: String,
    val threadSize: String,
    val notes: String,
    val verification: DataVerificationStatus = DataVerificationStatus.VERIFIED
)

data class RepairGuide(
    val id: String,
    val title: String,
    val category: String,
    val difficulty: String, // Łatwy, Średni, Zaawansowany
    val timeRequired: String,
    val toolsNeeded: List<String>,
    val partsNeeded: List<String>,
    val torqueSpecs: List<String>,
    val steps: List<String>,
    val proTips: String,
    val verification: DataVerificationStatus = DataVerificationStatus.VERIFIED
)

enum class TelemetryAvailability {
    AVAILABLE,
    UNAVAILABLE
}

data class TelemetryField<T>(
    val value: T? = null,
    val source: DataVerificationStatus = DataVerificationStatus.UNVERIFIED,
    val availability: TelemetryAvailability = TelemetryAvailability.UNAVAILABLE,
    val timestamp: Long? = null,
    val detail: String? = null
) {
    val isAvailable: Boolean
        get() = availability == TelemetryAvailability.AVAILABLE && value != null

    companion object {
        fun <T> measured(value: T, timestamp: Long = System.currentTimeMillis()): TelemetryField<T> =
            TelemetryField(
                value = value,
                source = DataVerificationStatus.MEASURED,
                availability = TelemetryAvailability.AVAILABLE,
                timestamp = timestamp
            )

        fun <T> simulated(value: T, timestamp: Long = System.currentTimeMillis()): TelemetryField<T> =
            TelemetryField(
                value = value,
                source = DataVerificationStatus.SIMULATED,
                availability = TelemetryAvailability.AVAILABLE,
                timestamp = timestamp
            )

        fun <T> unavailable(
            detail: String? = null,
            timestamp: Long? = null
        ): TelemetryField<T> =
            TelemetryField(
                value = null,
                source = DataVerificationStatus.UNVERIFIED,
                availability = TelemetryAvailability.UNAVAILABLE,
                timestamp = timestamp,
                detail = detail
            )
    }
}

data class LiveTelemetry(
    val rpm: TelemetryField<Int> = TelemetryField.unavailable(),
    val speedKmH: TelemetryField<Int> = TelemetryField.unavailable(),
    val boostBar: TelemetryField<Float> = TelemetryField.unavailable(),
    val railPressureBar: TelemetryField<Int> = TelemetryField.unavailable(),
    val coolantTempC: TelemetryField<Int> = TelemetryField.unavailable(),
    val oilTempC: TelemetryField<Int> = TelemetryField.unavailable(),
    val intakeAirTempC: TelemetryField<Int> = TelemetryField.unavailable(),
    val mafAirFlowGps: TelemetryField<Float> = TelemetryField.unavailable(),
    val engineLoadPercent: TelemetryField<Float> = TelemetryField.unavailable(),
    val throttlePercent: TelemetryField<Float> = TelemetryField.unavailable(),
    val mapPressureKpa: TelemetryField<Int> = TelemetryField.unavailable(),
    val dpfSootGrams: TelemetryField<Float> = TelemetryField.unavailable(),
    val oilDilutionPercent: TelemetryField<Float> = TelemetryField.unavailable(),
    val batteryVoltage: TelemetryField<Float> = TelemetryField.unavailable(),
    val fuelFlowLph: TelemetryField<Float> = TelemetryField.unavailable(),
    val egrPositionPercent: TelemetryField<Float> = TelemetryField.unavailable(),
    val injector1Correction: TelemetryField<Float> = TelemetryField.unavailable(),
    val injector2Correction: TelemetryField<Float> = TelemetryField.unavailable(),
    val injector3Correction: TelemetryField<Float> = TelemetryField.unavailable(),
    val injector4Correction: TelemetryField<Float> = TelemetryField.unavailable(),
    val isRegeneratingDpf: TelemetryField<Boolean> = TelemetryField.unavailable(),
    val isConnected: Boolean = false,
    val isSimulated: Boolean = true
)

data class SensorTrendPoint(
    val id: Long = 0,
    val sensorId: String,
    val value: Float,
    val unit: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: DataVerificationStatus = DataVerificationStatus.MEASURED
)

data class DiagnosticFaultHistoryEntry(
    val id: Long = 0,
    val dtcCode: String,
    val title: String,
    val system: String,
    val severity: DtcSeverity = DtcSeverity.CRITICAL,
    val timestamp: Long = System.currentTimeMillis(),
    val status: DataVerificationStatus = DataVerificationStatus.MEASURED,
    val source: String = "ECU SID307"
)

enum class HealthCheckStatus(val label: String, val colorHex: Long) {
    PASS("SPRAWNY", 0xFF00E676),
    WARNING("OSTRZEŻENIE", 0xFFFFEA00),
    ALERT("KRYTYCZNY", 0xFFFF1744),
    INFO("INFORMACJA", 0xFF00E5FF),
    UNKNOWN("NIEZNANY", 0xFF888888)
}

enum class DtcScanState(val label: String) {
    NOT_RUN("SKAN NIEWYKONANY"),
    RUNNING("SKANOWANIE"),
    COMPLETED("ZAKOŃCZONY"),
    FAILED("BŁĄD SKANU")
}

data class HealthCheckItem(
    val id: String,
    val name: String,
    val subsystem: String,
    val status: HealthCheckStatus,
    val measuredValue: String,
    val nominalRange: String,
    val message: String
)

data class DiagnosticCheckReport(
    val timestamp: Long = System.currentTimeMillis(),
    val overallStatus: HealthCheckStatus,
    val isSimulated: Boolean,
    val isConnected: Boolean,
    val activeDtcCount: Int,
    val pendingDtcCount: Int,
    val historyFaultsCount: Int,
    val checks: List<HealthCheckItem>,
    val summaryRecommendation: String
)
