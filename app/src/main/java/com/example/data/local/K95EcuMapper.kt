package com.example.data.local

import com.example.data.model.K95Ecu
import com.example.data.model.K95EcuStatus
import com.example.data.obd.ddt.DdtEcuDescriptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** Malformed persisted data is an error, never an empty or confirmed inventory. */
class K95EcuMapper {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val descriptors = moshi.adapter<List<DdtEcuDescriptor>>(
        Types.newParameterizedType(List::class.java, DdtEcuDescriptor::class.java)
    )
    private val identifiers = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )
    private val strings = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    fun toEntity(ecu: K95Ecu) = K95EcuEntity(
        id = ecu.id, commonName = ecu.commonName, status = ecu.status.name,
        diagnosticAddress = ecu.diagnosticAddress, responseAddress = ecu.responseAddress,
        canSpeed = ecu.canSpeed, lastSeen = ecu.lastSeen, physicalLocation = ecu.physicalLocation,
        notes = ecu.notes, ddtMatchesJson = descriptors.toJson(ecu.ddtMatches),
        identifiersJson = identifiers.toJson(ecu.identifiers),
        serviceMapNodeIdsJson = strings.toJson(ecu.serviceMapNodeIds), sourcesJson = strings.toJson(ecu.sources)
    )

    fun toDomain(entity: K95EcuEntity) = K95Ecu(
        id = entity.id, commonName = entity.commonName, status = K95EcuStatus.valueOf(entity.status),
        diagnosticAddress = entity.diagnosticAddress, responseAddress = entity.responseAddress,
        canSpeed = entity.canSpeed, lastSeen = entity.lastSeen, physicalLocation = entity.physicalLocation,
        notes = entity.notes, ddtMatches = requireNotNull(descriptors.fromJson(entity.ddtMatchesJson)),
        identifiers = requireNotNull(identifiers.fromJson(entity.identifiersJson)),
        serviceMapNodeIds = requireNotNull(strings.fromJson(entity.serviceMapNodeIdsJson)),
        sources = requireNotNull(strings.fromJson(entity.sourcesJson))
    )
}
