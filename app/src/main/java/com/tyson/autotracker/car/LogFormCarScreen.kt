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
import java.util.*

class LogFormCarScreen(
    carContext: CarContext,
    private val vehicle: Vehicle,
    private var logType: LogType
) : Screen(carContext) {

    private var cost = ""
    private var odometer = vehicle.currentKm.toString()
    private var description = "Logged via Android Auto"
    private var dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    
    // Additional fields
    private var fuelLiters = ""
    private var nextServiceKm = ""
    private var nextServiceDate = ""

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // 1. Log Type (Display only)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Category")
                .addText(logType.name.lowercase().replaceFirstChar { it.uppercase() })
                .build()
        )

        // 2. Date with Quick Selection
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Date")
                .addText(dateStr)
                .setOnClickListener {
                    showDateOptions()
                }
                .build()
        )

        // 3. Cost (Numeric Keypad)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Cost (₹)")
                .addText(if (cost.isEmpty()) "Tap to enter" else "₹ $cost")
                .setOnClickListener {
                    screenManager.push(NumericInputCarScreen(carContext, "Enter Cost", cost, true) {
                        cost = it
                        invalidate()
                    })
                }
                .build()
        )

        // 4. Odometer (Numeric Keypad)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Odometer Reading (KM)")
                .addText("$odometer km")
                .setOnClickListener {
                    screenManager.push(NumericInputCarScreen(carContext, "Enter Odometer", odometer, false) {
                        odometer = it
                        invalidate()
                    })
                }
                .build()
        )

        // 5. Fuel Liters (Conditional + Numeric Keypad)
        if (logType == LogType.REFUELING) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Fuel Filled (Liters)")
                    .addText(if (fuelLiters.isEmpty()) "Tap to enter" else "$fuelLiters L")
                    .setOnClickListener {
                        screenManager.push(NumericInputCarScreen(carContext, "Enter Liters", fuelLiters, true) {
                            fuelLiters = it
                            invalidate()
                        })
                    }
                    .build()
            )
        }

        // 6. Description (Keyboard)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Description")
                .addText(description)
                .setOnClickListener {
                    screenManager.push(TextInputCarScreen(carContext, "Add Note", description) {
                        description = it
                        invalidate()
                    })
                }
                .build()
        )

        // 7. Next Service (Conditional + Numeric Keypad)
        if (logType == LogType.SERVICE || logType == LogType.OIL_CHANGE) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Next Service KM")
                    .addText(if (nextServiceKm.isEmpty()) "Optional" else "$nextServiceKm km")
                    .setOnClickListener {
                        screenManager.push(NumericInputCarScreen(carContext, "Next Service KM", nextServiceKm, false) {
                            nextServiceKm = it
                            invalidate()
                        })
                    }
                    .build()
            )
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("SAVE LOG")
                    .setOnClickListener {
                        saveLog()
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Vehicle Log")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .build()
    }

    private fun showDateOptions() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

        val dateListBuilder = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Today").addText(today).setOnClickListener { dateStr = today; screenManager.pop(); invalidate() }.build())
            .addItem(Row.Builder().setTitle("Yesterday").addText(yesterday).setOnClickListener { dateStr = yesterday; screenManager.pop(); invalidate() }.build())
            .addItem(Row.Builder().setTitle("Custom...").addText("Type YYYY-MM-DD").setOnClickListener { 
                screenManager.push(TextInputCarScreen(carContext, "Enter Date", dateStr) {
                    dateStr = it
                    invalidate()
                })
            }.build())

        val dateScreen = object : Screen(carContext) {
            override fun onGetTemplate(): Template {
                return ListTemplate.Builder()
                    .setSingleList(dateListBuilder.build())
                    .setTitle("Select Date")
                    .setHeaderAction(Action.BACK)
                    .build()
            }
        }
        screenManager.push(dateScreen)
    }

    private fun saveLog() {
        if (cost.isEmpty() || odometer.isEmpty()) {
            CarToast.makeText(carContext, "Cost and Odometer are required", CarToast.LENGTH_LONG).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val dao = AutotrackerDatabase.getDatabase(carContext).vehicleDao()
            val newLog = VehicleLog(
                id = UUID.randomUUID().toString(),
                vehicleId = vehicle.id,
                type = logType,
                date = dateStr,
                cost = cost.toDoubleOrNull() ?: 0.0,
                kmReading = odometer.toIntOrNull() ?: vehicle.currentKm,
                description = description,
                fuelLiters = if (logType == LogType.REFUELING) fuelLiters.toDoubleOrNull() else null,
                nextServiceKm = nextServiceKm.toIntOrNull(),
                nextServiceDate = nextServiceDate.ifBlank { null }
            )
            dao.insertLog(newLog)

            launch(Dispatchers.Main) {
                CarToast.makeText(carContext, "Log Saved!", CarToast.LENGTH_SHORT).show()
                screenManager.pop()
            }
        }
    }
}
