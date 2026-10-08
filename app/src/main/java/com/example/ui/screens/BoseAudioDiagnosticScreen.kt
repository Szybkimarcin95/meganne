package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.K95ServiceMap

/** Renders supplied Service Map data only; no synthetic measurements or DTCs. */
@Composable
fun BoseAudioDiagnosticScreen(serviceMap: K95ServiceMap) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("BOSE • instalacja", style = MaterialTheme.typography.titleMedium)
        Text("Wzmacniacz 1917 → pasywny subwoofer 1987. Brak pomiarów z auta.")
        ConnectorDiagram(serviceMap)
        Text("R372: złącze kamery. R2: UNVERIFIED — identyfikacja fizycznej kostki niepotwierdzona.")
    }
}

@Composable
fun ConnectorDiagram(serviceMap: K95ServiceMap) {
    serviceMap.circuits.filter { it.from == "1917" && it.to == "1987" }.forEach { circuit ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("${circuit.code} • ${circuit.polarity}", style = MaterialTheme.typography.titleSmall)
                Text("${circuit.from} pin ${circuit.fromPin} → ${circuit.to} pin ${circuit.toPin}")
                Text("${circuit.gaugeMm2} mm² • ${circuit.verificationStatus}")
                Text("Kolor przewodu i napięcie: UNKNOWN")
            }
        }
    }
}
