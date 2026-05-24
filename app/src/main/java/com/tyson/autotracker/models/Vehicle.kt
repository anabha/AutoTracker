package com.tyson.autotracker.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class VehicleType { CAR, BIKE }
enum class LogType { SERVICE, MODIFICATION, OIL_CHANGE, REFUELING }

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: VehicleType = VehicleType.CAR,
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val currentKm: Double = 0.0,
    val createdAt: String = System.currentTimeMillis().toString(),
    val registrationNo: String? = null,
    val engineNo: String? = null,
    val puccDate: String? = null,
    val insuranceDate: String? = null,
    val bluetoothMacAddress: String? = null,
    val wifiSsid: String? = null,
    val imageUri: String? = null,
    val useAndroidAutoHandover: Boolean = false,
    val lastParkedLatitude: Double? = null,
    val lastParkedLongitude: Double? = null,
    val lastParkedAt: Long? = null
)

@Entity(tableName = "logs")
data class VehicleLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vehicleId: String = "",
    val type: LogType = LogType.SERVICE,
    val date: String = "",
    val cost: Double = 0.0,
    val description: String = "",
    val kmReading: Int = 0,
    val nextServiceKm: Int? = null,
    val nextServiceDate: String? = null,
    val fuelLiters: Double? = null // NEW: Tracks liters filled during refueling
)

@Entity(tableName = "trip_logs")
data class TripLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vehicleId: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val distanceMeters: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val routePoints: String = ""
)
