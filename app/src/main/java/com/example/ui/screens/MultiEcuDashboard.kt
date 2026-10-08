package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.K95Ecu
import com.example.data.model.K95EcuStatus
import com.example.ui.viewmodel.MultiEcuViewModel

@Composable
fun MultiEcuDashboard(modifier: Modifier = Modifier, viewModel: MultiEcuViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Sterowniki / ECU • K95", style = MaterialTheme.typography.titleLarge)
            Text("Metadane źródeł. Skan fizyczny nie jest podłączony.")
            Text("Potwierdzone w aucie: ${state.ecus.count { it.status == K95EcuStatus.CONFIRMED_IN_CAR }}")
            Button(onClick = viewModel::reloadSources, enabled = !state.loading) { Text("Odśwież źródła") }
            if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            state.messages.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        items(state.ecus, key = { it.id }) { ecu -> EcuInventoryCard(ecu) }
    }
}

@Composable
private fun EcuInventoryCard(ecu: K95Ecu) {
    var selected by remember(ecu.id) { mutableStateOf(0) }
    val tabs = listOf("Identyfikacja", "Live", "DTC", "Instalacja", "Źródła")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ecu.commonName, style = MaterialTheme.typography.titleMedium)
            Text(ecu.status.name, color = when (ecu.status) {
                K95EcuStatus.CONFIRMED_IN_CAR -> Color(0xFF2E7D32)
                K95EcuStatus.NOT_SEEN -> MaterialTheme.colorScheme.error
                K95EcuStatus.PHYSICAL_MATCH -> Color(0xFF1565C0)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            })
            ScrollableTabRow(selectedTabIndex = selected, edgePadding = 0.dp) {
                tabs.forEachIndexed { i, title -> Tab(selected = selected == i, onClick = { selected = i }, text = { Text(title) }) }
            }
            when (selected) {
                0 -> {
                    Text("TX: ${ecu.diagnosticAddress ?: "UNKNOWN"} • RX: ${ecu.responseAddress ?: "UNKNOWN"}")
                    Text("CAN: ${ecu.canSpeed?.toString() ?: "UNKNOWN"}")
                    Text("Ostatnia odpowiedź: ${ecu.lastSeen?.toString() ?: "brak"}")
                    ecu.identifiers.forEach { (key, value) -> Text("$key: $value") }
                    Text(ecu.notes)
                }
                1 -> Text("Brak pomiarów z tego ECU. Definicja DDT nie jest pomiarem.")
                2 -> Text("DTC nie odczytano. Brak odczytu nie oznacza braku błędów.")
                3 -> {
                    Text(ecu.physicalLocation ?: "Lokalizacja: UNKNOWN")
                    Text("Service Map: ${ecu.serviceMapNodeIds.joinToString().ifBlank { "brak powiązania" }}")
                }
                4 -> ecu.sources.forEach { Text(it) }
            }
        }
    }
}
