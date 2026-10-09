package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "k95_ecu")
data class K95EcuEntity(
    @PrimaryKey val id: String,
    val commonName: String,
    val status: String,
    val diagnosticAddress: String?,
    val responseAddress: String?,
    val canSpeed: Int?,
    val lastSeen: Long?,
    val physicalLocation: String?,
    val notes: String,
    val ddtMatchesJson: String,
    val identifiersJson: String,
    val serviceMapNodeIdsJson: String,
    val sourcesJson: String
)
