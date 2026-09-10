package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DiagnosticFaultHistoryEntity
import com.example.data.model.DiagnosticFaultHistoryEntry
import com.example.data.model.DtcSeverity
import com.example.data.model.SensorTrendEntity
import com.example.data.model.SensorTrendPoint
import com.example.data.model.toDomain
import com.example.data.model.toEntity
import com.example.data.obd.DataVerificationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SensorTrendAndFaultHistoryTest {

    private lateinit var db: AppDatabase
    private lateinit var sensorTrendDao: SensorTrendDao
    private lateinit var faultHistoryDao: DiagnosticFaultHistoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sensorTrendDao = db.sensorTrendDao()
        faultHistoryDao = db.diagnosticFaultHistoryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testSensorTrendEntityModelMapping() {
        val domain = SensorTrendPoint(
            id = 42L,
            sensorId = "turbocharger",
            value = 1.35f,
            unit = "bar",
            timestamp = 1700000000000L,
            status = DataVerificationStatus.MEASURED
        )

        val entity = domain.toEntity()
        assertEquals(42L, entity.id)
        assertEquals("turbocharger", entity.sensorId)
        assertEquals(1.35f, entity.value, 0.001f)
        assertEquals("bar", entity.unit)
        assertEquals(1700000000000L, entity.timestamp)
        assertEquals("MEASURED", entity.status)

        val convertedBack = entity.toDomain()
        assertEquals(domain.id, convertedBack.id)
        assertEquals(domain.sensorId, convertedBack.sensorId)
        assertEquals(domain.value, convertedBack.value, 0.001f)
        assertEquals(domain.unit, convertedBack.unit)
        assertEquals(domain.timestamp, convertedBack.timestamp)
        assertEquals(domain.status, convertedBack.status)
    }

    @Test
    fun testDiagnosticFaultHistoryEntityModelMapping() {
        val domain = DiagnosticFaultHistoryEntry(
            id = 7L,
            dtcCode = "DF1012",
            title = "Ogranicznik / Regulator prędkości",
            system = "Injection SID307",
            severity = DtcSeverity.CRITICAL,
            timestamp = 1700000005000L,
            status = DataVerificationStatus.VERIFIED,
            source = "ECU SID307"
        )

        val entity = domain.toEntity()
        assertEquals(7L, entity.id)
        assertEquals("DF1012", entity.dtcCode)
        assertEquals("CRITICAL", entity.severity)
        assertEquals("VERIFIED", entity.status)
        assertEquals("ECU SID307", entity.source)

        val convertedBack = entity.toDomain()
        assertEquals(domain.id, convertedBack.id)
        assertEquals(domain.dtcCode, convertedBack.dtcCode)
        assertEquals(domain.title, convertedBack.title)
        assertEquals(domain.system, convertedBack.system)
        assertEquals(domain.severity, convertedBack.severity)
        assertEquals(domain.timestamp, convertedBack.timestamp)
        assertEquals(domain.status, convertedBack.status)
        assertEquals(domain.source, convertedBack.source)
    }

    @Test
    fun testInsertAndObserveSensorTrends() = runBlocking {
        val entity1 = SensorTrendEntity(
            sensorId = "turbocharger",
            value = 0.85f,
            unit = "bar",
            timestamp = 1000L,
            status = "MEASURED"
        )
        val entity2 = SensorTrendEntity(
            sensorId = "turbocharger",
            value = 1.25f,
            unit = "bar",
            timestamp = 2000L,
            status = "MEASURED"
        )
        val entity3 = SensorTrendEntity(
            sensorId = "map_sensor",
            value = 101f,
            unit = "kPa",
            timestamp = 3000L,
            status = "MEASURED"
        )

        sensorTrendDao.insertTrend(entity1)
        sensorTrendDao.insertTrend(entity2)
        sensorTrendDao.insertTrend(entity3)

        val turboPoints = sensorTrendDao.getTrendsForSensor("turbocharger").first()
        assertEquals(2, turboPoints.size)
        assertEquals(0.85f, turboPoints[0].value, 0.001f)
        assertEquals(1.25f, turboPoints[1].value, 0.001f)

        val mapPoints = sensorTrendDao.getTrendsForSensor("map_sensor").first()
        assertEquals(1, mapPoints.size)
        assertEquals(101f, mapPoints[0].value, 0.001f)
    }

    @Test
    fun testClearSensorTrends() = runBlocking {
        sensorTrendDao.insertTrend(
            SensorTrendEntity(
                sensorId = "turbocharger",
                value = 1.0f,
                unit = "bar",
                timestamp = 1000L,
                status = "MEASURED"
            )
        )
        sensorTrendDao.insertTrend(
            SensorTrendEntity(
                sensorId = "egr_valve",
                value = 25f,
                unit = "%",
                timestamp = 1000L,
                status = "MEASURED"
            )
        )

        sensorTrendDao.clearTrendsForSensor("turbocharger")
        val turboPoints = sensorTrendDao.getTrendsForSensor("turbocharger").first()
        assertTrue(turboPoints.isEmpty())

        val egrPoints = sensorTrendDao.getTrendsForSensor("egr_valve").first()
        assertEquals(1, egrPoints.size)

        sensorTrendDao.clearAllTrends()
        val remainingEgr = sensorTrendDao.getTrendsForSensor("egr_valve").first()
        assertTrue(remainingEgr.isEmpty())
    }

    @Test
    fun testInsertAndObserveFaultHistory() = runBlocking {
        val fault1 = DiagnosticFaultHistoryEntity(
            dtcCode = "DF1012",
            title = "Błąd regulacji prędkości",
            system = "Wtrysk SID307",
            severity = "CRITICAL",
            timestamp = 5000L,
            status = "VERIFIED",
            source = "ECU SID307"
        )
        val fault2 = DiagnosticFaultHistoryEntity(
            dtcCode = "DF297",
            title = "Filtr cząstek stałych",
            system = "DPF",
            severity = "HIGH",
            timestamp = 6000L,
            status = "MEASURED",
            source = "OBD-II Mode 03"
        )

        val id1 = faultHistoryDao.insertFault(fault1)
        val id2 = faultHistoryDao.insertFault(fault2)
        assertTrue(id1 > 0)
        assertTrue(id2 > 0)

        val allFaults = faultHistoryDao.getAllFaultHistory().first()
        assertEquals(2, allFaults.size)
        // Ordered by timestamp DESC
        assertEquals("DF297", allFaults[0].dtcCode)
        assertEquals("DF1012", allFaults[1].dtcCode)

        // Delete single entry
        faultHistoryDao.deleteFaultById(id2)
        val afterDelete = faultHistoryDao.getAllFaultHistory().first()
        assertEquals(1, afterDelete.size)
        assertEquals("DF1012", afterDelete[0].dtcCode)

        // Clear all
        faultHistoryDao.clearAllFaultHistory()
        val afterClear = faultHistoryDao.getAllFaultHistory().first()
        assertTrue(afterClear.isEmpty())
    }
}
