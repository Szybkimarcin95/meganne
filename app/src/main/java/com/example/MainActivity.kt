package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.VehicleSpec
import com.example.data.obd.ObdConnectionState
import com.example.ui.screens.ArsenalScreen
import com.example.ui.screens.BlackBoxScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DigitalTwinScreen
import com.example.ui.screens.OracleScreen
import com.example.ui.theme.AmberBose
import com.example.ui.theme.CockpitBackground
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CyanHud
import com.example.ui.theme.CyanHudDim
import com.example.ui.theme.DiagnosticGreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningRed
import com.example.ui.viewmodel.OverlordTab
import com.example.ui.viewmodel.OverlordViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                OverlordApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlordApp(viewModel: OverlordViewModel = viewModel()) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(DiagnosticGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "MEGANE OVERLORD",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.5.sp,
                                        color = TextPrimary
                                    )
                                )
                            }
                            Text(
                                text = "KZ1406 • BOSE • 1.5 dCi K9K 636",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = CyanHud,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }

                        // Top Real-time Connection State Indicator Badge
                        val (btColor, btBg, btText, btIcon) = when {
                            telemetry.isSimulated -> {
                                QuadTuple(AmberBose, AmberBose.copy(alpha = 0.2f), "SIM 4Hz", Icons.Default.Bluetooth)
                            }
                            connectionState == ObdConnectionState.CONNECTED || connectionState == ObdConnectionState.READING -> {
                                QuadTuple(DiagnosticGreen, DiagnosticGreen.copy(alpha = 0.2f), "ELM OK", Icons.Default.BluetoothConnected)
                            }
                            connectionState == ObdConnectionState.CONNECTING -> {
                                QuadTuple(CyanHud, CyanHudDim, "ŁĄCZENIE", Icons.Default.BluetoothSearching)
                            }
                            connectionState == ObdConnectionState.ERROR -> {
                                QuadTuple(WarningRed, WarningRed.copy(alpha = 0.2f), "BŁĄD BT", Icons.Default.BluetoothDisabled)
                            }
                            else -> {
                                QuadTuple(TextSecondary, CockpitSurface, "ROZŁĄCZONY", Icons.Default.Bluetooth)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(btBg)
                                .border(1.dp, btColor, RoundedCornerShape(6.dp))
                                .clickable { viewModel.selectTab(OverlordTab.ORACLE) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = btIcon,
                                    contentDescription = "Status Bluetooth",
                                    tint = btColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = btText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = btColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CockpitBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CockpitSurface,
                modifier = Modifier
                    .border(1.dp, CockpitBorder)
                    .testTag("bottom_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = currentTab == OverlordTab.DASHBOARD,
                    onClick = { viewModel.selectTab(OverlordTab.DASHBOARD) },
                    icon = { Icon(Icons.Default.Speed, contentDescription = "Kokpit") },
                    label = { Text("Kokpit", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = CyanHud,
                        indicatorColor = CyanHud,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("tab_dashboard")
                )

                NavigationBarItem(
                    selected = currentTab == OverlordTab.DIGITAL_TWIN,
                    onClick = { viewModel.selectTab(OverlordTab.DIGITAL_TWIN) },
                    icon = { Icon(Icons.Default.DirectionsCar, contentDescription = "Bliźniak") },
                    label = { Text("Bliźniak", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = CyanHud,
                        indicatorColor = CyanHud,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("tab_digital_twin")
                )

                NavigationBarItem(
                    selected = currentTab == OverlordTab.ORACLE,
                    onClick = { viewModel.selectTab(OverlordTab.ORACLE) },
                    icon = { Icon(Icons.Default.Warning, contentDescription = "Wyrocznia") },
                    label = { Text("Wyrocznia", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = CyanHud,
                        indicatorColor = CyanHud,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("tab_oracle")
                )

                NavigationBarItem(
                    selected = currentTab == OverlordTab.BLACK_BOX,
                    onClick = { viewModel.selectTab(OverlordTab.BLACK_BOX) },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Skrzynka") },
                    label = { Text("Skrzynka", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = CyanHud,
                        indicatorColor = CyanHud,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("tab_black_box")
                )

                NavigationBarItem(
                    selected = currentTab == OverlordTab.ARSENAL,
                    onClick = { viewModel.selectTab(OverlordTab.ARSENAL) },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Arsenał") },
                    label = { Text("Arsenał", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = CyanHud,
                        indicatorColor = CyanHud,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("tab_arsenal")
                )
            }
        },
        containerColor = CockpitBackground,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        when (currentTab) {
            OverlordTab.DASHBOARD -> DashboardScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            OverlordTab.DIGITAL_TWIN -> DigitalTwinScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            OverlordTab.ORACLE -> OracleScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            OverlordTab.BLACK_BOX -> BlackBoxScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            OverlordTab.ARSENAL -> ArsenalScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Megane Overlord $name", modifier = modifier)
}

private data class QuadTuple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
