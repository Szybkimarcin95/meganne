package com.example.data.obd.ddt

/**
 * Vehicle-specific SID307 target established from the user's physical ECU identification.
 *
 * This profile deliberately contains no VIN and no write/security material.
 * It is used only to match a DDT ECU definition before exposing database-derived reads.
 */
object MeganeSid307Target {
    const val expectedDefinitionFile =
        "SID307_00F7_550_V05_20130313T104520.json"

    val physicalIdentity = PhysicalEcuIdentity(
        diagnosticVersion = "129",
        supplier = "4BE",
        software = "00F7",
        version = "5500",
        receiveCanId = "7E8",
        protocol = "CAN",
        baudRate = 500000
    )
}
