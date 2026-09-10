package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.FuelRecord
import com.example.data.model.ServiceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceRecordDao {
    @Query("SELECT * FROM service_records ORDER BY mileageKm DESC, id DESC")
    fun getAllServiceRecords(): Flow<List<ServiceRecord>>

    @Query("SELECT * FROM service_records WHERE category = :category ORDER BY mileageKm DESC")
    fun getRecordsByCategory(category: String): Flow<List<ServiceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ServiceRecord): Long

    @Update
    suspend fun updateRecord(record: ServiceRecord)

    @Delete
    suspend fun deleteRecord(record: ServiceRecord)

    @Query("SELECT SUM(costPln) FROM service_records")
    fun getTotalCost(): Flow<Double?>
}

@Dao
interface FuelRecordDao {
    @Query("SELECT * FROM fuel_records ORDER BY mileageKm DESC, id DESC")
    fun getAllFuelRecords(): Flow<List<FuelRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFuel(fuel: FuelRecord): Long

    @Delete
    suspend fun deleteFuel(fuel: FuelRecord)

    @Query("SELECT SUM(liters) FROM fuel_records")
    fun getTotalLiters(): Flow<Double?>

    @Query("SELECT SUM(costPln) FROM fuel_records")
    fun getTotalFuelCost(): Flow<Double?>
}

@Dao
interface TelemetryLogDao {
    @Query("SELECT * FROM telemetry_logs ORDER BY timestamp DESC LIMIT 500")
    fun getRecentTelemetry(): Flow<List<com.example.data.model.TelemetryLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetryLog(log: com.example.data.model.TelemetryLog): Long

    @Query("DELETE FROM telemetry_logs")
    suspend fun clearTelemetryLogs()
}

@Dao
interface SensorTrendDao {
    @Query("SELECT * FROM sensor_trends WHERE sensorId = :sensorId ORDER BY timestamp ASC LIMIT :limit")
    fun getTrendsForSensor(sensorId: String, limit: Int = 100): Flow<List<com.example.data.model.SensorTrendEntity>>

    @Query("SELECT * FROM sensor_trends ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTrends(limit: Int = 200): Flow<List<com.example.data.model.SensorTrendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrend(trend: com.example.data.model.SensorTrendEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrends(trends: List<com.example.data.model.SensorTrendEntity>)

    @Query("DELETE FROM sensor_trends WHERE sensorId = :sensorId")
    suspend fun clearTrendsForSensor(sensorId: String)

    @Query("DELETE FROM sensor_trends")
    suspend fun clearAllTrends()

    @Query("SELECT COUNT(*) FROM sensor_trends")
    suspend fun getTrendCount(): Int
}

@Dao
interface DiagnosticFaultHistoryDao {
    @Query("SELECT * FROM diagnostic_fault_history ORDER BY timestamp DESC")
    fun getAllFaultHistory(): Flow<List<com.example.data.model.DiagnosticFaultHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFault(fault: com.example.data.model.DiagnosticFaultHistoryEntity): Long

    @Query("DELETE FROM diagnostic_fault_history WHERE id = :id")
    suspend fun deleteFaultById(id: Long)

    @Query("DELETE FROM diagnostic_fault_history")
    suspend fun clearAllFaultHistory()

    @Query("SELECT COUNT(*) FROM diagnostic_fault_history")
    suspend fun getFaultCount(): Int
}

