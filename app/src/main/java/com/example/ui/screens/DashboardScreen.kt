package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.VehicleSpec
import com.example.ui.components.BluetoothConnectionIndicator
import com.example.ui.components.CockpitGauge
import com.example.ui.components.SystemHealthCard
import com.example.ui.components.VehicleHeaderCard
import com.example.ui.theme.AmberBose
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CyanHud
import com.example.ui.theme.DiagnosticGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningRed
import com.example.ui.viewmodel.OverlordTab
import com.example.ui.viewmodel.OverlordViewModel

@Composable
fun DashboardScreen(
    viewModel: OverlordViewModel,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val activeDtc by viewModel.activeDtcCodes.collectAsStateWithLifecycle()
    val serviceRecords by viewModel.serviceRecords.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Hero Card with generated image
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(CyanHud.copy(alpha = 0.5f), CockpitBorder))),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_megane_hero),
                        contentDescription = "Renault Megane III Grandtour",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0x99080C14), Color(0xFA080C14))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = CyanHud,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CYFROWY SYSTEM DOMINACJI POJAZDU",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = CyanHud,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                        Text(
                            text = "MEGANE OVERLORD",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                letterSpacing = 2.sp
                            )
                        )
                    }
                }
            }
        }

        // Bluetooth ELM327 Real-time Connection Indicator
        item {
            BluetoothConnectionIndicator(
                connectionState = connectionState,
                connectionStatusText = connectionStatus,
                isSimulated = telemetry.isSimulated,
                onClick = { viewModel.selectTab(OverlordTab.ORACLE) }
            )
        }

        // Vehicle specifications header
        item {
            VehicleHeaderCard(onScanClick = { viewModel.selectTab(OverlordTab.ORACLE) })
        }

        // Active Alert Warning (if DTC detected)
        if (activeDtc.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningRed.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(WarningRed, Color(0xFF880000)))),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectTab(OverlordTab.ORACLE) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WYKRYTO KODY DTC (${activeDtc.size})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = WarningRed,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = activeDtc.joinToString(", ") { it.code },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Dotknij, aby przejść do Wyroczni i analizy przyczyn",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }
            }
        }

        // Live Quick Telemetry Section
        item {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = CyanHud, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TELEMETRIA NA ŻYWO (K9K 636)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyanHud,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    Text(
                        text = if (telemetry.isSimulated) "SYMULACJA 4Hz" else "ELM327 CAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (telemetry.isSimulated) AmberBose else DiagnosticGreen,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CockpitGauge(
                        value = telemetry.rpm.toFloat(),
                        minValue = 0f,
                        maxValue = 5000f,
                        title = "Obroty",
                        unit = "RPM",
                        gaugeColor = CyanHud,
                        warningThreshold = 4400f,
                        modifier = Modifier.weight(1f)
                    )
                    CockpitGauge(
                        value = telemetry.boostBar,
                        minValue = 0.0f,
                        maxValue = 2.0f,
                        title = "Doładowanie",
                        unit = "BAR",
                        gaugeColor = AmberBose,
                        warningThreshold = 1.45f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CockpitGauge(
                        value = telemetry.railPressureBar.toFloat(),
                        minValue = 200f,
                        maxValue = 1800f,
                        title = "Szyna CR",
                        unit = "BAR",
                        gaugeColor = DiagnosticGreen,
                        modifier = Modifier.weight(1f)
                    )
                    CockpitGauge(
                        value = telemetry.coolantTempC.toFloat(),
                        minValue = 40f,
                        maxValue = 120f,
                        title = "Płyn chłod.",
                        unit = "°C",
                        gaugeColor = CyanHud,
                        warningThreshold = 100f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Subsystems Health Check
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "STATUS ZESPOŁÓW NAPĘDOWYCH",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                )
                SystemHealthCard(
                    title = "Układ oczyszczania DPF/FAP",
                    status = "SADZA: ${telemetry.dpfSootGrams}g / 45g",
                    isHealthy = telemetry.dpfSootGrams < 22f,
                    detail = if (telemetry.isRegeneratingDpf) "TRWA WYPALANIE DPF!" else "Rozcieńczenie oleju: ${telemetry.oilDilutionPercent}%"
                )
                SystemHealthCard(
                    title = "Akumulator Start-Stop (BMS)",
                    status = "${telemetry.batteryVoltage} V",
                    isHealthy = telemetry.batteryVoltage >= 12.4f,
                    detail = "Ładowanie alternatora z odzyskiem energii aktywne"
                )
                SystemHealthCard(
                    title = "Skrzynia biegów TL4 (Manualna 6b)",
                    status = "STAN PRAWIDŁOWY",
                    isHealthy = true,
                    detail = "Olej NFJ/NFX 75W80 (1.9L) • Moment korka spustowego 22 Nm"
                )
            }
        }

        // Service Countdown & Prevention
        item {
            val lastMileage = serviceRecords.firstOrNull { it.category.contains("Olej") }?.mileageKm ?: 178500
            val nextOilKm = lastMileage + 15000
            val currentEstimated = 179420
            val kmRemaining = nextOilKm - currentEstimated

            Card(
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(AmberBose.copy(alpha = 0.4f), CockpitBorder))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = AmberBose, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PREWENCJA SERWISOWA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AmberBose,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                        Text(
                            text = "RN0720 5W30 C4",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Następna wymiana oleju za: $kmRemaining km",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Zaplanowana przy przebiegu: $nextOilKm km (Pojemność 4.5L)",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.selectTab(OverlordTab.BLACK_BOX) },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberBose, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Otwórz Czarną Skrzynkę (Dziennik)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Quick Navigation Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ARCHITEKTURA DOMINACJI",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectTab(OverlordTab.DIGITAL_TWIN) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CyanHud)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cyfrowy Bliźniak", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Bezpieczniki & Komora K9K", fontSize = 11.sp, color = TextSecondary)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectTab(OverlordTab.ARSENAL) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = AmberBose)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Arsenał DIY", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Momenty śrub & Procedury", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}
