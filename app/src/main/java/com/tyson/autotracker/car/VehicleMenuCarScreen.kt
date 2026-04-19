package com.tyson.autotracker.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.tyson.autotracker.models.LogType
import com.tyson.autotracker.models.Vehicle

class VehicleMenuCarScreen(carContext: CarContext, val vehicle: Vehicle) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Flight Deck (Dashboard)")
                .addText("View live speed, GPS, and trip distance")
                .setOnClickListener {
                    screenManager.push(DrivingDashboardCarScreen(carContext, vehicle.id))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Quick Add Log")
                .addText("Add fuel or service records")
                .setOnClickListener {
                    // Navigate to Type selection first, then form
                    val types = listOf(LogType.REFUELING, LogType.SERVICE, LogType.OIL_CHANGE, LogType.MODIFICATION)
                    val typeListBuilder = ItemList.Builder()
                    types.forEach { type ->
                        typeListBuilder.addItem(
                            Row.Builder()
                                .setTitle(type.name.lowercase().replaceFirstChar { it.uppercase() })
                                .setOnClickListener {
                                    screenManager.push(LogFormCarScreen(carContext, vehicle, type))
                                }
                                .build()
                        )
                    }
                    
                    val typeSelectionScreen = object : Screen(carContext) {
                        override fun onGetTemplate(): Template {
                            return ListTemplate.Builder()
                                .setSingleList(typeListBuilder.build())
                                .setTitle("Select Log Type")
                                .setHeaderAction(Action.BACK)
                                .build()
                        }
                    }
                    screenManager.push(typeSelectionScreen)
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle(vehicle.name.ifBlank { "${vehicle.make} ${vehicle.model}" })
            .setHeaderAction(Action.BACK)
            .build()
    }
}