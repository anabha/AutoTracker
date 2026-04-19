package com.tyson.autotracker.services

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tyson.autotracker.data.AutotrackerDatabase
import com.tyson.autotracker.models.LogType
import com.tyson.autotracker.models.VehicleLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val database = AutotrackerDatabase.getDatabase(context)
            val dao = database.vehicleDao()
            
            CoroutineScope(Dispatchers.IO).launch {
                val vehicles = dao.getAllVehicles().first()
                val logs = dao.getAllLogs().first()
                
                logs.forEach { log ->
                    if (!log.nextServiceDate.isNullOrBlank()) {
                        val vehicleName = vehicles.find { it.id == log.vehicleId }?.name ?: "Vehicle"
                        scheduleReminders(context, log, vehicleName)
                    }
                }
            }
        }
    }

    private fun scheduleReminders(context: Context, log: VehicleLog, vehicleName: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        try {
            val expiry = sdf.parse(log.nextServiceDate!!) ?: return
            val calendar = Calendar.getInstance()
            calendar.time = expiry
            calendar.set(Calendar.HOUR_OF_DAY, 9)
            calendar.set(Calendar.MINUTE, 0)

            val typeStr = if (log.type == LogType.OIL_CHANGE) "Oil Change" else "Service"

            listOf(0, -1, -2).forEach { offset ->
                val scheduleTime = calendar.clone() as Calendar
                scheduleTime.add(Calendar.DAY_OF_YEAR, offset)

                if (scheduleTime.timeInMillis > System.currentTimeMillis()) {
                    val intent = Intent(context, ReminderReceiver::class.java).apply {
                        putExtra("TITLE", "$vehicleName $typeStr Reminder")
                        val msg = if (offset == 0) "Your $typeStr is due today!" else "$typeStr due in ${-offset} days"
                        putExtra("MESSAGE", msg)
                        putExtra("VEHICLE_ID", log.vehicleId)
                        putExtra("NOTIFICATION_ID", (log.id.hashCode() + offset))
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context, log.id.hashCode() + offset, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        scheduleTime.timeInMillis,
                        pendingIntent
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
