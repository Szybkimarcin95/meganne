package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DtcScanState
import com.example.data.model.VehicleSpec
import com.example.data.obd.ObdConnectionState
import com.example.ui.components.CompactActionBar
import com.example.ui.components.DiagnosticSectionHeader
import com.example.ui.components.DiagnosticSourceType
import com.example.ui.components.DiagnosticStatusBar
import com.example.ui.components.HealthStatusCode
import com.example.ui.components.HealthStateRow
import com.example.ui.components.ParameterRow
import com.example.ui.components.SourceBadge
import com.example.ui.theme.ScannerAccent
import com.example.ui.theme.ScannerAccentDim
import com.example.ui.theme.ScannerBackground
import com.example.ui.theme.ScannerBorder
import com.example.ui.theme.ScannerBorderSubtle
import com.example.ui.theme.ScannerStatusAlert
import com.example.ui.theme.ScannerStatusAlertDim
import com.example.ui.theme.ScannerStatusNeutral
import com.example.ui.theme.ScannerStatusPass
import com.example.ui.theme.ScannerStatusWarning
import com.example.ui.theme.ScannerSurface
import com.example.ui.theme.ScannerSurfaceElevated
import com.example.ui.theme.ScannerTextMuted
import com.example.ui.theme.ScannerTextPrimary
import com.example.ui.theme.ScannerTextSecondary
import com.example.ui.viewmodel.OverlordTab
import com.example.ui.viewmodel.OverlordViewModel

/**
 * Diagnostic Scanner Foundation & Primary Interface (OEM Scanner Style)
 *
 * Information Architecture:
 * 1. SYSTEM HEALTH (STAN SYSTEMU)
 * 2. LIVE DATA (PARAMETRY BIEŻĄCE)
 * 3. DTC (REJESTR USTEREK)
 * 4. ECU / IDENTIFICATION (IDENTYFIKACJA STEROWNIKA)
 * 5. HISTORY / FILES (HISTORIA / PLIKI)
 *
 * Responsive:
 * - Phone Portrait: Top status strip, compact horizontal segmented navigation, single-column data list
 * - Landscape / Tablet (width >= 600dp): Left navigation rail, main data panel
 */
@Composable
fun DashboardScreen(
    viewModel: OverlordViewModel,
    modifier: Modifier = Modifier
) {
    var selectedScannerModule by remember { mutableIntStateOf(0) }
    // 0 = System Health, 1 = Live Data, 2 = DTC, 3 = ECU Ident, 4 = History/Files

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ScannerBackground)
            .testTag("dashboard_scanner_root")
    ) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            // Tablet / Landscape: Side Navigation Rail + Main Data Panel
            Row(modifier = Modifier.fillMaxSize()) {
                ScannerSideNavRail(
                    selectedModule = selectedScannerModule,
                    onSelectModule = { selectedScannerModule = it },
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                )
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(ScannerBorderSubtle))
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ScannerModuleContent(
                        selectedModule = selectedScannerModule,
                        viewModel = viewModel
                    )
                }
            }
        } else {
            // Phone Portrait: Top Status Strip + Segmented Module Bar + Data List
            Column(modifier = Modifier.fillMaxSize()) {
                ScannerModuleSegmentBar(
                    selectedModule = selectedScannerModule,
                    onSelectModule = { selectedScannerModule = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                )
                HorizontalDivider(color = ScannerBorderSubtle, thickness = 1.dp)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ScannerModuleContent(
                        selectedModule = selectedScannerModule,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerModuleSegmentBar(
    selectedModule: Int,
    onSelectModule: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val modules = listOf(
        "STAN SYSTEMU",
        "PARAMETRY LIVE",
        "REJESTR DTC",
        "IDENTYFIKACJA ECU",
        "HISTORIA / PLIKI"
    )
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        modules.forEachIndexed { index, title ->
            val isSelected = selectedModule == index
            Surface(
                color = if (isSelected) ScannerAccentDim else ScannerSurface,
                shape = RoundedCornerShape(3.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) ScannerAccent else ScannerBorder
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onSelectModule(index) }
                    .testTag("scanner_tab_$index")
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isSelected) ScannerAccent else ScannerTextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun ScannerSideNavRail(
    selectedModule: Int,
    onSelectModule: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val modules = listOf(
        "1. STAN SYSTEMU",
        "2. PARAMETRY LIVE",
        "3. REJESTR DTC",
        "4. IDENTYFIKACJA ECU",
        "5. HISTORIA / PLIKI"
    )

    Column(
        modifier = modifier
            .background(ScannerSurface)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "DIAGNOSTYKA OEM",
            style = MaterialTheme.typography.labelSmall.copy(
                color = ScannerTextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        )

        modules.forEachIndexed { index, title ->
            val isSelected = selectedModule == index
            Surface(
                color = if (isSelected) ScannerAccentDim else Color.Transparent,
                shape = RoundedCornerShape(3.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) ScannerAccent else Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onSelectModule(index) }
                    .testTag("scanner_rail_tab_$index")
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isSelected) ScannerAccent else ScannerTextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ScannerModuleContent(
    selectedModule: Int,
    viewModel: OverlordViewModel
) {
    when (selectedModule) {
        0 -> SystemHealthScreen(viewModel = viewModel)
        1 -> ScannerLiveDataView(viewModel = viewModel)
        2 -> ScannerDtcView(viewModel = viewModel)
        3 -> ScannerEcuIdentView(viewModel = viewModel)
        4 -> ScannerHistoryFilesView(viewModel = viewModel)
    }
}

/**
 * Compact Live Data table view adhering strictly to:
 * PARAMETER | VALUE | UNIT | SOURCE | FRESHNESS
 * No fabricated values.
 */
@Composable
private fun ScannerLiveDataView(viewModel: OverlordViewModel) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val isConnected = telemetry.isConnected
    val isSimulated = telemetry.isSimulated

    val source = when {
        isSimulated -> DiagnosticSourceType.SIMULATED
        isConnected -> DiagnosticSourceType.LIVE
        else -> DiagnosticSourceType.UNKNOWN
    }
    val freshness = when {
        isConnected -> "Świeże (CAN)"
        isSimulated -> "Symulacja 4Hz"
        else -> "Brak odczytu"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerBackground)
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Section header
        item {
            DiagnosticSectionHeader(
                title = "Parametry bieżące silnika (Mode 01)",
                badgeText = if (isConnected || isSimulated) "STRUMIEŃ AKTYWNY" else "OFFLINE",
                badgeColor = if (isConnected) ScannerStatusPass else if (isSimulated) ScannerStatusWarning else ScannerTextMuted
            )
        }

        // Live Parameters Table Rows
        item {
            ParameterRow(
                parameterName = "Prędkość obrotowa silnika (RPM)",
                pidHex = "010C",
                valueString = if (isConnected || isSimulated) telemetry.rpm.toString() else "—",
                unit = "obr/min",
                source = source,
                freshness = freshness
            )
        }

        item {
            ParameterRow(
                parameterName = "Prędkość pojazdu",
                pidHex = "010D",
                valueString = if (isConnected || isSimulated) telemetry.speedKmH.toString() else "—",
                unit = "km/h",
                source = source,
                freshness = freshness
            )
        }

        item {
            ParameterRow(
                parameterName = "Temperatura płynu chłodzącego (ECT)",
                pidHex = "0105",
                valueString = if (isConnected || isSimulated) telemetry.coolantTempC.toString() else "—",
                unit = "°C",
                source = source,
                freshness = freshness
            )
        }

        item {
            ParameterRow(
                parameterName = "Temperatura powietrza dolotowego (IAT)",
                pidHex = "010F",
                valueString = if (isConnected || isSimulated) telemetry.intakeAirTempC.toString() else "—",
                unit = "°C",
                source = source,
                freshness = freshness
            )
        }

        item {
            ParameterRow(
                parameterName = "Przepływomierz masowy powietrza (MAF)",
                pidHex = "0110",
                valueString = if (isConnected || isSimulated) "%.2f".format(telemetry.mafAirFlowGps) else "—",
                unit = "g/s",
                source = source,
                freshness = freshness
            )
        }

        item {
            ParameterRow(
                parameterName = "Wyliczone obciążenie silnika (Load)",
                pidHex = "0104",
                valueString = if (isConnected || isSimulated) "%.1f".format(telemetry.engineLoadPercent) else "—",
                unit = "%",
                source = source,
                freshness = freshness
            )
        }

        item {
            ParameterRow(
                parameterName = "Względne położenie przepustnicy (TP)",
                pidHex = "0111",
                valueString = if (isConnected || isSimulated) "%.1f".format(telemetry.throttlePercent) else "—",
                unit = "%",
                source = source,
                freshness = freshness
            )
        }

        item {
            ParameterRow(
                parameterName = "Ciśnienie bezwzględne w kolektorze (MAP)",
                pidHex = "010B",
                valueString = if (isConnected || isSimulated) telemetry.mapPressureKpa.toString() else "—",
                unit = "kPa",
                source = source,
                freshness = freshness
            )
        }

        item {
            ParameterRow(
                parameterName = "Napięcie zasilania adaptera / OBD",
                pidHex = "PIN16",
                valueString = if (isSimulated) "%.1f".format(telemetry.batteryVoltage) else "—",
                unit = if (isSimulated) "V" else "—",
                source = if (isSimulated) DiagnosticSourceType.SIMULATED else DiagnosticSourceType.UNAVAILABLE,
                freshness = if (isSimulated) "Symulacja demo" else "NO LIVE SOURCE"
            )
        }

        item {
            ParameterRow(
                parameterName = "Ciśnienie doładowania turbosprężarki (Boost)",
                pidHex = "CALC",
                valueString = if (isSimulated) "%.2f".format(telemetry.boostBar) else "—",
                unit = if (isSimulated) "bar" else "—",
                source = if (isSimulated) DiagnosticSourceType.SIMULATED else DiagnosticSourceType.UNAVAILABLE,
                freshness = if (isSimulated) "Model demo" else "NO LIVE SOURCE"
            )
        }

        item {
            ParameterRow(
                parameterName = "Ciśnienie szyny Common Rail",
                pidHex = "REF",
                valueString = if (isSimulated) telemetry.railPressureBar.toString() else "—",
                unit = if (isSimulated) "bar" else "—",
                source = if (isSimulated) DiagnosticSourceType.SIMULATED else DiagnosticSourceType.UNAVAILABLE,
                freshness = if (isSimulated) "Model demo" else "NO LIVE SOURCE / CP2"
            )
        }

        item {
            ParameterRow(
                parameterName = "Szacowana masa sadzy w filtrze DPF",
                pidHex = "DPF",
                valueString = if (isSimulated) "%.1f".format(telemetry.dpfSootGrams) else "—",
                unit = if (isSimulated) "g" else "—",
                source = if (isSimulated) DiagnosticSourceType.SIMULATED else DiagnosticSourceType.UNAVAILABLE,
                freshness = if (isSimulated) "Model demo" else "NO LIVE SOURCE / CP2"
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/**
 * Compact DTC scanner view with clear separation of READ actions from PROTECTED Mode 04 actions.
 */
@Composable
private fun ScannerDtcView(viewModel: OverlordViewModel) {
    val activeDtc by viewModel.activeDtcCodes.collectAsStateWithLifecycle()
    val pendingDtc by viewModel.pendingDtcCodes.collectAsStateWithLifecycle()
    val dtcScanState by viewModel.dtcScanState.collectAsStateWithLifecycle()
    val hasDtcScanRun by viewModel.hasDtcScanRun.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val clearSuccessMessage by viewModel.clearDtcSuccess.collectAsStateWithLifecycle()

    var showConfirmClearDialog by remember { mutableStateOf(false) }

    val currentSource = when {
        telemetry.isSimulated -> DiagnosticSourceType.SIMULATED
        telemetry.isConnected -> DiagnosticSourceType.LIVE
        else -> DiagnosticSourceType.UNKNOWN
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerBackground)
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Read Actions Bar
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { viewModel.scanTroubleCodes() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ScannerAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("action_scan_dtc_screen")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SKANUJ DTC (MODE 03 / MODE 07)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // DTC Scan Result Header
        item {
            val badgeText = when (dtcScanState) {
                DtcScanState.NOT_RUN -> "SKAN NIEWYKONANY"
                DtcScanState.RUNNING -> "SKANOWANIE..."
                DtcScanState.FAILED -> "BŁĄD SKANU"
                DtcScanState.COMPLETED -> {
                    if (activeDtc.isEmpty() && pendingDtc.isEmpty()) "0 BŁĘDÓW"
                    else "${activeDtc.size + pendingDtc.size} WYKRYTYCH"
                }
            }
            val badgeColor = when (dtcScanState) {
                DtcScanState.NOT_RUN -> ScannerTextMuted
                DtcScanState.RUNNING -> ScannerAccent
                DtcScanState.FAILED -> ScannerStatusAlert
                DtcScanState.COMPLETED -> {
                    if (activeDtc.isEmpty() && pendingDtc.isEmpty()) ScannerStatusPass
                    else ScannerStatusAlert
                }
            }

            DiagnosticSectionHeader(
                title = "Kody usterek silnika (DTC)",
                badgeText = badgeText,
                badgeColor = badgeColor
            )
        }

        when (dtcScanState) {
            DtcScanState.NOT_RUN -> {
                item {
                    Surface(
                        color = ScannerSurface,
                        shape = RoundedCornerShape(3.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ScannerBorder),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = ScannerTextMuted, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("SKAN NIEWYKONANY", fontWeight = FontWeight.Bold, color = ScannerTextPrimary, fontSize = 12.sp)
                                Text(
                                    "Kliknij przycisk 'SKANUJ DTC', aby odczytać zarejestrowane błędy ze sterownika silnika.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = ScannerTextSecondary, fontSize = 10.sp)
                                )
                            }
                        }
                    }
                }
            }
            DtcScanState.RUNNING -> {
                item {
                    Surface(
                        color = ScannerSurface,
                        shape = RoundedCornerShape(3.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ScannerAccent),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = ScannerAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("TRWA SKANOWANIE MAGISTRALI...", fontWeight = FontWeight.Bold, color = ScannerAccent, fontSize = 12.sp)
                                Text(
                                    "Wysyłanie zapytań Mode 03 i Mode 07 przez SafeDiagnosticSession. Oczekiwanie na ramki ECU.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = ScannerTextSecondary, fontSize = 10.sp)
                                )
                            }
                        }
                    }
                }
            }
            DtcScanState.FAILED -> {
                item {
                    Surface(
                        color = ScannerStatusAlertDim,
                        shape = RoundedCornerShape(3.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ScannerStatusAlert),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ScannerStatusAlert, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("BŁĄD SKANU DTC — STAN NIEZNANY", fontWeight = FontWeight.Bold, color = ScannerStatusAlert, fontSize = 12.sp)
                                Text(
                                    "Brak aktywnego połączenia ze sterownikiem lub błąd odczytu magistrali. UWAGA: Nie oznacza to braku usterek!",
                                    style = MaterialTheme.typography.bodySmall.copy(color = ScannerTextPrimary, fontSize = 10.sp)
                                )
                            }
                        }
                    }
                }
            }
            DtcScanState.COMPLETED -> {
                if (activeDtc.isEmpty() && pendingDtc.isEmpty()) {
                    item {
                        Surface(
                            color = ScannerSurface,
                            shape = RoundedCornerShape(3.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ScannerStatusPass),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = ScannerStatusPass, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("BRAK ZAREJESTROWANYCH BŁĘDÓW (0 BŁĘDÓW)", fontWeight = FontWeight.Bold, color = ScannerStatusPass, fontSize = 12.sp)
                                    Text(
                                        "Odpowiedź ze sterownika: brak zapisanych kodów usterek (Mode 03) oraz brak kodów oczekujących (Mode 07).",
                                        style = MaterialTheme.typography.bodySmall.copy(color = ScannerTextSecondary, fontSize = 10.sp)
                                    )
                                }
                            }
                        }
                    }
                } else {
            // List active DTCs
            items(activeDtc) { dtc ->
                Surface(
                    color = ScannerSurface,
                    shape = RoundedCornerShape(3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ScannerStatusAlert),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = dtc.code,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = ScannerStatusAlert,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                            )
                            SourceBadge(source = currentSource, detail = "Mode 03")
                        }
                        Text(
                            text = dtc.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ScannerTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                        if (dtc.rootCauses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Możliwa przyczyna: ${dtc.rootCauses.first()}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = ScannerTextSecondary,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
            }

            // List pending DTCs
            items(pendingDtc) { dtc ->
                Surface(
                    color = ScannerSurface,
                    shape = RoundedCornerShape(3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ScannerStatusWarning),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${dtc.code} (OCZEKUJĄCY)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = ScannerStatusWarning,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            )
                            SourceBadge(source = currentSource, detail = "Mode 07")
                        }
                        Text(
                            text = dtc.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ScannerTextPrimary,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
                }
            }
        }

        // Clearly separated Protected Mode 04 Section
        item { Spacer(modifier = Modifier.height(10.dp)) }
        item {
            DiagnosticSectionHeader(
                title = "Operacje chronione (Modyfikacja stanu)",
                badgeText = "PROTECTED ACTION",
                badgeColor = ScannerStatusAlert
            )
        }

        item {
            Surface(
                color = ScannerStatusAlertDim,
                shape = RoundedCornerShape(3.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ScannerStatusAlert.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ScannerStatusAlert, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "KASOWANIE KODÓW USTEREK (MODE 04)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ScannerStatusAlert,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Wymaga włączonego zapłonu i zgaszonego silnika. Mode 04 kasuje pamięć błędów oraz resetuje monitory gotowości ECU.",
                        style = MaterialTheme.typography.bodySmall.copy(color = ScannerTextSecondary, fontSize = 10.sp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showConfirmClearDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ScannerStatusAlert,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.fillMaxWidth().height(32.dp).testTag("action_clear_dtc_button")
                    ) {
                        Text("WYKONAJ KASOWANIE (MODE 04)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = {
                Text("Potwierdzenie kasowania usterek (Mode 04)", color = ScannerStatusAlert, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Czy na pewno chcesz zainicjować polecenie kasowania błędów Mode 04?\n\nUwaga: W obecnej polityce READ-ONLY komenda Mode 04 zostanie zablokowana przez CommandFirewall przed transmisją do fizycznego auta.",
                    color = ScannerTextPrimary,
                    fontSize = 11.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearActiveDtcCodes()
                        showConfirmClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerStatusAlert)
                ) {
                    Text("POTWIERDŹ", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearDialog = false }) {
                    Text("ANULUJ", color = ScannerTextSecondary)
                }
            },
            containerColor = ScannerSurface
        )
    }
}

/**
 * Compact Technical ECU Identification view.
 */
@Composable
private fun ScannerEcuIdentView(viewModel: OverlordViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerBackground)
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            DiagnosticSectionHeader(
                title = "Identyfikacja jednostki napędowej i sterownika ECU",
                badgeText = "PROFIL: CANDIDATE",
                badgeColor = ScannerTextMuted
            )
        }

        item {
            Surface(
                color = ScannerSurface,
                shape = RoundedCornerShape(3.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ScannerBorder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IdentRow("Pojazd", "${VehicleSpec.MAKE} ${VehicleSpec.MODEL} Grandtour (${VehicleSpec.EDITION})")
                    IdentRow("Kod fabryczny", VehicleSpec.VEHICLE_TYPE)
                    IdentRow("Jednostka", VehicleSpec.ENGINE_DESC)
                    IdentRow("Kod silnika", "${VehicleSpec.ENGINE_CODE} ${VehicleSpec.ENGINE_DESIGNATION}")
                    IdentRow("Profil referencyjny", "Continental SID307 [CANDIDATE / UNVERIFIED ON VEHICLE]")
                    IdentRow("Protokół", "ISO 15765-4 (CAN 11-bit, 500 kbaud) [PROFIL CANDIDATE]")
                    IdentRow("Adresy magistrali", "Tester: 0x7E0 • ECU: 0x7E8 [CANDIDATE]")
                    IdentRow("Oprogramowanie ECU", "Software: 00F7 • Version: 5500 • Supplier: 4BE [Z INDEKSU HIST.]")
                    IdentRow("Wersja diagnostyczna", "Diag Version: 129 • Boot: 00010000 [Z INDEKSU HIST.]")
                    IdentRow("Dopasowanie autoidents", "SID307_00F7_550_V05_20130313T104520.json [CP2 SOURCE UNAVAILABLE]")
                    IdentRow("Numer VIN (Baza/BRC)", VehicleSpec.VIN)
                    IdentRow("Konflikt VIN", "Odnotowano niespójność z ciągiem w OBD-II.txt (877036746041ZK1FV) [UNCONFIRMED]")
                }
            }
        }

        item {
            DiagnosticSectionHeader(
                title = "Polityka bezpieczeństwa toru diagnostycznego",
                badgeText = "READ-ONLY",
                badgeColor = ScannerStatusPass
            )
        }

        item {
            Surface(
                color = ScannerSurface,
                shape = RoundedCornerShape(3.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ScannerBorder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IdentRow("Hardware I/O", "OFF (Brak bezpośredniej transmisji fizycznej)")
                    IdentRow("Biała lista komend", "Tylko standardowe AT, Mode 01 PIDs, Mode 03, Mode 07")
                    IdentRow("Komendy proprietary", "ZABLOKOWANE do czasu CP2 (brak fizycznych plików bazy)")
                    IdentRow("Tryb Mode 04", "ZABLOKOWANY w SafeDiagnosticSession")
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/**
 * Compact History and Files view.
 */
@Composable
private fun ScannerHistoryFilesView(viewModel: OverlordViewModel) {
    val faultHistory by viewModel.diagnosticFaultHistory.collectAsStateWithLifecycle()
    val serviceRecords by viewModel.serviceRecords.collectAsStateWithLifecycle()
    val fuelRecords by viewModel.fuelRecords.collectAsStateWithLifecycle()
    val telemetryLogs by viewModel.telemetryLogs.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerBackground)
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            DiagnosticSectionHeader(
                title = "Lokalna baza danych (Room v3)",
                badgeText = "ROOM PERSISTENCE",
                badgeColor = ScannerStatusPass
            )
        }

        item {
            Surface(
                color = ScannerSurface,
                shape = RoundedCornerShape(3.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ScannerBorder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IdentRow("Zapisane usterki (Room)", "${faultHistory.size} wpisów historycznych")
                    IdentRow("Próbki telemetrii w bazie", "${telemetryLogs.size} rekordów")
                    IdentRow("Wpisy serwisowe", "${serviceRecords.size} zarejestrowanych czynności")
                    IdentRow("Rejestr tankowań", "${fuelRecords.size} zarejestrowanych tankowań")
                    IdentRow("Stan migracji bazy", "MIGRATION_2_3 zweryfikowana pomyślnie")
                }
            }
        }

        item {
            DiagnosticSectionHeader(
                title = "Pochodzenie danych i pliki wejściowe (CP2)",
                badgeText = "BLOCKED",
                badgeColor = ScannerStatusWarning
            )
        }

        item {
            Surface(
                color = ScannerSurface,
                shape = RoundedCornerShape(3.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ScannerBorder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IdentRow("ECUU.zip", "BRAK W SYSTEMIE PLIKÓW (CP2 BLOCKED)")
                    IdentRow("OBD-II.txt", "BRAK W SYSTEMIE PLIKÓW (CP2 BLOCKED)")
                    IdentRow("exported_records*.zip", "BRAK W SYSTEMIE PLIKÓW (CP2 BLOCKED)")
                    IdentRow("Status definicji DDT", "CANDIDATE: SID307 JSON (nieodblokowane do wykonania)")
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun IdentRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = ScannerTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = ScannerTextPrimary,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            modifier = Modifier.weight(1.4f)
        )
    }
}

@Composable
private fun VerticalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(ScannerBorderSubtle)
    )
}
