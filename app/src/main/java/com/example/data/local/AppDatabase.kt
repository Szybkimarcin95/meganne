package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.DiagnosticFaultHistoryEntity
import com.example.data.model.FuelRecord
import com.example.data.model.SensorTrendEntity
import com.example.data.model.ServiceRecord
import com.example.data.model.TelemetryLog

@Database(
    entities = [
        ServiceRecord::class,
        FuelRecord::class,
        TelemetryLog::class,
        SensorTrendEntity::class,
        DiagnosticFaultHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serviceRecordDao(): ServiceRecordDao
    abstract fun fuelRecordDao(): FuelRecordDao
    abstract fun telemetryLogDao(): TelemetryLogDao
    abstract fun sensorTrendDao(): SensorTrendDao
    abstract fun diagnosticFaultHistoryDao(): DiagnosticFaultHistoryDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sensor_trends` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sensorId` TEXT NOT NULL,
                        `value` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `status` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `diagnostic_fault_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dtcCode` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `system` TEXT NOT NULL,
                        `severity` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `source` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "megane_overlord_db"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
