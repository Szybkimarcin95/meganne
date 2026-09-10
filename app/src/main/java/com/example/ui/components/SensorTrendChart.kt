package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SensorTrendPoint
import com.example.data.obd.DataVerificationStatus
import com.example.ui.theme.AmberBose
import com.example.ui.theme.CockpitBackground
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SensorTrendCard(
    sensorTitle: String,
    sensorSubtitle: String,
    points: List<SensorTrendPoint>,
    unit: String,
    accentColor: Color = CyanHud,
    onSampleNow: (() -> Unit)? = null,
    onClearHistory: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedPoint by remember { mutableStateOf<SensorTrendPoint?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(accentColor.copy(alpha = 0.45f), CockpitBorder)
            )
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title, Subtitle, latest value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sensorTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Text(
                        text = sensorSubtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                // Latest value badge
                val latest = points.lastOrNull()
                if (latest != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format(Locale.US, "%.2f %s", latest.value, unit),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = accentColor
                            )
                        )
                        StatusBadge(status = latest.status)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (points.isNotEmpty()) {
                // Summary Stats Row (Min, Max, Avg, Count)
                val minVal = points.minOf { it.value }
                val maxVal = points.maxOf { it.value }
                val avgVal = points.map { it.value }.average().toFloat()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CockpitSurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatMetric(label = "MIN", value = String.format(Locale.US, "%.2f", minVal), unit = unit)
                    StatMetric(label = "ŚREDNIA", value = String.format(Locale.US, "%.2f", avgVal), unit = unit)
                    StatMetric(label = "MAX", value = String.format(Locale.US, "%.2f", maxVal), unit = unit)
                    StatMetric(label = "PRÓBKI", value = "${points.size}", unit = "pkt")
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Native Compose Line Chart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CockpitBackground)
                        .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp, horizontal = 12.dp)
                ) {
                    SensorTrendCanvas(
                        points = points,
                        accentColor = accentColor,
                        selectedPoint = selectedPoint,
                        onPointSelected = { selectedPoint = it }
                    )

                    // Callout HUD if a point is selected
                    selectedPoint?.let { pt ->
                        val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CockpitSurfaceVariant.copy(alpha = 0.92f))
                                .border(1.dp, accentColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.2f %s", pt.value, unit)} @ ${timeFmt.format(Date(pt.timestamp))}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            )
                        }
                    }
                }
            } else {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CockpitBackground)
                        .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Brak zarejestrowanych próbek sensora",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                        Text(
                            text = "Dotknij 'Próbkuj teraz', aby zarejestrować bieżący stan.",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onClearHistory != null && points.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        colors = ButtonDefaults.textButtonColors(contentColor = WarningRed.copy(alpha = 0.8f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Wyczyść historię", fontSize = 11.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (onSampleNow != null) {
                    OutlinedButton(
                        onClick = onSampleNow,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                        border = ButtonDefaults.outlinedButtonBorder().copy(brush = Brush.horizontalGradient(listOf(accentColor, accentColor.copy(alpha = 0.5f)))),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Próbkuj teraz", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SensorTrendCanvas(
    points: List<SensorTrendPoint>,
    accentColor: Color,
    selectedPoint: SensorTrendPoint?,
    onPointSelected: (SensorTrendPoint?) -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedPoints = remember(points) { points.sortedBy { it.timestamp } }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(sortedPoints) {
                detectTapGestures { offset ->
                    if (sortedPoints.isEmpty()) return@detectTapGestures
                    val w = size.width.toFloat()
                    val stepX = if (sortedPoints.size > 1) w / (sortedPoints.size - 1) else w / 2f
                    var closest: SensorTrendPoint? = null
                    var closestDist = Float.MAX_VALUE
                    sortedPoints.forEachIndexed { idx, pt ->
                        val px = if (sortedPoints.size == 1) w / 2f else idx * stepX
                        val dist = kotlin.math.abs(offset.x - px)
                        if (dist < closestDist && dist < 48f) {
                            closestDist = dist
                            closest = pt
                        }
                    }
                    onPointSelected(closest)
                }
            }
    ) {
        val w = size.width
        val h = size.height

        if (sortedPoints.isEmpty()) return@Canvas

        // Calculate data bounds
        val values = sortedPoints.map { it.value }
        val rawMin = values.minOrNull() ?: 0f
        val rawMax = values.maxOrNull() ?: 1f
        val range = if (rawMax == rawMin) 1f else (rawMax - rawMin)
        val pad = range * 0.15f
        val minY = rawMin - pad
        val maxY = rawMax + pad
        val totalRange = maxY - minY

        // Draw horizontal grid lines (3 lines: top, mid, bottom)
        val gridLines = listOf(0.1f, 0.5f, 0.9f)
        gridLines.forEach { ratio ->
            val y = h * ratio
            drawLine(
                color = CockpitBorder.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )
        }

        if (sortedPoints.size == 1) {
            // Single point display
            val pt = sortedPoints.first()
            val cy = h * 0.5f
            val cx = w * 0.5f
            drawCircle(
                color = accentColor.copy(alpha = 0.3f),
                radius = 16f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = accentColor,
                radius = 6f,
                center = Offset(cx, cy)
            )
            return@Canvas
        }

        val stepX = w / (sortedPoints.size - 1)

        val coords = sortedPoints.mapIndexed { idx, pt ->
            val x = idx * stepX
            val normalizedY = 1f - ((pt.value - minY) / totalRange).coerceIn(0f, 1f)
            val y = normalizedY * h
            Offset(x, y)
        }

        // Build path for curve
        val linePath = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            for (i in 0 until coords.size - 1) {
                val p0 = coords[i]
                val p1 = coords[i + 1]
                val controlX1 = p0.x + (p1.x - p0.x) / 2f
                val controlY1 = p0.y
                val controlX2 = p0.x + (p1.x - p0.x) / 2f
                val controlY2 = p1.y
                cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
            }
        }

        // Fill path under the curve
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(coords.last().x, h)
            lineTo(coords.first().x, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.35f),
                    accentColor.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h
            )
        )

        // Draw line curve
        drawPath(
            path = linePath,
            color = accentColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        // Draw dots on data points
        coords.forEachIndexed { index, offset ->
            val pt = sortedPoints[index]
            val isSelected = selectedPoint?.id == pt.id && pt.id != 0L || selectedPoint === pt
            if (isSelected) {
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = offset
                )
                drawCircle(
                    color = accentColor,
                    radius = 5.dp.toPx(),
                    center = offset
                )
            } else {
                drawCircle(
                    color = CockpitBackground,
                    radius = 4.dp.toPx(),
                    center = offset
                )
                drawCircle(
                    color = accentColor,
                    radius = 2.5.dp.toPx(),
                    center = offset
                )
            }
        }
    }
}

@Composable
private fun StatMetric(
    label: String,
    value: String,
    unit: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary
            )
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                fontSize = 8.sp
            )
        )
    }
}

@Composable
private fun StatusBadge(status: DataVerificationStatus) {
    val (color, text) = when (status) {
        DataVerificationStatus.VERIFIED -> Pair(DiagnosticGreen, "VERIFIED")
        DataVerificationStatus.MEASURED -> Pair(CyanHud, "LIVE OBD")
        DataVerificationStatus.SIMULATED -> Pair(AmberBose, "SIMULATION")
        DataVerificationStatus.USER_PROVIDED -> Pair(AmberBose, "USER")
        DataVerificationStatus.INFERRED -> Pair(AmberBose, "INFERRED")
        DataVerificationStatus.UNVERIFIED -> Pair(WarningRed, "UNVERIFIED")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color,
                fontWeight = FontWeight.Black,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}
