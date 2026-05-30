package com.tyson.autotracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tyson.autotracker.services.LocationService
import com.tyson.autotracker.ui.screens.*
import com.tyson.autotracker.ui.theme.AutoTrackerTheme
import com.tyson.autotracker.ui.viewmodels.VehicleViewModel
import com.tyson.autotracker.ui.viewmodels.NavigationEvent
import com.tyson.autotracker.auth.GoogleAuthManager

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var intentState by mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intentState = intent

        setContent {
            val vehicleViewModel: VehicleViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )

            val themeMode by vehicleViewModel.themeMode.collectAsState()
            val useDynamicColor by vehicleViewModel.useDynamicColor.collectAsState()
            val hasAcceptedDisclosure by vehicleViewModel.hasAcceptedLocationDisclosure.collectAsState()
            val continueWithoutLogin by vehicleViewModel.continueWithoutLogin.collectAsState()

            AutoTrackerTheme(themeMode = themeMode, dynamicColor = useDynamicColor) {
                val navController = rememberNavController()
                val context = LocalContext.current

                val authManager = remember { GoogleAuthManager(context) }
                val startRoute = remember(hasAcceptedDisclosure, continueWithoutLogin) {
                    if (!hasAcceptedDisclosure) {
                        "disclosure"
                    } else if (authManager.getCurrentUser() != null || continueWithoutLogin) {
                        "dashboard"
                    } else {
                        "login"
                    }
                }

                // Logic to handle navigation after disclosure acceptance
                LaunchedEffect(hasAcceptedDisclosure) {
                    if (hasAcceptedDisclosure) {
                        val currentRoute = navController.currentDestination?.route
                        if (currentRoute == "disclosure") {
                            // After disclosure, ALWAYS go to login screen first
                            navController.navigate("login") {
                                popUpTo("disclosure") { inclusive = true }
                            }
                        }
                    }
                }

                val backgroundPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    // Background permission handled
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    // Request background location after notification permission if needed
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }

                val fineLocationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    if (fineGranted) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // On older versions where background permission is required but notification isn't
                            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                }

                // Apply initial vehicle ID if it's already in the intent
                LaunchedEffect(Unit) {
                    intentState?.getStringExtra("VEHICLE_ID")?.let {
                        vehicleViewModel.manuallySelectVehicle(it)
                    }
                }

                LaunchedEffect(intentState) {
                    val currentIntent = intentState
                    val screen = currentIntent?.getStringExtra("SCREEN")
                    val vId = currentIntent?.getStringExtra("VEHICLE_ID")
                    
                    when (screen) {
                        "DRIVING" -> {
                            vId?.let { vehicleViewModel.manuallySelectVehicle(it) }
                            if (navController.currentDestination?.route != "driving") {
                                navController.navigate("driving") {
                                    popUpTo("dashboard") { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        }
                        "TRIP_LOGS" -> {
                            vId?.let {
                                navController.navigate("trip_logs/$it") {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                    // Reset intent state after handling
                    intentState = null
                }

                LaunchedEffect(Unit) {
                    vehicleViewModel.navigationEvent.collect { event ->
                        when (event) {
                            is NavigationEvent.StartDriving -> {
                                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission && navController.currentDestination?.route != "driving") {
                                    navController.navigate("driving") {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                        val intent = Intent(context, LocationService::class.java)
                        context.startForegroundService(intent)
                        navController.navigate("driving")
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(500, easing = FastOutSlowInEasing)
                        )
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth / 3 },
                            animationSpec = tween(500, easing = FastOutSlowInEasing)
                        )
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth / 3 },
                            animationSpec = tween(500, easing = FastOutSlowInEasing)
                        )
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(500, easing = FastOutSlowInEasing)
                        )
                    }
                ) {

                    composable("disclosure") {
                        LocationDisclosureScreen(
                            viewModel = vehicleViewModel,
                            onAgreed = {
                                vehicleViewModel.acceptLocationDisclosure()
                                
                                val nextRoute = if (authManager.getCurrentUser() != null) "dashboard" else "login"
                                navController.navigate(nextRoute) {
                                    popUpTo("disclosure") { inclusive = true }
                                }
                                
                                // Sequential permission chain: Fine -> Notification -> Background
                                fineLocationLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ))
                            },
                            onDenied = {
                                finish()
                            }
                        )
                    }

                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = {
                                vehicleViewModel.clearContinueWithoutLogin()
                                vehicleViewModel.restoreFromFirebase { success ->
                                    navController.navigate("dashboard") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            },
                            onContinueWithoutLogin = {
                                vehicleViewModel.continueWithoutLogin()
                                navController.navigate("dashboard") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("dashboard") {
                        DashboardScreen(
                            viewModel = vehicleViewModel,
                            onSelectVehicle = { id -> navController.navigate("details/$id") },
                            onNavigateToAddVehicle = { navController.navigate("add_vehicle") },
                            onNavigateToInsights = { navController.navigate("insights") },
                            onNavigateToSettings = { navController.navigate("settings") },
                            onNavigateToVisitedPlaces = { navController.navigate("visited_places") },
                            onStartTrip = {
                                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    navController.navigate("driving")
                                } else {
                                    permissionLauncher.launch(arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ))
                                }
                            }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            viewModel = vehicleViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onLogoutSuccess = {
                                vehicleViewModel.clearContinueWithoutLogin()
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = "add_vehicle?vehicleId={vehicleId}",
                        arguments = listOf(navArgument("vehicleId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                    ) { backStackEntry ->
                        val vehicleId = backStackEntry.arguments?.getString("vehicleId")
                        AddVehicleScreen(
                            viewModel = vehicleViewModel,
                            vehicleId = vehicleId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = "details/{vehicleId}",
                        arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val vehicleId = backStackEntry.arguments?.getString("vehicleId") ?: ""
                        VehicleDetailsScreen(
                            vehicleId = vehicleId,
                            viewModel = vehicleViewModel,
                            onBack = { navController.popBackStack() },
                            onEdit = { id -> navController.navigate("add_vehicle?vehicleId=$id") },
                            onAddLog = { id -> navController.navigate("add_log/$id") },
                            // FIXED: Added the missing onEditLog callback
                            onEditLog = { logId -> navController.navigate("add_log/$vehicleId?logId=$logId") },
                            onNavigateToTrips = { id -> navController.navigate("trip_logs/$id") },
                            onStartTrip = {
                                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    vehicleViewModel.manuallySelectVehicle(vehicleId)
                                    navController.navigate("driving")
                                } else {
                                    permissionLauncher.launch(arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ))
                                }
                            }
                        )
                    }

                    composable(
                        route = "trip_logs/{vehicleId}",
                        arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val vehicleId = backStackEntry.arguments?.getString("vehicleId") ?: ""
                        TripLogsScreen(
                            vehicleId = vehicleId,
                            viewModel = vehicleViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    // FIXED: Updated Add Log route to accept the optional logId for editing
                    composable(
                        route = "add_log/{vehicleId}?logId={logId}",
                        arguments = listOf(
                            navArgument("vehicleId") { type = NavType.StringType },
                            navArgument("logId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val vehicleId = backStackEntry.arguments?.getString("vehicleId") ?: ""
                        val logId = backStackEntry.arguments?.getString("logId")
                        AddLogScreen(
                            vehicleId = vehicleId,
                            logId = logId,
                            viewModel = vehicleViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("insights") {
                        InsightsScreen(
                            viewModel = vehicleViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("visited_places") {
                        VisitedPlacesScreen(
                            viewModel = vehicleViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("driving") {
                        DrivingDashboardScreen(
                            viewModel = vehicleViewModel,
                            onExit = {
                                vehicleViewModel.stopTrip(this@MainActivity)

                                if (!vehicleViewModel.isBtConnected.value) {
                                    vehicleViewModel.manuallySelectVehicle(null)
                                }

                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
