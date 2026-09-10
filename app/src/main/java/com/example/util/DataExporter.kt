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

    suspend fun exportTelemetryToCsv(context: Context, uri: Uri, logs: List<TelemetryLog>): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.append("ID,Timestamp,PID,Nazwa,Wartość,Jednostka,Źródło\n")
                    for (log in logs) {
                        writer.append("${log.id},${log.timestamp},${log.pid},\"${log.name}\",${log.value},${log.unit},${log.source}\n")
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
                    put("id", log.id)
                    put("timestamp", log.timestamp)
                    put("pid", log.pid)
                    put("name", log.name)
                    put("value", log.value)
                    put("unit", log.unit)
                    put("source", log.source)
                }
                jsonArray.put(obj)
            }
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
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
                OutputStreamWriter(os).use { writer ->
                    writer.append("ID,Tytuł,Kategoria,Przebieg_km,Data,Koszt_PLN,Części,Faktura,Uwagi,Status\n")
                    for (r in records) {
                        writer.append("${r.id},\"${r.title}\",\"${r.category}\",${r.mileageKm},${r.dateStr},${r.costPln},\"${r.partsUsed}\",\"${r.invoiceNumber}\",\"${r.notes}\",${r.verification}\n")
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
                OutputStreamWriter(os).use { writer ->
                    writer.append("ID,Data,Przebieg_km,Litry,Koszt_PLN,Cena_za_litr,Stacja,Pełny_bak,Status\n")
                    for (r in records) {
                        writer.append("${r.id},${r.dateStr},${r.mileageKm},${r.liters},${r.costPln},${r.pricePerLiter},\"${r.station}\",${r.isFullTank},${r.verification}\n")
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
