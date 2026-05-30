package com.tyson.autotracker.data.repository

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import com.tyson.autotracker.data.VisitedPlaceDao
import com.tyson.autotracker.models.PlaceVisitHistory
import com.tyson.autotracker.models.VisitedPlace
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class VisitedPlaceRepository(private val dao: VisitedPlaceDao) {

    fun getAllVisitedPlaces(): Flow<List<VisitedPlace>> = dao.getAllVisitedPlaces()

    fun getHistoryForPlace(placeId: String): Flow<List<PlaceVisitHistory>> = dao.getHistoryForPlace(placeId)

    suspend fun deletePlace(placeId: String) {
        dao.deletePlace(placeId)
    }

    suspend fun recordVisit(lat: Double, lng: Double, context: Context) {
        try {
            val allPlaces = dao.getAllVisitedPlacesSync()
            val results = FloatArray(1)
            var matchedPlace: VisitedPlace? = null

            for (place in allPlaces) {
                Location.distanceBetween(lat, lng, place.latitude, place.longitude, results)
                if (results[0] <= 100f) {
                    matchedPlace = place
                    break
                }
            }

            val now = System.currentTimeMillis()

            if (matchedPlace != null) {
                // Existing place - increment visit count
                val updatedPlace = matchedPlace.copy(
                    visitCount = matchedPlace.visitCount + 1,
                    lastVisitDate = now
                )
                dao.updatePlace(updatedPlace)
                dao.insertHistory(PlaceVisitHistory(placeId = updatedPlace.placeId, visitTimestamp = now))
                Log.d("VisitedPlaceRepository", "Recorded visit to existing place: ${updatedPlace.placeName}")
            } else {
                // New place - geocode the address
                val placeName = try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    if (!addresses.isNullOrEmpty()) {
                        addresses[0].getAddressLine(0) ?: "Unknown Place"
                    } else {
                        "Unknown Place"
                    }
                } catch (e: Exception) {
                    Log.e("VisitedPlaceRepository", "Geocoding failed", e)
                    "Unknown Place"
                }

                val newPlace = VisitedPlace(latitude = lat, longitude = lng, placeName = placeName, lastVisitDate = now)
                dao.insertPlace(newPlace)
                dao.insertHistory(PlaceVisitHistory(placeId = newPlace.placeId, visitTimestamp = now))
                Log.d("VisitedPlaceRepository", "Recorded visit to new place: ${newPlace.placeName}")
            }
        } catch (e: Exception) {
            Log.e("VisitedPlaceRepository", "Error recording visit", e)
        }
    }
}
