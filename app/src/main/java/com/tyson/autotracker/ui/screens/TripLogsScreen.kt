package com.tyson.autotracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.compose.*
import com.tyson.autotracker.models.TripLog
import com.tyson.autotracker.ui.theme.GlassBackground
import com.tyson.autotracker.ui.theme.glassCard
import com.tyson.autotracker.ui.viewmodels.VehicleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.location.Geocoder
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripLogsScreen(
    vehicleId: String,
    viewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val trips by viewModel.getTripsForVehicle(vehicleId).collectAsState(emptyList())
    val vehicles by viewModel.allVehicles.collectAsState()
    val vehicle = vehicles.find { it.id == vehicleId }
    val previousOdo = vehicle?.currentKm ?: 0
    
    val formatTime = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())

    // State for the delete confirmation bottom sheet
    var tripToDelete by remember { mutableStateOf<TripLog?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // State for the manual trip add modal
    var showAddTripModal by remember { mutableStateOf(false) }

    val blurEffect by animateDpAsState(
        targetValue = if (showAddTripModal || tripToDelete != null) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "BlurEffect"
    )

    GlassBackground {
        Scaffold(
            modifier = Modifier.blur(blurEffect),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Trip History", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                    navigationIcon = {
                        Row(
                            modifier = Modifier.clickable { onNavigateBack() }.padding(8.dp).glassCard(RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.width(8.dp))
                            Text("Back", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAddTripModal = true },
                            modifier = Modifier.padding(end = 8.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Trip", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
            ) {
                if (trips.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().glassCard().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No trips recorded yet.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        }
                    }
                } else {
                    items(trips) { trip ->
                        // Pass the trip to the bottom sheet state instead of deleting immediately
                        TripLogCard(trip, formatTime) { tripToDelete = trip }
                    }
                }
            }
        }

        // --- MANUAL TRIP ADD MODAL ---
        if (showAddTripModal) {
            ManualTripAddModal(
                vehicleId = vehicleId,
                previousOdo = previousOdo,
                onDismiss = { showAddTripModal = false },
                onSave = { trip ->
                    viewModel.addTrip(trip)
                    showAddTripModal = false
                }
            )
        }

        // Delete Confirmation Bottom Sheet
        if (tripToDelete != null) {
            ModalBottomSheet(
                onDismissRequest = { tripToDelete = null },
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
                        text = "Delete Trip?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Are you sure you want to delete this trip? The distance will be subtracted from the vehicle's odometer. This cannot be undone.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { tripToDelete = null },
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
                                tripToDelete?.let { viewModel.deleteTrip(it) }
                                tripToDelete = null
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

@Composable
fun TripLogCard(trip: TripLog, formatTime: SimpleDateFormat, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val durationMins = ((trip.endTime - trip.startTime) / 60000).coerceAtLeast(1)

    Box(modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(16.dp)).clickable { expanded = !expanded }) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(formatTime.format(Date(trip.startTime)), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp).clickable { onDelete() })
            }

            Spacer(Modifier.height(16.dp))

            // Stats 2x2 Grid Layout
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TripStatBlock("DISTANCE", "%.1f km".format(trip.distanceMeters / 1000f), Modifier.weight(1f))
                    TripStatBlock("DURATION", "$durationMins min", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TripStatBlock("AVG SPEED", "%.1f km/h".format(trip.avgSpeedKmh), Modifier.weight(1f))
                    TripStatBlock("MAX SPEED", "%.1f km/h".format(trip.maxSpeedKmh), Modifier.weight(1f))
                }
            }

            // Expandable Mini-Map Polyline
            AnimatedVisibility(visible = expanded) {
                val points = remember(trip.routePoints) {
                    trip.routePoints.split("|").mapNotNull {
                        val parts = it.split(",")
                        if (parts.size == 2) LatLng(parts[0].toDouble(), parts[1].toDouble()) else null
                    }
                }

                if (points.size >= 2) {
                    val cameraState = rememberCameraPositionState()

                    LaunchedEffect(points) {
                        val bounds = LatLngBounds.Builder().apply { points.forEach { include(it) } }.build()
                        delay(200)
                        cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                    }

                    val darkBlueMapStyle = """[{"elementType":"geometry","stylers":[{"color":"#020617"}]},{"elementType":"labels","stylers":[{"visibility":"off"}]},{"featureType":"road","elementType":"geometry","stylers":[{"color":"#0f172a"}]},{"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#1e293b"}]},{"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#1d4ed8"}]},{"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#2563eb"}]},{"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#1e40af"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#000000"}]}]"""

                    Box(modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraState,
                            uiSettings = MapUiSettings(zoomControlsEnabled = false, scrollGesturesEnabled = false, rotationGesturesEnabled = false),
                            properties = MapProperties(mapStyleOptions = MapStyleOptions(darkBlueMapStyle))
                        ) {
                            Polyline(points = points, color = MaterialTheme.colorScheme.primary, width = 12f)
                            Marker(state = MarkerState(points.first()), title = "Start", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                            Marker(state = MarkerState(points.last()), title = "End", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        }
                    }
                } else {
                    Text("GPS data too brief to map.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

@Composable
fun TripStatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTripAddModal(
    vehicleId: String,
    previousOdo: Int,
    onDismiss: () -> Unit,
    onSave: (TripLog) -> Unit
) {
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var startTimeStr by remember { mutableStateOf("09:00") }
    var endTimeStr by remember { mutableStateOf("10:00") }
    
    var startLocation by remember { mutableStateOf<LatLng?>(null) }
    var endLocation by remember { mutableStateOf<LatLng?>(null) }
    var startAddress by remember { mutableStateOf("") }
    var endAddress by remember { mutableStateOf("") }
    
    var currentOdoStr by remember { mutableStateOf("") }
    var distanceCalcMode by remember { mutableStateOf("MAP") } // "MAP" or "ODO"

    var isSelectingStart by remember { mutableStateOf(true) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val mapDistanceMeters = remember(startLocation, endLocation) {
        if (startLocation != null && endLocation != null) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                startLocation!!.latitude, startLocation!!.longitude,
                endLocation!!.latitude, endLocation!!.longitude,
                results
            )
            results[0]
        } else 0f
    }

    val odoDistanceMeters = remember(currentOdoStr, previousOdo) {
        val currentOdo = currentOdoStr.toIntOrNull() ?: previousOdo
        (currentOdo - previousOdo).coerceAtLeast(0) * 1000f
    }

    val distanceMeters = if (distanceCalcMode == "ODO") odoDistanceMeters else mapDistanceMeters

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(13.0827, 80.2707), 10f)
    }

    // Auto-zoom to fit both markers
    LaunchedEffect(startLocation, endLocation) {
        if (startLocation != null && endLocation != null) {
            val bounds = LatLngBounds.builder()
                .include(startLocation!!)
                .include(endLocation!!)
                .build()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .glassCard(shape = RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add Manual Trip", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    IconButton(onClick = onDismiss, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), CircleShape)) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onBackground)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) {
                        LocalDatePickerField("Date", date) { date = it }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) {
                        GlassTimePickerField("Start Time", startTimeStr) { startTimeStr = it }
                    }
                    Box(Modifier.weight(1f)) {
                        GlassTimePickerField("End Time", endTimeStr) { endTimeStr = it }
                    }
                }

                // Distance Calculation Mode Toggle
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Distance Source", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .background(if (distanceCalcMode == "MAP") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { distanceCalcMode = "MAP" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Map Calculation", color = if (distanceCalcMode == "MAP") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .background(if (distanceCalcMode == "ODO") MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { distanceCalcMode = "ODO" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Odometer Reading", color = if (distanceCalcMode == "ODO") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (distanceCalcMode == "ODO") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) {
                            OdoInputField("Previous Odo", previousOdo.toString(), {}, readOnly = true)
                        }
                        Box(Modifier.weight(1f)) {
                            val isError = currentOdoStr.isNotEmpty() && (currentOdoStr.toIntOrNull() ?: 0) < previousOdo
                            OdoInputField(
                                label = "Current Odo",
                                value = currentOdoStr,
                                onValueChange = { currentOdoStr = it },
                                isError = isError
                            )
                        }
                    }
                }

                // Location Search & Selection (Always visible for route visualization)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LocationSearchInput(
                        label = "Start Location",
                        currentAddress = startAddress,
                        isSelected = isSelectingStart,
                        onSelect = { isSelectingStart = true },
                        onLocationFound = { latLng, addr ->
                            startLocation = latLng
                            startAddress = addr
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                        },
                        onCurrentLocationClick = {
                            if (hasLocationPermission) {
                                try {
                                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                        loc?.let {
                                            val latLng = LatLng(it.latitude, it.longitude)
                                            startLocation = latLng
                                            scope.launch(Dispatchers.IO) {
                                                val geocoder = Geocoder(context)
                                                val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                                                startAddress = addresses?.getOrNull(0)?.getAddressLine(0) ?: "Current Location"
                                            }
                                            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                                        }
                                    }
                                } catch (e: SecurityException) {}
                            }
                        }
                    )

                    LocationSearchInput(
                        label = "End Location",
                        currentAddress = endAddress,
                        isSelected = !isSelectingStart,
                        onSelect = { isSelectingStart = false },
                        onLocationFound = { latLng, addr ->
                            endLocation = latLng
                            endAddress = addr
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                        },
                        onCurrentLocationClick = {
                            if (hasLocationPermission) {
                                try {
                                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                        loc?.let {
                                            val latLng = LatLng(it.latitude, it.longitude)
                                            endLocation = latLng
                                            scope.launch(Dispatchers.IO) {
                                                val geocoder = Geocoder(context)
                                                val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                                                endAddress = addresses?.getOrNull(0)?.getAddressLine(0) ?: "Current Location"
                                            }
                                            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                                        }
                                    }
                                } catch (e: SecurityException) {}
                            }
                        }
                    )
                }

                // Map Display
                Box(modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(20.dp))) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        onMapClick = { latLng ->
                            if (isSelectingStart) {
                                startLocation = latLng
                                scope.launch(Dispatchers.IO) {
                                    val geocoder = Geocoder(context)
                                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                                    startAddress = addresses?.getOrNull(0)?.getAddressLine(0) ?: "Custom Point"
                                }
                            } else {
                                endLocation = latLng
                                scope.launch(Dispatchers.IO) {
                                    val geocoder = Geocoder(context)
                                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                                    endAddress = addresses?.getOrNull(0)?.getAddressLine(0) ?: "Custom Point"
                                }
                            }
                        },
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = hasLocationPermission),
                        properties = MapProperties(isMyLocationEnabled = hasLocationPermission)
                    ) {
                        startLocation?.let { Marker(state = MarkerState(it), title = "Start: $startAddress", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)) }
                        endLocation?.let { Marker(state = MarkerState(it), title = "End: $endAddress", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) }
                        if (startLocation != null && endLocation != null) {
                            Polyline(
                                points = listOf(startLocation!!, endLocation!!),
                                color = MaterialTheme.colorScheme.primary,
                                width = 12f,
                                jointType = com.google.android.gms.maps.model.JointType.ROUND,
                                endCap = com.google.android.gms.maps.model.RoundCap()
                            )
                        }
                    }
                    
                    // Map Overlay Hint
                    Box(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).glassCard(CircleShape, alpha = 0.6f).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(if (isSelectingStart) "Tap map for Start Point" else "Tap map for End Point", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Distance & Summary
                Row(
                    modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(20.dp), alpha = 0.1f).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ESTIMATED DISTANCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        Text("%.2f km".format(distanceMeters / 1000f), fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Icon(Icons.Default.DirectionsCar, null, tint = if (distanceCalcMode == "ODO") MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                }

                    val isValidOdo = distanceCalcMode != "ODO" || (currentOdoStr.toIntOrNull() ?: 0) >= previousOdo

                    Button(
                        onClick = {
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val startTimestamp = sdf.parse("$date $startTimeStr")?.time ?: System.currentTimeMillis()
                            val endTimestamp = sdf.parse("$date $endTimeStr")?.time ?: System.currentTimeMillis()
                            
                            val routePoints = if (startLocation != null && endLocation != null) {
                                "${startLocation!!.latitude},${startLocation!!.longitude}|${endLocation!!.latitude},${endLocation!!.longitude}"
                            } else ""

                            val newTrip = TripLog(
                                vehicleId = vehicleId,
                                startTime = startTimestamp,
                                endTime = endTimestamp,
                                distanceMeters = distanceMeters,
                                avgSpeedKmh = if (endTimestamp > startTimestamp) (distanceMeters / 1000f) / ((endTimestamp - startTimestamp) / 3600000f) else 0f,
                                maxSpeedKmh = 0f,
                                routePoints = routePoints
                            )
                            onSave(newTrip)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = distanceMeters > 0 && (distanceCalcMode == "ODO" || (startLocation != null && endLocation != null)) && isValidOdo,
                        colors = ButtonDefaults.buttonColors(containerColor = if (distanceCalcMode == "ODO") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                    ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Confirm & Save Trip", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LocationSearchInput(
    label: String,
    currentAddress: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLocationFound: (LatLng, String) -> Unit,
    onCurrentLocationClick: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<android.location.Address>()) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(query) {
        if (query.length > 2) {
            delay(600) // Debounce to avoid excessive Geocoder calls
            withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context)
                    // limit to 5 results for efficiency
                    val addresses = geocoder.getFromLocationName(query, 5)
                    withContext(Dispatchers.Main) {
                        suggestions = addresses ?: emptyList()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { suggestions = emptyList() }
                }
            }
        } else {
            suggestions = emptyList()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        OutlinedTextField(
            value = query,
            onValueChange = { 
                query = it
                if (!isSelected) onSelect()
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onSelect() },
            placeholder = { 
                Text(
                    text = currentAddress.ifEmpty { "Search address..." }, 
                    maxLines = 1, 
                    fontSize = 14.sp, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ) 
            },
            leadingIcon = { 
                Icon(
                    Icons.Default.LocationOn, 
                    null, 
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ) 
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; suggestions = emptyList() }) {
                            Icon(Icons.Default.Close, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onCurrentLocationClick) {
                        Icon(Icons.Default.MyLocation, "Current", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                focusedContainerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent,
                unfocusedContainerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent
            )
        )

        AnimatedVisibility(visible = suggestions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(RoundedCornerShape(16.dp), alpha = 0.2f)
                    .padding(vertical = 4.dp)
            ) {
                suggestions.forEach { address ->
                    val addressLine = address.getAddressLine(0) ?: ""
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLocationFound(LatLng(address.latitude, address.longitude), addressLine)
                                query = ""
                                suggestions = emptyList()
                                focusManager.clearFocus()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = addressLine,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            val subText = listOfNotNull(address.locality, address.adminArea, address.countryName).joinToString(", ")
                            if (subText.isNotEmpty()) {
                                Text(
                                    text = subText,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (address != suggestions.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlassTimePickerField(label: String, value: String, onTimeSelected: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        Box(modifier = Modifier.clickable {
            val parts = value.split(":")
            android.app.TimePickerDialog(context, { _, h, m ->
                onTimeSelected("%02d:%02d".format(h, m))
            }, parts[0].toInt(), parts[1].toInt(), true).show()
        }.glassCard(RoundedCornerShape(16.dp), alpha = 0.05f)) {
            OutlinedTextField(
                value = value,
                onValueChange = { },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDatePickerField(label: String, value: String, onDateSelected: (String) -> Unit) {
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

@Composable
fun OdoInputField(label: String, value: String, onValueChange: (String) -> Unit, readOnly: Boolean = false, isError: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            enabled = !readOnly,
            isError = isError,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                unfocusedBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
