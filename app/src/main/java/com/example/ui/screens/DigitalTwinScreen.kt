package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.model.EngineComponent
import com.example.data.model.FuseItem
import com.example.ui.components.SensorTrendCard
import com.example.ui.theme.AmberBose
import com.example.ui.theme.CockpitBackground
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

fun getComponentSensorUnit(id: String): String = when (id) {
    "turbocharger" -> "bar"
    "map_sensor" -> "kPa"
    "hp_fuel_pump" -> "bar"
    "dpf_differential" -> "g"
    "piezo_injectors" -> "mg/skok"
    "egr_valve" -> "%"
    "glow_plugs" -> "V"
    else -> "wartość"
}

@Composable
fun DigitalTwinScreen(
    viewModel: OverlordViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Bezpieczniki, 1 = Komora K9K, 2 = Trendy

    val fuses by viewModel.filteredFuses.collectAsStateWithLifecycle()
    val selectedFuse by viewModel.selectedFuse.collectAsStateWithLifecycle()
    val searchQuery by viewModel.fuseSearchQuery.collectAsStateWithLifecycle()
    val locationFilter by viewModel.fuseLocationFilter.collectAsStateWithLifecycle()

    val engineComponents = viewModel.engineComponents
    val selectedEngineComponent by viewModel.selectedEngineComponent.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

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
                text = { Text("Bezpieczniki", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Komora K9K", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                text = { Text("Trendy Sensorów", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedSubTab) {
            0 -> {
                // Fuse Box View
                FuseBoxView(
                    fuses = fuses,
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.setFuseSearch(it) },
                    locationFilter = locationFilter,
                    onFilterChange = { viewModel.setFuseFilter(it) },
                    onFuseClick = { viewModel.selectFuse(it) }
                )
            }
            1 -> {
                // Engine Bay View
                EngineBayView(
                    components = engineComponents,
                    onComponentClick = { viewModel.selectEngineComponent(it) }
                )
            }
            else -> {
                // Sensor Trends View
                SensorTrendsView(
                    viewModel = viewModel,
                    components = engineComponents
                )
            }
        }
    }

    // Modal dialog for Fuse details
    selectedFuse?.let { fuse ->
        FuseDetailDialog(fuse = fuse, onDismiss = { viewModel.selectFuse(null) })
    }

    // Modal dialog for Engine Component details
    selectedEngineComponent?.let { component ->
        EngineComponentDialog(
            component = component,
            viewModel = viewModel,
            onDismiss = { viewModel.selectEngineComponent(null) }
        )
    }
}

@Composable
fun FuseBoxView(
    fuses: List<FuseItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    locationFilter: String,
    onFilterChange: (String) -> Unit,
    onFuseClick: (FuseItem) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Search & Filter
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Szukaj bezpiecznika (np. F1, radio, ECU, 15A)...", fontSize = 13.sp, color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanHud) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                        }
                    }
                },
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

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Wszystkie", "UPC", "BSI")) { filter ->
                    val isSelected = locationFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterChange(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    "UPC" -> "UPC (Komora silnika)"
                                    "BSI" -> "BSI (W kabinie pod kierownicą)"
                                    else -> "Wszystkie moduły"
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanHud.copy(alpha = 0.2f),
                            selectedLabelColor = CyanHud,
                            containerColor = CockpitSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) CyanHud else CockpitBorder
                        )
                    )
                }
            }
        }

        // Fuses List
        items(fuses) { fuse ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(Color(fuse.colorHex).copy(alpha = 0.5f), CockpitBorder))),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFuseClick(fuse) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    // Fuse physical appearance badge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(fuse.colorHex).copy(alpha = 0.25f))
                            .border(2.dp, Color(fuse.colorHex), RoundedCornerShape(8.dp))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = fuse.id,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(fuse.colorHex)
                                )
                            )
                            Text(
                                text = "${fuse.ratingAmps}A",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = fuse.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = fuse.protectedCircuit,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 12.sp
                            ),
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF0A111C))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = fuse.location.take(18) + "...",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = AmberBose,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "OEM: ${fuse.oemNumber}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun FuseDetailDialog(
    fuse: FuseItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CockpitSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(fuse.colorHex))
                ) {
                    Text(
                        text = "${fuse.ratingAmps}A",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${fuse.id} • ${fuse.name}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    Text(
                        text = fuse.location,
                        style = MaterialTheme.typography.labelSmall.copy(color = AmberBose)
                    )
                }
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("CHRONIONY OBWÓD ELEKTRYCZNY", style = MaterialTheme.typography.labelSmall.copy(color = CyanHud, fontWeight = FontWeight.Bold))
                    Text(fuse.protectedCircuit, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                }

                item {
                    Text("KOD WIĄZKI / LINIA", style = MaterialTheme.typography.labelSmall.copy(color = CyanHud, fontWeight = FontWeight.Bold))
                    Text(fuse.wireCode, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontFamily = FontFamily.Monospace))
                }

                item {
                    Text("OBJAWY PRZEPALENIA (AWARII)", style = MaterialTheme.typography.labelSmall.copy(color = WarningRed, fontWeight = FontWeight.Bold))
                    Text(fuse.failureSymptoms, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                }

                item {
                    Text("INSTRUKCJA WYMIANY I ZAMIENNIKI", style = MaterialTheme.typography.labelSmall.copy(color = DiagnosticGreen, fontWeight = FontWeight.Bold))
                    Text(fuse.replacementGuide, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                    Text("Numer katalogowy OEM: ${fuse.oemNumber}", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij", color = CyanHud, fontWeight = FontWeight.Bold)
            }
        }
    )
}


@Composable
fun EngineBayView(
    components: List<EngineComponent>,
    onComponentClick: (EngineComponent) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Engine Bay Wireframe Schematic
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(CyanHud.copy(alpha = 0.4f), CockpitBorder))),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Futuristic Blueprint grid background
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Draw engine block outline
                        drawRoundRect(
                            color = Color(0xFF132035),
                            topLeft = androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.18f),
                            size = androidx.compose.ui.geometry.Size(w * 0.70f, h * 0.65f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f)
                        )

                        // 4 Cylinders K9K
                        for (i in 0..3) {
                            drawCircle(
                                color = Color(0xFF1E3250),
                                radius = 22f,
                                center = androidx.compose.ui.geometry.Offset(w * (0.32f + i * 0.12f), h * 0.50f)
                            )
                        }
                    }

                    // Hotspots
                    components.forEach { comp ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(
                                    start = (comp.xRatio * 300).dp,
                                    top = (comp.yRatio * 180).dp
                                )
                                .clip(CircleShape)
                                .background(CyanHud)
                                .border(2.dp, Color.White, CircleShape)
                                .clickable { onComponentClick(comp) }
                                .padding(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = comp.name,
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // Info overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC080C14))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "K9K 636 • SCHEMAT INTERAKTYWNY (Dotknij punktu lub listy)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = CyanHud,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        )
                    }
                }
            }
        }

        // List of all components with category
        item {
            Text(
                text = "ZESPOŁY I CZUJNIKI SILNIKA 1.5 dCi",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            )
        }

        items(components) { comp ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(CyanHud.copy(alpha = 0.3f), CockpitBorder))),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onComponentClick(comp) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyanHud.copy(alpha = 0.15f))
                            .border(1.dp, CyanHud, RoundedCornerShape(10.dp))
                    ) {
                        Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = CyanHud, modifier = Modifier.size(22.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(comp.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        Text(comp.polishName, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = comp.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(color = AmberBose, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "OEM: ${comp.oemNumber.take(12)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun EngineComponentDialog(
    component: EngineComponent,
    viewModel: OverlordViewModel,
    onDismiss: () -> Unit
) {
    val trendPoints by viewModel.getSensorTrends(component.id).collectAsStateWithLifecycle(emptyList())
    val unit = getComponentSensorUnit(component.id)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CockpitSurfaceVariant,
        title = {
            Column {
                Text(component.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                Text(component.polishName, style = MaterialTheme.typography.bodySmall.copy(color = CyanHud))
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Sensor Trend Chart right inside Component Dialog
                item {
                    SensorTrendCard(
                        sensorTitle = "HISTORIA TELEMETRII SENSORA",
                        sensorSubtitle = "${component.name} (${component.category})",
                        points = trendPoints,
                        unit = unit,
                        accentColor = CyanHud,
                        onSampleNow = { viewModel.logCurrentSensorSample(component.id) },
                        onClearHistory = { viewModel.clearSensorTrends(component.id) }
                    )
                }

                item {
                    Text("FUNKCJA W SILNIKU K9K 636", style = MaterialTheme.typography.labelSmall.copy(color = CyanHud, fontWeight = FontWeight.Bold))
                    Text(component.functionDescription, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                }

                item {
                    Text("PARAMETRY REFERENCYJNE (OBD-II)", style = MaterialTheme.typography.labelSmall.copy(color = AmberBose, fontWeight = FontWeight.Bold))
                    Text(component.nominalParameters, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontFamily = FontFamily.Monospace))
                }

                item {
                    Text("OBJAWY USZKODZENIA", style = MaterialTheme.typography.labelSmall.copy(color = WarningRed, fontWeight = FontWeight.Bold))
                    Text(component.failureSymptoms, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                }

                item {
                    Text("PROCEDURA DIAGNOSTYCZNA KROK PO KROKU", style = MaterialTheme.typography.labelSmall.copy(color = DiagnosticGreen, fontWeight = FontWeight.Bold))
                    Text(component.diagnosticsProcedure, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                }

                item {
                    Text("CZĘŚCI I ZAMIENNIKI", style = MaterialTheme.typography.labelSmall.copy(color = CyanHud, fontWeight = FontWeight.Bold))
                    Text("Oryginał OEM: ${component.oemNumber}", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                    Text("Zalecane zamienniki: ${component.aftermarketOptions}", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
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

@Composable
fun SensorTrendsView(
    viewModel: OverlordViewModel,
    components: List<EngineComponent>
) {
    val selectedSensorId by viewModel.selectedTrendSensorId.collectAsStateWithLifecycle()
    val trendPoints by viewModel.currentSensorTrends.collectAsStateWithLifecycle()
    val activeComponent = components.find { it.id == selectedSensorId } ?: components.first()
    val unit = getComponentSensorUnit(activeComponent.id)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "WYBIERZ CZUJNIK / PODSYSTEM DO ANALIZY TRENDÓW",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(components) { comp ->
                    val isSelected = comp.id == selectedSensorId
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectTrendSensor(comp.id) },
                        label = {
                            Text(
                                text = comp.name.substringBefore(" ("),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanHud.copy(alpha = 0.25f),
                            selectedLabelColor = CyanHud,
                            containerColor = CockpitSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) CyanHud else CockpitBorder
                        )
                    )
                }
            }
        }

        item {
            SensorTrendCard(
                sensorTitle = activeComponent.name,
                sensorSubtitle = activeComponent.polishName,
                points = trendPoints,
                unit = unit,
                accentColor = CyanHud,
                onSampleNow = { viewModel.logCurrentSensorSample(activeComponent.id) },
                onClearHistory = { viewModel.clearSensorTrends(activeComponent.id) }
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(AmberBose.copy(alpha = 0.4f), CockpitBorder))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "PARAMETRY REFERENCYJNE RENAULT SID307",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AmberBose,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeComponent.nominalParameters,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "TYPOWE OBJAWY DEGRADACJI",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = WarningRed,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeComponent.failureSymptoms,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
