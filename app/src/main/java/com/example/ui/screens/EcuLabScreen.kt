package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.eculab.EcuLabServiceFilter
import com.example.ui.eculab.EcuLabSort
import com.example.ui.eculab.EcuLabStatus
import com.example.ui.eculab.EcuLabUiState
import com.example.ui.eculab.EcuReadResult
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

@Composable
fun EcuLabScreen(
    state: EcuLabUiState,
    onQueryChange: (String) -> Unit,
    onStatusFilterChange: (EcuLabStatus?) -> Unit,
    onServiceFilterChange: (EcuLabServiceFilter) -> Unit,
    onGroupBySessionChange: (Boolean) -> Unit,
    onSortChange: (EcuLabSort) -> Unit,
    onCancelScan: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        EcuLabSummary(state)

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text("Szukaj po nazwie lub request/DID") },
            placeholder = { Text("np. soot, rail, 0x2224") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ecu_lab_search")
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusFilterChip("ALL", state.statusFilter == null) { onStatusFilterChange(null) }
            EcuLabStatus.entries.forEach { status ->
                StatusFilterChip(status.name, state.statusFilter == status) {
                    onStatusFilterChange(status)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EcuLabServiceFilter.entries.forEach { filter ->
                StatusFilterChip(
                    label = when (filter) {
                        EcuLabServiceFilter.ALL -> "ALL"
                        EcuLabServiceFilter.READ_22 -> "READ 0x22"
                        EcuLabServiceFilter.DTC_19 -> "DTC 0x19"
                        EcuLabServiceFilter.MEMORY_21 -> "MEMORY 0x21"
                    },
                    selected = state.serviceFilter == filter
                ) { onServiceFilterChange(filter) }
            }

            Text("Grupuj sesją", color = TextSecondary, fontSize = 11.sp)
            Switch(
                checked = state.groupBySession,
                onCheckedChange = onGroupBySessionChange,
                modifier = Modifier.testTag("ecu_lab_group_session")
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EcuLabSort.entries.forEach { sort ->
                StatusFilterChip(
                    label = when (sort) {
                        EcuLabSort.REQUEST -> "Request"
                        EcuLabSort.NAME -> "Name"
                        EcuLabSort.STATUS -> "Status"
                        EcuLabSort.VALUE_ASC -> "Value ↑"
                        EcuLabSort.VALUE_DESC -> "Value ↓"
                    },
                    selected = state.sort == sort
                ) { onSortChange(sort) }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCancelScan,
                enabled = state.isScanning && !state.cancelRequested,
                colors = ButtonDefaults.buttonColors(containerColor = WarningRed),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ecu_lab_cancel")
            ) {
                Text(if (state.cancelRequested) "CANCELLING…" else "Cancel Scan")
            }
            Button(
                onClick = onExport,
                enabled = state.results.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ecu_lab_export")
            ) {
                Text("Export JSON")
            }
        }

        Spacer(Modifier.height(8.dp))

        EcuLabResultHeader()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("ecu_lab_results"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items = state.filteredResults,
                key = { "${it.requestHex}:${it.sourceLine}" }
            ) { result ->
                EcuLabResultRow(result)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun EcuLabSummary(state: EcuLabUiState) {
    val c = state.counters
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CockpitSurface, RoundedCornerShape(12.dp))
            .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(
            "SID307 ECU LAB — ${state.totalRequests} READ requests",
            color = TextPrimary,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CounterBadge("SUPPORTED", c.supported, DiagnosticGreen, "count_supported")
            CounterBadge("NRC", c.nrc, AmberBose, "count_nrc")
            CounterBadge("TIMEOUT", c.timeout, WarningRed, "count_timeout")
            CounterBadge("SESSION", c.sessionRequired, CyanHud, "count_session")
            CounterBadge("NOT TESTED", c.notTested, TextMuted, "count_not_tested")
            CounterBadge("CANCELLED", c.cancelled, TextSecondary, "count_cancelled")
        }

        Spacer(Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { state.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .testTag("ecu_lab_progress"),
            color = CyanHud,
            trackColor = CockpitSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        val percent = (state.progressFraction * 100).toInt()
        val eta = state.etaSeconds?.let { "~${it}s" } ?: "—"
        Text(
            "Progress: ${state.completedCount}/${state.totalRequests} (${percent}%)  •  ETA: ${eta}",
            color = TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun CounterBadge(label: String, count: Int, color: Color, tag: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag(tag)
    ) {
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(count.toString(), color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
private fun StatusFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 10.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyanHud.copy(alpha = 0.18f),
            selectedLabelColor = CyanHud
        )
    )
}

@Composable
private fun EcuLabResultHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CockpitSurfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text("REQUEST", color = TextMuted, fontSize = 9.sp, modifier = Modifier.width(78.dp))
        Text("NAME / VALUE", color = TextMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
        Text("STATUS", color = TextMuted, fontSize = 9.sp, modifier = Modifier.width(94.dp))
    }
}

@Composable
private fun EcuLabResultRow(result: EcuReadResult) {
    var expanded by rememberSaveable(result.requestHex, result.sourceLine) { mutableStateOf(false) }
    val color = statusColor(result.status)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CockpitSurface, RoundedCornerShape(9.dp))
            .border(1.dp, CockpitBorder, RoundedCornerShape(9.dp))
            .clickable { expanded = !expanded }
            .padding(8.dp)
            .testTag("ecu_row_${result.requestHex}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                result.displayRequest,
                color = CyanHud,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.width(78.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.name,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    maxLines = 2
                )
                Text(
                    physicalValueText(result),
                    color = if (result.status == EcuLabStatus.SUPPORTED) DiagnosticGreen else TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }

            Box(
                modifier = Modifier
                    .width(94.dp)
                    .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                    .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            ) {
                Text(
                    result.status.name,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Service: 0x${result.service.toString(16).uppercase()}  •  Session: ${result.requiredSession ?: "—"}",
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
            if (result.nrc != null) {
                Text(
                    "NRC: 0x${result.nrc.toString(16).uppercase().padStart(2, '0')} ${result.nrcMeaning.orEmpty()}",
                    color = AmberBose,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
            if (!result.raw.isNullOrBlank()) {
                Text(
                    "RAW: ${result.raw}",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
            Text(
                "Source: ${result.sourceFile}:${result.sourceLine}",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }
    }
}

private fun physicalValueText(result: EcuReadResult): String {
    if (result.status != EcuLabStatus.SUPPORTED) return "—"
    val value = result.physicalValue?.toString() ?: "—"
    return listOf(value, result.unit).filterNotNull().joinToString(" ")
}

private fun statusColor(status: EcuLabStatus): Color = when (status) {
    EcuLabStatus.SUPPORTED -> DiagnosticGreen
    EcuLabStatus.NRC -> AmberBose
    EcuLabStatus.TIMEOUT -> WarningRed
    EcuLabStatus.SESSION_REQUIRED -> CyanHud
    EcuLabStatus.NOT_TESTED -> TextMuted
    EcuLabStatus.CANCELLED -> TextSecondary
}
