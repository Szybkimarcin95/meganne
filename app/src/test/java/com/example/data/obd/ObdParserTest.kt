package com.example.data.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdParserTest {

    @Test
    fun testCleanResponse() {
        val raw = "41 0C 1A F8\r\n>"
        val clean = ObdParser.cleanResponse(raw)
        assertEquals("410C1AF8", clean)
    }

    @Test
    fun testIsErrorOrNoData() {
        assertTrue(ObdParser.isErrorOrNoData("NO DATA"))
        assertTrue(ObdParser.isErrorOrNoData("CAN ERROR"))
        assertTrue(ObdParser.isErrorOrNoData("UNABLE TO CONNECT"))
        assertTrue(ObdParser.isErrorOrNoData("   "))
    }

    @Test
    fun testParseRpm() {
        // ((0x1A * 256) + 0xF8) / 4 = ((26 * 256) + 248) / 4 = 6904 / 4 = 1726.0 RPM
        val raw = "41 0C 1A F8>"
        val res = ObdParser.parseMode01("010C", raw)
        assertNotNull(res)
        assertEquals(1726.0, res!!.value, 0.01)
        assertEquals("obr/min", res.unit)
    }

    @Test
    fun testParseSpeed() {
        // 0x5A = 90 km/h
        val raw = "41 0D 5A"
        val res = ObdParser.parseMode01("010D", raw)
        assertNotNull(res)
        assertEquals(90.0, res!!.value, 0.01)
        assertEquals("km/h", res.unit)
    }

    @Test
    fun testParseCoolantTemp() {
        // 0x78 = 120 -> 120 - 40 = 80 °C
        val raw = "41 05 78\r"
        val res = ObdParser.parseMode01("0105", raw)
        assertNotNull(res)
        assertEquals(80.0, res!!.value, 0.01)
        assertEquals("°C", res.unit)
    }

    @Test
    fun testParseDtcResponse() {
        // 43 01 04 20 00 00 -> 0x0104 = P0104, 0x2000 = P2000
        val raw = "43 01 04 20 00 00 00>"
        val codes = ObdParser.parseDtcResponse(raw, "43")
        assertEquals(2, codes.size)
        assertEquals("P0104", codes[0])
        assertEquals("P2000", codes[1])
    }

    @Test
    fun testParseDtcEmptyOrNoData() {
        val raw = "NO DATA"
        val codes = ObdParser.parseDtcResponse(raw, "43")
        assertTrue(codes.isEmpty())
    }

    @Test
    fun testParseMode07PendingDtc() {
        // Mode 07 (prefix 47): 47 01 01 03 80 -> P0101, P0380
        val raw = "47 01 01 03 80 00 00>"
        val codes = ObdParser.parseDtcResponse(raw, "47")
        assertEquals(2, codes.size)
        assertEquals("P0101", codes[0])
        assertEquals("P0380", codes[1])
    }

    @Test
    fun testParseMafSensor() {
        // ((0x04 * 256) + 0x50) / 100 = ((1024) + 80) / 100 = 1104 / 100 = 11.04 g/s
        val raw = "41 10 04 50\r\n>"
        val res = ObdParser.parseMode01("0110", raw)
        assertNotNull(res)
        assertEquals(11.04, res!!.value, 0.01)
        assertEquals("g/s", res.unit)
    }

    @Test
    fun testParseEngineLoad() {
        // (0x80 * 100) / 255 = (128 * 100) / 255 = 50.2%
        val raw = "41 04 80>"
        val res = ObdParser.parseMode01("0104", raw)
        assertNotNull(res)
        assertEquals(50.2, res!!.value, 0.1)
        assertEquals("%", res.unit)
    }

    @Test
    fun testParseMapSensor() {
        // 0x64 = 100 kPa
        val raw = "41 0B 64\r>"
        val res = ObdParser.parseMode01("010B", raw)
        assertNotNull(res)
        assertEquals(100.0, res!!.value, 0.01)
        assertEquals("kPa", res.unit)
    }

    @Test
    fun testEchoAndPromptStripping() {
        val rawWithEcho = "010C\r41 0C 1F 40\r\n>"
        val res = ObdParser.parseMode01("010C", rawWithEcho)
        assertNotNull(res)
        // (31 * 256 + 64) / 4 = 8000 / 4 = 2000 RPM
        assertEquals(2000.0, res!!.value, 0.01)
    }

    @Test
    fun testMalformedOrTimeoutResponse() {
        val timeoutResp = "ERROR: TIMEOUT"
        assertNull(ObdParser.parseMode01("010C", timeoutResp))
        val errorResp = "CAN ERROR"
        assertNull(ObdParser.parseMode01("010C", errorResp))
    }
}
