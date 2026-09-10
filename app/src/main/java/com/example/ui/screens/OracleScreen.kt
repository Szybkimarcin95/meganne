package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DiagnosticFaultHistoryEntry
import com.example.data.model.DtcCode
import com.example.ui.components.BluetoothConnectionIndicator
import com.example.ui.components.CockpitGauge
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OracleScreen(
    viewModel: OverlordViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Live Telemetry, 1 = DTC Kody

    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()

    val activeDtc by viewModel.activeDtcCodes.collectAsStateWithLifecycle()
    val faultHistory by viewModel.diagnosticFaultHistory.collectAsStateWithLifecycle()
    val filteredDtc by viewModel.filteredDtcDatabase.collectAsStateWithLifecycle()
    val dtcSearch by viewModel.dtcSearchQuery.collectAsStateWithLifecycle()
    val selectedDtc by viewModel.selectedDtc.collectAsStateWithLifecycle()
    val clearSuccess by viewModel.clearDtcSuccess.collectAsStateWithLifecycle()

    var showBluetoothDialog by remember { mutableStateOf(false) }
    var showConfirmClearDtc by remember { mutableStateOf(false) }
    var showConfirmClearFaultHistory by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Bluetooth Connection Indicator with quick actions
        BluetoothConnectionIndicator(
            connectionState = connectionState,
            connectionStatusText = connectionStatus,
            isSimulated = telemetry.isSimulated,
            onClick = { showBluetoothDialog = true }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Controls Bar for Bluetooth / Demo mode
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Text(
                text = if (telemetry.isConnected) "Transmisja aktywna • Mode 01 CAN" else "Wybierz urządzenie aby nawiązać sesję",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { showBluetoothDialog = true },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = CyanHud, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Zmień ELM327", fontSize = 11.sp, color = CyanHud, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = { viewModel.enableSimulation() },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AmberBose, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Symulacja", fontSize = 11.sp, color = AmberBose, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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
                text = { Text("Telemetria Live (Mode 01)", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Diagnostyka DTC", fontWeight = FontWeight.Bold)
                        if (activeDtc.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(WarningRed)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text("${activeDtc.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedSubTab == 0) {
            // Live Telemetry Tab
            val isLogging by viewModel.isLogging.collectAsStateWithLifecycle()
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Telemetry Source Indicator & Logger Control
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (telemetry.isSimulated) AmberBose.copy(alpha = 0.12f) else DiagnosticGreen.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (telemetry.isSimulated) "STATUS ŹRÓDŁA: SIMULATION / DEMO (K9K 636)" else "STATUS ŹRÓDŁA: FIZYCZNE OBD-II (MEASURED)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (telemetry.isSimulated) AmberBose else DiagnosticGreen,
                                        fontWeight = FontWeight.Black
                                    )
                                )
                                Text(
                                    text = if (telemetry.isSimulated) "Dane syntetyczne generowane do testów aplikacji" else "Dane na żywo z magistrali CAN / adaptera ELM327",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp)
                                )
                            }
                            Button(
                                onClick = { viewModel.toggleTelemetryLogging() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLogging) WarningRed else CyanHud,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isLogging) "STOP LOG" else "START LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CockpitGauge(
                            value = telemetry.rpm.toFloat(),
                            minValue = 0f,
                            maxValue = 5000f,
                            title = "Obroty (010C)",
                            unit = "RPM",
                            gaugeColor = CyanHud,
                            warningThreshold = 4400f,
                            modifier = Modifier.weight(1f)
                        )
                        CockpitGauge(
                            value = telemetry.speedKmH.toFloat(),
                            minValue = 0f,
                            maxValue = 220f,
                            title = "Prędkość (010D)",
                            unit = "KM/H",
                            gaugeColor = CyanHud,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CockpitGauge(
                            value = telemetry.coolantTempC.toFloat(),
                            minValue = 40f,
                            maxValue = 120f,
                            title = "Ciecz (0105)",
                            unit = "°C",
                            gaugeColor = if (telemetry.coolantTempC > 100) WarningRed else CyanHud,
                            warningThreshold = 102f,
                            modifier = Modifier.weight(1f)
                        )
                        CockpitGauge(
                            value = telemetry.intakeAirTempC.toFloat(),
                            minValue = -10f,
                            maxValue = 70f,
                            title = "Dolot (010F)",
                            unit = "°C",
                            gaugeColor = AmberBose,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Mode 01 Standard PIDs Details Table
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(CyanHud.copy(alpha = 0.4f), CockpitBorder))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("STANDARDOWE PARAMETRY OBD-II (MODE 01)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = CyanHud))
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("0110: Przepływomierz MAF", color = TextSecondary, fontSize = 12.sp)
                                Text("${telemetry.mafAirFlowGps} g/s", color = TextPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("0104: Obciążenie silnika (Load)", color = TextSecondary, fontSize = 12.sp)
                                Text("${telemetry.engineLoadPercent} %", color = TextPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("0111: Przepustnica (Throttle)", color = TextSecondary, fontSize = 12.sp)
                                Text("${telemetry.throttlePercent} %", color = TextPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("010B: Ciśnienie dolotu (MAP)", color = TextSecondary, fontSize = 12.sp)
                                Text("${telemetry.mapPressureKpa} kPa", color = TextPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Napięcie akumulatora (ELM327 ATRV)", color = TextSecondary, fontSize = 12.sp)
                                Text("${telemetry.batteryVoltage} V", color = DiagnosticGreen, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                    }
                }


                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CockpitGauge(
                            value = telemetry.boostBar,
                            minValue = 0.0f,
                            maxValue = 2.0f,
                            title = "Doładowanie Turbo",
                            unit = "BAR",
                            gaugeColor = AmberBose,
                            warningThreshold = 1.45f,
                            modifier = Modifier.weight(1f)
                        )
                        CockpitGauge(
                            value = telemetry.railPressureBar.toFloat(),
                            minValue = 200f,
                            maxValue = 1800f,
                            title = "Ciśnienie CR",
                            unit = "BAR",
                            gaugeColor = DiagnosticGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // DPF Soot Level Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(AmberBose.copy(alpha = 0.4f), CockpitBorder))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("MASA SADZY W FILTRZE DPF", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextSecondary))
                                Text(
                                    "${telemetry.dpfSootGrams}g / 45g (Max)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (telemetry.dpfSootGrams > 20f) WarningRed else AmberBose
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (telemetry.dpfSootGrams / 45f).coerceIn(0f, 1f) },
                                color = if (telemetry.dpfSootGrams > 20f) WarningRed else AmberBose,
                                trackColor = Color(0xFF131F33),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (telemetry.isRegeneratingDpf) "TRWA REGENERACJA AKTYWNA" else "Stan: Pasywny (Regeneracja nieaktywna)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (telemetry.isRegeneratingDpf) DiagnosticGreen else TextMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "Rozcieńczenie oleju: ${telemetry.oilDilutionPercent}%",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                )
                            }
                        }
                    }
                }

                // Injector Corrections Grid
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("KOREKTY WTRYSKIWACZY CONTINENTAL (mg/skok)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = CyanHud))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("CYL 1", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                                    Text(
                                        "%.2f".format(telemetry.injector1Correction),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("CYL 2", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                                    Text(
                                        "%.2f".format(telemetry.injector2Correction),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("CYL 3", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                                    Text(
                                        "%.2f".format(telemetry.injector3Correction),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("CYL 4", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                                    Text(
                                        "%.2f".format(telemetry.injector4Correction),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        } else {
            // DTC Diagnostics Tab
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Success banner if DTC cleared
                clearSuccess?.let { msg ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DiagnosticGreen.copy(alpha = 0.15f)),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(DiagnosticGreen, Color(0xFF006622)))),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = DiagnosticGreen)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(msg, color = DiagnosticGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.dismissClearMessage() }) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = DiagnosticGreen)
                                }
                            }
                        }
                    }
                }

                // Active DTC header & Clear button
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (activeDtc.isEmpty()) "BRAK AKTYWNYCH KODÓW DTC" else "AKTYWNE BŁĘDY W PAMIĘCI (${activeDtc.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (activeDtc.isEmpty()) DiagnosticGreen else WarningRed,
                                letterSpacing = 1.sp
                            )
                        )

                        if (activeDtc.isNotEmpty()) {
                            Button(
                                onClick = { showConfirmClearDtc = true },
                                colors = ButtonDefaults.buttonColors(containerColor = WarningRed, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Wyczyść DTC", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Active DTC list
                if (activeDtc.isNotEmpty()) {
                    items(activeDtc) { dtc ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                            shape = RoundedCornerShape(14.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(WarningRed, CockpitBorder))),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectDtc(dtc) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        dtc.code,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = WarningRed
                                        )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(WarningRed.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            dtc.severity.label,
                                            style = MaterialTheme.typography.labelSmall.copy(color = WarningRed, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(dtc.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                                Text(dtc.system, style = MaterialTheme.typography.bodySmall.copy(color = CyanHud, fontSize = 11.sp))
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(10.dp)) }

                // Local Fault History (Room Database Audit Log)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(CyanHud.copy(alpha = 0.45f), CockpitBorder))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "LOKALNA HISTORIA WYKRYTYCH FAULTÓW (ROOM)",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = CyanHud,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Text(
                                    "${faultHistory.size} zarejestrowanych wpisów w trwałej pamięci",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            if (faultHistory.isNotEmpty()) {
                                TextButton(
                                    onClick = { showConfirmClearFaultHistory = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = WarningRed.copy(alpha = 0.8f))
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Wyczyść historię", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                if (faultHistory.isNotEmpty()) {
                    items(faultHistory) { entry ->
                        FaultHistoryCard(
                            entry = entry,
                            onClick = {
                                val found = filteredDtc.find { it.code.equals(entry.dtcCode, ignoreCase = true) }
                                    ?: DtcCode(
                                        code = entry.dtcCode,
                                        system = entry.system,
                                        title = entry.title,
                                        severity = entry.severity,
                                        symptoms = listOf("Zarejestrowano w historii diagnostycznej pojazdu"),
                                        rootCauses = listOf("Wykryto przez moduł: ${entry.source}"),
                                        diagnosticSteps = listOf("Sprawdź parametry zamrożonej ramki (Freeze Frame) oraz stan podzespołu"),
                                        urgencyScore = 5,
                                        isRenaultSpecific = entry.dtcCode.startsWith("DF", ignoreCase = true)
                                    )
                                viewModel.selectDtc(found)
                            },
                            onDelete = { viewModel.deleteFaultHistoryEntry(entry.id) }
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CockpitSurface)
                                .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Brak zapisanych faultów w lokalnej historii Room.\nPrzeskanuj ECU, aby zarejestrować usterki.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(10.dp)) }

                // Full DTC Knowledge Base search
                item {
                    Text(
                        "ENCYKLOPEDIA BŁĘDÓW RENAULT (DF & OBD P)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = dtcSearch,
                        onValueChange = { viewModel.setDtcSearch(it) },
                        placeholder = { Text("Szukaj kodu błędu (np. DF1012, DF297, EGR, P0420)...", fontSize = 13.sp, color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanHud) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanHud,
                            unfocusedBorderColor = CockpitBorder,
                            focusedContainerColor = CockpitSurface,
                            unfocusedContainerColor = CockpitSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                items(filteredDtc) { dtc ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectDtc(dtc) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dtc.code, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(dtc.severity.colorHex))
                                Text(dtc.title, fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                                Text(dtc.system, fontSize = 10.sp, color = TextSecondary)
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // Modal dialog for DTC details
    selectedDtc?.let { dtc ->
        DtcDetailDialog(dtc = dtc, onDismiss = { viewModel.selectDtc(null) })
    }

    // Bluetooth Connection Dialog
    if (showBluetoothDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothDialog = false },
            containerColor = CockpitSurfaceVariant,
            title = { Text("Połączenie OBD-II (ELM327)", color = CyanHud, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Wybierz sparowany adapter ELM327 Bluetooth lub przełącz w tryb symulatora telemetrycznego K9K 636:",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )

                    if (pairedDevices.isEmpty()) {
                        Text(
                            "Brak sparowanych urządzeń Bluetooth w systemie Android. Włącz Bluetooth w ustawieniach i sparuj adapter 'OBDII' / 'ELM327'.",
                            style = MaterialTheme.typography.bodySmall.copy(color = AmberBose)
                        )
                    } else {
                        pairedDevices.forEach { dev ->
                            Button(
                                onClick = {
                                    viewModel.connectBluetooth(dev)
                                    showBluetoothDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CockpitSurface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = CyanHud)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(dev.name ?: dev.address, color = TextPrimary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            viewModel.enableSimulation()
                            showBluetoothDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberBose, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Uruchom Tryb Symulacji Telemetrii", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBluetoothDialog = false }) {
                    Text("Anuluj", color = CyanHud)
                }
            }
        )
    }

    // Confirm Clear DTC dialog
    if (showConfirmClearDtc) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDtc = false },
            containerColor = CockpitSurfaceVariant,
            title = { Text("Kasowanie kodów DTC (Mode 04)", color = WarningRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "„Kasowanie kodów może usunąć informacje diagnostyczne. Kontynuować?”\n\nUwaga: Standardowa komenda OBD-II Mode 04 czyści błędy emisji z ECU (SID307). Nie obsługuje modułów specyficznych Renault (np. BCM, ABS, moduł poduszek).",
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearActiveDtcCodes()
                        showConfirmClearDtc = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed, contentColor = Color.White)
                ) {
                    Text("Kontynuuj")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearDtc = false }) {
                    Text("Anuluj", color = TextSecondary)
                }
            }
        )
    }

    // Modal confirmation dialog for Clearing Local Fault History
    if (showConfirmClearFaultHistory) {
        AlertDialog(
            onDismissRequest = { showConfirmClearFaultHistory = false },
            containerColor = CockpitSurfaceVariant,
            title = {
                Text("WYCZYŚĆ LOKALNĄ HISTORIĘ FAULTÓW", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = WarningRed))
            },
            text = {
                Text(
                    "Czy na pewno chcesz usunąć wszystkie historyczne wpisy faultów z lokalnej bazy danych Room? Ta operacja jest nieodwracalna, ale nie wpływa na pamięć ECU w pojeździe.",
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearFaultHistory()
                        showConfirmClearFaultHistory = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed, contentColor = Color.White)
                ) {
                    Text("Wyczyść historię")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearFaultHistory = false }) {
                    Text("Anuluj", color = TextSecondary)
                }
            }
        )
    }

}

@Composable
fun FaultHistoryCard(
    entry: DiagnosticFaultHistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(entry.timestamp) { dateFmt.format(Date(entry.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(
                    Color(entry.severity.colorHex).copy(alpha = 0.5f),
                    CockpitBorder
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.dtcCode,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color(entry.severity.colorHex)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(entry.severity.colorHex).copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            entry.severity.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(entry.severity.colorHex),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyanHud.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            entry.status.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = CyanHud,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Usuń wpis",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(entry.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
            Text(entry.system, style = MaterialTheme.typography.bodySmall.copy(color = CyanHud, fontSize = 11.sp))

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Wykryto: $formattedTime",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = entry.source,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
fun DtcDetailDialog(
    dtc: DtcCode,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CockpitSurfaceVariant,
        title = {
            Column {
                Text(dtc.code, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Color(dtc.severity.colorHex)))
                Text(dtc.title, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                Text(dtc.system, style = MaterialTheme.typography.bodySmall.copy(color = CyanHud))
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("OBJAWY W RENAULT MEGANE III", style = MaterialTheme.typography.labelSmall.copy(color = WarningRed, fontWeight = FontWeight.Bold))
                    dtc.symptoms.forEach { sym ->
                        Text("• $sym", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                    }
                }

                item {
                    Text("NAJCZĘSTSZE PRZYCZYNY (ROOT CAUSES)", style = MaterialTheme.typography.labelSmall.copy(color = AmberBose, fontWeight = FontWeight.Bold))
                    dtc.rootCauses.forEach { cause ->
                        Text("• $cause", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                    }
                }

                item {
                    Text("ZALECANA PROCEDURA NAPRAWCZA", style = MaterialTheme.typography.labelSmall.copy(color = DiagnosticGreen, fontWeight = FontWeight.Bold))
                    dtc.diagnosticSteps.forEach { step ->
                        Text(step, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Rozumiem", color = CyanHud, fontWeight = FontWeight.Bold)
            }
        }
    )
}
