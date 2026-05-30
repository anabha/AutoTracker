package com.tyson.autotracker.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyson.autotracker.models.LogType
import com.tyson.autotracker.models.Vehicle
import com.tyson.autotracker.models.VehicleLog
import com.tyson.autotracker.models.VehicleType
import com.tyson.autotracker.ui.theme.*
import com.tyson.autotracker.ui.viewmodels.VehicleViewModel
import com.tyson.autotracker.utils.ParkingLocationUtils
import com.tyson.autotracker.utils.StoredLocation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tyson.autotracker.ui.viewmodels.NavigationEvent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleDetailsScreen(
    vehicleId: String,
    viewModel: VehicleViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAddLog: (String) -> Unit,
    onEditLog: (String) -> Unit, // NEW: Added callback for editing a log
    onNavigateToTrips: (String) -> Unit,
    onStartTrip: () -> Unit
) {
    val vehicles by viewModel.allVehicles.collectAsState()
    val vehicle = vehicles.find { it.id == vehicleId }
    val logs by viewModel.getLogsForVehicle(vehicleId).collectAsState(initial = emptyList())

    // States for bottom sheet confirmations
    var showDeleteVehicleSheet by remember { mutableStateOf(false) }
    var logToDelete by remember { mutableStateOf<VehicleLog?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val blurEffect by animateDpAsState(
        targetValue = if (showDeleteVehicleSheet || logToDelete != null) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "BlurEffect"
    )

    val context = LocalContext.current
    var vehicleBitmap by remember(vehicle?.imageUri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(vehicle?.imageUri) {
        if (!vehicle?.imageUri.isNullOrEmpty()) {
            try {
                val uri = android.net.Uri.parse(vehicle!!.imageUri)
                val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                vehicleBitmap = bmp.asImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.StartDriving -> onStartTrip()
            }
        }
    }

    if (vehicle == null) return

    val formatMonthYear = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    val formatFullDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    GlassBackground {
        Scaffold(
            modifier = Modifier.blur(blurEffect),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        Row(
                            modifier = Modifier.clickable { onBack() }.padding(8.dp)
                                .glassCard(RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Back",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier.clickable { onEdit(vehicleId) }.padding(8.dp)
                                .glassCard(RoundedCornerShape(12.dp)).padding(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.clickable {
                            showDeleteVehicleSheet = true
                        }.padding(8.dp).glassCard(RoundedCornerShape(12.dp)).padding(8.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
            ) {

                // --- 1. HERO GLASS PANEL ---
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp))
                            .padding(24.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier.size(80.dp).background(
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                                        RoundedCornerShape(20.dp)
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (vehicleBitmap != null) {
                                        Image(
                                            bitmap = vehicleBitmap!!,
                                            contentDescription = "Vehicle Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (vehicle.type == VehicleType.CAR) Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                                            contentDescription = null,
                                            tint = if (vehicle.type == VehicleType.CAR) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            vehicle.name.ifBlank { "${vehicle.make} ${vehicle.model}" },
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        if (!vehicle.registrationNo.isNullOrBlank()) {
                                            Box(
                                                modifier = Modifier.background(
                                                    MaterialTheme.colorScheme.onBackground.copy(
                                                        alpha = 0.2f
                                                    ), RoundedCornerShape(8.dp)
                                                ).padding(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    vehicle.registrationNo,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                PillTag(
                                    Icons.Default.LocationOn,
                                    "${"%,.1f".format(vehicle.currentKm)} km",
                                    MaterialTheme.colorScheme.primary
                                )
                                PillTag(
                                    Icons.Default.DateRange,
                                    "Added ${formatMonthYear.format(Date(vehicle.createdAt.toLong()))}",
                                    MaterialTheme.colorScheme.tertiary
                                )
                                if (!vehicle.engineNo.isNullOrBlank()) {
                                    PillTag(
                                        Icons.Default.Settings,
                                        "Eng: ${vehicle.engineNo}",
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                    )
                                }
                                if ((vehicle.fuelCapacityLiters ?: 0.0) > 0.0) {
                                    val estRange by viewModel.getEstimatedRange(vehicle.id).collectAsState(initial = 0f)
                                    if (estRange > 0f) {
                                        PillTag(
                                            Icons.Default.LocalGasStation,
                                            "Est. Range: ~${"%.0f".format(estRange)} km",
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }

                            if (!vehicle.puccDate.isNullOrBlank() || !vehicle.insuranceDate.isNullOrBlank()) {
                                Column(modifier = Modifier.padding(top = 32.dp)) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onBackground.copy(
                                            alpha = 0.2f
                                        ), thickness = 1.dp
                                    )
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        if (!vehicle.puccDate.isNullOrBlank()) ComplianceWidget(
                                            "PUCC Expiry",
                                            vehicle.puccDate,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                        if (!vehicle.insuranceDate.isNullOrBlank()) ComplianceWidget(
                                            "Insurance Expiry",
                                            vehicle.insuranceDate,
                                            MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 2. PARKING LOCATION SECTION ---
                item {
                    Text(
                        "Parking Location",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                item {
                    ParkingLocationCard(vehicle = vehicle)
                }

                // --- 2. SERVICE HISTORY SECTION ---
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Service History",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.glassCard(
                                    RoundedCornerShape(12.dp),
                                    alpha = 0.2f,
                                    borderAlpha = 0.5f
                                )
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    .clickable { onNavigateToTrips(vehicle.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Map,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Trips",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.glassCard(RoundedCornerShape(12.dp))
                                    .clickable { onAddLog(vehicle.id) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Add",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (logs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .glassCard(RoundedCornerShape(16.dp)).padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No service logs yet.",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    items(logs) { log ->
                        ReactStyleLogCard(
                            log = log,
                            formatter = formatFullDate,
                            onEditLog = {
                                // FIXED: Trigger navigation to Edit Log Screen with this log's ID
                                onEditLog(log.id)
                            },
                            onDeleteLog = { logToDelete = log }
                        )
                    }
                }

                // --- 3. AUTO TRACKING SECTION ---
                item {
                    Text(
                        "Auto Tracking",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                item {
                    AutoTrackingCard(
                        vehicle = vehicle,
                        viewModel = viewModel,
                        onStartTrip = onStartTrip
                    )
                }

                // --- 4. UPCOMING MAINTENANCE SECTION ---
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp))
                            .padding(24.dp)
                    ) {
                        Column {
                            Text(
                                "Upcoming Maintenance",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            val upcomingLogs =
                                logs.filter { it.nextServiceKm != null || it.nextServiceDate != null }

                            if (upcomingLogs.isEmpty()) {
                                Text(
                                    "No upcoming maintenance scheduled.",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            } else {
                                val nextLog = upcomingLogs.first()
                                val kmRemaining =
                                    nextLog.nextServiceKm?.let { it - vehicle.currentKm.toInt() }
                                val warningColor = Amber400

                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(
                                            warningColor.copy(alpha = 0.1f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp,
                                            warningColor.copy(alpha = 0.3f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(16.dp)
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.InvertColors,
                                                contentDescription = null,
                                                tint = warningColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                nextLog.type.name.replace("_", " ").lowercase()
                                                    .replaceFirstChar { it.uppercase() },
                                                fontWeight = FontWeight.Bold,
                                                color = warningColor,
                                                fontSize = 14.sp
                                            )
                                        }

                                        if (kmRemaining != null) {
                                            Row {
                                                Text(
                                                    "${
                                                        if (kmRemaining > 0) "%,d".format(
                                                            kmRemaining
                                                        ) else 0
                                                    } km ",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    "remaining",
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }

                                        if (nextLog.nextServiceDate != null) {
                                            val displayDate = try {
                                                val d = SimpleDateFormat(
                                                    "yyyy-MM-dd",
                                                    Locale.getDefault()
                                                ).parse(nextLog.nextServiceDate)
                                                if (d != null) formatFullDate.format(d) else nextLog.nextServiceDate
                                            } catch (e: Exception) {
                                                nextLog.nextServiceDate
                                            }

                                            Row(modifier = Modifier.padding(top = 4.dp)) {
                                                Text(
                                                    "Due by ",
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    displayDate,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete Vehicle Confirmation Bottom Sheet
        if (showDeleteVehicleSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDeleteVehicleSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "Delete Vehicle?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Are you sure you want to delete this vehicle and all its logs? This action cannot be undone.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { showDeleteVehicleSheet = false },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                showDeleteVehicleSheet = false
                                viewModel.deleteVehicle(vehicleId)
                                onBack()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Delete", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Delete Log Confirmation Bottom Sheet
        if (logToDelete != null) {
            ModalBottomSheet(
                onDismissRequest = { logToDelete = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "Delete Log?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Are you sure you want to delete this service log? This action cannot be undone.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { logToDelete = null },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                logToDelete?.let { viewModel.deleteLog(it) }
                                logToDelete = null
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Delete", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ... Keep PillTag, ComplianceWidget, ReactStyleLogCard, and AutoTrackingCard exactly as they are below this point ...
@Composable
fun PillTag(icon: ImageVector, text: String, iconColor: Color) {
    Row(
        modifier = Modifier.glassCard(
            RoundedCornerShape(12.dp),
            alpha = 0.1f,
            borderAlpha = 0.2f
        ).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ComplianceWidget(title: String, dateStr: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Icon(Icons.Default.DateRange, null, tint = color, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title.uppercase(),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                dateStr,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun ParkingLocationCard(vehicle: Vehicle) {
    val context = LocalContext.current
    val parkedLocation = remember(vehicle.lastParkedLatitude, vehicle.lastParkedLongitude) {
        val latitude = vehicle.lastParkedLatitude
        val longitude = vehicle.lastParkedLongitude
        if (latitude != null && longitude != null) StoredLocation(latitude, longitude) else null
    }
    var displayAddress by remember(parkedLocation) {
        mutableStateOf(parkedLocation?.let { ParkingLocationUtils.formatLatLng(it) } ?: "No parked location saved yet.")
    }

    LaunchedEffect(parkedLocation) {
        displayAddress = if (parkedLocation != null) {
            ParkingLocationUtils.reverseGeocode(context, parkedLocation)
        } else {
            "No parked location saved yet."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Icon(
                        Icons.Default.LocalParking,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Last Parked Location",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        displayAddress,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                    )
                    vehicle.lastParkedAt?.let { parkedAt ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Saved ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(parkedAt))}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    parkedLocation?.let { ParkingLocationUtils.launchNavigation(context, it) }
                },
                enabled = parkedLocation != null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Navigate to Vehicle", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ReactStyleLogCard(
    log: VehicleLog,
    formatter: SimpleDateFormat,
    onEditLog: () -> Unit,
    onDeleteLog: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val warningColor = Amber400
    val successColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val (iconColor, iconBg, iconVector) = when (log.type) {
        LogType.SERVICE -> Triple(
            primaryColor,
            primaryColor.copy(alpha = 0.2f),
            Icons.Default.Build
        )

        LogType.OIL_CHANGE -> Triple(
            warningColor,
            warningColor.copy(alpha = 0.2f),
            Icons.Default.InvertColors
        )

        LogType.REFUELING -> Triple(
            successColor,
            successColor.copy(alpha = 0.2f),
            Icons.Default.LocalGasStation
        )

        LogType.MODIFICATION -> Triple(
            tertiaryColor,
            tertiaryColor.copy(alpha = 0.2f),
            Icons.Default.Settings
        )

        LogType.INSURANCE -> Triple(
            primaryColor,
            primaryColor.copy(alpha = 0.2f),
            Icons.Default.Security
        )

        LogType.POLLUTION -> Triple(
            successColor,
            successColor.copy(alpha = 0.2f),
            Icons.Default.Eco
        )
    }

    Box(modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(16.dp))) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(iconColor))

            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier.background(iconBg, RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    iconColor.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                ).padding(8.dp)
                        ) {
                            Icon(
                                iconVector,
                                null,
                                tint = iconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                log.type.name.replace("_", " ").lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            val displayDate = try {
                                formatter.format(Date(log.date))
                            } catch (e: Exception) {
                                log.date
                            }
                            Text(
                                displayDate,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "₹${"%,.0f".format(log.cost)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "${"%,d".format(log.kmReading)} km",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (log.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        log.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth().background(
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                            RoundedCornerShape(12.dp)
                        ).padding(12.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                    thickness = 1.dp
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (log.nextServiceKm != null || log.nextServiceDate != null) {
                            Text(
                                "NEXT DUE:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.width(8.dp))

                            val nextInfo = mutableListOf<String>()
                            if (log.nextServiceKm != null) nextInfo.add("${"%,d".format(log.nextServiceKm)} km")
                            if (log.nextServiceDate != null) {
                                try {
                                    val d = SimpleDateFormat(
                                        "yyyy-MM-dd",
                                        Locale.getDefault()
                                    ).parse(log.nextServiceDate)
                                    if (d != null) nextInfo.add(formatter.format(d)) else nextInfo.add(
                                        log.nextServiceDate
                                    )
                                } catch (e: Exception) {
                                    nextInfo.add(log.nextServiceDate)
                                }
                            }
                            Text(
                                nextInfo.joinToString(" • "),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { onEditLog() }.padding(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { onDeleteLog() }.padding(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutoTrackingCard(
    vehicle: Vehicle,
    viewModel: VehicleViewModel,
    onStartTrip: () -> Unit
) {
    val isBtConnected by viewModel.isBtConnected.collectAsState()
    val isWifiConnected by viewModel.isWifiConnected.collectAsState()

    val activeId by viewModel.activeVehicleId.collectAsState()
    val isThisVehicleActive = activeId == vehicle.id

    val isTracking by com.tyson.autotracker.services.LocationService.isTracking.collectAsState()
    val tripDistance by com.tyson.autotracker.services.LocationService.tripDistance.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    // Launcher for Bluetooth Settings popup
    val connectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        viewModel.syncBluetoothState(context, vehicle.id)
    }

    var btDeviceName by remember(vehicle.bluetoothMacAddress) { mutableStateOf("No Device Linked") }

    LaunchedEffect(vehicle.bluetoothMacAddress) {
        if (!vehicle.bluetoothMacAddress.isNullOrEmpty()) {
            try {
                val btManager =
                    context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                val pairedDevice =
                    btManager.adapter?.bondedDevices?.find { it.address == vehicle.bluetoothMacAddress }
                if (pairedDevice != null) {
                    btDeviceName = pairedDevice.name ?: "Linked Device"
                }
            } catch (e: SecurityException) {
            }
            viewModel.syncBluetoothState(context, vehicle.id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        Column {

            // ==========================================
            // BLUETOOTH ROW
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val isActive = isBtConnected && isThisVehicleActive
                    val iconBg =
                        if (isActive) MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.2f
                        ) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                    val iconTint =
                        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(
                            alpha = 0.7f
                        )

                    Box(
                        modifier = Modifier
                            .background(iconBg, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = btDeviceName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isActive) "Connected" else "Disconnected",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (vehicle.bluetoothMacAddress != null) MaterialTheme.colorScheme.onBackground.copy(
                                alpha = 0.2f
                            ) else MaterialTheme.colorScheme.primary
                        )
                        .clickable {
                            viewModel.toggleBluetooth(
                                vehicleId = vehicle.id,
                                onConnectionFailed = {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                    connectLauncher.launch(intent)
                                }
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (vehicle.bluetoothMacAddress != null) "Clear" else "Link",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (vehicle.bluetoothMacAddress != null) MaterialTheme.colorScheme.onBackground else Color.White,
                        maxLines = 1
                    )
                }
            }

            // ==========================================
            // WI-FI ROW
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val isActive = isWifiConnected && isThisVehicleActive
                    val iconBg =
                        if (isActive) MaterialTheme.colorScheme.tertiary.copy(
                            alpha = 0.2f
                        ) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                    val iconTint =
                        if (isActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground.copy(
                            alpha = 0.7f
                        )

                    Box(
                        modifier = Modifier
                            .background(iconBg, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = vehicle.wifiSsid ?: "No Wi-Fi Linked",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isActive) "Connected" else "Disconnected",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (vehicle.wifiSsid != null) MaterialTheme.colorScheme.onBackground.copy(
                                alpha = 0.2f
                            ) else MaterialTheme.colorScheme.tertiary
                        )
                        .clickable {
                            if (vehicle.wifiSsid != null) {
                                viewModel.unlinkWifi(vehicle.id)
                            } else {
                                viewModel.linkCurrentWifi(context, vehicle.id)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (vehicle.wifiSsid != null) "Unlink" else "Link Local",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (vehicle.wifiSsid != null) MaterialTheme.colorScheme.onBackground else Color.White,
                        maxLines = 1
                    )
                }
            }

            // ==========================================
            // ANDROID AUTO HANDOVER ROW
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val isAAConnected by viewModel.isAAConnected.collectAsState()
                    val iconBg = if (isAAConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                    val iconTint = if (isAAConnected) MaterialTheme.colorScheme.primary 
                                  else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)

                    Box(
                        modifier = Modifier
                            .background(iconBg, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AA Handover",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (vehicle.useAndroidAutoHandover) "Active Connection" else "Disabled",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }

                Switch(
                    checked = vehicle.useAndroidAutoHandover,
                    onCheckedChange = { viewModel.updateVehicle(vehicle.copy(useAndroidAutoHandover = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
            }

            // ==========================================
            // TRACKING STATUS UI
            // ==========================================
            val isAAConnected by viewModel.isAAConnected.collectAsState()
            val shouldShowTracking = ((isBtConnected || isWifiConnected) || (vehicle.useAndroidAutoHandover && isAAConnected)) && isThisVehicleActive
            
            if (shouldShowTracking) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        thickness = 1.dp
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "GPS Tracking",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }

                        if (isTracking) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                                    .clickable {
                                        val intent = android.content.Intent(context, com.tyson.autotracker.services.LocationService::class.java).apply {
                                            action = "STOP"
                                        }
                                        context.startService(intent)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Stop",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            val successColor = MaterialTheme.colorScheme.secondary
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(successColor.copy(alpha = 0.2f))
                                    .clickable { onStartTrip() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = successColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Start Trip",
                                    color = successColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    "TRIP DISTANCE",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        "%.2f".format(tripDistance / 1000f),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "km",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(
                                            alpha = 0.7f
                                        ),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    "STATUS",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val statusColor =
                                        if (isTracking) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground.copy(
                                            alpha = 0.5f
                                        )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusColor)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isTracking) "Tracking Active" else "Idle",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
