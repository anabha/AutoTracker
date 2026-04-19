package com.tyson.autotracker.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import com.tyson.autotracker.data.AutotrackerDatabase
import com.tyson.autotracker.models.LogType
import com.tyson.autotracker.models.Vehicle
import com.tyson.autotracker.models.VehicleLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AddLogCarScreen(carContext: CarContext, val vehicle: Vehicle) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        val logTypes = listOf(
            Pair("Refueling", LogType.REFUELING),
            Pair("Basic Service", LogType.SERVICE),
            Pair("Oil Change", LogType.OIL_CHANGE)
        )

        logTypes.forEach { (title, type) ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Log $title")
                    .addText("Current KM: ${vehicle.currentKm}")
                    .setOnClickListener {
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = AutotrackerDatabase.getDatabase(carContext).vehicleDao()

                            // Format today's date properly as a String
                            val currentDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                            // Creates a basic log instantly
                            val newLog = VehicleLog(
                                id = UUID.randomUUID().toString(),
                                vehicleId = vehicle.id,
                                type = type,
                                date = currentDateStr, // FIXED: Now passes a String instead of a Long
                                cost = 0.0, // Edit later on phone
                                kmReading = vehicle.currentKm,
                                description = "Quick Logged via Android Auto"
                            )

                            dao.insertLog(newLog)

                            // Show success message and go back
                            CarToast.makeText(carContext, "$title Logged Successfully!", CarToast.LENGTH_SHORT).show()
                            screenManager.pop()
                        }
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Quick Add Log")
            .setHeaderAction(Action.BACK)
            .build()
    }
}