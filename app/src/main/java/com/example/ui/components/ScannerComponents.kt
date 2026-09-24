package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.obd.DataVerificationStatus
import com.example.data.obd.ObdConnectionState
import com.example.ui.theme.ScannerAccent
import com.example.ui.theme.ScannerAccentDim
import com.example.ui.theme.ScannerBackground
import com.example.ui.theme.ScannerBorder
import com.example.ui.theme.ScannerBorderSubtle
import com.example.ui.theme.ScannerStatusAlert
import com.example.ui.theme.ScannerStatusAlertDim
import com.example.ui.theme.ScannerStatusNeutral
import com.example.ui.theme.ScannerStatusNeutralDim
import com.example.ui.theme.ScannerStatusPass
import com.example.ui.theme.ScannerStatusPassDim
import com.example.ui.theme.ScannerStatusWarning
import com.example.ui.theme.ScannerStatusWarningDim
import com.example.ui.theme.ScannerSurface
import com.example.ui.theme.ScannerSurfaceElevated
import com.example.ui.theme.ScannerTextMuted
import com.example.ui.theme.ScannerTextPrimary
import com.example.ui.theme.ScannerTextSecondary

/**
 * Diagnostic data source classification following strict provenance rules:
 * LIVE: Fresh data received directly from vehicle ECU / physical ELM327
 * FILE: Reference / history data from local Room DB or validated import file
 * SIMULATED: Generated synthetic demonstration data
 * UNKNOWN: No connection or missing diagnostic read
 * UNAVAILABLE: Parameter not physically supported or no live source implemented
 * CANDIDATE: Vehicle / ECU / address profile candidate not yet physically confirmed
 */
enum class DiagnosticSourceType(val label: String) {
    LIVE("LIVE"),
    FILE("PLIK"),
    SIMULATED("SYMULACJA"),
    UNKNOWN("BRAK DANYCH"),
    UNAVAILABLE("NIEDOSTĘPNE"),
    CANDIDATE("KANDYDAT")
}

/**
 * Standard Source Badge ensuring provenance is ALWAYS visible via a clear text label
 * and never inferred from color alone.
 */
@Composable
fun SourceBadge(
    source: DiagnosticSourceType,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    val (badgeBg, borderColor, textColor) = when (source) {
        DiagnosticSourceType.LIVE -> Triple(ScannerStatusPassDim, ScannerStatusPass, ScannerStatusPass)
        DiagnosticSourceType.FILE -> Triple(ScannerAccentDim, ScannerAccent, ScannerAccent)
        DiagnosticSourceType.SIMULATED -> Triple(ScannerStatusWarningDim, ScannerStatusWarning, ScannerStatusWarning)
        DiagnosticSourceType.UNKNOWN -> Triple(ScannerStatusNeutralDim, ScannerBorder, ScannerTextMuted)
        DiagnosticSourceType.UNAVAILABLE -> Triple(Color(0xFF1E2126), Color(0xFF4A515E), ScannerTextMuted)
        DiagnosticSourceType.CANDIDATE -> Triple(Color(0xFF2C2438), Color(0xFF9575CD), Color(0xFFB39DDB))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(badgeBg)
            .border(1.dp, borderColor, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .testTag("source_badge_${source.name.lowercase()}")
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(textColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (detail != null) "${source.label} • $detail" else source.label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        )
    }
}

/**
 * Compact Top Diagnostic Status Bar showing real-time transport, ECU, and protocol state.
 */
@Composable
fun DiagnosticStatusBar(
    connectionState: ObdConnectionState,
    connectionStatusText: String,
    isSimulated: Boolean,
    onConfigureClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (stateColor, stateBg, stateIcon, stateText) = when {
        isSimulated -> QuadStatus(
            ScannerStatusWarning,
            ScannerStatusWarningDim,
            Icons.Default.Refresh,
            "SYMULACJA 4Hz"
        )
        connectionState == ObdConnectionState.READING -> QuadStatus(
            ScannerStatusPass,
            ScannerStatusPassDim,
            Icons.Default.BluetoothConnected,
            "ODCZYT CAN"
        )
        connectionState == ObdConnectionState.CONNECTED -> QuadStatus(
            ScannerStatusPass,
            ScannerStatusPassDim,
            Icons.Default.BluetoothConnected,
            "ELM327 OK"
        )
        connectionState == ObdConnectionState.CONNECTING -> QuadStatus(
            ScannerAccent,
            ScannerAccentDim,
            Icons.Default.BluetoothSearching,
            "ŁĄCZENIE"
        )
        connectionState == ObdConnectionState.ERROR -> QuadStatus(
            ScannerStatusAlert,
            ScannerStatusAlertDim,
            Icons.Default.BluetoothDisabled,
            "BŁĄD BT"
        )
        else -> QuadStatus(
            ScannerTextSecondary,
            ScannerSurfaceElevated,
            Icons.Default.Bluetooth,
            "ROZŁĄCZONY"
        )
    }

    Surface(
        color = ScannerSurface,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ScannerBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("diagnostic_status_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Left: Protocol & ECU Identification
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isSimulated) "PROFIL: K9K 636 (DEMO)" else "ECU: PROFIL SID307 [CANDIDATE]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ScannerTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSimulated) "SYMULACJA ISO 15765-4" else if (connectionState == ObdConnectionState.READING || connectionState == ObdConnectionState.CONNECTED) "CAN (OCZEKUJE NA IDENTYFIKACJĘ ECU)" else "ROZŁĄCZONY [HARDWARE NOT VERIFIED]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ScannerTextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
                Text(
                    text = connectionStatusText,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ScannerTextSecondary,
                        fontSize = 10.sp
                    )
                )
            }

            // Right: Connection State Badge & Action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(stateBg)
                    .border(1.dp, stateColor, RoundedCornerShape(3.dp))
                    .then(if (onConfigureClick != null) Modifier.clickable { onConfigureClick() } else Modifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = stateIcon,
                    contentDescription = stateText,
                    tint = stateColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = stateColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    }
}

/**
 * Diagnostic Section Header with clean OEM divider and optional status chip.
 */
@Composable
fun DiagnosticSectionHeader(
    title: String,
    badgeText: String? = null,
    badgeColor: Color = ScannerAccent,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = ScannerTextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(1.dp, badgeColor, RoundedCornerShape(2.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        HorizontalDivider(color = ScannerBorderSubtle, thickness = 1.dp)
    }
}

/**
 * Evidence-driven Health State Row displaying explicit diagnostic observation.
 */
@Composable
fun HealthStateRow(
    title: String,
    statusText: String,
    statusCode: HealthStatusCode,
    source: DiagnosticSourceType,
    measuredValue: String? = null,
    nominalCondition: String? = null,
    detailMessage: String? = null,
    tooltipText: String? = null,
    modifier: Modifier = Modifier
) {
    var showTooltipDialog by remember { mutableStateOf(false) }
    val (statusBg, statusBorder, statusIcon, _) = when (statusCode) {
        HealthStatusCode.PASS -> QuadStatus(ScannerStatusPassDim, ScannerStatusPass, Icons.Default.CheckCircle, statusText)
        HealthStatusCode.ALERT -> QuadStatus(ScannerStatusAlertDim, ScannerStatusAlert, Icons.Default.Warning, statusText)
        HealthStatusCode.WARNING -> QuadStatus(ScannerStatusWarningDim, ScannerStatusWarning, Icons.Default.Warning, statusText)
        HealthStatusCode.NOT_SCANNED -> QuadStatus(ScannerStatusNeutralDim, ScannerBorder, Icons.Default.Info, statusText)
        HealthStatusCode.INFO -> QuadStatus(ScannerAccentDim, ScannerAccent, Icons.Default.Info, statusText)
    }

    Surface(
        color = ScannerSurface,
        shape = RoundedCornerShape(3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ScannerBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag("health_row_${title.lowercase().replace(" ", "_")}")
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Top: Title, Source Badge, and Status Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = ScannerTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    SourceBadge(source = source)
                    if (tooltipText != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        androidx.compose.material3.IconButton(
                            onClick = { showTooltipDialog = true },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("info_button_${title.lowercase().replace(" ", "_")}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Informacja o statusie $title",
                                tint = ScannerAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Evidence status tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(statusBg)
                        .border(1.dp, statusBorder, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusBorder,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = statusBorder,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            // Middle: Measured Value vs Nominal (if applicable)
            if (measuredValue != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(ScannerSurfaceElevated)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Odczyt: $measuredValue",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ScannerTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )
                    if (nominalCondition != null) {
                        Text(
                            text = "Norma: $nominalCondition",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ScannerTextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }

            // Bottom: Diagnostic Note / Detail
            if (detailMessage != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = detailMessage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ScannerTextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                )
            }
        }
    }

    if (showTooltipDialog && tooltipText != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTooltipDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = ScannerAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wyjaśnienie statusu: $title",
                        style = MaterialTheme.typography.titleMedium.copy(color = ScannerTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = tooltipText,
                        style = MaterialTheme.typography.bodySmall.copy(color = ScannerTextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showTooltipDialog = false }) {
                    Text("ZAMKNIJ", color = ScannerTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            containerColor = ScannerSurface
        )
    }
}

/**
 * Compact Parameter Row for Live Data table layout.
 * Displays: PARAMETER, VALUE, UNIT, SOURCE, FRESHNESS.
 */
@Composable
fun ParameterRow(
    parameterName: String,
    pidHex: String,
    valueString: String,
    unit: String,
    source: DiagnosticSourceType,
    freshness: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ScannerSurface,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, ScannerBorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .testTag("param_row_$pidHex")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            // Parameter & PID
            Column(modifier = Modifier.weight(1.3f)) {
                Text(
                    text = parameterName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ScannerTextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = "PID: $pidHex",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ScannerTextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            // Value & Unit (Monospace OEM display)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1.0f).padding(horizontal = 6.dp)
            ) {
                Text(
                    text = valueString,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (source == DiagnosticSourceType.UNKNOWN) ScannerTextMuted else ScannerTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                )
                if (unit.isNotBlank()) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ScannerTextMuted,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            // Source & Freshness
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.9f)) {
                SourceBadge(source = source)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = freshness,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ScannerTextMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    }
}

/**
 * Compact Action Bar separating READ ACTIONS from STATE-CHANGING / PROTECTED ACTIONS.
 */
@Composable
fun CompactActionBar(
    onScanDtcClick: () -> Unit,
    onRunHealthCheckClick: () -> Unit,
    onClearDtcProtectedClick: () -> Unit,
    isScanning: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ScannerSurface,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ScannerBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("compact_action_bar")
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Read Actions Row (Safe, non-destructive)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onScanDtcClick,
                    enabled = !isScanning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ScannerAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag("action_scan_dtc")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SKANUJ DTC (MODE 03/07)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRunHealthCheckClick,
                    enabled = !isScanning,
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ScannerTextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ScannerBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag("action_run_health_check")
                ) {
                    Text("STAN SYSTEMU", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Protected / State-changing Section (Mode 04 - CLEAR DTC)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(ScannerStatusAlertDim)
                    .border(1.dp, ScannerStatusAlert.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Ostrzeżenie",
                        tint = ScannerStatusAlert,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "OPERACJA CHRONIONA • MODE 04",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ScannerStatusAlert,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text(
                            text = "Kasowanie pamięci ECU (blokowane w trybie READ-ONLY)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ScannerTextMuted,
                                fontSize = 8.sp
                            )
                        )
                    }
                }

                OutlinedButton(
                    onClick = onClearDtcProtectedClick,
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ScannerStatusAlert
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ScannerStatusAlert),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("action_clear_dtc_protected")
                ) {
                    Text("KASUJ DTC", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

enum class HealthStatusCode {
    PASS,
    ALERT,
    WARNING,
    NOT_SCANNED,
    INFO
}

private data class QuadStatus<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
