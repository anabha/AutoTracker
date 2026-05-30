package com.tyson.autotracker.data

import androidx.room.*
import com.tyson.autotracker.models.PlaceVisitHistory
import com.tyson.autotracker.models.VisitedPlace
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedPlaceDao {
    @Query("SELECT * FROM visited_places ORDER BY lastVisitDate DESC")
    fun getAllVisitedPlaces(): Flow<List<VisitedPlace>>

    @Query("SELECT * FROM visited_places")
    suspend fun getAllVisitedPlacesSync(): List<VisitedPlace>

    @Query("SELECT * FROM place_visit_history WHERE placeId = :placeId ORDER BY visitTimestamp DESC")
    fun getHistoryForPlace(placeId: String): Flow<List<PlaceVisitHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: VisitedPlace)

    @Update
    suspend fun updatePlace(place: VisitedPlace)

    @Query("DELETE FROM visited_places WHERE placeId = :placeId")
    suspend fun deletePlace(placeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaceVisitHistory)

    @Query("DELETE FROM visited_places")
    suspend fun deleteAllVisitedPlaces()
}
