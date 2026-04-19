package com.tyson.autotracker.ui.viewmodels

import com.google.gson.Gson
import com.tyson.autotracker.models.LogType
import com.tyson.autotracker.models.TripLog
import com.tyson.autotracker.models.Vehicle
import com.tyson.autotracker.models.VehicleLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.system.measureTimeMillis

class BackupStressTest {

    @Test
    fun testLargeDatasetSerializationPerformance() {
        val vehicleCount = 10
        val logsPerVehicle = 500
        val tripsPerVehicle = 1000

        val vehicles = List(vehicleCount) { 
            Vehicle(id = UUID.randomUUID().toString(), name = "Vehicle $it") 
        }
        val logs = vehicles.flatMap { vehicle ->
            List(logsPerVehicle) { 
                VehicleLog(id = UUID.randomUUID().toString(), vehicleId = vehicle.id, cost = 50.0) 
            }
        }
        val trips = vehicles.flatMap { vehicle ->
            List(tripsPerVehicle) { 
                TripLog(id = UUID.randomUUID().toString(), vehicleId = vehicle.id, distanceMeters = 15000f) 
            }
        }

        val backup = VehicleViewModel.BackupPayload(vehicles, logs, trips)
        val gson = Gson()

        val serializationTime = measureTimeMillis {
            val json = gson.toJson(backup)
            assertTrue(json.isNotEmpty())
        }

        println("Serialization of ${vehicles.size} vehicles, ${logs.size} logs, and ${trips.size} trips took $serializationTime ms")
        
        // Assert it takes less than 2 seconds for a reasonably large dataset
        assertTrue("Serialization too slow: $serializationTime ms", serializationTime < 2000)
    }

    @Test
    fun testDataIntegrityAfterRoundTrip() {
        val vehicle = Vehicle(id = "v1", name = "Test Car")
        val log = VehicleLog(id = "l1", vehicleId = "v1", type = LogType.SERVICE, cost = 100.0)
        val trip = TripLog(id = "t1", vehicleId = "v1", distanceMeters = 5000f)

        val originalBackup = VehicleViewModel.BackupPayload(listOf(vehicle), listOf(log), listOf(trip))
        val gson = Gson()
        
        val json = gson.toJson(originalBackup)
        val restoredBackup = gson.fromJson(json, VehicleViewModel.BackupPayload::class.java)

        assertEquals(originalBackup.vehicles.size, restoredBackup.vehicles.size)
        assertEquals(originalBackup.logs.size, restoredBackup.logs.size)
        assertEquals(originalBackup.trips.size, restoredBackup.trips.size)
        
        assertEquals(originalBackup.vehicles[0].name, restoredBackup.vehicles[0].name)
        assertEquals(originalBackup.logs[0].cost, restoredBackup.logs[0].cost, 0.001)
        assertEquals(originalBackup.trips[0].distanceMeters, restoredBackup.trips[0].distanceMeters, 0.001f)
    }
}
