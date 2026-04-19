package com.tyson.autotracker.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tyson.autotracker.data.AutotrackerDatabase
import com.tyson.autotracker.models.Vehicle
import com.tyson.autotracker.services.LocationService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DrivingDashboardCarScreen(carContext: CarContext, val vehicleId: String) : Screen(carContext), DefaultLifecycleObserver {

    private var vehicle: Vehicle? = null
    private var speed = 0f
    private var distance = 0f
    private var isTracking = false
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        // Fetch vehicle for Odometer
        lifecycleScope.launch {
            val dao = AutotrackerDatabase.getDatabase(carContext).vehicleDao()
            dao.getAllVehicles().collect { list ->
                vehicle = list.find { it.id == vehicleId }
                invalidate()
            }
        }

        // Listen to the exact same GPS service the phone uses
        lifecycleScope.launch {
            LocationService.currentLocation.collect { loc ->
                speed = (loc?.speed ?: 0f) * 3.6f
                invalidate() // Forces Android Auto to redraw the numbers
            }
        }
        lifecycleScope.launch {
            LocationService.tripDistance.collect { dist ->
                distance = dist
                invalidate()
            }
        }
        lifecycleScope.launch {
            LocationService.isTracking.collect { track ->
                isTracking = track
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        // 1. VELOCITY (Speedometer)
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("VELOCITY")
                .addText("${speed.toInt()} kph")
                .build()
        )
        
        // 2. ODOMETER
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("ODOMETER")
                .addText("${vehicle?.currentKm ?: 0} km")
                .build()
        )

        // 3. TRIP DISTANCE
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("TRIP DISTANCE")
                .addText(String.format("%.2f km", distance / 1000f))
                .build()
        )

        // 4. SYSTEM TIME (Clock)
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("SYSTEM TIME")
                .addText(timeFormat.format(Date()))
                .build()
        )

        // The Action Button (Start/Stop) - Matching labels and colors from mobile UI
        val actionTitle = if (isTracking) "END MISSION" else "INITIATE TRIP"
        val actionColor = if (isTracking) CarColor.RED else CarColor.GREEN

        paneBuilder.addAction(
            Action.Builder()
                .setTitle(actionTitle)
                .setBackgroundColor(actionColor)
                .setOnClickListener {
                    val intent = Intent(carContext, LocationService::class.java)
                    if (!isTracking) {
                        intent.putExtra("VEHICLE_ID", vehicleId)
                        ContextCompat.startForegroundService(carContext, intent)
                    } else {
                        intent.action = "STOP"
                        carContext.startService(intent)
                    }
                }
                .build()
        )

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(vehicle?.name?.uppercase() ?: "FLIGHT DECK")
            .setHeaderAction(Action.BACK)
            .build()
    }
}