package com.tyson.autotracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import com.tyson.autotracker.models.LogType
import com.tyson.autotracker.models.VehicleLog
import com.tyson.autotracker.ui.theme.*
import com.tyson.autotracker.ui.viewmodels.VehicleViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogTypeButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor =
        if (isSelected) activeColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.3f
        )
    val borderColor = if (isSelected) activeColor else Color.Transparent
    val contentColor =
        if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = text,
                tint = contentColor,
                modifier = Modifier.size(24.dp).padding(bottom = 4.dp)
            )
            Text(text, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = contentColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTextAreaField(
    label: String, value: String, onValueChange: (String) -> Unit, placeholder: String
) {
    Column {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLogScreen(
    vehicleId: String,
    logId: String? = null, // NEW: Accepts an optional logId for editing
    viewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    // NEW: Fetch existing log if editing
    val logs by viewModel.getLogsForVehicle(vehicleId).collectAsState(initial = emptyList())
    val existingLog = remember(logId, logs) { logs.find { it.id == logId } }

    // Pre-fill states if existingLog is not null
    var type by remember(existingLog) { mutableStateOf(existingLog?.type ?: LogType.SERVICE) }
    var date by remember(existingLog) {
        mutableStateOf(
            existingLog?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
    }

    // Format cost elegantly (remove .0 if it's a flat number)
    var cost by remember(existingLog) {
        mutableStateOf(existingLog?.cost?.let {
            if (it % 1 == 0.0) it.toInt().toString() else it.toString()
        } ?: "")
    }
    var kmReading by remember(existingLog) {
        mutableStateOf(
            existingLog?.kmReading?.toString() ?: ""
        )
    }
    var description by remember(existingLog) { mutableStateOf(existingLog?.description ?: "") }

    // Optional Next Service
    var nextServiceKm by remember(existingLog) {
        mutableStateOf(
            existingLog?.nextServiceKm?.toString() ?: ""
        )
    }
    var nextServiceDate by remember(existingLog) {
        mutableStateOf(
            existingLog?.nextServiceDate ?: ""
        )
    }

    // NEW: Fuel Liters
    var fuelLiters by remember(existingLog) {
        mutableStateOf(
            existingLog?.fuelLiters?.toString() ?: ""
        )
    }

    val scrollState = rememberScrollState()

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.padding(8.dp).background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(50)
                            )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = if (existingLog != null) "Edit Log" else "Add Log",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // --- 4-BUTTON TYPE SELECTOR ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LogTypeButton(
                        text = "Service",
                        icon = Icons.Default.Build,
                        isSelected = type == LogType.SERVICE,
                        activeColor = MaterialTheme.colorScheme.primary,
                        onClick = { type = LogType.SERVICE },
                        modifier = Modifier.weight(1f)
                    )
                    LogTypeButton(
                        text = "Oil",
                        icon = Icons.Default.InvertColors,
                        isSelected = type == LogType.OIL_CHANGE,
                        activeColor = Amber400,
                        onClick = { type = LogType.OIL_CHANGE },
                        modifier = Modifier.weight(1f)
                    )
                    LogTypeButton(
                        text = "Fuel",
                        icon = Icons.Default.LocalGasStation,
                        isSelected = type == LogType.REFUELING,
                        activeColor = MaterialTheme.colorScheme.secondary,
                        onClick = { type = LogType.REFUELING },
                        modifier = Modifier.weight(1f)
                    )
                    LogTypeButton(
                        text = "Mod",
                        icon = Icons.Default.Settings,
                        isSelected = type == LogType.MODIFICATION,
                        activeColor = MaterialTheme.colorScheme.tertiary,
                        onClick = { type = LogType.MODIFICATION },
                        modifier = Modifier.weight(1f)
                    )
                }

                // --- FORM FIELDS ---
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) { GlassDatePickerField("Date", date) { date = it } }
                    Box(Modifier.weight(1f)) {
                        GlassInputField(
                            "Cost (₹)",
                            cost,
                            { cost = it },
                            "0",
                            KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) {
                        GlassInputField(
                            "Odometer (KM)",
                            kmReading,
                            { kmReading = it },
                            "Current KM",
                            KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    // NEW: Show fuel liters field ONLY when Refueling is selected
                    if (type == LogType.REFUELING) {
                        Box(Modifier.weight(1f)) {
                            GlassInputField(
                                "Fuel Filled (Liters)",
                                fuelLiters,
                                { fuelLiters = it },
                                "e.g. 10.5",
                                KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                }

                GlassTextAreaField(
                    label = "Description",
                    value = description,
                    onValueChange = { description = it },
                    placeholder = if (type == LogType.REFUELING) "Petrol/Diesel amount..." else "What was done?"
                )

                // --- CONDITIONAL NEXT SERVICE SECTION ---
                if (type == LogType.SERVICE || type == LogType.OIL_CHANGE) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = if (type == LogType.OIL_CHANGE) "Next Engine Oil Change (Optional)" else "Next Service (Optional)",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.weight(1f)) {
                                val placeholderKm =
                                    if (type == LogType.OIL_CHANGE && kmReading.isNotEmpty()) "${(kmReading.toIntOrNull() ?: 0) + 5000}" else ""
                                GlassInputField(
                                    "At KM",
                                    nextServiceKm,
                                    { nextServiceKm = it },
                                    placeholderKm,
                                    KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                GlassDatePickerField(
                                    "By Date",
                                    nextServiceDate
                                ) { nextServiceDate = it }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- SUBMIT BUTTON ---
                Button(
                    onClick = {
                        val newLog = VehicleLog(
                            id = existingLog?.id ?: UUID.randomUUID().toString(),
                            vehicleId = vehicleId,
                            type = type,
                            date = date,
                            cost = cost.toDoubleOrNull() ?: 0.0,
                            description = description,
                            kmReading = kmReading.toIntOrNull() ?: 0,
                            nextServiceKm = nextServiceKm.toIntOrNull(),
                            nextServiceDate = nextServiceDate.ifBlank { null },
                            fuelLiters = if (type == LogType.REFUELING) fuelLiters.toDoubleOrNull() else null
                        )

                        if (existingLog != null) {
                            viewModel.updateLog(newLog)
                        } else {
                            viewModel.addLog(newLog)
                        }
                        onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = kmReading.isNotBlank() && cost.isNotBlank()
                ) {
                    Text(
                        text = if (existingLog != null) "Update Log" else "Save Log",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
