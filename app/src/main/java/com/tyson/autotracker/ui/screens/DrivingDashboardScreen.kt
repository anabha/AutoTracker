package com.tyson.autotracker.ui.screens

import android.app.Activity
import android.location.Geocoder
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import com.tyson.autotracker.models.Vehicle
import com.tyson.autotracker.services.LocationService
import com.tyson.autotracker.ui.viewmodels.VehicleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DrivingDashboardScreen(
    viewModel: VehicleViewModel,
    onExit: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val activeVehicleId by viewModel.activeVehicleId.collectAsState()
    val vehicles by viewModel.allVehicles.collectAsState()
    val activeVehicle = vehicles.find { it.id == activeVehicleId }

    val btConnected by viewModel.isBtConnected.collectAsState()
    val isTracking by LocationService.isTracking.collectAsState()
    val tripDistance by LocationService.tripDistance.collectAsState()

    val currentLocation by LocationService.currentLocation.collectAsState()
    val currentSpeed = currentLocation?.speed?.times(3.6f) ?: 0f

    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    // --- KEEP SCREEN ON ---
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // --- SYSTEM OFFLINE SCREEN ---
    if (activeVehicleId == null || activeVehicle == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(colorScheme.background).padding(systemBarsPadding).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse), label = "alpha"
                )
                Icon(Icons.Default.Warning, null, tint = colorScheme.primary.copy(alpha = alpha), modifier = Modifier.size(64.dp).padding(bottom = 16.dp))

                Text("SYSTEM OFFLINE", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colorScheme.onBackground, letterSpacing = 2.sp)
                Text("Select vehicle to manually initialize flight deck", color = colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(bottom = 32.dp))

                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth(0.8f)) {
                    vehicles.forEach { v ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(colorScheme.surface, RoundedCornerShape(16.dp))
                                .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .clickable { viewModel.manuallySelectVehicle(v.id) }
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(v.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colorScheme.onSurface)
                                Text("${v.make} ${v.model}".uppercase(), fontSize = 12.sp, color = colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
                            }
                            Icon(Icons.Default.Navigation, null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(Modifier.height(48.dp))
                Row(modifier = Modifier.clickable { onExit() }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChevronLeft, null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ABORT MISSION", color = colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
        }
        return
    }

    // --- ACTIVE FLIGHT DECK HUD ---
    var currentTime by remember { mutableStateOf(Date()) }
    var animationTrigger by remember { mutableStateOf(false) }
    var isImmersiveMode by remember { mutableStateOf(false) }

    LaunchedEffect(isImmersiveMode) {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, view)
            if (isImmersiveMode) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(Unit) {
        animationTrigger = true
        while (true) {
            currentTime = Date()
            delay(1000)
        }
    }

    var showTopBar by remember { mutableStateOf(false) }
    var showSpeedo by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var showOdo by remember { mutableStateOf(false) }
    var showBottomBar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100); showTopBar = true
        delay(150); showSpeedo = true
        delay(150); showMap = true
        delay(150); showOdo = true
        delay(150); showBottomBar = true
    }

    val timeFormatLandscape = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeFormatPortrait = SimpleDateFormat("h:mm a", Locale.getDefault())
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isLargeScreen = configuration.smallestScreenWidthDp >= 600

    val uiAlpha by animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing), label = "uiAlpha"
    )
    val uiScale by animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f), label = "uiScale"
    )

    // Smoothly shift gauges to exact absolute center when immersive mode is on
    val gaugesYOffset by animateDpAsState(
        targetValue = if (isImmersiveMode) 0.dp else (-24).dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f), label = "gaugesShift"
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .background(colorScheme.background)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            isImmersiveMode = !isImmersiveMode
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSize = 120f
            val dotRadius = 2f
            for (x in 0..size.width.toInt() step gridSize.toInt()) {
                for (y in 0..size.height.toInt() step gridSize.toInt()) {
                    drawCircle(color = colorScheme.onBackground.copy(alpha = 0.05f), radius = dotRadius, center = Offset(x.toFloat(), y.toFloat()))
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(colors = listOf(Color.Transparent, colorScheme.background.copy(alpha = 0.9f)), radius = 1800f)
        ))

        // --- LAYER 1: GAUGES ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(uiAlpha)
                .scale(uiScale)
                .offset(y = gaugesYOffset),
            contentAlignment = Alignment.Center
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showSpeedo,
                            enter = scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.7f)) + fadeIn(tween(600))
                        ) {
                            ThemeSpeedometerGauge(currentSpeed, modifier = Modifier.aspectRatio(1f))
                        }
                    }

                    if (isLargeScreen) {
                        Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showMap,
                                enter = scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.7f)) + fadeIn(tween(600))
                            ) {
                                ThemeLiveCenterMap(isTracking, currentLocation, modifier = Modifier.aspectRatio(1.5f))
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showOdo,
                            enter = scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.7f)) + fadeIn(tween(600))
                        ) {
                            ThemeOdometerGauge(activeVehicle, tripDistance, modifier = Modifier.aspectRatio(1f))
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.fillMaxWidth(0.85f), contentAlignment = Alignment.Center) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showSpeedo,
                            enter = scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.7f)) + fadeIn(tween(600))
                        ) {
                            ThemeSpeedometerGauge(currentSpeed, modifier = Modifier.aspectRatio(1f))
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showOdo && !isImmersiveMode,
                            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(tween(600)),
                            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(300))
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 40.dp)) {
                                Text(timeFormatPortrait.format(currentTime), fontSize = 64.sp, fontWeight = FontWeight.Light, color = colorScheme.onBackground, fontFamily = FontFamily.Monospace)
                                Text("SYSTEM TIME", fontSize = 12.sp, color = colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                            }
                        }
                    }
                }
            }
        }

        // --- LAYER 2: FLOATING CONTROLS OVERLAY ---
        Box(
            modifier = Modifier.fillMaxSize().alpha(uiAlpha).scale(uiScale)
        ) {
            // TOP CONTROLS
            androidx.compose.animation.AnimatedVisibility(
                visible = showTopBar && !isImmersiveMode,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(500)),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(colorScheme.background.copy(alpha = 0.95f), Color.Transparent)))
                        .padding(
                            top = systemBarsPadding.calculateTopPadding() + 16.dp,
                            start = 24.dp, end = 24.dp, bottom = 48.dp
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        if (isLandscape && isLargeScreen) {
                            Column {
                                Text(timeFormatLandscape.format(currentTime), fontSize = 36.sp, fontWeight = FontWeight.Light, color = colorScheme.onBackground, fontFamily = FontFamily.Monospace)
                                Text("HEADING NORTH", fontSize = 10.sp, color = colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                            }
                        } else {
                            Column {
                                Text("HEADING NORTH", fontSize = 10.sp, color = colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Icon(Icons.Default.ChevronLeft, null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp).clickable { onExit() })
                                Box(modifier = Modifier.width(1.dp).height(16.dp).background(colorScheme.onSurface.copy(alpha = 0.2f)))
                                Text(activeVehicle.name.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = colorScheme.onSurface)
                                Box(modifier = Modifier.width(1.dp).height(16.dp).background(colorScheme.onSurface.copy(alpha = 0.2f)))

                                Icon(
                                    Icons.Default.Bluetooth, null,
                                    tint = if (btConnected) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp).clickable {
                                        viewModel.toggleBluetooth(
                                            vehicleId = activeVehicle.id,
                                            onConnectionFailed = {
                                                android.widget.Toast.makeText(context, "Could not connect.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                )
                            }
                            if (isLandscape) {
                                Spacer(Modifier.height(8.dp))
                                Text("SYSTEM DIAGNOSTICS: OPTIMAL", fontSize = 10.sp, color = colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("+ 24 °C", fontSize = if (isLandscape) 20.sp else 16.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                            Text("AMBIENT", fontSize = 10.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        }
                    }
                }
            }

            // IMMERSIVE MODE CLOCK
            androidx.compose.animation.AnimatedVisibility(
                visible = showTopBar && isImmersiveMode,
                enter = fadeIn(tween(500)) + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                ) {
                    Text(timeFormatLandscape.format(currentTime), fontSize = if(isLandscape) 42.sp else 32.sp, fontWeight = FontWeight.Light, color = colorScheme.onBackground, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Text("SYSTEM TIME", fontSize = 10.sp, color = colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                }
            }

            // BOTTOM CONTROLS
            androidx.compose.animation.AnimatedVisibility(
                visible = showBottomBar && !isImmersiveMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(500)),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, colorScheme.background.copy(alpha = 0.95f))))
                        .padding(bottom = systemBarsPadding.calculateBottomPadding() + 24.dp, start = 32.dp, end = 32.dp, top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val btnColor = if (isTracking) colorScheme.error else colorScheme.secondary
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, btnColor.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .background(btnColor.copy(alpha = 0.1f))
                                .clickable {
                                    if (isTracking) {
                                        viewModel.stopTrip(context)
                                    } else {
                                        viewModel.startTrip(context, activeVehicle.id, isManual = true)
                                    }
                                }
                                .padding(horizontal = 48.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (isTracking) Icons.Default.Stop else Icons.Default.PowerSettingsNew, null, tint = btnColor, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(if (isTracking) "END MISSION" else "INITIATE TRIP", color = btnColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 3.sp)
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (isTracking) colorScheme.secondary else colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
                                Spacer(Modifier.height(4.dp))
                                Text("GPS", fontSize = 8.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (btConnected) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
                                Spacer(Modifier.height(4.dp))
                                Text("OBD2", fontSize = 8.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// LIVE MAP COMPONENT (Strict Blue/Slate Styling with Native Geocoder Search)
// =========================================================================

@Composable
fun ThemeLiveCenterMap(isTracking: Boolean, currentLocation: android.location.Location?, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val targetLocation = currentLocation?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(13.1232, 80.1065)

    // State for searching
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<android.location.Address>>(emptyList()) }

    var searchedLocation by remember { mutableStateOf<LatLng?>(null) }
    var searchedTitle by remember { mutableStateOf("") }

    val context = LocalContext.current

    // Geocoder Search Logic (Native, no extra SDK needed)
    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            delay(500) // Debounce typing
            withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val results = geocoder.getFromLocationName(searchQuery, 4)
                    searchResults = results ?: emptyList()
                } catch (e: Exception) {
                    searchResults = emptyList()
                }
            }
        } else {
            searchResults = emptyList()
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(targetLocation, 16f)
    }

    // Camera Logic: Prioritize searched location, fallback to live tracking
    LaunchedEffect(targetLocation, isTracking, searchedLocation) {
        if (searchedLocation != null) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(searchedLocation!!, 15f))
        } else if (isTracking) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(targetLocation, 17f))
        }
    }

    val darkBlueMapStyle = """
        [
          { "elementType": "geometry", "stylers": [{"color": "#020617"}] },
          { "elementType": "labels", "stylers": [{"visibility": "off"}] },
          { "featureType": "road", "elementType": "geometry", "stylers": [{"color": "#0f172a"}] },
          { "featureType": "road", "elementType": "geometry.stroke", "stylers": [{"color": "#1e293b"}] },
          { "featureType": "road.arterial", "elementType": "geometry", "stylers": [{"color": "#1d4ed8"}] },
          { "featureType": "road.highway", "elementType": "geometry", "stylers": [{"color": "#2563eb"}] },
          { "featureType": "road.highway", "elementType": "geometry.stroke", "stylers": [{"color": "#1e40af"}] },
          { "featureType": "water", "elementType": "geometry", "stylers": [{"color": "#000000"}] }
        ]
    """.trimIndent()

    Box(modifier = modifier) {
        // The Base Map
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(colorScheme.surface)
                .border(1.dp, colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = isTracking && searchedLocation == null,
                    mapStyleOptions = MapStyleOptions(darkBlueMapStyle)
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    compassEnabled = false,
                    myLocationButtonEnabled = false
                )
            ) {
                // Static marker if tracking is off and we haven't searched anything
                if (!isTracking && searchedLocation == null) {
                    Marker(state = MarkerState(position = targetLocation), title = "Current Position")
                }

                // Display the searched location with a theme-matching blue marker
                searchedLocation?.let { loc ->
                    Marker(
                        state = MarkerState(position = loc),
                        title = searchedTitle,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                }
            }

            // Radar Pulse Effect (Only when tracking AND not looking at a searched location)
            if (isTracking && searchedLocation == null) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f, targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(animation = tween(1500), repeatMode = RepeatMode.Reverse), label = "scale"
                )
                Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(50)).border(2.dp, colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(50)).graphicsLayer(scaleX = scale, scaleY = scale))
            }
        }

        // Search Bar Overlay
        Box(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colorScheme.surface.copy(alpha = 0.95f))
                        .border(1.dp, colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                ) {
                    if (isSearchExpanded) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = colorScheme.onSurface, fontSize = 14.sp),
                            modifier = Modifier
                                .width(180.dp)
                                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search destination...", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isSearchExpanded && searchQuery.isNotEmpty()) {
                                isSearchExpanded = false
                            } else {
                                isSearchExpanded = !isSearchExpanded
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded && searchQuery.isEmpty()) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = colorScheme.primary
                        )
                    }
                }

                // Search Results Dropdown
                androidx.compose.animation.AnimatedVisibility(visible = searchResults.isNotEmpty() && isSearchExpanded) {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(228.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorScheme.surface.copy(alpha = 0.95f))
                            .border(1.dp, colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    ) {
                        searchResults.forEach { result ->
                            val addressName = result.getAddressLine(0) ?: result.featureName ?: "Unknown Location"
                            Text(
                                text = addressName,
                                color = colorScheme.onSurface,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchedLocation = LatLng(result.latitude, result.longitude)
                                        searchedTitle = addressName
                                        isSearchExpanded = false
                                        searchQuery = ""
                                        searchResults = emptyList()
                                    }
                                    .padding(12.dp),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            HorizontalDivider(color = colorScheme.primary.copy(alpha = 0.1f))
                        }
                    }
                }
            }
        }

        // Recenter Button (Appears when viewing a searched location)
        if (searchedLocation != null) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(50))
                    .background(colorScheme.surface.copy(alpha = 0.95f))
                    .border(1.dp, colorScheme.secondary.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clickable {
                        searchedLocation = null // Clears search and snaps back to live tracking
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recenter", tint = colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Recenter", color = colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =========================================================================
// GAUGE COMPONENTS
// =========================================================================

@Composable
fun ThemeSpeedometerGauge(speed: Float, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val animatedSpeed by animateFloatAsState(targetValue = speed, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "")

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().background(colorScheme.primary.copy(alpha = 0.02f), RoundedCornerShape(50)))
        Box(modifier = Modifier.fillMaxSize().border(1.dp, colorScheme.onSurfaceVariant.copy(alpha = 0.1f), RoundedCornerShape(50)))

        Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            val startAngle = 140f
            val sweepAngle = 260f

            drawArc(
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )

            val activeSweep = (animatedSpeed / 200f).coerceIn(0.01f, 1f) * sweepAngle
            drawArc(
                color = colorScheme.primary.copy(alpha = 0.3f),
                startAngle = startAngle, sweepAngle = activeSweep, useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = colorScheme.primary,
                startAngle = startAngle, sweepAngle = activeSweep, useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )

            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2
            for (i in 0..10) {
                val angleInDegrees = startAngle + (i * (sweepAngle / 10))
                val angleInRad = Math.toRadians(angleInDegrees.toDouble())
                val start = Offset(
                    x = center.x + (radius - 12.dp.toPx()) * cos(angleInRad).toFloat(),
                    y = center.y + (radius - 12.dp.toPx()) * sin(angleInRad).toFloat()
                )
                val end = Offset(
                    x = center.x + radius * cos(angleInRad).toFloat(),
                    y = center.y + radius * sin(angleInRad).toFloat()
                )
                drawLine(color = colorScheme.onSurfaceVariant.copy(alpha = 0.3f), start = start, end = end, strokeWidth = 2.dp.toPx())
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SPEED", fontSize = 10.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Text(animatedSpeed.toInt().toString(), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = colorScheme.onBackground, fontFamily = FontFamily.Monospace)
            Text("kph", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = colorScheme.primary, letterSpacing = 2.sp)

            Spacer(Modifier.height(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(0.6f)) {
                Text("FM1", fontSize = 8.sp, color = colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
                Text("97.6 MHz", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun ThemeOdometerGauge(vehicle: Vehicle, tripDistance: Float, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().background(colorScheme.primary.copy(alpha = 0.02f), RoundedCornerShape(50)))
        Box(modifier = Modifier.fillMaxSize().border(1.dp, colorScheme.onSurfaceVariant.copy(alpha = 0.1f), RoundedCornerShape(50)))

        Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            val startAngle = 140f
            val sweepAngle = 260f

            drawArc(
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )

            drawArc(
                color = colorScheme.primary.copy(alpha = 0.3f),
                startAngle = startAngle, sweepAngle = sweepAngle * 0.4f, useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = colorScheme.primary,
                startAngle = startAngle, sweepAngle = sweepAngle * 0.4f, useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ODOMETER", fontSize = 10.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Text(vehicle.currentKm.toString(), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = colorScheme.onBackground, fontFamily = FontFamily.Monospace)
            Text("km", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = colorScheme.primary, letterSpacing = 2.sp)

            Spacer(Modifier.height(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TRIP", fontSize = 10.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("%.2f km".format(tripDistance / 1000f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colorScheme.onBackground, fontFamily = FontFamily.Monospace)

                // Battery / Fuel Progress Bar
                Box(modifier = Modifier.width(64.dp).height(4.dp).padding(top = 8.dp).background(colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(50))) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.4f).background(colorScheme.primary, RoundedCornerShape(50)))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("BATTERY / FUEL: 40%", fontSize = 8.sp, color = colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
        }
    }
}
