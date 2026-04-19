package com.tyson.autotracker.data

import androidx.room.*
import com.tyson.autotracker.models.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY createdAt DESC")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicles(vehicles: List<Vehicle>)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteVehicle(id: String)

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getVehicleByIdSync(id: String): Vehicle?

    // --- LOG QUERIES ---
    @Query("SELECT * FROM logs WHERE vehicleId = :vId ORDER BY date DESC")
    fun getLogsForVehicle(vId: String): Flow<List<VehicleLog>>

    @Query("SELECT * FROM logs")
    fun getAllLogs(): Flow<List<VehicleLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: VehicleLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<VehicleLog>)

    // FIXED: Added missing Update and Delete functions for Logs
    @Update
    suspend fun updateLog(log: VehicleLog)

    @Delete
    suspend fun deleteLog(log: VehicleLog)

    // --- TRIP LOG QUERIES ---
    @Query("SELECT * FROM trip_logs WHERE vehicleId = :vId ORDER BY startTime DESC")
    fun getTripsForVehicle(vId: String): Flow<List<TripLog>>

    @Query("SELECT * FROM trip_logs")
    fun getAllTrips(): Flow<List<TripLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<TripLog>)

    @Query("DELETE FROM trip_logs WHERE id = :id")
    suspend fun deleteTrip(id: String)

    @Transaction
    suspend fun clearAllData() {
        deleteAllVehicles()
        deleteAllLogs()
        deleteAllTripLogs()
    }

    @Query("DELETE FROM vehicles")
    suspend fun deleteAllVehicles()

    @Query("DELETE FROM logs")
    suspend fun deleteAllLogs()

    @Query("DELETE FROM trip_logs")
    suspend fun deleteAllTripLogs()
}