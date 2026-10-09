package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.K95Ecu
import com.example.data.model.K95EcuStatus
import com.example.data.obd.ddt.*
import com.example.data.repository.K95EcuRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class K95EcuPersistenceTest {
    private fun fixture() = K95Ecu(
        id = "TEST_FIXTURE", commonName = "Sterownik • żółć", status = K95EcuStatus.CONFIRMED_IN_CAR,
        diagnosticAddress = "123", responseAddress = "456", canSpeed = 500000, lastSeen = 1234L,
        identifiers = mapOf("VIN" to "TEST_FIXTURE", "SW" to "00F7"),
        serviceMapNodeIds = listOf("fixture-node"), physicalLocation = "fixture-location",
        sources = listOf("fixture.json"), notes = "TEST FIXTURE",
        ddtMatches = listOf(DdtEcuDescriptor("fixture", "CAN", "123", "456", null, 500000, "Big",
            listOf(DdtAutoIdent("1", "2", "3", "4")),
            listOf(DdtCapability("fixture-read", "22FFFF", "62FFFF", DdtOperationClass.READ,
                false, emptyList(), listOf("fixture-output"), 3, 0, listOf("fixture-session"))), "fixture.json"))
    )

    @Test fun fullModelSurvivesDatabaseReopenAndCandidateRefresh() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "k95-persistence-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val original = fixture()
            K95EcuRepository(db.k95EcuDao()).save(original)
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            val repository = K95EcuRepository(db.k95EcuDao())
            repository.importCandidates(listOf(original.copy(status = K95EcuStatus.DATABASE_CANDIDATE,
                lastSeen = null, identifiers = emptyMap())))
            assertEquals(original, repository.getById(original.id))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun migration3To4PreservesOldTablesAndRoomValidatesSchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "k95-migration-test.db"
        context.deleteDatabase(name)
        // Existing v3 tables have identical schemas to v4; remove only the new table and Room identity.
        val seed = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        val sql = seed.openHelper.writableDatabase
        sql.execSQL("INSERT INTO sensor_trends (sensorId, value, unit, timestamp, status) VALUES ('fixture', 1.0, 'fixture', 123, 'TEST_FIXTURE')")
        sql.execSQL("DROP TABLE k95_ecu")
        sql.execSQL("DROP TABLE room_master_table")
        sql.execSQL("PRAGMA user_version = 3")
        seed.close()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_3_4).allowMainThreadQueries().build()
        try {
            val db = migrated.openHelper.writableDatabase
            db.query("PRAGMA user_version").use { assertTrue(it.moveToFirst()); assertEquals(4, it.getInt(0)) }
            db.query("SELECT sensorId FROM sensor_trends").use { assertTrue(it.moveToFirst()); assertEquals("fixture", it.getString(0)) }
            db.query("SELECT count(*) FROM k95_ecu").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun corruptedJsonIsNotSilentlyConvertedToEmptyInventory() {
        val mapper = K95EcuMapper()
        val row = mapper.toEntity(fixture()).copy(identifiersJson = "invalid-json")
        var rejected = false
        try { mapper.toDomain(row) } catch (_: Exception) { rejected = true }
        assertTrue(rejected)
    }
}
