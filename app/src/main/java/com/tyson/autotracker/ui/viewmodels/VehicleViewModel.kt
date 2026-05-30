package com.tyson.autotracker.ui.viewmodels

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.car.app.connection.CarConnection
import com.tyson.autotracker.data.AutotrackerDatabase
import com.tyson.autotracker.models.TripLog
import com.tyson.autotracker.models.Vehicle
import com.tyson.autotracker.models.VehicleLog
import com.tyson.autotracker.models.VisitedPlace
import com.tyson.autotracker.models.PlaceVisitHistory
import com.tyson.autotracker.services.LocationService
import com.tyson.autotracker.utils.ParkingLocationStore
import com.tyson.autotracker.utils.ParkingLocationUtils
import com.tyson.autotracker.utils.ParkingPlaces
import com.tyson.autotracker.utils.StoredLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

import com.tyson.autotracker.data.repository.VehicleRepository

data class ReminderAlert(val vehicleName: String, val type: String, val message: String, val isUrgent: Boolean)
enum class ThemeMode { LIGHT, DARK, SYSTEM }
sealed class NavigationEvent { object StartDriving : NavigationEvent() }

class VehicleViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AutotrackerDatabase.getDatabase(application)
    private val dao = db.vehicleDao()
    private val visitedPlaceDao = db.visitedPlaceDao()
    private val repository = VehicleRepository(dao)
    private val themePrefs = application.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    private val disclosurePrefs = application.getSharedPreferences("location_prefs", Context.MODE_PRIVATE)
    private val authPrefs = application.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val parkingLocationStore = ParkingLocationStore(application)

    private val _themeMode = MutableStateFlow(ThemeMode.valueOf(themePrefs.getString("mode", "SYSTEM") ?: "SYSTEM"))
    val themeMode = _themeMode.asStateFlow()

    private val _useDynamicColor = MutableStateFlow(themePrefs.getBoolean("dynamic_color", true))
    val useDynamicColor = _useDynamicColor.asStateFlow()

    private val _hasAcceptedLocationDisclosure = MutableStateFlow(disclosurePrefs.getBoolean("has_accepted_location_disclosure", false))
    val hasAcceptedLocationDisclosure = _hasAcceptedLocationDisclosure.asStateFlow()

    private val _continueWithoutLogin = MutableStateFlow(authPrefs.getBoolean("continue_without_login", false))
    val continueWithoutLogin = _continueWithoutLogin.asStateFlow()

    private val _parkingPlaces = MutableStateFlow(parkingLocationStore.getPlaces())
    val parkingPlaces = _parkingPlaces.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    var pendingVehicleIdForBluetooth: String? = null

    fun setThemeMode(mode: ThemeMode) {
        themePrefs.edit().putString("mode", mode.name).apply()
        _themeMode.value = mode
    }

    fun setUseDynamicColor(use: Boolean) {
        themePrefs.edit().putBoolean("dynamic_color", use).apply()
        _useDynamicColor.value = use
    }

    fun acceptLocationDisclosure() {
        disclosurePrefs.edit().putBoolean("has_accepted_location_disclosure", true).apply()
        _hasAcceptedLocationDisclosure.value = true
    }

    fun continueWithoutLogin() {
        authPrefs.edit().putBoolean("continue_without_login", true).apply()
        _continueWithoutLogin.value = true
    }

    fun clearContinueWithoutLogin() {
        authPrefs.edit().putBoolean("continue_without_login", false).apply()
        _continueWithoutLogin.value = false
    }

    fun setHomeLocationFromCurrent(context: Context, onComplete: (Boolean) -> Unit) {
        saveParkingPlaceFromCurrent(context, isHome = true, onComplete = onComplete)
    }

    fun setWorkLocationFromCurrent(context: Context, onComplete: (Boolean) -> Unit) {
        saveParkingPlaceFromCurrent(context, isHome = false, onComplete = onComplete)
    }

    fun clearHomeLocation() {
        parkingLocationStore.clearHomeLocation()
        _parkingPlaces.value = parkingLocationStore.getPlaces()
    }

    fun clearWorkLocation() {
        parkingLocationStore.clearWorkLocation()
        _parkingPlaces.value = parkingLocationStore.getPlaces()
    }

    private fun saveParkingPlaceFromCurrent(
        context: Context,
        isHome: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val location = ParkingLocationUtils.getCurrentExactLocation(context)
            if (location == null) {
                onComplete(false)
                return@launch
            }

            val storedLocation = StoredLocation(location.latitude, location.longitude)
            if (isHome) {
                parkingLocationStore.saveHomeLocation(storedLocation)
            } else {
                parkingLocationStore.saveWorkLocation(storedLocation)
            }
            _parkingPlaces.value = parkingLocationStore.getPlaces()
            onComplete(true)
        }
    }

    fun deleteAccountAndData(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = FirebaseAuth.getInstance().currentUser ?: run {
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }
                val uid = user.uid
                val firestore = FirebaseFirestore.getInstance()

                // 1. Wipe Firestore
                val collections = listOf("vehicles", "logs", "trips")
                for (collection in collections) {
                    val snapshot = firestore.collection("users").document(uid).collection(collection).get().await()
                    for (doc in snapshot.documents) {
                        doc.reference.delete().await()
                    }
                }
                firestore.collection("users").document(uid).delete().await()

                // 2. Wipe Local Room DB
                dao.clearAllData()
                visitedPlaceDao.deleteAllVisitedPlaces()

                // 3. Delete Auth User
                user.delete().await()

                withContext(Dispatchers.Main) {
                    onComplete(true)
                }
            } catch (e: Exception) {
                Log.e("VehicleViewModel", "Error deleting account", e)
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    val allVehicles: StateFlow<List<Vehicle>> = dao.getAllVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalInvestment: StateFlow<Double> = dao.getAllLogs().map { logs -> logs.sumOf { it.cost } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalLogsCount: StateFlow<Int> = dao.getAllLogs().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val categoryBreakdown: StateFlow<Map<com.tyson.autotracker.models.LogType, Double>> = dao.getAllLogs().map { logs ->
        logs.groupBy { it.type }.mapValues { (_, typeLogs) -> typeLogs.sumOf { it.cost } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allVisitedPlaces: StateFlow<List<VisitedPlace>> = visitedPlaceDao.getAllVisitedPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getVisitHistory(placeId: String): Flow<List<PlaceVisitHistory>> =
        visitedPlaceDao.getHistoryForPlace(placeId)

    fun deleteVisitedPlace(placeId: String) = viewModelScope.launch {
        visitedPlaceDao.deletePlace(placeId)
    }

    // --- CONNECTION STATES ---
    private val _isBtConnected = MutableStateFlow(false)
    val isBtConnected = _isBtConnected.asStateFlow()

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected = _isWifiConnected.asStateFlow()

    private val _isAAConnected = MutableStateFlow(false)
    val isAAConnected = _isAAConnected.asStateFlow()

    private val _activeVehicleId = MutableStateFlow<String?>(null)
    val activeVehicleId = _activeVehicleId.asStateFlow()

    // --- BATTERY OPTIMIZATION TRACKERS ---
    private var lastSeenWifiSsid: String? = ""
    private var lastSyncTime = 0L

    private fun getCurrentWifiSsid(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val ssid = info?.ssid?.removeSurrounding("\"")
            if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                return ssid
            }
        } catch (e: Exception) {}
        return null
    }

    private fun isBluetoothConnected(context: Context, mac: String?): Boolean {
        if (mac.isNullOrEmpty()) return false
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = btManager.adapter ?: return false

            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else true

            if (!hasPermission) return false

            val device = adapter.getRemoteDevice(mac)
            val isConnectedMethod = device.javaClass.getMethod("isConnected")
            return isConnectedMethod.invoke(device) as Boolean
        } catch (e: Exception) {
            return false
        }
    }

    // Evaluates connections simultaneously on a background thread to save battery.
    private fun syncConnections(context: Context, forced: Boolean = false) {
        // BATTERY FIX 1: Throttle checks to a maximum of once every 3 seconds (unless forced by disconnect)
        val now = System.currentTimeMillis()
        if (!forced && now - lastSyncTime < 3000) return
        lastSyncTime = now

        // BATTERY FIX 2: Move heavy Java Reflection off the Main Thread
        viewModelScope.launch(Dispatchers.IO) {
            val vehicles = allVehicles.value
            if (vehicles.isEmpty()) return@launch

            val activeWifiSsid = getCurrentWifiSsid(context)
            val aaConnected = _isAAConnected.value

            var foundVehicle: Vehicle? = null
            var isBt = false
            var isWifi = false
            var shouldContinueCurrent = false

            // 1. Check current vehicle first
            val currentId = _activeVehicleId.value
            if (currentId != null) {
                val currentVehicle = vehicles.find { it.id == currentId }
                if (currentVehicle != null) {
                    val btMatch = isBluetoothConnected(context, currentVehicle.bluetoothMacAddress)
                    val wifiMatch = currentVehicle.wifiSsid != null && currentVehicle.wifiSsid == activeWifiSsid

                    if (btMatch || wifiMatch || (currentVehicle.useAndroidAutoHandover && aaConnected)) {
                        shouldContinueCurrent = true
                        isBt = btMatch
                        isWifi = wifiMatch
                    }
                }
            }

            // 2. If current vehicle disconnected, look for a new one
            if (!shouldContinueCurrent) {
                for (v in vehicles) {
                    val btMatch = isBluetoothConnected(context, v.bluetoothMacAddress)
                    val wifiMatch = v.wifiSsid != null && v.wifiSsid == activeWifiSsid

                    if (btMatch || wifiMatch) {
                        foundVehicle = v
                        isBt = btMatch
                        isWifi = wifiMatch
                        break
                    }
                }
            }

            // 3. Switch back to Main Thread to update UI and start/stop Services
            withContext(Dispatchers.Main) {
                if (shouldContinueCurrent) {
                    _isBtConnected.value = isBt
                    _isWifiConnected.value = isWifi
                } else if (foundVehicle != null) {
                    _activeVehicleId.value = foundVehicle.id
                    // AUTO-START TRIP
                    val serviceIntent = Intent(context, LocationService::class.java).apply {
                        putExtra("VEHICLE_ID", foundVehicle.id)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                    viewModelScope.launch { _navigationEvent.emit(NavigationEvent.StartDriving) }
                    _isBtConnected.value = isBt
                    _isWifiConnected.value = isWifi
                } else {
                    // STOP TRIP IF DISCONNECTED (No BT, No WiFi, No AA)
                    if (_activeVehicleId.value != null) {
                        // Short Delay to check for BT ghosting after AA unplug
                        if (forced) {
                            delay(3000)
                            // Re-verify after delay
                            val vehiclesAfter = allVehicles.value
                            val currentId = _activeVehicleId.value
                            val v = vehiclesAfter.find { it.id == currentId }
                            val btStill = if (v != null) isBluetoothConnected(context, v.bluetoothMacAddress) else false
                            val wifiStill = if (v != null) v.wifiSsid != null && v.wifiSsid == getCurrentWifiSsid(context) else false
                            
                            if (btStill || wifiStill || (v?.useAndroidAutoHandover == true && _isAAConnected.value)) {
                                return@withContext 
                            }
                        }

                        _activeVehicleId.value = null
                        _isBtConnected.value = false
                        _isWifiConnected.value = false
                        val serviceIntent = Intent(context, LocationService::class.java).apply { action = "STOP" }
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val mac = device?.address ?: return

                    // Handle manual linking from Add Vehicle screen
                    if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED && pendingVehicleIdForBluetooth != null) {
                        val v = allVehicles.value.find { it.id == pendingVehicleIdForBluetooth }
                        if (v != null) {
                            updateVehicle(v.copy(bluetoothMacAddress = mac))
                        }
                        pendingVehicleIdForBluetooth = null
                    }

                    // BATTERY FIX 3: Ignore all smartwatch/headphone broadcasts.
                    // Only wake up the app if the MAC address belongs to one of your cars.
                    val isKnownVehicle = allVehicles.value.any { it.bluetoothMacAddress == mac }
                    if (isKnownVehicle || pendingVehicleIdForBluetooth != null) {
                        syncConnections(context, forced = intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    }
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    val newSsid = getCurrentWifiSsid(context)

                    // BATTERY FIX 4: Ignore background Wi-Fi roaming spam.
                    // Only wake up the app if the network name physically changed.
                    if (newSsid != lastSeenWifiSsid) {
                        lastSeenWifiSsid = newSsid
                        syncConnections(context, forced = newSsid == null)
                    }
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            allVehicles.collect {
                syncConnections(getApplication())
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(connectionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            application.registerReceiver(connectionReceiver, filter)
        }

        // Initialize active vehicle from service if it's already running
        _activeVehicleId.value = LocationService.activeVehicleId.value

        CarConnection(application).type.observeForever { connectionType ->
            val isAA = connectionType != CarConnection.CONNECTION_TYPE_NOT_CONNECTED
            if (_isAAConnected.value != isAA) {
                _isAAConnected.value = isAA
                // Ensure we check connectivity when AA status changes, bypassing throttle for disconnections
                syncConnections(application, forced = !isAA)
            }
        }
    }

    // --- Wi-Fi Setup Methods ---
    fun linkCurrentWifi(context: Context, vehicleId: String) {
        val ssid = getCurrentWifiSsid(context)

        if (ssid.isNullOrEmpty()) {
            Toast.makeText(context, "Cannot read Wi-Fi. Ensure Location is ON.", Toast.LENGTH_SHORT).show()
            return
        }

        val vehicle = allVehicles.value.find { it.id == vehicleId }
        if (vehicle != null) {
            updateVehicle(vehicle.copy(wifiSsid = ssid))
            Toast.makeText(context, "Linked to $ssid", Toast.LENGTH_SHORT).show()

            viewModelScope.launch {
                delay(200)
                syncConnections(context)
            }
        }
    }

    fun unlinkWifi(vehicleId: String) {
        val vehicle = allVehicles.value.find { it.id == vehicleId }
        if (vehicle != null) {
            updateVehicle(vehicle.copy(wifiSsid = null))
            viewModelScope.launch {
                delay(200)
                syncConnections(getApplication())
            }
        }
    }

    fun manuallySelectVehicle(vehicleId: String?) {
        _activeVehicleId.value = vehicleId
    }

    fun startTrip(context: Context, vehicleId: String, isManual: Boolean = false) {
        val serviceIntent = Intent(context, LocationService::class.java).apply {
            putExtra("VEHICLE_ID", vehicleId)
            putExtra("IS_MANUAL", isManual)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun stopTrip(context: Context) {
        val serviceIntent = Intent(context, LocationService::class.java).apply {
            action = "STOP"
        }
        context.startService(serviceIntent)
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(connectionReceiver)
    }

    fun syncBluetoothState(context: Context, vehicleId: String) {
        syncConnections(context)
    }

    fun toggleBluetooth(vehicleId: String, onConnectionFailed: () -> Unit) {
        viewModelScope.launch {
            val vehicle = allVehicles.value.find { it.id == vehicleId } ?: return@launch
            val isCurrentlyConnected = isBluetoothConnected(getApplication(), vehicle.bluetoothMacAddress)

            if (isCurrentlyConnected && _activeVehicleId.value == vehicleId) {
                _activeVehicleId.value = null
                _isBtConnected.value = false
                _isWifiConnected.value = false
                val serviceIntent = Intent(getApplication(), LocationService::class.java).apply { action = "STOP" }
                getApplication<Application>().startService(serviceIntent)
            } else {
                pendingVehicleIdForBluetooth = vehicleId
                syncConnections(getApplication())
                if (!_isBtConnected.value) {
                    onConnectionFailed()
                } else {
                    pendingVehicleIdForBluetooth = null
                }
            }
        }
    }

    data class BackupPayload(
        val vehicles: List<Vehicle>,
        val logs: List<VehicleLog>,
        val trips: List<TripLog>
    )

    fun exportDatabase(context: Context, uri: android.net.Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val vehiclesToExport = allVehicles.value
                val logsToExport = dao.getAllLogs().first()
                val tripsToExport = dao.getAllTrips().first()

                val backup = BackupPayload(vehiclesToExport, logsToExport, tripsToExport)
                val json = com.google.gson.Gson().toJson(backup)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    java.io.OutputStreamWriter(outputStream).use { writer ->
                        writer.write(json)
                    }
                }
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun importDatabase(context: Context, uri: android.net.Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    java.io.BufferedReader(java.io.InputStreamReader(inputStream)).use { reader ->
                        reader.readText()
                    }
                } ?: return@launch withContext(Dispatchers.Main) { onComplete(false) }

                val backupType = object : com.google.gson.reflect.TypeToken<BackupPayload>() {}.type
                val backup: BackupPayload = com.google.gson.Gson().fromJson(json, backupType)

                dao.insertVehicles(backup.vehicles)
                dao.insertLogs(backup.logs)
                dao.insertTrips(backup.trips)

                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun restoreFromFirebase(onComplete: (Boolean) -> Unit) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: run {
            onComplete(false)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                val uid = user.uid

                // 1. Fetch Vehicles
                val vehiclesSnapshot = db.collection("users").document(uid).collection("vehicles").get().await()
                val vehicles = vehiclesSnapshot.toObjects(Vehicle::class.java)

                // 2. Fetch Logs
                val logsSnapshot = db.collection("users").document(uid).collection("logs").get().await()
                val logs = logsSnapshot.toObjects(VehicleLog::class.java)

                // 3. Fetch Trips
                val tripsSnapshot = db.collection("users").document(uid).collection("trips").get().await()
                val trips = tripsSnapshot.toObjects(TripLog::class.java)

                // 4. Batch Insert into Room
                vehicles.forEach { dao.insertVehicle(it) }
                logs.forEach { dao.insertLog(it) }
                trips.forEach { dao.insertTrip(it) }

                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

    /**
     * Runs [action] against the user's Firestore document. Any returned [com.google.android.gms.tasks.Task]
     * gets success/failure listeners attached so silent failures are visible in Logcat.
     * If the user is not signed in (or chose "continue without login"), the call is a logged no-op.
     */
    private fun syncToFirebase(
        label: String = "sync",
        action: (com.google.firebase.firestore.DocumentReference) -> com.google.android.gms.tasks.Task<*>?
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.w("VehicleViewModel", "Skipping Firestore $label: user not signed in")
            return
        }
        val userDoc = firestore.collection("users").document(uid)
        try {
            val task = action(userDoc)
            task?.addOnSuccessListener {
                    Log.d("VehicleViewModel", "Firestore $label succeeded")
                }
                ?.addOnFailureListener { e ->
                    Log.e("VehicleViewModel", "Firestore $label failed", e)
                }
        } catch (e: Exception) {
            Log.e("VehicleViewModel", "Firestore $label threw", e)
        }
    }

    fun addVehicle(vehicle: Vehicle) = viewModelScope.launch {
        dao.insertVehicle(vehicle)
        syncToFirebase("addVehicle ${vehicle.id}") { userDoc ->
            userDoc.collection("vehicles").document(vehicle.id).set(vehicle)
        }
    }

    fun updateVehicle(vehicle: Vehicle) = viewModelScope.launch {
        dao.updateVehicle(vehicle)
        syncToFirebase("updateVehicle ${vehicle.id}") { userDoc ->
            userDoc.collection("vehicles").document(vehicle.id).set(vehicle)
        }
    }

    fun deleteVehicle(vehicleId: String) = viewModelScope.launch {
        dao.deleteVehicle(vehicleId)
        syncToFirebase("deleteVehicle $vehicleId") { userDoc ->
            userDoc.collection("vehicles").document(vehicleId).delete()
        }
    }

    fun addLog(log: VehicleLog) = viewModelScope.launch {
        dao.insertLog(log)
        syncToFirebase("addLog ${log.id}") { userDoc ->
            userDoc.collection("logs").document(log.id).set(log)
        }

        if (log.type == com.tyson.autotracker.models.LogType.SERVICE || log.type == com.tyson.autotracker.models.LogType.OIL_CHANGE) {
            val allLogs = dao.getLogsForVehicle(log.vehicleId).first()
            allLogs.forEach { oldLog ->
                if (oldLog.id != log.id) cancelReminders(oldLog)
            }
        }

        // Sync expiry date to vehicle for Insurance/Pollution logs
        syncExpiryDateToVehicle(log)

        scheduleReminders(log)
    }

    fun addTrip(trip: TripLog) = viewModelScope.launch {
        dao.insertTrip(trip)
        syncToFirebase("addTrip ${trip.id}") { userDoc ->
            userDoc.collection("trips").document(trip.id).set(trip)
        }

        val vehicle = dao.getVehicleByIdSync(trip.vehicleId)
        if (vehicle != null) {
            val kmAdded = trip.distanceMeters / 1000.0
            if (kmAdded > 0) {
                val updatedVehicle = vehicle.copy(currentKm = vehicle.currentKm + kmAdded)
                dao.updateVehicle(updatedVehicle)
                syncToFirebase("addTrip:updateVehicle ${updatedVehicle.id}") { userDoc ->
                    userDoc.collection("vehicles").document(updatedVehicle.id).set(updatedVehicle)
                }
            }
        }
    }

    private fun scheduleReminders(log: VehicleLog) {
        // Support reminders for service/oil nextServiceDate AND insurance/pollution expiryDate
        val dateToSchedule = when (log.type) {
            com.tyson.autotracker.models.LogType.INSURANCE, com.tyson.autotracker.models.LogType.POLLUTION -> log.expiryDate
            else -> log.nextServiceDate
        }
        if (dateToSchedule == null || dateToSchedule.isBlank()) return

        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        try {
            val expiry = sdf.parse(dateToSchedule) ?: return
            val calendar = Calendar.getInstance()
            calendar.time = expiry
            calendar.set(Calendar.HOUR_OF_DAY, 9)
            calendar.set(Calendar.MINUTE, 0)

            val vehicleName = allVehicles.value.find { it.id == log.vehicleId }?.name ?: "Vehicle"
            val typeStr = when (log.type) {
                com.tyson.autotracker.models.LogType.OIL_CHANGE -> "Oil Change"
                com.tyson.autotracker.models.LogType.INSURANCE -> "Insurance"
                com.tyson.autotracker.models.LogType.POLLUTION -> "Pollution"
                else -> "Service"
            }

            val offsets = listOf(0, -1, -2)
            offsets.forEach { offset ->
                val scheduleTime = calendar.clone() as Calendar
                scheduleTime.add(Calendar.DAY_OF_YEAR, offset)

                if (scheduleTime.timeInMillis > System.currentTimeMillis()) {
                    val intent = Intent(context, com.tyson.autotracker.services.ReminderReceiver::class.java).apply {
                        putExtra("TITLE", "$vehicleName $typeStr Reminder")
                        val msg = if (offset == 0) "Your $typeStr is due today!" else "$typeStr due in ${-offset} days"
                        putExtra("MESSAGE", msg)
                        putExtra("VEHICLE_ID", log.vehicleId)
                        putExtra("NOTIFICATION_ID", (log.id.hashCode() + offset))
                    }

                    val pendingIntent = android.app.PendingIntent.getBroadcast(
                        context, log.id.hashCode() + offset, intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )

                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        scheduleTime.timeInMillis,
                        pendingIntent
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteLog(log: VehicleLog) = viewModelScope.launch {
        dao.deleteLog(log)
        syncToFirebase("deleteLog ${log.id}") { userDoc ->
            userDoc.collection("logs").document(log.id).delete()
        }
        cancelReminders(log)
    }

    private fun cancelReminders(log: VehicleLog) {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

        listOf(0, -1, -2).forEach { offset ->
            val intent = Intent(context, com.tyson.autotracker.services.ReminderReceiver::class.java)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context, log.id.hashCode() + offset, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    fun updateLog(log: VehicleLog) = viewModelScope.launch {
        dao.updateLog(log)
        syncToFirebase("updateLog ${log.id}") { userDoc ->
            userDoc.collection("logs").document(log.id).set(log)
        }
        cancelReminders(log)
        scheduleReminders(log)

        // Sync expiry date to vehicle for Insurance/Pollution logs
        syncExpiryDateToVehicle(log)
    }

    private suspend fun syncExpiryDateToVehicle(log: VehicleLog) {
        if (log.expiryDate.isNullOrBlank()) return
        val vehicle = dao.getVehicleByIdSync(log.vehicleId) ?: return

        val updatedVehicle = when (log.type) {
            com.tyson.autotracker.models.LogType.INSURANCE -> vehicle.copy(insuranceDate = log.expiryDate)
            com.tyson.autotracker.models.LogType.POLLUTION -> vehicle.copy(puccDate = log.expiryDate)
            else -> return
        }

        dao.updateVehicle(updatedVehicle)
        syncToFirebase("syncExpiry:updateVehicle ${updatedVehicle.id}") { userDoc ->
            userDoc.collection("vehicles").document(updatedVehicle.id).set(updatedVehicle)
        }
    }

    fun getLogsForVehicle(vehicleId: String) = dao.getLogsForVehicle(vehicleId)
    fun getTripsForVehicle(vehicleId: String) = dao.getTripsForVehicle(vehicleId)

    /**
     * Cumulative mileage: (lastKm - firstKm) / totalLiters (excluding last fill-up).
     * Accuracy improves with every additional refueling log.
     */
    fun getAverageMileage(vehicleId: String): Flow<Float> =
        dao.getLogsForVehicle(vehicleId).map { logs ->
            val fuelLogs = logs
                .filter { it.type == com.tyson.autotracker.models.LogType.REFUELING && it.fuelLiters != null && it.fuelLiters > 0 }
                .sortedBy { it.kmReading }

            if (fuelLogs.size < 2) return@map 0f

            val totalDistance = fuelLogs.last().kmReading - fuelLogs.first().kmReading
            // Exclude the last fill-up — that fuel hasn't been driven on yet
            val totalLiters = fuelLogs.dropLast(1).sumOf { it.fuelLiters ?: 0.0 }

            if (totalLiters <= 0 || totalDistance <= 0) 0f
            else (totalDistance / totalLiters).toFloat()
        }

    /**
     * Estimated full-tank range = averageMileage × fuelCapacityLiters
     */
    fun getEstimatedRange(vehicleId: String): Flow<Float> =
        combine(
            getAverageMileage(vehicleId),
            allVehicles.map { list -> list.find { it.id == vehicleId } }
        ) { mileage, vehicle ->
            val capacity = vehicle?.fuelCapacityLiters ?: 0.0
            if (mileage <= 0f || capacity <= 0.0) 0f
            else mileage * capacity.toFloat()
        }

    fun deleteTrip(trip: TripLog) = viewModelScope.launch {
        val vehicle = dao.getVehicleByIdSync(trip.vehicleId)
        if (vehicle != null) {
            val kmRemoved = trip.distanceMeters / 1000.0
            if (kmRemoved > 0) {
                val newKm = max(0.0, vehicle.currentKm - kmRemoved)
                val updatedVehicle = vehicle.copy(currentKm = newKm)
                dao.updateVehicle(updatedVehicle)
                syncToFirebase("deleteTrip:updateVehicle ${updatedVehicle.id}") { userDoc ->
                    userDoc.collection("vehicles").document(updatedVehicle.id).set(updatedVehicle)
                }
            }
        }
        dao.deleteTrip(trip.id)
        syncToFirebase("deleteTrip ${trip.id}") { userDoc ->
            userDoc.collection("trips").document(trip.id).delete()
        }
    }

    val upcomingReminders: StateFlow<List<ReminderAlert>> = combine(allVehicles, dao.getAllLogs()) { vehicles, logs ->
        val alerts = mutableListOf<ReminderAlert>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = Calendar.getInstance().time

        vehicles.forEach { vehicle ->
            checkDateReminder(vehicle.name, "PUCC", vehicle.puccDate, sdf, today)?.let { alerts.add(it) }
            checkDateReminder(vehicle.name, "Insurance", vehicle.insuranceDate, sdf, today)?.let { alerts.add(it) }
            val lastLog = logs.filter { it.vehicleId == vehicle.id }.maxByOrNull { it.date }
            lastLog?.nextServiceKm?.let { nextKm ->
                val diff = nextKm - vehicle.currentKm.toInt()
                if (diff <= 500) alerts.add(ReminderAlert(vehicle.name, "Service", "Service due in $diff km", diff <= 100))
            }
        }
        alerts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun checkDateReminder(vName: String, type: String, dateStr: String?, sdf: SimpleDateFormat, today: Date): ReminderAlert? {
        if (dateStr == null) return null
        return try {
            val expiry = sdf.parse(dateStr) ?: return null
            val diffDays = ((expiry.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
            if (diffDays <= 30) ReminderAlert(vName, type, if (diffDays < 0) "Expired ${-diffDays} days ago" else "Expires in $diffDays days", diffDays <= 7) else null
        } catch (e: Exception) { null }
    }
}
