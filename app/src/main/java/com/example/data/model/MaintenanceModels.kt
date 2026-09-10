package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.obd.DataVerificationStatus

@Entity(tableName = "service_records")
data class ServiceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String, // Olej RN0720, Rozrząd, Filtr paliwa, Hamulce, Zawieszenie, Inne
    val mileageKm: Int,
    val dateStr: String,
    val costPln: Double,
    val partsUsed: String = "",
    val invoiceNumber: String = "",
    val notes: String = "",
    val verification: String = "VERIFIED"
)

@Entity(tableName = "fuel_records")
data class FuelRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateStr: String,
    val mileageKm: Int,
    val liters: Double,
    val costPln: Double,
    val pricePerLiter: Double = if (liters > 0) costPln / liters else 0.0,
    val station: String = "Stacja paliw",
    val isFullTank: Boolean = true,
    val verification: String = "USER_PROVIDED"
)

@Entity(tableName = "telemetry_logs")
data class TelemetryLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val pid: String,
    val name: String,
    val value: Double,
    val unit: String,
    val source: String = DataVerificationStatus.MEASURED.name
)

data class VehicleProfile(
    val vin: String = VehicleSpec.VIN,
    val make: String = VehicleSpec.MAKE,
    val model: String = VehicleSpec.MODEL,
    val generation: String = "III (Phase 2 / X32)",
    val bodyStyle: String = "Grandtour (Kombi)",
    val version: String = VehicleSpec.EDITION,
    val engine: String = VehicleSpec.ENGINE_DESC,
    val engineFamily: String = VehicleSpec.ENGINE_CODE,
    val engineVariant: String = VehicleSpec.ENGINE_DESIGNATION,
    val gearbox: String = VehicleSpec.GEARBOX_TYPE,
    val year: Int = 2013,
    val mileageKm: Int = 184500,
    val firstRegistrationDate: String = "2013-09-14",
    val equipment: String = VehicleSpec.FACTORY_OPTIONS,
    val notes: String = "Sterownik Continental SID307, norma Euro 5 z DPF i Start-Stop."
)

@Entity(tableName = "sensor_trends")
data class SensorTrendEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sensorId: String,
    val value: Float,
    val unit: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = DataVerificationStatus.MEASURED.name
)

@Entity(tableName = "diagnostic_fault_history")
data class DiagnosticFaultHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dtcCode: String,
    val title: String,
    val system: String,
    val severity: String = "CRITICAL",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = DataVerificationStatus.MEASURED.name,
    val source: String = "ECU SID307"
)

fun SensorTrendEntity.toDomain(): SensorTrendPoint = SensorTrendPoint(
    id = id,
    sensorId = sensorId,
    value = value,
    unit = unit,
    timestamp = timestamp,
    status = try { DataVerificationStatus.valueOf(status) } catch (_: Exception) { DataVerificationStatus.MEASURED }
)

fun SensorTrendPoint.toEntity(): SensorTrendEntity = SensorTrendEntity(
    id = id,
    sensorId = sensorId,
    value = value,
    unit = unit,
    timestamp = timestamp,
    status = status.name
)

fun DiagnosticFaultHistoryEntity.toDomain(): DiagnosticFaultHistoryEntry = DiagnosticFaultHistoryEntry(
    id = id,
    dtcCode = dtcCode,
    title = title,
    system = system,
    severity = try { DtcSeverity.valueOf(severity) } catch (_: Exception) { DtcSeverity.CRITICAL },
    timestamp = timestamp,
    status = try { DataVerificationStatus.valueOf(status) } catch (_: Exception) { DataVerificationStatus.MEASURED },
    source = source
)

fun DiagnosticFaultHistoryEntry.toEntity(): DiagnosticFaultHistoryEntity = DiagnosticFaultHistoryEntity(
    id = id,
    dtcCode = dtcCode,
    title = title,
    system = system,
    severity = severity.name,
    timestamp = timestamp,
    status = status.name,
    source = source
)

