package com.tyson.autotracker.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyson.autotracker.models.LogType
import com.tyson.autotracker.ui.theme.GlassBackground
import com.tyson.autotracker.ui.theme.glassCard
import com.tyson.autotracker.ui.viewmodels.VehicleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val vehicles by viewModel.allVehicles.collectAsState()
    val totalInvestment by viewModel.totalInvestment.collectAsState()

    // FETCH REAL DATA FROM VIEW MODEL
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent, // Ensure background shows through
            topBar = {
                TopAppBar(
                    title = {
                        Text("Analytics & Insights", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    },
                    navigationIcon = {
                        Row(
                            modifier = Modifier
                                .clickable { onNavigateBack() }
                                .padding(8.dp)
                                .glassCard(RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.width(8.dp))
                            Text("Back", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    // GLASS TOTAL SPEND CARD
                    Box(modifier = Modifier.fillMaxWidth().glassCard()) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text("TOTAL SPEND", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("₹${"%,.0f".format(totalInvestment)}", fontSize = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }

                item {
                    Text("Spending Breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }

                if (categoryBreakdown.isEmpty()) {
                    item {
                        Text("No spending data available yet.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    }
                } else {
                    // Draw real horizontal bar charts for each category
                    // Sorted so the most expensive categories appear at the top
                    categoryBreakdown.entries.sortedByDescending { it.value }.forEach { (type, amount) ->
                        // Protect against division by zero
                        val percentage = if (totalInvestment > 0) (amount / totalInvestment).toFloat() else 0f

                        item {
                            CategoryBar(
                                label = type.name.replace("_", " "),
                                amount = amount,
                                percentage = percentage,
                                color = getColorForType(type)
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Fuel Efficiency (Simulated)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }

                vehicles.forEach { vehicle ->
                    item {
                        // GLASS VEHICLE EFFICIENCY CARD
                        Box(modifier = Modifier.fillMaxWidth().glassCard()) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(vehicle.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                    Text("${vehicle.make} ${vehicle.model}", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 12.sp)
                                }
                                val averageMileage by viewModel.getAverageMileage(vehicle.id).collectAsState(initial = 0f)
                                if (averageMileage > 0f) {
                                    Text("${"%.1f".format(averageMileage)} km/L", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF10b981))
                                } else {
                                    Text("Need more logs", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryBar(label: String, amount: Double, percentage: Float, color: Color) {
    var animationPlayed by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) percentage else 0f,
        animationSpec = tween(durationMillis = 1000), label = "progress"
    )

    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            Text("₹${"%,.0f".format(amount)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)), // Rounded ends for glass look
            color = color,
            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
        )
    }
}

fun getColorForType(type: LogType): Color {
    return when(type) {
        LogType.REFUELING -> Color(0xFF3b82f6) // Blue
        LogType.SERVICE -> Color(0xFFf59e0b)   // Amber
        LogType.MODIFICATION -> Color(0xFF8b5cf6) // Purple
        LogType.OIL_CHANGE -> Color(0xFF10b981)   // Green
        LogType.INSURANCE -> Color(0xFF0ea5e9)    // Sky Blue
        LogType.POLLUTION -> Color(0xFF22c55e)    // Emerald
    }
}