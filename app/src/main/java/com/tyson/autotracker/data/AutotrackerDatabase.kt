package com.tyson.autotracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tyson.autotracker.models.TripLog
import com.tyson.autotracker.models.Vehicle
import com.tyson.autotracker.models.VehicleLog

// Updated Version to 10 due to adding AA Handover to Vehicle and fuelLiters to VehicleLog
@Database(entities = [Vehicle::class, VehicleLog::class, TripLog::class], version = 10, exportSchema = false)
abstract class AutotrackerDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao

    companion object {
        @Volatile
        private var INSTANCE: AutotrackerDatabase? = null

        fun getDatabase(context: Context): AutotrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutotrackerDatabase::class.java,
                    "autotracker_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}