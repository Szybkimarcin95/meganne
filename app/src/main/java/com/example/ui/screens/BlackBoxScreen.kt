package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FuelRecord
import com.example.data.model.ServiceRecord
import com.example.ui.theme.AmberBose
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceVariant
import com.example.ui.theme.CyanHud
import com.example.ui.theme.DiagnosticGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningRed
import com.example.ui.viewmodel.OverlordViewModel
import com.example.util.DataExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BlackBoxScreen(
    viewModel: OverlordViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Dziennik Serwisowy, 1 = Tankowania, 2 = Telemetria

    val serviceRecords by viewModel.serviceRecords.collectAsStateWithLifecycle()
    val fuelRecords by viewModel.fuelRecords.collectAsStateWithLifecycle()
    val telemetryLogs by viewModel.telemetryLogs.collectAsStateWithLifecycle()
    val totalServiceCost by viewModel.totalServiceCost.collectAsStateWithLifecycle()
    val totalFuelCost by viewModel.totalFuelCost.collectAsStateWithLifecycle()

    var showAddServiceDialog by remember { mutableStateOf(false) }
    var showAddFuelDialog by remember { mutableStateOf(false) }
    var selectedRecordForDetails by remember { mutableStateOf<ServiceRecord?>(null) }

    // SAF Export Launchers
    val exportServiceCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            scope.launch {
                val ok = DataExporter.exportServiceHistoryToCsv(context, it, serviceRecords)
                Toast.makeText(context, if (ok) "Eksport serwisu CSV zakończony" else "Błąd eksportu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportFuelCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            scope.launch {
                val ok = DataExporter.exportFuelHistoryToCsv(context, it, fuelRecords)
                Toast.makeText(context, if (ok) "Eksport tankowań CSV zakończony" else "Błąd eksportu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportTelemetryCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            scope.launch {
                val ok = DataExporter.exportTelemetryToCsv(context, it, telemetryLogs)
                Toast.makeText(context, if (ok) "Eksport telemetrii CSV zakończony" else "Błąd eksportu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportTelemetryJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                val ok = DataExporter.exportTelemetryToJson(context, it, telemetryLogs)
                Toast.makeText(context, if (ok) "Eksport telemetrii JSON zakończony" else "Błąd eksportu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Sub-tabs
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = CockpitSurface,
                contentColor = CyanHud,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("Serwis", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("Paliwo", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    text = { Text("Telemetria (${telemetryLogs.size})", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedSubTab == 0) {
                // Service Log View
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Summary KPI Card & Export Button
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                            shape = RoundedCornerShape(14.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(AmberBose.copy(alpha = 0.4f), CockpitBorder))),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Column {
                                    Text("SUMA NAKŁADÓW SERWISOWYCH", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                                    Text(
                                        "%.2f PLN".format(totalServiceCost ?: 0.0),
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = AmberBose
                                        )
                                    )
                                }
                                Button(
                                    onClick = { exportServiceCsvLauncher.launch("megane_service_log.csv") },
                                    colors = ButtonDefaults.buttonColors(containerColor = CockpitSurfaceVariant, contentColor = CyanHud),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }


                    item {
                        Text(
                            "HISTORIA OBSŁUGI POJAZDU (ROOM DATABASE)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                        )
                    }

                    if (serviceRecords.isEmpty()) {
                        item {
                            Text(
                                "Brak wpisów serwisowych. Użyj przycisku '+' aby dodać pierwszą naprawę lub wymianę.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(serviceRecords) { record ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                                shape = RoundedCornerShape(14.dp),
                                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(CyanHud.copy(alpha = 0.2f), CockpitBorder))),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRecordForDetails = record }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(AmberBose.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                record.category,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = AmberBose,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }

                                        Text(
                                            "%.2f PLN".format(record.costPln),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = DiagnosticGreen
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        record.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                    )

                                    if (record.partsUsed.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "Części: ${record.partsUsed}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp),
                                            maxLines = 2
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "${record.mileageKm} km • ${record.dateStr}",
                                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                                        )

                                        IconButton(
                                            onClick = { viewModel.deleteServiceEntry(record) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = TextMuted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            } else if (selectedSubTab == 1) {
                // Fuel Log View
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                            shape = RoundedCornerShape(14.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(CyanHud.copy(alpha = 0.4f), CockpitBorder))),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Column {
                                    Text("ŁĄCZNE WYDATKI NA PALIWO", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                                    Text(
                                        "%.2f PLN".format(totalFuelCost ?: 0.0),
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = CyanHud
                                        )
                                    )
                                }
                                Button(
                                    onClick = { exportFuelCsvLauncher.launch("megane_fuel_log.csv") },
                                    colors = ButtonDefaults.buttonColors(containerColor = CockpitSurfaceVariant, contentColor = CyanHud),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "HISTORIA TANKOWAŃ DIESEL (1.5 dCi)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                        )
                    }

                    items(fuelRecords) { fuel ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyanHud.copy(alpha = 0.15f))
                                ) {
                                    Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = CyanHud)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${fuel.liters} L • %.2f PLN".format(fuel.costPln),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                    )
                                    Text(
                                        "${fuel.station} • %.2f zł/L".format(fuel.pricePerLiter),
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                                    )
                                    Text(
                                        "Przebieg: ${fuel.mileageKm} km • ${fuel.dateStr}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteFuelEntry(fuel) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            } else {
                // Telemetry Log View (Sub-tab 2)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                            shape = RoundedCornerShape(14.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(CyanHud.copy(alpha = 0.4f), CockpitBorder))),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Column {
                                    Text("BUFOR PRÓBEK TELEMETRII", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                                    Text(
                                        "${telemetryLogs.size} próbek",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = CyanHud
                                        )
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { exportTelemetryCsvLauncher.launch("megane_telemetry.csv") },
                                        colors = ButtonDefaults.buttonColors(containerColor = CockpitSurfaceVariant, contentColor = CyanHud),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { exportTelemetryJsonLauncher.launch("megane_telemetry.json") },
                                        colors = ButtonDefaults.buttonColors(containerColor = CockpitSurfaceVariant, contentColor = AmberBose),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "OSTATNIE PRÓBKI PID (OBD / CAN)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                        )
                    }

                    if (telemetryLogs.isEmpty()) {
                        item {
                            Text(
                                "Brak próbek w bazie. Przejdź do zakładki Wyrocznia i włącz zbieranie telemetrii.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(telemetryLogs.take(50)) { log ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Column {
                                        Text(log.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                                        Text(
                                            "PID: ${log.pid} • Źródło: ${log.source}",
                                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
                                        )
                                    }
                                    Text(
                                        "%.2f %s".format(log.value, log.unit),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = CyanHud
                                        )
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // Floating Action Button to Add Entry (Only for Service and Fuel tabs)
        if (selectedSubTab != 2) {
            FloatingActionButton(
                onClick = {
                    if (selectedSubTab == 0) showAddServiceDialog = true else showAddFuelDialog = true
                },
                containerColor = CyanHud,
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Dodaj wpis")
            }
        }

    }

    // Add Service Record Dialog
    if (showAddServiceDialog) {
        AddServiceDialog(
            onDismiss = { showAddServiceDialog = false },
            onSave = { title, category, km, date, cost, parts, invoice, notes ->
                viewModel.addServiceEntry(title, category, km, date, cost, parts, invoice, notes)
                showAddServiceDialog = false
            }
        )
    }

    // Add Fuel Dialog
    if (showAddFuelDialog) {
        AddFuelDialog(
            onDismiss = { showAddFuelDialog = false },
            onSave = { date, km, liters, cost, station ->
                viewModel.addFuelEntry(date, km, liters, cost, station)
                showAddFuelDialog = false
            }
        )
    }

    // Detailed Service Record Dialog
    selectedRecordForDetails?.let { record ->
        AlertDialog(
            onDismissRequest = { selectedRecordForDetails = null },
            containerColor = CockpitSurfaceVariant,
            title = {
                Column {
                    Text(record.category.uppercase(), style = MaterialTheme.typography.labelSmall.copy(color = AmberBose, fontWeight = FontWeight.Bold))
                    Text(record.title, style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Przebieg: ${record.mileageKm} km", color = CyanHud, fontWeight = FontWeight.Bold)
                    Text("Data: ${record.dateStr} • Koszt: %.2f PLN".format(record.costPln), color = TextPrimary)
                    if (record.partsUsed.isNotBlank()) {
                        Text("Zastosowane części i płyny:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(record.partsUsed, color = TextPrimary, fontSize = 12.sp)
                    }
                    if (record.invoiceNumber.isNotBlank()) {
                        Text("Dokument zakupu: ${record.invoiceNumber}", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    if (record.notes.isNotBlank()) {
                        Text("Notatki serwisowe:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(record.notes, color = TextPrimary, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedRecordForDetails = null }) {
                    Text("Zamknij", color = CyanHud, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun AddServiceDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, category: String, mileageKm: Int, dateStr: String, costPln: Double, partsUsed: String, invoiceNumber: String, notes: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Olej RN0720") }
    var mileageStr by remember { mutableStateOf("179420") }
    var dateStr by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var costStr by remember { mutableStateOf("") }
    var partsUsed by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val categories = listOf("Olej RN0720", "Rozrząd", "Filtr paliwa", "Hamulce", "Zawieszenie", "Inne")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CockpitSurfaceVariant,
        title = { Text("Nowy wpis serwisowy", color = CyanHud, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Tytuł naprawy / serwisu") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("Kategoria:", fontSize = 11.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        categories.take(3).forEach { cat ->
                            Button(
                                onClick = { category = cat },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (category == cat) AmberBose else CockpitSurface,
                                    contentColor = if (category == cat) Color.Black else TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(cat, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = mileageStr,
                            onValueChange = { mileageStr = it },
                            label = { Text("Przebieg km") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = costStr,
                            onValueChange = { costStr = it },
                            label = { Text("Koszt PLN") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = partsUsed,
                        onValueChange = { partsUsed = it },
                        label = { Text("Użyte części (np. Elf 5W30, Purflux...)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = invoiceNumber,
                        onValueChange = { invoiceNumber = it },
                        label = { Text("Nr faktury / paragonu") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Uwagi i zalecenia") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val km = mileageStr.toIntOrNull() ?: 179420
                    val cost = costStr.toDoubleOrNull() ?: 0.0
                    onSave(title.ifBlank { "Serwis" }, category, km, dateStr, cost, partsUsed, invoiceNumber, notes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanHud, contentColor = Color.Black)
            ) {
                Text("Zapisz w Czarnej Skrzynce")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj", color = TextSecondary) }
        }
    )
}

@Composable
fun AddFuelDialog(
    onDismiss: () -> Unit,
    onSave: (dateStr: String, mileageKm: Int, liters: Double, costPln: Double, station: String) -> Unit
) {
    var mileageStr by remember { mutableStateOf("179420") }
    var litersStr by remember { mutableStateOf("50.0") }
    var costStr by remember { mutableStateOf("345.0") }
    var station by remember { mutableStateOf("Orlen Verva Diesel") }
    var dateStr by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CockpitSurfaceVariant,
        title = { Text("Nowe tankowanie ON", color = CyanHud, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = litersStr,
                        onValueChange = { litersStr = it },
                        label = { Text("Litry (L)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it },
                        label = { Text("Kwota PLN") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = mileageStr,
                    onValueChange = { mileageStr = it },
                    label = { Text("Aktualny przebieg (km)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = station,
                    onValueChange = { station = it },
                    label = { Text("Stacja paliw") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val km = mileageStr.toIntOrNull() ?: 179420
                    val lit = litersStr.toDoubleOrNull() ?: 0.0
                    val cost = costStr.toDoubleOrNull() ?: 0.0
                    onSave(dateStr, km, lit, cost, station)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanHud, contentColor = Color.Black)
            ) {
                Text("Zapisz tankowanie")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj", color = TextSecondary) }
        }
    )
}
