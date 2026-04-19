package com.tyson.autotracker.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.tyson.autotracker.data.AutotrackerDatabase
import com.tyson.autotracker.models.Vehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainCarScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private var vehicles: List<Vehicle> = emptyList()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        // Observe lifecycle to know when the car screen is active
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // Fetch vehicles from Room Database when screen starts
        val dao = AutotrackerDatabase.getDatabase(carContext).vehicleDao()

        coroutineScope.launch {
            dao.getAllVehicles().collect { vList ->
                vehicles = vList
                // Tell Android Auto to redraw the screen with the new data
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        if (vehicles.isEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Your garage is empty")
                    .addText("Add a vehicle on your phone first.")
                    .build()
            )
        } else {
            // Build a row for each vehicle
            vehicles.forEach { vehicle ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(vehicle.name)
                        .addText("${vehicle.year} ${vehicle.make} ${vehicle.model} • ${vehicle.currentKm} km")
                        .setOnClickListener {
                            // Navigate to the Vehicle Menu to choose Driving or Logging
                            screenManager.push(VehicleMenuCarScreen(carContext, vehicle))
                        }
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("AutoTracker Garage")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}