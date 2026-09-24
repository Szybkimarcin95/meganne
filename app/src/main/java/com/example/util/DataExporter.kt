package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.model.FuelRecord
import com.example.data.model.ServiceRecord
import com.example.data.model.TelemetryLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter

object DataExporter {

    fun sanitizeJsonValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Double -> if (value.isNaN() || value.isInfinite()) null else value
            is Float -> if (value.isNaN() || value.isInfinite()) null else value.toDouble()
            else -> value
        }
    }

    fun formatCsvField(value: Any?): String {
        if (value == null) return ""
        val str = when (value) {
            is Double -> if (value.isNaN() || value.isInfinite()) return "" else value.toString()
            is Float -> if (value.isNaN() || value.isInfinite()) return "" else value.toString()
            else -> value.toString()
        }
        if (str.isEmpty()) return ""
        // Formula injection protection: =, +, -, @
        val guarded = if (str.startsWith("=") || str.startsWith("+") || str.startsWith("-") || str.startsWith("@")) {
            "'$str"
        } else {
            str
        }
        val escaped = guarded.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    suspend fun exportTelemetryToCsv(context: Context, uri: Uri, logs: List<TelemetryLog>): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.append("ID,Timestamp,PID,Nazwa,Wartość,Jednostka,Źródło\n")
                    for (log in logs) {
                        val idStr = formatCsvField(log.id)
                        val tsStr = formatCsvField(log.timestamp)
                        val pidStr = formatCsvField(log.pid)
                        val nameStr = formatCsvField(log.name)
                        val valStr = formatCsvField(log.value)
                        val unitStr = formatCsvField(log.unit)
                        val srcStr = formatCsvField(log.source)
                        writer.append("$idStr,$tsStr,$pidStr,$nameStr,$valStr,$unitStr,$srcStr\n")
                    }
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun exportTelemetryToJson(context: Context, uri: Uri, logs: List<TelemetryLog>): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            for (log in logs) {
                val obj = JSONObject().apply {
                    put("id", sanitizeJsonValue(log.id))
                    put("timestamp", sanitizeJsonValue(log.timestamp))
                    put("pid", sanitizeJsonValue(log.pid))
                    put("name", sanitizeJsonValue(log.name))
                    put("value", sanitizeJsonValue(log.value))
                    put("unit", sanitizeJsonValue(log.unit))
                    put("source", sanitizeJsonValue(log.source))
                }
                jsonArray.put(obj)
            }
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.write(jsonArray.toString(2))
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun exportServiceHistoryToCsv(context: Context, uri: Uri, records: List<ServiceRecord>): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.append("ID,Tytuł,Kategoria,Przebieg_km,Data,Koszt_PLN,Części,Faktura,Uwagi,Status\n")
                    for (r in records) {
                        writer.append("${formatCsvField(r.id)},${formatCsvField(r.title)},${formatCsvField(r.category)},${formatCsvField(r.mileageKm)},${formatCsvField(r.dateStr)},${formatCsvField(r.costPln)},${formatCsvField(r.partsUsed)},${formatCsvField(r.invoiceNumber)},${formatCsvField(r.notes)},${formatCsvField(r.verification)}\n")
                    }
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun exportFuelHistoryToCsv(context: Context, uri: Uri, records: List<FuelRecord>): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.append("ID,Data,Przebieg_km,Litry,Koszt_PLN,Cena_za_litr,Stacja,Pełny_bak,Status\n")
                    for (r in records) {
                        writer.append("${formatCsvField(r.id)},${formatCsvField(r.dateStr)},${formatCsvField(r.mileageKm)},${formatCsvField(r.liters)},${formatCsvField(r.costPln)},${formatCsvField(r.pricePerLiter)},${formatCsvField(r.station)},${formatCsvField(r.isFullTank)},${formatCsvField(r.verification)}\n")
                    }
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
