package com.example.data.model

import com.example.data.obd.ddt.DdtEcuDescriptor

enum class K95EcuStatus {
    CONFIRMED_IN_CAR, PHYSICAL_MATCH, DATABASE_CANDIDATE, NOT_SEEN, UNVERIFIED
}

/** Database metadata never proves that a controller is installed or responding. */
data class K95Ecu(
    val id: String,
    val commonName: String,
    val status: K95EcuStatus,
    val diagnosticAddress: String? = null,
    val responseAddress: String? = null,
    val canSpeed: Int? = null,
    val ddtMatches: List<DdtEcuDescriptor> = emptyList(),
    val lastSeen: Long? = null,
    val identifiers: Map<String, String> = emptyMap(),
    val serviceMapNodeIds: List<String> = emptyList(),
    val physicalLocation: String? = null,
    val sources: List<String> = emptyList(),
    val notes: String = ""
)

object K95InventoryCatalog {
    /** Caller supplies a vehicle-specific subset, not the entire DDT database. */
    fun fromDdt(descriptors: List<DdtEcuDescriptor>): List<K95Ecu> =
        descriptors.distinctBy { it.sourceFile }.map { descriptor ->
            K95Ecu(
                id = "DDT:${descriptor.sourceFile}",
                commonName = descriptor.ecuName ?: descriptor.sourceFile,
                status = K95EcuStatus.DATABASE_CANDIDATE,
                diagnosticAddress = descriptor.sendId,
                responseAddress = descriptor.receiveId,
                canSpeed = descriptor.baudRate,
                ddtMatches = listOf(descriptor),
                sources = listOf(descriptor.sourceFile),
                notes = "DDT_DATABASE • VEHICLE MATCH UNCONFIRMED"
            )
        }

    fun fromServiceMap(map: K95ServiceMap?): List<K95Ecu> =
        map?.components.orEmpty().filter { it.id == "1917" }.map {
            K95Ecu(
                id = "SERVICE_MAP:${it.id}", commonName = it.name,
                status = K95EcuStatus.UNVERIFIED,
                serviceMapNodeIds = listOf(it.id), physicalLocation = it.location,
                sources = listOf("service-map/k95-service-map.json"),
                notes = "${it.verificationStatus} • Adres diagnostyczny i obecność na magistrali niepotwierdzone. ${it.notes}"
            )
        }
}
