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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.example.data.model.RepairGuide
import com.example.ui.theme.AmberBose
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceVariant
import com.example.ui.theme.CyanHud
import com.example.ui.theme.DiagnosticGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.OverlordViewModel

@Composable
fun ArsenalScreen(
    viewModel: OverlordViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Poradniki DIY, 1 = Momenty Dokręcania

    val repairGuides = viewModel.repairGuides
    val selectedGuide by viewModel.selectedGuide.collectAsStateWithLifecycle()

    val torqueSpecs by viewModel.filteredTorqueSpecs.collectAsStateWithLifecycle()
    val torqueSearch by viewModel.torqueSearch.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

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
                text = { Text("Poradniki Napraw DIY", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Momenty Śrub (Nm)", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedSubTab == 0) {
            // Repair Guides
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        "BAZA PROCEDUR SERWISOWYCH RENAULT K9K 636",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                    )
                }

                items(repairGuides) { guide ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(AmberBose.copy(alpha = 0.3f), CockpitBorder))),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectGuide(guide) }
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
                                        guide.category,
                                        style = MaterialTheme.typography.labelSmall.copy(color = AmberBose, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(guide.timeRequired, style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                guide.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Poziom trudności: ${guide.difficulty}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (guide.difficulty.contains("Łatwy")) DiagnosticGreen else CyanHud,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    "${guide.steps.size} kroków • Kliknij by otworzyć",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        } else {
            // Torque Specs
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    OutlinedTextField(
                        value = torqueSearch,
                        onValueChange = { viewModel.setTorqueSearch(it) },
                        placeholder = { Text("Szukaj momentu (np. koła, olej, wtrysk, świeca)...", fontSize = 13.sp, color = TextMuted) },
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

                items(torqueSpecs) { spec ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(CyanHud.copy(alpha = 0.2f), CockpitBorder))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyanHud.copy(alpha = 0.15f))
                                    .border(1.dp, CyanHud, RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    spec.torqueNm,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyanHud,
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(spec.component, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                                Text("${spec.category} • ${spec.threadSize}", style = MaterialTheme.typography.labelSmall.copy(color = AmberBose, fontSize = 10.sp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(spec.notes, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // Modal Guide Dialog
    selectedGuide?.let { guide ->
        GuideDetailDialog(guide = guide, onDismiss = { viewModel.selectGuide(null) })
    }
}

@Composable
fun GuideDetailDialog(
    guide: RepairGuide,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CockpitSurfaceVariant,
        title = {
            Column {
                Text(guide.category.uppercase(), style = MaterialTheme.typography.labelSmall.copy(color = AmberBose, fontWeight = FontWeight.Bold))
                Text(guide.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                Text("Czas: ${guide.timeRequired} • Trudność: ${guide.difficulty}", style = MaterialTheme.typography.labelSmall.copy(color = CyanHud))
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("WYMAGANE NARZĘDZIA", style = MaterialTheme.typography.labelSmall.copy(color = CyanHud, fontWeight = FontWeight.Bold))
                    guide.toolsNeeded.forEach { tool ->
                        Text("• $tool", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                    }
                }

                item {
                    Text("CZĘŚCI I PŁYNY EKSPLOATACYJNE", style = MaterialTheme.typography.labelSmall.copy(color = AmberBose, fontWeight = FontWeight.Bold))
                    guide.partsNeeded.forEach { part ->
                        Text("• $part", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                    }
                }

                item {
                    Text("MOMENTY DOKRĘCANIA", style = MaterialTheme.typography.labelSmall.copy(color = DiagnosticGreen, fontWeight = FontWeight.Bold))
                    guide.torqueSpecs.forEach { t ->
                        Text("• $t", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                    }
                }

                item {
                    Text("INSTRUKCJA KROK PO KROKU", style = MaterialTheme.typography.labelSmall.copy(color = CyanHud, fontWeight = FontWeight.Bold))
                    guide.steps.forEachIndexed { idx, step ->
                        Text("${idx + 1}. $step", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AmberBose.copy(alpha = 0.15f))
                            .border(1.dp, AmberBose, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = AmberBose, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WSKAZÓWKA EKSPERTA K9K", style = MaterialTheme.typography.labelSmall.copy(color = AmberBose, fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(guide.proTips, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontSize = 11.sp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Gotowe", color = CyanHud, fontWeight = FontWeight.Bold)
            }
        }
    )
}
