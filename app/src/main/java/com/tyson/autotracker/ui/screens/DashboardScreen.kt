package com.tyson.autotracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyson.autotracker.models.Vehicle
import com.tyson.autotracker.models.VehicleType
import com.tyson.autotracker.ui.theme.*
import com.tyson.autotracker.ui.viewmodels.ThemeMode
import com.tyson.autotracker.ui.viewmodels.VehicleViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: VehicleViewModel,
    onSelectVehicle: (String) -> Unit,
    onNavigateToAddVehicle: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onStartTrip: () -> Unit
) {
    val vehicles by viewModel.allVehicles.collectAsState()
    val totalInvestment by viewModel.totalInvestment.collectAsState()
    val totalLogs by viewModel.totalLogsCount.collectAsState()
    val reminders by viewModel.upcomingReminders.collectAsState()
    val currentTheme by viewModel.themeMode.collectAsState()

    // State to prevent multiple rapid navigations on a single swipe
    var hasNavigated by remember { mutableStateOf(false) }

    GlassBackground(
        modifier = Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = { hasNavigated = false },
                onDragCancel = { hasNavigated = false }
            ) { change, dragAmount ->
                // Threshold: If swiped left with intent, trigger standard NavHost transition
                if (!hasNavigated && dragAmount < -20) {
                    hasNavigated = true
                    change.consume()
                    onNavigateToSettings()
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = "Logo", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("AutoTracker", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    actions = {
                        Row(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .glassCard(RoundedCornerShape(50), alpha = 0.1f)
                                .clickable {
                                    val nextTheme = when (currentTheme) {
                                        ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                        ThemeMode.LIGHT -> ThemeMode.DARK
                                        ThemeMode.DARK -> ThemeMode.SYSTEM
                                    }
                                    viewModel.setThemeMode(nextTheme)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (currentTheme) {
                                    ThemeMode.LIGHT -> Icons.Default.LightMode
                                    ThemeMode.DARK -> Icons.Default.DarkMode
                                    ThemeMode.SYSTEM -> Icons.Default.SettingsSystemDaydream
                                },
                                contentDescription = "Theme",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(currentTheme.name.lowercase().replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .glassCard(RoundedCornerShape(16.dp)),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
            ) {
                if (reminders.isNotEmpty()) {
                    item {
                        Text("Alerts", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(reminders) { alert ->
                        Box(modifier = Modifier.fillMaxWidth().glassCard(borderAlpha = if (alert.isUrgent) 0.8f else 0.3f)) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (alert.isUrgent) Icons.Default.Warning else Icons.Default.Info,
                                    null,
                                    tint = if (alert.isUrgent) MaterialTheme.colorScheme.error else Amber400
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${alert.vehicleName}: ${alert.type}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                    Text(alert.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard("Total Vehicles", vehicles.size.toString(), Icons.Default.DirectionsCar, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        StatCard("Total Logs", totalLogs.toString(), Icons.Default.Build, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    }
                }
                item {
                    StatCard("Total Investment", "₹${"%,.0f".format(totalInvestment)}", Icons.Default.Add, MaterialTheme.colorScheme.secondary, Modifier.fillMaxWidth())
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("My Garage", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Row(modifier = Modifier.clickable { onNavigateToAddVehicle() }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Vehicle", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.weight(1f).height(48.dp).glassCard(RoundedCornerShape(12.dp)).clickable { onStartTrip() },
                                contentAlignment = Alignment.Center
                            ) {
                                Row {
                                    Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Driving", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
                                }
                            }
                            Box(
                                modifier = Modifier.weight(1f).height(48.dp).glassCard(RoundedCornerShape(12.dp)).clickable { onNavigateToInsights() },
                                contentAlignment = Alignment.Center
                            ) {
                                Row {
                                    Icon(Icons.Default.Analytics, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Insights", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                if (vehicles.isEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp)).padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), RoundedCornerShape(32.dp)).padding(16.dp)) {
                                Icon(Icons.Default.DirectionsCar, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Your garage is empty", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = onNavigateToAddVehicle, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(12.dp)) {
                                Text("Add My First Vehicle", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    items(vehicles) { vehicle ->
                        VehicleCard(vehicle = vehicle, viewModel = viewModel, onClick = { onSelectVehicle(vehicle.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, iconColor: Color, modifier: Modifier) {
    Box(modifier = modifier.glassCard()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = iconColor)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleCard(vehicle: Vehicle, viewModel: VehicleViewModel, onClick: () -> Unit) {
    val totalLogs by viewModel.totalLogsCount.collectAsState()

    Box(modifier = Modifier.fillMaxWidth().glassCard().clickable { onClick() }) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(16.dp)) {
                        Icon(
                            imageVector = if (vehicle.type == VehicleType.CAR) Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                            contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(vehicle.name.ifBlank { "${vehicle.make} ${vehicle.model}" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(4.dp))
                        Text("${vehicle.year} ${vehicle.make} ${vehicle.model}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${"%,.1f".format(vehicle.currentKm)} km", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text("${totalLogs} LOGS", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}