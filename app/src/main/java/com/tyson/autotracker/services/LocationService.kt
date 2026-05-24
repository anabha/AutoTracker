package com.tyson.autotracker.services

import android.app.*
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.tyson.autotracker.MainActivity
import com.tyson.autotracker.data.AutotrackerDatabase
import com.tyson.autotracker.models.TripLog
import com.tyson.autotracker.utils.ParkingLocationStore
import com.tyson.autotracker.utils.ParkingLocationUtils
import com.tyson.autotracker.utils.StoredLocation
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import androidx.lifecycle.LiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private var lastLocation: Location? = null
    private var lastSavedRouteLocation: Location? = null

    // Wall-clock timestamps of the first and last GPS fix received during this trip.
    // Used to compute avgSpeed reliably even if `tripStartTime` is stale (e.g. after
    // the service was killed and restarted by the OS via START_STICKY).
    private var firstFixWallTime: Long = 0L
    private var lastFixWallTime: Long = 0L

    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var notificationJob: Job? = null
    private var carConnectionLiveData: LiveData<Int>? = null
    private val carConnectionObserver = Observer<Int> { connectionType ->
        isAAConnected.value = connectionType != CarConnection.CONNECTION_TYPE_NOT_CONNECTED
        if (!isAAConnected.value && isTracking.value) {
            checkConnectionAndStopIfNeeded()
        }
    }

    companion object {
        val tripDistance = MutableStateFlow(0f)
        val currentLocation = MutableStateFlow<Location?>(null)
        val isTracking = MutableStateFlow(false)
        val activeVehicleId = MutableStateFlow<String?>(null)

        val routePoints = MutableStateFlow<List<String>>(emptyList())
        val tripStartTime = MutableStateFlow(0L)
        val maxSpeed = MutableStateFlow(0f)
        val isAAConnected = MutableStateFlow(false)
        val isManualTrip = MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()
        carConnectionLiveData = CarConnection(this).type
        carConnectionLiveData?.observeForever(carConnectionObserver)
    }

    override fun onDestroy() {
        carConnectionLiveData?.removeObserver(carConnectionObserver)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val newVehicleId = intent?.getStringExtra("VEHICLE_ID") ?: activeVehicleId.value
        val isManual = intent?.getBooleanExtra("IS_MANUAL", false) ?: false

        if (action == "STOP") {
            stopTracking()
            return START_NOT_STICKY
        }

        if (action == "CHECK_CONNECTION") {
            checkConnectionAndStopIfNeeded()
            return START_STICKY
        }

        if (newVehicleId != null) {
            activeVehicleId.value = newVehicleId
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

        if (isTracking.value && activeVehicleId.value == newVehicleId) {
            if (intent?.hasExtra("IS_MANUAL") == true) {
                isManualTrip.value = isManual
            }
            return START_STICKY
        }

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AutoTracker::LocationWakelock")
        wakeLock?.acquire(12 * 60 * 60 * 1000L)

        tripDistance.value = 0f
        routePoints.value = emptyList()
        tripStartTime.value = System.currentTimeMillis()
        maxSpeed.value = 0f
        lastLocation = null
        lastSavedRouteLocation = null
        firstFixWallTime = 0L
        lastFixWallTime = 0L
        isManualTrip.value = isManual

        isTracking.value = true
        startLocationUpdates()

        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            tripDistance.collect {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(1, createNotification())
            }
        }

        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val now = System.currentTimeMillis()
                    if (firstFixWallTime == 0L) firstFixWallTime = now
                    lastFixWallTime = now

                    if (lastLocation != null) {
                        val distance = lastLocation!!.distanceTo(location)
                        tripDistance.value += distance

                        val currentSpeedKmh = (location.speed * 3.6f)
                        if (currentSpeedKmh > maxSpeed.value) {
                            maxSpeed.value = currentSpeedKmh
                        }

                        if (lastSavedRouteLocation == null || lastSavedRouteLocation!!.distanceTo(location) >= 50) {
                            val point = "${location.latitude},${location.longitude}"
                            routePoints.value += point
                            lastSavedRouteLocation = location
                        }
                    }
                    lastLocation = location
                    currentLocation.value = location
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        } catch (_: SecurityException) {
            isTracking.value = false
        }
    }

    private fun stopTracking() {
        if (!isTracking.value) return
        isTracking.value = false

        notificationJob?.cancel()
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }

        saveTripToDatabase {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun saveTripToDatabase(onComplete: (() -> Unit)? = null) {
        // Apply GPS calibration multiplier to compensate for the "corner-cutting" effect
        // of point-to-point distance calculation between sparse GPS samples.
        val gpsCalibrationFactor = 1.015f
        val distance = tripDistance.value * gpsCalibrationFactor
        val endTime = System.currentTimeMillis()

        // Prefer GPS-fix wall-clock times for duration. They survive even if the static
        // tripStartTime has been clobbered (e.g. service restart). Fall back to the
        // companion-object start time, then finally to "now" if neither is usable.
        val effectiveStart = when {
            firstFixWallTime > 0L -> firstFixWallTime
            tripStartTime.value > 0L -> tripStartTime.value
            else -> endTime
        }
        val effectiveEnd = if (lastFixWallTime > effectiveStart) lastFixWallTime else endTime
        val startTime = if (tripStartTime.value > 0L) tripStartTime.value else effectiveStart
        val vehicleId = activeVehicleId.value
        val points = routePoints.value
        val topSpeed = maxSpeed.value

        val durationHours = (effectiveEnd - effectiveStart) / (1000f * 60f * 60f)
        val avgSpeed = if (durationHours > 0f) (distance / 1000f) / durationHours else 0f

        Log.d(
            "LocationService",
            "saveTripToDatabase: distance=$distance, vehicleId=$vehicleId, " +
                "durationHours=$durationHours, avgSpeed=$avgSpeed"
        )

        if (vehicleId == null || distance < 10) {
            Log.d("LocationService", "Trip too short or no vehicle ID, skipping save")
            onComplete?.invoke()
            return
        }

        val trip = TripLog(
            id = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            startTime = startTime,
            endTime = endTime,
            distanceMeters = distance,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = topSpeed,
            routePoints = points.joinToString("|")
        )

        serviceScope.launch {
            try {
                val db = AutotrackerDatabase.getDatabase(this@LocationService)
                db.vehicleDao().insertTrip(trip)
                Log.d("LocationService", "Trip saved to database")

                // Mirror trip to Firestore (best-effort, errors are logged but not fatal).
                syncTripToFirestore(trip)

                val vehicle = db.vehicleDao().getVehicleByIdSync(vehicleId)
                val vehicleName = vehicle?.name ?: "Unknown Vehicle"

                if (vehicle != null) {
                    var updatedVehicle = vehicle

                    val kmAdded = distance / 1000.0
                    if (kmAdded > 0) {
                        updatedVehicle = updatedVehicle.copy(currentKm = updatedVehicle.currentKm + kmAdded)
                    }

                    val currentParkingLocation = getTripEndLocation()
                    if (currentParkingLocation != null) {
                        val parkingStore = ParkingLocationStore(this@LocationService)
                        val shouldSaveParkingLocation = ParkingLocationUtils.shouldSaveParkedLocation(
                            currentLocation = currentParkingLocation,
                            homeLocation = parkingStore.getHomeLocation(),
                            workLocation = parkingStore.getWorkLocation()
                        )

                        if (shouldSaveParkingLocation) {
                            updatedVehicle = updatedVehicle.copy(
                                lastParkedLatitude = currentParkingLocation.latitude,
                                lastParkedLongitude = currentParkingLocation.longitude,
                                lastParkedAt = endTime
                            )
                        }
                    }

                    if (updatedVehicle != vehicle) {
                        db.vehicleDao().updateVehicle(updatedVehicle)
                        // Mirror odometer/parking update to Firestore.
                        syncVehicleToFirestore(updatedVehicle)
                    }
                }

                withContext(Dispatchers.Main) {
                    showFinishedNotification(vehicleName)
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Error saving trip", e)
            } finally {
                withContext(Dispatchers.Main) {
                    Log.d("LocationService", "Trip save process complete, stopping service")
                    onComplete?.invoke()
                }
            }
        }
    }

    private suspend fun getTripEndLocation(): StoredLocation? {
        val exactLocation = ParkingLocationUtils.getCurrentExactLocation(this)
        val location = exactLocation ?: currentLocation.value ?: lastLocation
        return location?.let { StoredLocation(it.latitude, it.longitude) }
    }

    private fun syncTripToFirestore(trip: TripLog) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.w("LocationService", "Skipping trip Firestore sync: user not signed in")
            return
        }
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("trips").document(trip.id)
            .set(trip)
            .addOnSuccessListener { Log.d("LocationService", "Trip ${trip.id} synced to Firestore") }
            .addOnFailureListener { e -> Log.e("LocationService", "Trip Firestore sync failed", e) }
    }

    private fun syncVehicleToFirestore(vehicle: com.tyson.autotracker.models.Vehicle) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.w("LocationService", "Skipping vehicle Firestore sync: user not signed in")
            return
        }
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("vehicles").document(vehicle.id)
            .set(vehicle)
            .addOnSuccessListener { Log.d("LocationService", "Vehicle ${vehicle.id} synced to Firestore") }
            .addOnFailureListener { e -> Log.e("LocationService", "Vehicle Firestore sync failed", e) }
    }

    private fun createNotification(): Notification {
        // Bump channel ID one last time to clear the OS cache for the chronometer removal
        val channelId = "location_service_fluid_v3"
        val channel = NotificationChannel(channelId, "Live Trip Tracking", NotificationManager.IMPORTANCE_HIGH)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val stopIntent = Intent(this, LocationService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("SCREEN", "DRIVING")
            putExtra("VEHICLE_ID", activeVehicleId.value)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // --- Calculate live metrics ---
        val distanceKm = tripDistance.value / 1000f
        val currentSpeedKmh = (currentLocation.value?.speed ?: 0f) * 3.6f
        val topSpeedKmh = maxSpeed.value

        // Formatted strings for the notification tray
        val collapsedText = "Distance: %.2f km".format(distanceKm)
        val expandedText = "Distance: %.2f km\nCurrent Speed: %d km/h\nTop Speed: %d km/h".format(distanceKm, currentSpeedKmh.toInt(), topSpeedKmh.toInt())

        // 🔥 The compact text for the OxygenOS Fluid Cloud Pill
        val islandText = "%.1f km • %d km/h".format(distanceKm, currentSpeedKmh.toInt())

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AutoTracker")
            .setContentText(collapsedText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setSubText(islandText) // Secondary fallback for OxygenOS parsing
            .setTicker(islandText) // Primary fallback for OxygenOS parsing
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        // REMOVED: .setUsesChronometer(true) - This was blocking your custom text!

        // 🔥 THE OEM ISLAND LOGIC
        val oemExtras = Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            putBoolean("android.app.request_promoted_ongoing", true)

            // Force OxygenOS to display this exact string inside the pill
            putCharSequence("android.shortCriticalText", islandText)
            putCharSequence("android.app.short_critical_text", islandText)

            // Mimic your Beam app's exact Progress template structure
            putString("android.template", "android.app.Notification\$ProgressStyle")
            putInt("android.progress", 0)
            putInt("android.progressMax", 100)
        }
        builder.addExtras(oemExtras)

        // Indeterminate progress to trigger the active pulse in Fluid Cloud
        builder.setProgress(100, 0, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        builder.setContentIntent(mainPendingIntent)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Trip", stopPendingIntent)

        return builder.build()
    }

    private fun showFinishedNotification(vehicleName: String) {
        Log.d("LocationService", "showFinishedNotification for $vehicleName")
        val channelId = "trip_summary"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, "Trip Summary", NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)

        val distanceKm = String.format(Locale.getDefault(), "%.2f", tripDistance.value / 1000f)
        val durationMin = (System.currentTimeMillis() - tripStartTime.value) / 60000

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("SCREEN", "TRIP_LOGS")
            putExtra("VEHICLE_ID", activeVehicleId.value)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Trip Finished: $vehicleName")
            .setContentText("Drove $distanceKm km in $durationMin mins.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(2, notification)
    }

    private fun checkConnectionAndStopIfNeeded() {
        if (isManualTrip.value) return

        val currentVehicleId = activeVehicleId.value ?: return
        serviceScope.launch {
            val db = AutotrackerDatabase.getDatabase(this@LocationService)
            val vehicle = db.vehicleDao().getVehicleByIdSync(currentVehicleId) ?: return@launch

            val aaConnected = isAAConnected.value
            val useAAHandover = vehicle.useAndroidAutoHandover

            if (useAAHandover && !aaConnected) {
                delay(3000)
            }

            val isBtConnected = if (!vehicle.bluetoothMacAddress.isNullOrEmpty()) {
                try {
                    val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = btManager.adapter
                    if (adapter != null) {
                        val device = adapter.getRemoteDevice(vehicle.bluetoothMacAddress)
                        val isConnectedMethod = device.javaClass.getMethod("isConnected")
                        isConnectedMethod.invoke(device) as Boolean
                    } else false
                } catch (_: Exception) {
                    false
                }
            } else false

            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val connectionInfo: WifiInfo? = wifiManager.connectionInfo
            val currentSsid = connectionInfo?.ssid?.removeSurrounding("\"")
            val isWifiConnected = !currentSsid.isNullOrBlank() && (currentSsid == vehicle.wifiSsid || currentSsid == "\"${vehicle.wifiSsid}\"")

            if (!isBtConnected && !isWifiConnected) {
                if (!useAAHandover || !aaConnected) {
                    withContext(Dispatchers.Main) {
                        stopTracking()
                    }
                }
            }
        }
    }

    override fun onBind(p0: Intent?): IBinder? = null
}
