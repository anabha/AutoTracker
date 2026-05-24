package com.tyson.autotracker.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyson.autotracker.models.Vehicle
import com.tyson.autotracker.models.VehicleType
import com.tyson.autotracker.ui.theme.GlassBackground
import com.tyson.autotracker.ui.theme.glassCard
import com.tyson.autotracker.ui.viewmodels.VehicleViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleScreen(
    viewModel: VehicleViewModel,
    vehicleId: String? = null,
    onNavigateBack: () -> Unit
) {
    val vehicles by viewModel.allVehicles.collectAsState()
    val existingVehicle = remember(vehicleId, vehicles) {
        vehicles.find { it.id == vehicleId }
    }

    var name by remember { mutableStateOf(existingVehicle?.name ?: "") }
    var selectedType by remember { mutableStateOf(existingVehicle?.type ?: VehicleType.CAR) }
    var make by remember { mutableStateOf(existingVehicle?.make ?: "") }
    var model by remember { mutableStateOf(existingVehicle?.model ?: "") }
    var year by remember { mutableStateOf(existingVehicle?.year?.toString() ?: Calendar.getInstance().get(Calendar.YEAR).toString()) }
    var currentKm by remember { mutableStateOf(existingVehicle?.currentKm?.let { "%.1f".format(it) } ?: "0") }
    var registrationNo by remember { mutableStateOf(existingVehicle?.registrationNo ?: "") }
    var engineNo by remember { mutableStateOf(existingVehicle?.engineNo ?: "") }
    var puccDate by remember { mutableStateOf(existingVehicle?.puccDate ?: "") }
    var insuranceDate by remember { mutableStateOf(existingVehicle?.insuranceDate ?: "") }
    var selectedMacAddress by remember { mutableStateOf(existingVehicle?.bluetoothMacAddress ?: "") }
    var selectedWifiSsid by remember { mutableStateOf(existingVehicle?.wifiSsid ?: "") }

    // NEW: Image URI State
    var imageUri by remember { mutableStateOf(existingVehicle?.imageUri ?: "") }
    var bitmap by remember(imageUri) { mutableStateOf<ImageBitmap?>(null) }

    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Screen Transition Animation State
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    // --- NEW: PHOTO PICKER LAUNCHER ---
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                // Keep permission to read the image after app restarts
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            imageUri = uri.toString()
        }
    }

    // Convert URI to Bitmap securely
    LaunchedEffect(imageUri) {
        if (imageUri.isNotEmpty()) {
            try {
                val uri = Uri.parse(imageUri)
                val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                bitmap = bmp.asImageBitmap()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val pairedDevicesResult = remember {
        try {
            val btManager = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            val devices = btManager.adapter?.bondedDevices?.map {
                Pair(it.name ?: it.address, it.address)
            } ?: emptyList()
            Result.success(devices)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isVisible = false
                                onNavigateBack()
                            },
                            modifier = Modifier.padding(8.dp).glassCard(RoundedCornerShape(50), alpha = 0.2f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(initialOffsetY = { it / 4 }, animationSpec = tween(400)) + fadeIn(tween(400)),
                exit = slideOutVertically(targetOffsetY = { it / 4 }, animationSpec = tween(300)) + fadeOut(tween(300))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp)
                        .imePadding()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = if (existingVehicle != null) "Edit Vehicle" else "Add Vehicle",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // --- PHOTO PICKER UI ---
                    val outlineColor = MaterialTheme.colorScheme.outline
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .glassCard(RoundedCornerShape(24.dp), alpha = 0.1f)
                            .drawBehind {
                                if (bitmap == null) {
                                    drawRoundRect(
                                        color = outlineColor,
                                        style = Stroke(
                                            width = 4f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                                        ),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                                    )
                                }
                            }
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap!!,
                                contentDescription = "Vehicle Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp).padding(bottom = 8.dp))
                                Text("Add Vehicle Photo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TypeSelectButton(
                            text = "Car", icon = Icons.Default.DirectionsCar, isSelected = selectedType == VehicleType.CAR,
                            onClick = { selectedType = VehicleType.CAR }, modifier = Modifier.weight(1f)
                        )
                        TypeSelectButton(
                            text = "Bike", icon = Icons.Default.TwoWheeler, isSelected = selectedType == VehicleType.BIKE,
                            onClick = { selectedType = VehicleType.BIKE }, modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) { GlassInputField("Nickname (Optional)", name, { name = it }, "My Daily Driver") }
                        Box(Modifier.weight(1f)) { GlassInputField("Registration No.", registrationNo, { registrationNo = it }, "MH 12 AB 1234") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) { GlassInputField("Make", make, { make = it }, "Toyota") }
                        Box(Modifier.weight(1f)) { GlassInputField("Model", model, { model = it }, "Camry") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) { GlassInputField("Year", year, { year = it }, "2024", KeyboardOptions(keyboardType = KeyboardType.Number)) }
                        Box(Modifier.weight(1f)) { GlassInputField("Current KM", currentKm, { currentKm = it }, "0", KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
                    }

                    GlassInputField("Engine No.", engineNo, { engineNo = it }, "ENG123456789")

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) { GlassDatePickerField("PUCC Expiry", puccDate, { puccDate = it }) }
                        Box(Modifier.weight(1f)) { GlassDatePickerField("Insurance Expiry", insuranceDate, { insuranceDate = it }) }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(top = 8.dp))
                    Text("Auto Tracking Setup", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

                    // --- BLUETOOTH DROPDOWN ---
                    var btExpanded by remember { mutableStateOf(false) }
                    val currentDeviceName = remember(selectedMacAddress, pairedDevicesResult) {
                        if (selectedMacAddress.isEmpty()) "No Device Selected"
                        else pairedDevicesResult.getOrNull()?.find { it.second == selectedMacAddress }?.first ?: "Paired Device Linked"
                    }

                    Column {
                        Text("Bluetooth Connection", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                        ExposedDropdownMenuBox(
                            expanded = btExpanded,
                            onExpandedChange = { btExpanded = !btExpanded }
                        ) {
                            OutlinedTextField(
                                value = currentDeviceName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = btExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth().glassCard(RoundedCornerShape(16.dp), alpha = 0.1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                )
                            )
                            ExposedDropdownMenu(expanded = btExpanded, onDismissRequest = { btExpanded = false }) {
                                DropdownMenuItem(text = { Text("None") }, onClick = { selectedMacAddress = ""; btExpanded = false })
                                if (pairedDevicesResult.isFailure) {
                                    DropdownMenuItem(text = { Text("Bluetooth Permission Required") }, onClick = { btExpanded = false })
                                } else {
                                    pairedDevicesResult.getOrNull()?.forEach { device ->
                                        DropdownMenuItem(text = { Text(device.first) }, onClick = { selectedMacAddress = device.second; btExpanded = false })
                                    }
                                }
                            }
                        }
                    }

                    // --- NEW: WI-FI SELECTION ---
                    Column {
                        Text("Wi-Fi Connection", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                        OutlinedTextField(
                            value = selectedWifiSsid,
                            onValueChange = { selectedWifiSsid = it },
                            placeholder = { Text("e.g. MyCar_WiFi", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                                    val ssid = wifiManager.connectionInfo.ssid?.removeSurrounding("\"")
                                    if (ssid != null && ssid != "<unknown ssid>") {
                                        selectedWifiSsid = ssid
                                    } else {
                                        Toast.makeText(context, "Turn Location ON to read Wi-Fi automatically.", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.Wifi, contentDescription = "Get Current Wi-Fi", tint = MaterialTheme.colorScheme.tertiary)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(16.dp), alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = MaterialTheme.colorScheme.tertiary, unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Text("Tap the Wi-Fi icon to grab your currently connected network.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- MASSIVE ADD BUTTON ---
                    Button(
                        onClick = {
                            val vehicle = Vehicle(
                                id = existingVehicle?.id ?: UUID.randomUUID().toString(),
                                name = name.ifBlank { "$make $model" },
                                type = selectedType,
                                make = make,
                                model = model,
                                year = year.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR),
                                currentKm = currentKm.toDoubleOrNull() ?: 0.0,
                                createdAt = existingVehicle?.createdAt ?: System.currentTimeMillis().toString(),
                                registrationNo = registrationNo.ifBlank { null },
                                engineNo = engineNo.ifBlank { null },
                                puccDate = puccDate.ifBlank { null },
                                insuranceDate = insuranceDate.ifBlank { null },
                                bluetoothMacAddress = selectedMacAddress.ifBlank { null },
                                wifiSsid = selectedWifiSsid.ifBlank { null },
                                imageUri = imageUri.ifBlank { null }
                            )

                            if (existingVehicle != null) {
                                viewModel.updateVehicle(vehicle)
                            } else {
                                viewModel.addVehicle(vehicle)
                            }
                            isVisible = false
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp) // HUGE Button
                            .padding(bottom = 16.dp)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(50)),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                        enabled = make.isNotBlank() && model.isNotBlank() && year.isNotBlank()
                    ) {
                        Text(
                            text = if (existingVehicle != null) "UPDATE VEHICLE" else "ADD VEHICLE",
                            fontSize = 18.sp, // Bigger text
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// --- CUSTOM UI COMPONENTS ---

@Composable
fun TypeSelectButton(text: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .glassCard(RoundedCornerShape(20.dp), alpha = if(isSelected) 0.1f else 0.05f, borderAlpha = if(isSelected) 1f else 0.2f)
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = text, tint = contentColor, modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
            Text(text, fontWeight = FontWeight.Bold, color = contentColor, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassInputField(
    label: String, value: String, onValueChange: (String) -> Unit, placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(16.dp), alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = keyboardOptions,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassDatePickerField(label: String, value: String, onDateSelected: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    Column {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        Box(modifier = Modifier.clickable { showDialog = true }.glassCard(RoundedCornerShape(16.dp), alpha = 0.05f)) {
            OutlinedTextField(
                value = value,
                onValueChange = { },
                placeholder = { Text("YYYY-MM-DD", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledContainerColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    disabledTextColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    }

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
                        onDateSelected(date)
                    }
                    showDialog = false
                }) { Text("OK") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}