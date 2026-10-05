package com.example.ui.eculab

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter

suspend fun exportEcuLabResultsToJson(
    context: Context,
    uri: Uri,
    state: EcuLabUiState
): Boolean = withContext(Dispatchers.IO) {
    try {
        val counters = state.counters
        val root = JSONObject().apply {
            put("schema", "ecu-lab-ui-v1")
            put("totalRequests", state.totalRequests)
            put("completed", state.completedCount)
            put("progress", state.progressFraction.toDouble())
            put("summary", JSONObject().apply {
                put("SUPPORTED", counters.supported)
                put("NRC", counters.nrc)
                put("TIMEOUT", counters.timeout)
                put("SESSION_REQUIRED", counters.sessionRequired)
                put("NOT_TESTED", counters.notTested)
                put("CANCELLED", counters.cancelled)
            })
            put("results", JSONArray().apply {
                state.results.forEach { result ->
                    put(JSONObject().apply {
                        put("requestHex", result.requestHex)
                        put("name", result.name)
                        put("service", "0x${result.service.toString(16).uppercase()}")
                        put("status", result.status.name)
                        put("nrc", result.nrc ?: JSONObject.NULL)
                        put("nrcMeaning", result.nrcMeaning ?: JSONObject.NULL)
                        put("raw", result.raw ?: JSONObject.NULL)
                        put("physicalValue", result.physicalValue ?: JSONObject.NULL)
                        put("unit", result.unit ?: JSONObject.NULL)
                        put("sourceFile", result.sourceFile)
                        put("sourceLine", result.sourceLine)
                        put("requiredSession", result.requiredSession ?: JSONObject.NULL)
                    })
                }
            })
        }

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                writer.write(root.toString(2))
                writer.flush()
            }
        } ?: return@withContext false

        true
    } catch (_: Exception) {
        false
    }
}
