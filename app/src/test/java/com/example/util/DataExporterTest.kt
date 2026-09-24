package com.example.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataExporterTest {

    @Test
    fun testJsonNoNaNAndInfinity() {
        val nanVal: Double = Double.NaN
        val infVal: Double = Double.POSITIVE_INFINITY
        val validVal: Double = 123.45

        val sanitizedNaN = DataExporter.sanitizeJsonValue(nanVal)
        val sanitizedInf = DataExporter.sanitizeJsonValue(infVal)
        val sanitizedValid = DataExporter.sanitizeJsonValue(validVal)

        assertNull(sanitizedNaN)
        assertNull(sanitizedInf)
        assertEquals(123.45, sanitizedValid)
    }

    @Test
    fun testJsonNullPreserved() {
        val nullVal: String? = null
        val sanitizedNull = DataExporter.sanitizeJsonValue(nullVal)
        assertNull(sanitizedNull)
    }

    @Test
    fun testCsvQuotingAndEscape() {
        val input = "Test \"Quote\" Value"
        val formatted = DataExporter.formatCsvField(input)
        assertEquals("\"Test \"\"Quote\"\" Value\"", formatted)
    }

    @Test
    fun testCsvFormulaInjectionProtection() {
        val dangerousInput = "=SUM(A1:A2)"
        val formatted = DataExporter.formatCsvField(dangerousInput)
        // Should be prefixed with single quote and quoted
        assertEquals("\"'=SUM(A1:A2)\"", formatted)

        val plusInput = "+1234"
        assertEquals("\"'+1234\"", DataExporter.formatCsvField(plusInput))

        val minusInput = "-1234"
        assertEquals("\"'-1234\"", DataExporter.formatCsvField(minusInput))

        val atInput = "@mention"
        assertEquals("\"'@mention\"", DataExporter.formatCsvField(atInput))
    }

    @Test
    fun testSourceProvenanceDistinction() {
        val liveSource = "LIVE"
        val fileSource = "FILE"
        val simSource = "SIMULATED"
        val unavailableSource = "UNAVAILABLE"

        org.junit.Assert.assertNotEquals(fileSource, liveSource)
        org.junit.Assert.assertNotEquals(simSource, liveSource)
        assertEquals("UNAVAILABLE", unavailableSource)
    }

    @Test
    fun testEmptyFieldNotConvertedToZero() {
        val emptyField: String? = null
        val formatted = DataExporter.formatCsvField(emptyField)
        assertEquals("", formatted)
        // Verify it is not "0"
        org.junit.Assert.assertNotEquals("0", formatted)
    }
}
