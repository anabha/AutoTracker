package com.tyson.autotracker.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyson.autotracker.BuildConfig
import com.tyson.autotracker.auth.GoogleAuthManager
import com.tyson.autotracker.ui.theme.GlassBackground
import com.tyson.autotracker.ui.theme.glassCard
import com.tyson.autotracker.ui.viewmodels.VehicleViewModel
import com.tyson.autotracker.utils.ParkingLocationUtils
import com.tyson.autotracker.utils.StoredLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: VehicleViewModel,
    onNavigateBack: () -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val context = LocalContext.current
    val authManager = remember { GoogleAuthManager(context) }
    val user = authManager.getCurrentUser()

    val themeMode by viewModel.themeMode.collectAsState()
    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
    val parkingPlaces by viewModel.parkingPlaces.collectAsState()

    var isSyncEnabled by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportDatabase(context, it) { success ->
                val msg = if (success) "Backup exported successfully!" else "Export failed"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importDatabase(context, it) { success ->
                val msg = if (success) "Backup imported successfully!" else "Invalid backup file"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // GUARANTEE: Physical back button AND System Edge Swipe returns to Dashboard safely
    BackHandler {
        onNavigateBack()
    }

    // Removed the custom pointerInput drag gesture.
    // We now rely purely on Android's native back gesture (edge swipe) and the BackHandler.
    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
            ) {
                // --- PROFILE SECTION ---
                item {
                    Box(modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(24.dp)).padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val initial = user?.displayName?.take(1)?.uppercase() ?: "U"
                                Text(initial, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(user?.displayName ?: "Unknown Driver", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(Modifier.height(4.dp))
                                Text(user?.email ?: "No email linked", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                // --- THEME SECTION ---
                item {
                    Text("Appearance", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 8.dp))
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(16.dp))) {
                        Column {
                            // Dynamic Color Toggle (Android 12+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column {
                                            Text("Dynamic Color", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                            Text("Use system accent colors", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                                        }
                                    }
                                    Switch(
                                        checked = useDynamicColor,
                                        onCheckedChange = { viewModel.setUseDynamicColor(it) }
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }

                            // Theme Mode Selector
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                                    Icon(
                                        imageVector = when(themeMode) {
                                            com.tyson.autotracker.ui.viewmodels.ThemeMode.LIGHT -> Icons.Default.LightMode
                                            com.tyson.autotracker.ui.viewmodels.ThemeMode.DARK -> Icons.Default.DarkMode
                                            else -> Icons.Default.BrightnessAuto
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Theme Mode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                    Text(themeMode.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                                }
                                
                                val modes = com.tyson.autotracker.ui.viewmodels.ThemeMode.values()
                                var expanded by remember { mutableStateOf(false) }
                                
                                Box {
                                    IconButton(onClick = { expanded = true }) {
                                        Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        modes.forEach { mode ->
                                            DropdownMenuItem(
                                                text = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                                onClick = {
                                                    viewModel.setThemeMode(mode)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- PARKING PLACES SECTION ---
                item {
                    Text("Parking Places", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 8.dp))
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(16.dp))) {
                        Column {
                            ParkingPlaceSettingsRow(
                                icon = Icons.Default.Home,
                                iconColor = MaterialTheme.colorScheme.primary,
                                title = "Home Location",
                                location = parkingPlaces.home,
                                onSetCurrentLocation = {
                                    viewModel.setHomeLocationFromCurrent(context) { success ->
                                        Toast.makeText(
                                            context,
                                            if (success) "Home location saved" else "Couldn't read current location",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onClear = {
                                    viewModel.clearHomeLocation()
                                    Toast.makeText(context, "Home location cleared", Toast.LENGTH_SHORT).show()
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            ParkingPlaceSettingsRow(
                                icon = Icons.Default.Work,
                                iconColor = MaterialTheme.colorScheme.tertiary,
                                title = "Work Location",
                                location = parkingPlaces.work,
                                onSetCurrentLocation = {
                                    viewModel.setWorkLocationFromCurrent(context) { success ->
                                        Toast.makeText(
                                            context,
                                            if (success) "Work location saved" else "Couldn't read current location",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onClear = {
                                    viewModel.clearWorkLocation()
                                    Toast.makeText(context, "Work location cleared", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // --- CLOUD SYNC SECTION ---
                item {
                    Text("Data & Backup", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 8.dp))
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(16.dp))) {
                        Column {
                            // Live Sync Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text("Firebase Live Sync", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                        Text(if (isSyncEnabled) "Real-time backup active" else "Sync paused", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                                    }
                                }
                                Switch(
                                    checked = isSyncEnabled,
                                    onCheckedChange = { isSyncEnabled = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.secondary, checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            SettingsMenuItem(
                                icon = Icons.Default.CloudDownload,
                                iconColor = MaterialTheme.colorScheme.secondary,
                                title = "Restore from Cloud",
                                subtitle = "Download your saved data from Firebase"
                            ) {
                                viewModel.restoreFromFirebase { success ->
                                    val msg = if (success) "Cloud restore complete!" else "Restore failed"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            SettingsMenuItem(
                                icon = Icons.Default.FileUpload,
                                iconColor = MaterialTheme.colorScheme.primary,
                                title = "Export Garage Data",
                                subtitle = "Save a local backup copy"
                            ) {
                                exportLauncher.launch("AutoTracker_Backup_${System.currentTimeMillis()}.json")
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            SettingsMenuItem(
                                icon = Icons.Default.FileDownload,
                                iconColor = MaterialTheme.colorScheme.tertiary,
                                title = "Import Garage Data",
                                subtitle = "Restore from a local backup"
                            ) {
                                importLauncher.launch(arrayOf("application/json"))
                            }
                        }
                    }
                }

                // --- SUPPORT SECTION ---
                item {
                    Text("Support", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 8.dp))
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(16.dp))) {
                        SettingsMenuItem(
                            icon = Icons.Default.Email,
                            iconColor = MaterialTheme.colorScheme.primary,
                            title = "Contact Support",
                            subtitle = "Reach out for help or feedback",
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:tyson323.dev@gmail.com")
                                    putExtra(Intent.EXTRA_SUBJECT, "AutoTracker App Support")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No email app found on this device.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                // --- ACCOUNT SECTION ---
                item {
                    Text("Account", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 8.dp))
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().glassCard(RoundedCornerShape(16.dp))) {
                        Column {
                            SettingsMenuItem(
                                icon = Icons.Default.Logout,
                                iconColor = MaterialTheme.colorScheme.error,
                                title = "Sign Out",
                                subtitle = "Disconnect this device",
                                onClick = {
                                    authManager.signOut()
                                    onLogoutSuccess()
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            SettingsMenuItem(
                                icon = Icons.Default.DeleteForever,
                                iconColor = MaterialTheme.colorScheme.error,
                                title = "Delete Account & Data",
                                subtitle = "Permanently wipe everything",
                                onClick = { showDeleteDialog = true }
                            )
                        }
                    }
                }

                // --- VERSION INFO ---
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "AutoTracker v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Made with ❤️ by Tyson",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
                title = { Text("Delete Account & Data?") },
                text = { Text("Are you absolutely sure? This will permanently wipe all your vehicles, service logs, and trips from this device and the cloud. This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            isDeleting = true
                            viewModel.deleteAccountAndData { success ->
                                isDeleting = false
                                showDeleteDialog = false
                                if (success) {
                                    onLogoutSuccess()
                                } else {
                                    Toast.makeText(context, "Error deleting account. Please try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = !isDeleting
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Delete Permanently")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteDialog = false },
                        enabled = !isDeleting
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ParkingPlaceSettingsRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    location: StoredLocation?,
    onSetCurrentLocation: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    var displayAddress by remember(location) {
        mutableStateOf(location?.let { ParkingLocationUtils.formatLatLng(it) } ?: "Not set")
    }

    LaunchedEffect(location) {
        displayAddress = if (location != null) {
            ParkingLocationUtils.reverseGeocode(context, location)
        } else {
            "Not set"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.background(iconColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(8.dp)) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(displayAddress, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), maxLines = 2)
        }
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = onSetCurrentLocation) {
            Icon(Icons.Default.MyLocation, contentDescription = "Use current location", tint = iconColor)
        }
        if (location != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Clear location", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun SettingsMenuItem(icon: ImageVector, iconColor: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.background(iconColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(8.dp)) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
    }
}
