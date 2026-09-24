package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.obd.ObdConnectionState
import com.example.ui.components.CompactActionBar
import com.example.ui.components.DiagnosticSectionHeader
import com.example.ui.components.DiagnosticSourceType
import com.example.ui.components.DiagnosticStatusBar
import com.example.ui.components.HealthStatusCode
import com.example.ui.components.HealthStateRow
import com.example.ui.components.SourceBadge
import com.example.ui.theme.ScannerAccent
import com.example.ui.theme.ScannerBackground
import com.example.ui.theme.ScannerBorder
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
import com.example.ui.viewmodel.OverlordViewModel

/**
 * System Health Screen (OEM Scanner Foundation)
 *
 * Utilitarian, compact, information-dense manufacturer-scanner presentation.
 * Displays evidence-driven diagnostic states across 6 defined subsystems:
 * 1. Transport & Adapter Link
 * 2. ECU Communication
 * 3. DTC Scan State (Stored/Pending)
 * 4. Live Data Coverage
 * 5. Stored / Local History (Room Database)
 * 6. File / Import Identification Status
 *
 * Strict Provenance Rules:
 * - Never invents an artificial numeric health percentage
 * - Never displays "No faults" when a scan has not been completed ("SKAN NIEWYKONANY")
 * - Source labels are explicitly displayed on all states
 */
@Composable
fun SystemHealthScreen(
    viewModel: OverlordViewModel,
    modifier: Modifier = Modifier,
    onNavigateToLive: (() -> Unit)? = null,
    onNavigateToDtc: (() -> Unit)? = null
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()

    val activeDtc by viewModel.activeDtcCodes.collectAsStateWithLifecycle()
    val pendingDtc by viewModel.pendingDtcCodes.collectAsStateWithLifecycle()
    val dtcScanState by viewModel.dtcScanState.collectAsStateWithLifecycle()
    val hasDtcScanRun by viewModel.hasDtcScanRun.collectAsStateWithLifecycle()
    val diagnosticReport by viewModel.diagnosticCheckReport.collectAsStateWithLifecycle()
    val isDiagnosticRunning by viewModel.isDiagnosticCheckRunning.collectAsStateWithLifecycle()

    val faultHistory by viewModel.diagnosticFaultHistory.collectAsStateWithLifecycle()
    val serviceRecords by viewModel.serviceRecords.collectAsStateWithLifecycle()
    val fuelRecords by viewModel.fuelRecords.collectAsStateWithLifecycle()
    val telemetryLogs by viewModel.telemetryLogs.collectAsStateWithLifecycle()

    var showBluetoothDialog by remember { mutableStateOf(false) }
    var showProtectedMode04Dialog by remember { mutableStateOf(false) }
    var clearMessage by remember { mutableStateOf<String?>(null) }

    val currentSource = when {
        telemetry.isSimulated -> DiagnosticSourceType.SIMULATED
        telemetry.isConnected -> DiagnosticSourceType.LIVE
        else -> DiagnosticSourceType.UNKNOWN
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ScannerBackground)
            .padding(horizontal = 12.dp)
            .testTag("system_health_screen"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Top Compact Diagnostic Status Bar
        item {
            DiagnosticStatusBar(
                connectionState = connectionState,
                connectionStatusText = connectionStatus,
                isSimulated = telemetry.isSimulated,
                onConfigureClick = { showBluetoothDialog = true }
            )
        }

        // Compact Action Bar (Read Actions vs Protected Mode 04)
        item {
            CompactActionBar(
                onScanDtcClick = { viewModel.scanTroubleCodes() },
                onRunHealthCheckClick = { viewModel.runDiagnosticCheck() },
                onClearDtcProtectedClick = { showProtectedMode04Dialog = true },
                isScanning = isDiagnosticRunning
            )
        }

        // ==========================================
        // 1. TRANSPORT & ADAPTER LINK
        // ==========================================
        item {
            DiagnosticSectionHeader(
                title = "1. Transport i adapter ELM327",
                badgeText = if (telemetry.isConnected) "POŁĄCZONO" else if (telemetry.isSimulated) "SYMULACJA" else "ROZŁĄCZONY",
                badgeColor = if (telemetry.isConnected) ScannerStatusPass else if (telemetry.isSimulated) ScannerStatusWarning else ScannerTextMuted
            )
        }

        item {
            val transportStatusCode = when {
                telemetry.isConnected -> HealthStatusCode.PASS
                telemetry.isSimulated -> HealthStatusCode.WARNING
                connectionState == ObdConnectionState.ERROR -> HealthStatusCode.ALERT
                else -> HealthStatusCode.NOT_SCANNED
            }
            HealthStateRow(
                title = "Łącze fizyczne ELM327 Bluetooth",
                statusText = when {
                    telemetry.isConnected -> "CONNECTED"
                    telemetry.isSimulated -> "SIMULATED"
                    connectionState == ObdConnectionState.ERROR -> "ERROR"
                    else -> "DISCONNECTED"
                },
                statusCode = transportStatusCode,
                source = currentSource,
                measuredValue = if (telemetry.isConnected) "Aktywny (SPP RFCOMM)" else if (telemetry.isSimulated) "Programowy wirtualny" else "Brak sesji",
                nominalCondition = "ELM327 v1.5 / v2.1 Bluetooth SPP",
                detailMessage = if (telemetry.isSimulated) {
                    "Tryb demonstracyjny / symulacja telemetrii 4Hz K9K 636."
                } else if (telemetry.isConnected) {
                    "Bezpośredni strumień szeregowy. SafeDiagnosticSession z kolejkowaniem transakcji."
                } else {
                    "Wybierz sparowany adapter w menu konfiguracji."
                }
            )
        }

        item {
            HealthStateRow(
                title = "Kolejkowanie transakcji diagnostycznych",
                statusText = "SERIALIZED",
                statusCode = HealthStatusCode.PASS,
                source = DiagnosticSourceType.FILE,
                measuredValue = "1 transakcja in-flight (Mutex)",
                nominalCondition = "Brak przeplatania zapytań magistrali",
                detailMessage = "Gwarancja SafeDiagnosticSession: pojedyncza transakcja na magistrali z kontrolą timeout i generacji."
            )
        }

        // ==========================================
        // 2. ECU COMMUNICATION
        // ==========================================
        item {
            DiagnosticSectionHeader(
                title = "2. Komunikacja ze sterownikiem silnika (ECU)",
                badgeText = "PROFIL: CANDIDATE",
                badgeColor = ScannerTextMuted
            )
        }

        item {
            HealthStateRow(
                title = "Magistrala CAN & Protokół ISO 15765-4",
                statusText = if (telemetry.isConnected) "COMMUNICATING (GENERIC OBD)" else if (telemetry.isSimulated) "SIMULATED" else "HARDWARE NOT VERIFIED",
                statusCode = if (telemetry.isConnected) HealthStatusCode.PASS else if (telemetry.isSimulated) HealthStatusCode.INFO else HealthStatusCode.NOT_SCANNED,
                source = DiagnosticSourceType.CANDIDATE,
                measuredValue = "Kandydat profilu: CAN 11-bit / 500k (Tx: 0x7E0, Rx: 0x7E8) [HARDWARE NOT VERIFIED]",
                nominalCondition = "Identyfikacja SID307 niepotwierdzona z pojazdu (hardware_io=OFF)",
                detailMessage = "Adresy 0x7E0/0x7E8 i protokół ISO 15765-4 pochodzą z bazy referencyjnej. Brak aktywnej fizycznej weryfikacji DID sterownika."
            )
        }

        item {
            HealthStateRow(
                title = "Zabezpieczenie Firewall komend",
                statusText = "READ_ONLY_ACTIVE",
                statusCode = HealthStatusCode.PASS,
                source = DiagnosticSourceType.FILE,
                measuredValue = "Biała lista Least Privilege",
                nominalCondition = "Zablokowane serwisy 04, 11, 27, 2E, 2F, 31, 34, 36, 3D",
                detailMessage = "CommandFirewall uniemożliwia wysłanie niebezpiecznych komend modyfikujących pamięć sterownika."
            )
        }

        // ==========================================
        // 3. DTC SCAN STATE (STORED & PENDING)
        // ==========================================
        item {
            val dtcCountBadge = when (dtcScanState) {
                DtcScanState.NOT_RUN -> "SKAN NIEWYKONANY"
                DtcScanState.RUNNING -> "SKANOWANIE..."
                DtcScanState.FAILED -> "BŁĄD SKANU"
                DtcScanState.COMPLETED -> {
                    if (activeDtc.isEmpty() && pendingDtc.isEmpty()) "0 BŁĘDÓW"
                    else "${activeDtc.size + pendingDtc.size} BŁĘDÓW"
                }
            }
            val dtcBadgeColor = when (dtcScanState) {
                DtcScanState.NOT_RUN -> ScannerTextMuted
                DtcScanState.RUNNING -> ScannerAccent
                DtcScanState.FAILED -> ScannerStatusAlert
                DtcScanState.COMPLETED -> {
                    if (activeDtc.isEmpty() && pendingDtc.isEmpty()) ScannerStatusPass
                    else ScannerStatusAlert
                }
            }

            DiagnosticSectionHeader(
                title = "3. Rejestr usterek diagnostycznych (DTC)",
                badgeText = dtcCountBadge,
                badgeColor = dtcBadgeColor
            )
        }

        item {
            when (dtcScanState) {
                DtcScanState.NOT_RUN -> {
                    HealthStateRow(
                        title = "Kody usterek zapisane (Mode 03)",
                        statusText = "NOT SCANNED",
                        statusCode = HealthStatusCode.NOT_SCANNED,
                        source = DiagnosticSourceType.UNKNOWN,
                        measuredValue = "Skan niewykonany",
                        nominalCondition = "0 zarejestrowanych usterek",
                        detailMessage = "Odczyt pamięci błędów nie został jeszcze wywołany. Kliknij 'SKANUJ DTC', aby odpytać sterownik."
                    )
                }
                DtcScanState.RUNNING -> {
                    HealthStateRow(
                        title = "Kody usterek zapisane (Mode 03)",
                        statusText = "RUNNING",
                        statusCode = HealthStatusCode.INFO,
                        source = if (telemetry.isSimulated) DiagnosticSourceType.SIMULATED else DiagnosticSourceType.LIVE,
                        measuredValue = "Trwa odpytywanie pamięci błędów (03)...",
                        nominalCondition = "0 zarejestrowanych usterek",
                        detailMessage = "Oczekiwanie na ramkę odpowiedzi z ECU."
                    )
                }
                DtcScanState.FAILED -> {
                    HealthStateRow(
                        title = "Kody usterek zapisane (Mode 03)",
                        statusText = "BŁĄD SKANU",
                        statusCode = HealthStatusCode.ALERT,
                        source = DiagnosticSourceType.UNAVAILABLE,
                        measuredValue = "Błąd odczytu pamięci usterek",
                        nominalCondition = "Poprawna ramka odpowiedzi 43 xx",
                        detailMessage = "Nie udało się odpytać sterownika (brak połączenia lub błąd sesji). Stan błędów NIEZNANY (nie traktować jako brak usterek!)."
                    )
                }
                DtcScanState.COMPLETED -> {
                    val hasActive = activeDtc.isNotEmpty()
                    HealthStateRow(
                        title = "Kody usterek zapisane (Mode 03)",
                        statusText = if (hasActive) "FAULTS DETECTED" else "PASS",
                        statusCode = if (hasActive) HealthStatusCode.ALERT else HealthStatusCode.PASS,
                        source = currentSource,
                        measuredValue = "${activeDtc.size} kodów zapisanych",
                        nominalCondition = "0 zarejestrowanych usterek",
                        detailMessage = if (hasActive) {
                            "Wykryte błędy: " + activeDtc.joinToString(", ") { "${it.code} (${it.title})" }
                        } else {
                            "Brak potwierdzonych kodów usterek w pamięci trwałej ECU."
                        }
                    )
                }
            }
        }

        item {
            when (dtcScanState) {
                DtcScanState.NOT_RUN -> {
                    HealthStateRow(
                        title = "Kody oczekujące (Mode 07 Pending)",
                        statusText = "NOT SCANNED",
                        statusCode = HealthStatusCode.NOT_SCANNED,
                        source = DiagnosticSourceType.UNKNOWN,
                        measuredValue = "Skan niewykonany",
                        nominalCondition = "0 usterek oczekujących",
                        detailMessage = "Monitory diagnostyczne nie zostały zbadane w tej sesji."
                    )
                }
                DtcScanState.RUNNING -> {
                    HealthStateRow(
                        title = "Kody oczekujące (Mode 07 Pending)",
                        statusText = "RUNNING",
                        statusCode = HealthStatusCode.INFO,
                        source = if (telemetry.isSimulated) DiagnosticSourceType.SIMULATED else DiagnosticSourceType.LIVE,
                        measuredValue = "Trwa odpytywanie kodów oczekujących (07)...",
                        nominalCondition = "0 usterek oczekujących",
                        detailMessage = "Oczekiwanie na ramkę odpowiedzi z ECU."
                    )
                }
                DtcScanState.FAILED -> {
                    HealthStateRow(
                        title = "Kody oczekujące (Mode 07 Pending)",
                        statusText = "BŁĄD SKANU",
                        statusCode = HealthStatusCode.ALERT,
                        source = DiagnosticSourceType.UNAVAILABLE,
                        measuredValue = "Błąd odczytu kodów oczekujących",
                        nominalCondition = "Poprawna ramka odpowiedzi 47 xx",
                        detailMessage = "Brak możliwości weryfikacji monitorów diagnostycznych. Stan monitorów NIEZNANY."
                    )
                }
                DtcScanState.COMPLETED -> {
                    val hasPending = pendingDtc.isNotEmpty()
                    HealthStateRow(
                        title = "Kody oczekujące (Mode 07 Pending)",
                        statusText = if (hasPending) "PENDING DETECTED" else "PASS",
                        statusCode = if (hasPending) HealthStatusCode.WARNING else HealthStatusCode.PASS,
                        source = currentSource,
                        measuredValue = "${pendingDtc.size} kodów oczekujących",
                        nominalCondition = "0 usterek oczekujących",
                        detailMessage = if (hasPending) {
                            "Wykryto usterki w trakcie monitorowania: " + pendingDtc.joinToString(", ") { "${it.code} (${it.title})" }
                        } else {
                            "Brak oczekujących usterek w cyklu monitorowania ECU."
                        }
                    )
                }
            }
        }

        // ==========================================
        // 4. LIVE-DATA COVERAGE
        // ==========================================
        item {
            DiagnosticSectionHeader(
                title = "4. Pokrycie parametrów bieżących (Mode 01)",
                badgeText = "8 STANDARD PIDs",
                badgeColor = ScannerAccent
            )
        }

        item {
            val liveStatusText = when {
                telemetry.isConnected -> "STREAMING (8 PIDs)"
                telemetry.isSimulated -> "SIMULATED (8 PIDs)"
                else -> "UNAVAILABLE"
            }
            val liveStatusCode = when {
                telemetry.isConnected -> HealthStatusCode.PASS
                telemetry.isSimulated -> HealthStatusCode.INFO
                else -> HealthStatusCode.NOT_SCANNED
            }

            HealthStateRow(
                title = "Standardowy zestaw Mode 01 (fizycznie odpytywany)",
                statusText = liveStatusText,
                statusCode = liveStatusCode,
                source = currentSource,
                measuredValue = if (telemetry.isConnected || telemetry.isSimulated) {
                    "8 PID: 010C, 010D, 0105, 010F, 0110, 0104, 0111, 010B"
                } else {
                    "Brak aktywnej pętli odpytywania"
                },
                nominalCondition = "Potwierdzone w kodzie: RPM, Speed, ECT, IAT, MAF, Load, Throttle, MAP",
                detailMessage = "Wszystkie 8 PIDs są sekwencyjnie pobierane w pętli ObdManager.startLiveObdPolling."
            )
        }

        item {
            HealthStateRow(
                title = "Napięcie zasilania (OBD Pin 16 / ATRV)",
                statusText = if (telemetry.isSimulated) "SIMULATED" else "NO LIVE SOURCE",
                statusCode = if (telemetry.isSimulated) HealthStatusCode.INFO else HealthStatusCode.NOT_SCANNED,
                source = if (telemetry.isSimulated) DiagnosticSourceType.SIMULATED else DiagnosticSourceType.UNAVAILABLE,
                measuredValue = if (telemetry.isSimulated) "%.1f V (Syntetyczne w demo)".format(telemetry.batteryVoltage) else "Brak odczytu LIVE",
                nominalCondition = "Wymaga dedykowanej komendy AT RV (brak w pętli Mode 01)",
                detailMessage = "Napięcie zasilania nie jest obecnie fizycznie odpytywane z magistrali w trybie LIVE."
            )
        }

        item {
            HealthStateRow(
                title = "Rozszerzone parametry (Boost, Rail, DPF, Korekty)",
                statusText = if (telemetry.isSimulated) "SIMULATED" else "NO LIVE SOURCE",
                statusCode = if (telemetry.isSimulated) HealthStatusCode.INFO else HealthStatusCode.NOT_SCANNED,
                source = if (telemetry.isSimulated) DiagnosticSourceType.SIMULATED else DiagnosticSourceType.UNAVAILABLE,
                measuredValue = if (telemetry.isSimulated) "Model matematyczny w demo" else "Brak źródeł LIVE (Wymaga UDS 0x22 / CP2)",
                nominalCondition = "Zablokowane w CP3A do czasu fizycznej weryfikacji bazy",
                detailMessage = "Pola modelu LiveTelemetry (ciśnienie szyny, doładowanie, masa sadzy, korekty wtryskiwaczy) nie są odpytywane w standardowym Mode 01."
            )
        }

        item {
            HealthStateRow(
                title = "Ochrona świeżości parametrów (Freshness protection)",
                statusText = "PARTIAL",
                statusCode = HealthStatusCode.WARNING,
                source = DiagnosticSourceType.FILE,
                measuredValue = "Brak per-parameter timestamps w LiveTelemetry",
                nominalCondition = "Niezależny timestamp i flaga ważności dla każdego PID",
                detailMessage = "Nie wszystkie pola LiveTelemetry mają jeszcze per-parameter freshness. W przypadku błędu parsowania ObdManager zachowuje poprzednią wartość."
            )
        }

        // ==========================================
        // 5. STORED / LOCAL HISTORY (ROOM DB)
        // ==========================================
        item {
            DiagnosticSectionHeader(
                title = "5. Historia lokalna i trwałość (Room DB)",
                badgeText = "ROOM v3 ACTIVE",
                badgeColor = ScannerStatusPass
            )
        }

        item {
            HealthStateRow(
                title = "Rejestr historycznych usterek (DTC Fault History)",
                statusText = "STORED IN ROOM",
                statusCode = HealthStatusCode.PASS,
                source = DiagnosticSourceType.FILE,
                measuredValue = "${faultHistory.size} zarejestrowanych wpisów",
                nominalCondition = "Trwałość SQLite WAL z MIGRATION_2_3",
                detailMessage = "Każdy zarejestrowany błąd posiada zapisany znacznik czasu i źródło weryfikacji."
            )
        }

        item {
            HealthStateRow(
                title = "Logi telemetrii & Dziennik eksploatacji",
                statusText = "DATABASE READY",
                statusCode = HealthStatusCode.PASS,
                source = DiagnosticSourceType.FILE,
                measuredValue = "${telemetryLogs.size} próbek telemetrii • ${serviceRecords.size} wpisów serwisu",
                nominalCondition = "Zgodność z architekturą MVVM",
                detailMessage = "Lokalna baza danych z bezpieczną migracją bez destruktywnego czyszczenia."
            )
        }

        // ==========================================
        // 6. FILE / IMPORT IDENTIFICATION STATUS
        // ==========================================
        item {
            DiagnosticSectionHeader(
                title = "6. Identyfikacja z plików i pochodzenie danych",
                badgeText = "CP2 BLOCKED",
                badgeColor = ScannerStatusWarning
            )
        }

        item {
            HealthStateRow(
                title = "Dopasowanie definicji ECU z pliku",
                statusText = "CANDIDATE",
                statusCode = HealthStatusCode.INFO,
                source = DiagnosticSourceType.CANDIDATE,
                measuredValue = "SID307_00F7_550_V05_20130313T104520.json (Kandydat historyczny)",
                nominalCondition = "CP2 source unavailable (Weryfikacja plików wstrzymana)",
                detailMessage = "Identyfikator autoidents z indeksu: {soft:00F7, diagver:129, supplier:4BE, ver:5500}. Brak fizycznych plików archiwów w środowisku."
            )
        }

        item {
            HealthStateRow(
                title = "Zewnętrzne archiwa bazy (ECUU.zip, OBD-II.txt)",
                statusText = "BLOCKED_PENDING_VERIFICATION",
                statusCode = HealthStatusCode.WARNING,
                source = DiagnosticSourceType.UNKNOWN,
                measuredValue = "Brak fizycznych plików w kontenerze",
                nominalCondition = "Wymagana fizyczna obecność plików przed odblokowaniem importu",
                detailMessage = "Zgodnie z regułą Least Privilege: komendy proprietary Renault/SID307 pozostają zablokowane."
            )
        }

        item {
            HealthStateRow(
                title = "Stan toru sprzętowego (Hardware I/O)",
                statusText = "HARDWARE_IO_OFF",
                statusCode = HealthStatusCode.PASS,
                source = DiagnosticSourceType.FILE,
                measuredValue = "Brak bezpośredniej transmisji do auta",
                nominalCondition = "Wyłącznie bezpieczna weryfikacja programowa",
                detailMessage = "Aplikacja działa w trybie bezpiecznym bez wykonywania fizycznych poleceń magistrali."
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    // Bluetooth Connection Dialog
    if (showBluetoothDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothDialog = false },
            title = {
                Text(
                    text = "Konfiguracja połączenia diagnostycznego",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = ScannerTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Wybierz sparowany adapter ELM327 Bluetooth lub uruchom tryb symulacji telemetrycznej:",
                        style = MaterialTheme.typography.bodySmall.copy(color = ScannerTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            viewModel.enableSimulation()
                            showBluetoothDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ScannerStatusWarning,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("URUCHOM TRYB SYMULACJI (4Hz)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (pairedDevices.isEmpty()) {
                        Text(
                            text = "Brak sparowanych urządzeń Bluetooth w systemie.",
                            style = MaterialTheme.typography.bodySmall.copy(color = ScannerTextMuted)
                        )
                    } else {
                        Text(
                            text = "Sparowane urządzenia Bluetooth:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ScannerTextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        pairedDevices.forEach { device ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(ScannerSurfaceElevated)
                                    .clickable {
                                        viewModel.connectBluetooth(device)
                                        showBluetoothDialog = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Column {
                                    Text(
                                        text = device.name ?: "Nieznane urządzenie",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = ScannerTextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = ScannerTextMuted,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                                Text("POŁĄCZ", color = ScannerAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBluetoothDialog = false }) {
                    Text("ZAMKNIJ", color = ScannerTextPrimary)
                }
            },
            containerColor = ScannerSurface
        )
    }

    // Protected Mode 04 Dialog
    if (showProtectedMode04Dialog) {
        AlertDialog(
            onDismissRequest = { showProtectedMode04Dialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ScannerStatusAlert,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Operacja chroniona: Kasowanie DTC (Mode 04)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = ScannerStatusAlert,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Polecenie Mode 04 usuwa zarejestrowane kody błędów oraz resetuje monitory gotowości diagnostycznej w sterowniku silnika (ECU).",
                        style = MaterialTheme.typography.bodySmall.copy(color = ScannerTextPrimary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Wymogi serwisowe:\n• Włączony zapłon (zapłon aktywny)\n• Zgaszony silnik (RPM = 0)\n• Dźwignia biegów w pozycji neutralnej",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = ScannerTextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "INFORMACJA BEZPIECZEŃSTWA: W bieżącym trybie bezpiecznym READ-ONLY firewall komend uniemożliwia fizyczną transmisję komendy 04 na magistralę pojazdu.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ScannerStatusAlert,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearActiveDtcCodes()
                        showProtectedMode04Dialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ScannerStatusAlert,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text("POTWIERDŹ ŻĄDANIE KASOWANIA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProtectedMode04Dialog = false }) {
                    Text("ANULUJ", color = ScannerTextSecondary)
                }
            },
            containerColor = ScannerSurface
        )
    }
}
