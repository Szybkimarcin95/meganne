package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class K95ServiceMap(
    val vehicle: String,
    val verificationStatus: String,
    val components: List<K95ServiceComponent>,
    val circuits: List<K95ServiceCircuit>,
    val connectors: List<K95ServiceConnector>
)

@JsonClass(generateAdapter = true)
data class K95ServiceComponent(
    val id: String,
    val name: String,
    val location: String,
    val verificationStatus: String,
    val notes: String
)

@JsonClass(generateAdapter = true)
data class K95ServiceCircuit(
    val code: String,
    val from: String,
    val fromPin: String,
    val to: String,
    val toPin: String,
    val gaugeMm2: Double,
    val polarity: String,
    val verificationStatus: String
)

@JsonClass(generateAdapter = true)
data class K95ServiceConnector(
    val id: String,
    val name: String,
    val pins: List<String>,
    val verificationStatus: String,
    val notes: String
)
