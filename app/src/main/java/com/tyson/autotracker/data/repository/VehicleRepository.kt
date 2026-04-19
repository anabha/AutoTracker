package com.tyson.autotracker.data.repository

import com.tyson.autotracker.data.VehicleDao
import com.tyson.autotracker.models.TripLog
import com.tyson.autotracker.models.Vehicle
import com.tyson.autotracker.models.VehicleLog
import kotlinx.coroutines.flow.Flow

class VehicleRepository(private val dao: VehicleDao) {
    fun getAllVehicles(): Flow<List<Vehicle>> = dao.getAllVehicles()
    fun getAllLogs(): Flow<List<VehicleLog>> = dao.getAllLogs()
    
    suspend fun insertVehicle(vehicle: Vehicle) = dao.insertVehicle(vehicle)
    suspend fun updateVehicle(vehicle: Vehicle) = dao.updateVehicle(vehicle)
    suspend fun deleteVehicle(id: String) = dao.deleteVehicle(id)
    
    fun getLogsForVehicle(vehicleId: String): Flow<List<VehicleLog>> = dao.getLogsForVehicle(vehicleId)
    suspend fun insertLog(log: VehicleLog) = dao.insertLog(log)
    suspend fun updateLog(log: VehicleLog) = dao.updateLog(log)
    suspend fun deleteLog(log: VehicleLog) = dao.deleteLog(log)
    
    fun getTripsForVehicle(vehicleId: String): Flow<List<TripLog>> = dao.getTripsForVehicle(vehicleId)
    suspend fun insertTrip(trip: TripLog) = dao.insertTrip(trip)
    suspend fun deleteTrip(id: String) = dao.deleteTrip(id)
}
