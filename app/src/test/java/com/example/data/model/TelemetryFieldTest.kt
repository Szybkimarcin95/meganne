package com.example.data.model

import com.example.data.obd.DataVerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryFieldTest {

    @Test
    fun unavailableFieldHasNoValueOrTimestamp() {
        val field = TelemetryField.unavailable<Float>("not read in this session")

        assertNull(field.value)
        assertNull(field.timestamp)
        assertFalse(field.isAvailable)
        assertEquals(DataVerificationStatus.UNVERIFIED, field.source)
        assertEquals(TelemetryAvailability.UNAVAILABLE, field.availability)
    }

    @Test
    fun measuredFieldCarriesIndependentProvenance() {
        val field = TelemetryField.measured(2150, timestamp = 1234L)

        assertEquals(2150, field.value)
        assertEquals(1234L, field.timestamp)
        assertTrue(field.isAvailable)
        assertEquals(DataVerificationStatus.MEASURED, field.source)
    }

    @Test
    fun simulatedFieldRemainsExplicitlySimulated() {
        val field = TelemetryField.simulated(14.2f, timestamp = 5678L)

        assertEquals(14.2f, field.value)
        assertEquals(5678L, field.timestamp)
        assertTrue(field.isAvailable)
        assertEquals(DataVerificationStatus.SIMULATED, field.source)
    }

    @Test
    fun physicalSessionDefaultsContainNoSyntheticSensorValues() {
        val telemetry = LiveTelemetry(isConnected = true, isSimulated = false)

        assertFalse(telemetry.rpm.isAvailable)
        assertFalse(telemetry.boostBar.isAvailable)
        assertFalse(telemetry.railPressureBar.isAvailable)
        assertFalse(telemetry.dpfSootGrams.isAvailable)
        assertFalse(telemetry.batteryVoltage.isAvailable)
        assertFalse(telemetry.injector1Correction.isAvailable)
        assertEquals(DataVerificationStatus.UNVERIFIED, telemetry.rpm.source)
        assertEquals(DataVerificationStatus.UNVERIFIED, telemetry.dpfSootGrams.source)
    }
}
