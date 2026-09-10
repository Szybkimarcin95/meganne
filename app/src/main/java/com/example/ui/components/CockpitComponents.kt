package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.platform.testTag
import com.example.data.model.VehicleSpec
import com.example.data.obd.ObdConnectionState
import com.example.ui.theme.AmberBose
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceVariant
import com.example.ui.theme.CyanHud
import com.example.ui.theme.CyanHudDim
import com.example.ui.theme.DiagnosticGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningRed

@Composable
fun CockpitGauge(
    value: Float,
    minValue: Float,
    maxValue: Float,
    title: String,
    unit: String,
    modifier: Modifier = Modifier,
    size: Dp = 130.dp,
    gaugeColor: Color = CyanHud,
    warningThreshold: Float? = null
) {
    val progress = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "gaugeProgress")

    val activeColor = if (warningThreshold != null && value >= warningThreshold) {
        WarningRed
    } else {
        gaugeColor
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(CockpitBorder, Color(0xFF131D2D)))),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
                Canvas(modifier = Modifier.size(size)) {
                    val strokeWidth = 10.dp.toPx()
                    val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
                    val arcOffset = Offset(strokeWidth / 2, strokeWidth / 2)

                    // Track background arc (240 degrees)
                    drawArc(
                        color = Color(0xFF152235),
                        startAngle = 150f,
                        sweepAngle = 240f,
                        useCenter = false,
                        topLeft = arcOffset,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active value arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(activeColor.copy(alpha = 0.4f), activeColor)
                        ),
                        startAngle = 150f,
                        sweepAngle = 240f * animatedProgress,
                        useCenter = false,
                        topLeft = arcOffset,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val formattedVal = if (value < 10 && value != value.toInt().toFloat()) {
                        "%.2f".format(value)
                    } else if (value < 100) {
                        "%.1f".format(value)
                    } else {
                        value.toInt().toString()
                    }

                    Text(
                        text = formattedVal,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = activeColor
                        )
                    )
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "${minValue.toInt()}",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp)
                )
                Text(
                    text = "${maxValue.toInt()}",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp)
                )
            }
        }
    }
}

@Composable
fun VehicleHeaderCard(
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CockpitSurfaceVariant),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(CyanHudDim, AmberBose.copy(alpha = 0.3f)))),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(DiagnosticGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CSDP AKTYWNY • SYSTEM ONLINE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = DiagnosticGreen,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AmberBose.copy(alpha = 0.2f))
                        .border(1.dp, AmberBose, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "BOSE EDITION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AmberBose,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "${VehicleSpec.MAKE} ${VehicleSpec.MODEL}",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
            )

            Text(
                text = "${VehicleSpec.ENGINE_DESC} • Skrzynia ${VehicleSpec.GEARBOX_TYPE} ${VehicleSpec.GEARBOX_DESIGNATION}",
                style = MaterialTheme.typography.bodyMedium.copy(color = CyanHud)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Technical Grid
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A101A))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    Text("VIN", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                    Text(
                        VehicleSpec.VIN,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )
                }
                Column {
                    Text("KOD SILNIKA", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                    Text(
                        "${VehicleSpec.ENGINE_CODE} ${VehicleSpec.ENGINE_DESIGNATION}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AmberBose,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )
                }
                Column {
                    Text("TYP", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                    Text(
                        VehicleSpec.VEHICLE_TYPE,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SystemHealthCard(
    title: String,
    status: String,
    isHealthy: Boolean,
    detail: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(CockpitBorder, Color(0xFF131D2D)))),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = if (isHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isHealthy) DiagnosticGreen else WarningRed,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
                Text(
                    text = "$status • $detail",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isHealthy) TextSecondary else WarningRed,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

/**
 * Visual indicator showing the real-time Bluetooth connection state between the Android device
 * and the ELM327 adapter.
 *
 * Supports DISCONNECTED, CONNECTING (pulsing cyan), CONNECTED/READING (green), and ERROR (red) states,
 * with explicit labeling when running in synthetic simulation mode.
 */
@Composable
fun BluetoothConnectionIndicator(
    connectionState: ObdConnectionState,
    connectionStatusText: String,
    isSimulated: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bluetooth_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val (statusColor, badgeBg, icon, badgeText) = when {
        isSimulated -> {
            Quad(
                AmberBose,
                AmberBose.copy(alpha = 0.15f),
                Icons.Default.Sync,
                "SYMULACJA 4Hz"
            )
        }
        connectionState == ObdConnectionState.CONNECTED || connectionState == ObdConnectionState.READING -> {
            Quad(
                DiagnosticGreen,
                DiagnosticGreen.copy(alpha = 0.15f),
                Icons.Default.BluetoothConnected,
                if (connectionState == ObdConnectionState.READING) "ODCZYT OBD-II" else "POŁĄCZONO ELM"
            )
        }
        connectionState == ObdConnectionState.CONNECTING -> {
            Quad(
                CyanHud,
                CyanHud.copy(alpha = 0.20f),
                Icons.Default.BluetoothSearching,
                "ŁĄCZENIE..."
            )
        }
        connectionState == ObdConnectionState.ERROR -> {
            Quad(
                WarningRed,
                WarningRed.copy(alpha = 0.20f),
                Icons.Default.BluetoothDisabled,
                "BŁĄD POŁĄCZENIA"
            )
        }
        else -> { // DISCONNECTED
            Quad(
                TextSecondary,
                CockpitSurfaceVariant,
                Icons.Default.Bluetooth,
                "ROZŁĄCZONO"
            )
        }
    }

    val isTransitioning = connectionState == ObdConnectionState.CONNECTING ||
            (isSimulated.not() && connectionState == ObdConnectionState.READING)
    val dotAlpha = if (isTransitioning) pulseAlpha else 1.0f

    Card(
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(statusColor.copy(alpha = 0.5f), CockpitBorder)
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("bluetooth_connection_indicator")
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Pulsing dot / status indicator ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.25f * dotAlpha))
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = dotAlpha))
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "ADAPTER ELM327 BLUETOOTH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = connectionStatusText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status chip badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeBg)
                    .border(1.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Bluetooth Status",
                        tint = statusColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = statusColor,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            fontSize = 9.sp
                        )
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

