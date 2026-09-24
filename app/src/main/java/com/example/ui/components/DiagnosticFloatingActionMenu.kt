package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberBose
import com.example.ui.theme.DiagnosticGreen
import com.example.ui.theme.ScannerAccent
import com.example.ui.theme.ScannerBorder
import com.example.ui.theme.ScannerSurface
import com.example.ui.theme.ScannerSurfaceElevated
import com.example.ui.theme.ScannerTextPrimary
import com.example.ui.theme.ScannerTextSecondary

/**
 * Floating Action Menu providing quick-access triggers for common diagnostic routines:
 * - Verify ECU (Weryfikuj ECU)
 * - Scan Sensors (Skanuj czujniki)
 * - Refresh Config (Odśwież konfigurację)
 */
@Composable
fun DiagnosticFloatingActionMenu(
    onVerifyEcu: () -> Unit,
    onScanSensors: () -> Unit,
    onRefreshConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 135f else 0f,
        label = "fab_rotation"
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.padding(bottom = 8.dp, end = 4.dp)
    ) {
        // Quick Action Items (Visible when expanded)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Action 1: Verify ECU
                QuickActionButton(
                    icon = Icons.Default.Memory,
                    label = "Verify ECU",
                    description = "Skan DTC Mode 03/07 i identyfikacja",
                    accentColor = DiagnosticGreen,
                    testTag = "fab_action_verify_ecu",
                    onClick = {
                        expanded = false
                        onVerifyEcu()
                    }
                )

                // Action 2: Scan Sensors
                QuickActionButton(
                    icon = Icons.Default.Sensors,
                    label = "Scan Sensors",
                    description = "Próbkowanie telemetrii K9K 636",
                    accentColor = ScannerAccent,
                    testTag = "fab_action_scan_sensors",
                    onClick = {
                        expanded = false
                        onScanSensors()
                    }
                )

                // Action 3: Refresh Config
                QuickActionButton(
                    icon = Icons.Default.Refresh,
                    label = "Refresh Config",
                    description = "Odświeżenie adaptera i urządzeń BT",
                    accentColor = AmberBose,
                    testTag = "fab_action_refresh_config",
                    onClick = {
                        expanded = false
                        onRefreshConfig()
                    }
                )
            }
        }

        // Main Toggle FAB
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = ScannerAccent,
            contentColor = Color.Black,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            shape = CircleShape,
            modifier = Modifier
                .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
                .testTag("fab_diagnostic_menu")
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Tune,
                contentDescription = if (expanded) "Zamknij menu diagnostyczne" else "Otwórz szybkie menu diagnostyczne",
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation)
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    description: String,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = ScannerSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ScannerBorder),
        shadowElevation = 4.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 10.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = ScannerTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ScannerTextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor, CircleShape)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
