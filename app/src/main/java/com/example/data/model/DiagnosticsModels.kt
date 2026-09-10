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

data class LiveTelemetry(
    val rpm: Int = 850,
    val speedKmH: Int = 0,
    val boostBar: Float = 0.02f,
    val railPressureBar: Int = 280,
    val coolantTempC: Int = 88,
    val oilTempC: Int = 91,
    val intakeAirTempC: Int = 24,
    val mafAirFlowGps: Float = 12.5f,
    val engineLoadPercent: Float = 18.0f,
    val throttlePercent: Float = 0.0f,
    val mapPressureKpa: Int = 101,
    val dpfSootGrams: Float = 14.8f,
    val oilDilutionPercent: Float = 3.2f,
    val batteryVoltage: Float = 14.2f,
    val fuelFlowLph: Float = 0.6f,
    val egrPositionPercent: Float = 22.0f,
    val injector1Correction: Float = -0.12f,
    val injector2Correction: Float = 0.08f,
    val injector3Correction: Float = -0.05f,
    val injector4Correction: Float = 0.09f,
    val isConnected: Boolean = false,
    val isSimulated: Boolean = true,
    val isRegeneratingDpf: Boolean = false,
    val dataSource: DataVerificationStatus = DataVerificationStatus.SIMULATED
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

