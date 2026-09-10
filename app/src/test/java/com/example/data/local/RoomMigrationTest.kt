package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_migration_overlord.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun testMigrationFrom2To3PreservesExistingDataAndCreatesNewTables() {
        // Step 1: Create v2 database schema manually with sample data
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `service_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `title` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `mileageKm` INTEGER NOT NULL,
                            `dateStr` TEXT NOT NULL,
                            `costPln` REAL NOT NULL,
                            `partsUsed` TEXT NOT NULL,
                            `invoiceNumber` TEXT NOT NULL,
                            `notes` TEXT NOT NULL,
                            `verification` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `fuel_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `dateStr` TEXT NOT NULL,
                            `mileageKm` INTEGER NOT NULL,
                            `liters` REAL NOT NULL,
                            `costPln` REAL NOT NULL,
                            `pricePerLiter` REAL NOT NULL,
                            `station` TEXT NOT NULL,
                            `isFullTank` INTEGER NOT NULL,
                            `verification` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `telemetry_logs` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `pid` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `value` REAL NOT NULL,
                            `unit` TEXT NOT NULL,
                            `source` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v2Db = helper.writableDatabase

        // Verify initial user_version is 2
        var cursor = v2Db.query("PRAGMA user_version")
        cursor.moveToFirst()
        assertEquals(2, cursor.getInt(0))
        cursor.close()

        // Insert sample records into v2 tables
        v2Db.execSQL(
            """
            INSERT INTO `service_records` (id, title, category, mileageKm, dateStr, costPln, partsUsed, invoiceNumber, notes, verification)
            VALUES (1, 'Wymiana oleju RN0720', 'Silnik', 175000, '2026-05-10', 420.50, 'Elf Full-Tech FE 5W30', 'FV/12/2026', 'Wymiana z filtrem Purflux', 'VERIFIED_USER')
            """.trimIndent()
        )
        v2Db.execSQL(
            """
            INSERT INTO `fuel_records` (id, dateStr, mileageKm, liters, costPln, pricePerLiter, station, isFullTank, verification)
            VALUES (1, '2026-06-01', 175600, 52.4, 356.32, 6.80, 'Orlen Verva Diesel', 1, 'VERIFIED_USER')
            """.trimIndent()
        )
        v2Db.execSQL(
            """
            INSERT INTO `telemetry_logs` (id, timestamp, pid, name, value, unit, source)
            VALUES (1, 1717200000000, '010C', 'Obroty silnika', 850.0, 'rpm', 'OBD-II')
            """.trimIndent()
        )
        v2Db.close()
        helper.close()

        // Step 2: Open database via Room with MIGRATION_2_3
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()

        val roomDb = migratedDb.openHelper.writableDatabase

        // Section 14: Check PRAGMA user_version = 3
        cursor = roomDb.query("PRAGMA user_version")
        cursor.moveToFirst()
        assertEquals(3, cursor.getInt(0))
        cursor.close()

        // Section 12: Assertions for preserved v2 data
        // service_records
        cursor = roomDb.query("SELECT id, title, mileageKm, costPln FROM service_records WHERE id = 1")
        assertTrue("service_records record must exist after migration", cursor.moveToFirst())
        assertEquals(1L, cursor.getLong(0))
        assertEquals("Wymiana oleju RN0720", cursor.getString(1))
        assertEquals(175000, cursor.getInt(2))
        assertEquals(420.50, cursor.getDouble(3), 0.001)
        cursor.close()

        // fuel_records
        cursor = roomDb.query("SELECT id, mileageKm, liters, costPln FROM fuel_records WHERE id = 1")
        assertTrue("fuel_records record must exist after migration", cursor.moveToFirst())
        assertEquals(1L, cursor.getLong(0))
        assertEquals(175600, cursor.getInt(1))
        assertEquals(52.4, cursor.getDouble(2), 0.001)
        assertEquals(356.32, cursor.getDouble(3), 0.001)
        cursor.close()

        // telemetry_logs
        cursor = roomDb.query("SELECT id, timestamp, pid, value FROM telemetry_logs WHERE id = 1")
        assertTrue("telemetry_logs record must exist after migration", cursor.moveToFirst())
        assertEquals(1L, cursor.getLong(0))
        assertEquals(1717200000000L, cursor.getLong(1))
        assertEquals("010C", cursor.getString(2))
        assertEquals(850.0, cursor.getDouble(3), 0.001)
        cursor.close()

        // Section 13: Assertions for new v3 tables
        // Test INSERT & SELECT on sensor_trends
        roomDb.execSQL(
            """
            INSERT INTO `sensor_trends` (sensorId, value, unit, timestamp, status)
            VALUES ('turbocharger', 1.42, 'bar', 1717200050000, 'MEASURED')
            """.trimIndent()
        )
        cursor = roomDb.query("SELECT id, sensorId, value, unit, timestamp, status FROM sensor_trends WHERE sensorId = 'turbocharger'")
        assertTrue("sensor_trends record must be queryable", cursor.moveToFirst())
        assertNotNull(cursor.getLong(0))
        assertEquals("turbocharger", cursor.getString(1))
        assertEquals(1.42, cursor.getFloat(2).toDouble(), 0.01)
        assertEquals("bar", cursor.getString(3))
        assertEquals(1717200050000L, cursor.getLong(4))
        assertEquals("MEASURED", cursor.getString(5))
        cursor.close()

        // Test INSERT & SELECT on diagnostic_fault_history
        roomDb.execSQL(
            """
            INSERT INTO `diagnostic_fault_history` (dtcCode, title, system, severity, timestamp, status, source)
            VALUES ('P0380', 'Układ podgrzewania wstępnego', 'Świece żarowe', 'HIGH', 1717200060000, 'MEASURED', 'OBD-II Mode 03')
            """.trimIndent()
        )
        cursor = roomDb.query("SELECT id, dtcCode, title, severity, status FROM diagnostic_fault_history WHERE dtcCode = 'P0380'")
        assertTrue("diagnostic_fault_history record must be queryable", cursor.moveToFirst())
        assertNotNull(cursor.getLong(0))
        assertEquals("P0380", cursor.getString(1))
        assertEquals("Układ podgrzewania wstępnego", cursor.getString(2))
        assertEquals("HIGH", cursor.getString(3))
        assertEquals("MEASURED", cursor.getString(4))
        cursor.close()

        migratedDb.close()
    }
}
