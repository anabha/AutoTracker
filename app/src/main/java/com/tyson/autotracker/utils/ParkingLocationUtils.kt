package com.tyson.autotracker.utils

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

data class StoredLocation(
    val latitude: Double,
    val longitude: Double
)

data class ParkingPlaces(
    val home: StoredLocation?,
    val work: StoredLocation?
)

class ParkingLocationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPlaces(): ParkingPlaces = ParkingPlaces(
        home = getLocation(KEY_HOME_LAT, KEY_HOME_LNG),
        work = getLocation(KEY_WORK_LAT, KEY_WORK_LNG)
    )

    fun getHomeLocation(): StoredLocation? = getLocation(KEY_HOME_LAT, KEY_HOME_LNG)

    fun getWorkLocation(): StoredLocation? = getLocation(KEY_WORK_LAT, KEY_WORK_LNG)

    fun saveHomeLocation(location: StoredLocation) {
        saveLocation(KEY_HOME_LAT, KEY_HOME_LNG, location)
    }

    fun saveWorkLocation(location: StoredLocation) {
        saveLocation(KEY_WORK_LAT, KEY_WORK_LNG, location)
    }

    fun clearHomeLocation() {
        clearLocation(KEY_HOME_LAT, KEY_HOME_LNG)
    }

    fun clearWorkLocation() {
        clearLocation(KEY_WORK_LAT, KEY_WORK_LNG)
    }

    private fun getLocation(latKey: String, lngKey: String): StoredLocation? {
        if (!prefs.contains(latKey) || !prefs.contains(lngKey)) return null
        val latitude = Double.fromBits(prefs.getLong(latKey, 0L))
        val longitude = Double.fromBits(prefs.getLong(lngKey, 0L))
        return StoredLocation(latitude, longitude)
    }

    private fun saveLocation(latKey: String, lngKey: String, location: StoredLocation) {
        prefs.edit()
            .putLong(latKey, location.latitude.toBits())
            .putLong(lngKey, location.longitude.toBits())
            .apply()
    }

    private fun clearLocation(latKey: String, lngKey: String) {
        prefs.edit()
            .remove(latKey)
            .remove(lngKey)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "parking_location_prefs"
        const val KEY_HOME_LAT = "home_latitude"
        const val KEY_HOME_LNG = "home_longitude"
        const val KEY_WORK_LAT = "work_latitude"
        const val KEY_WORK_LNG = "work_longitude"
    }
}

object ParkingLocationUtils {
    const val PARKING_EXCLUSION_RADIUS_METERS = 100f

    fun distanceMeters(from: StoredLocation, to: StoredLocation): Float {
        val start = Location("stored_start").apply {
            latitude = from.latitude
            longitude = from.longitude
        }
        val end = Location("stored_end").apply {
            latitude = to.latitude
            longitude = to.longitude
        }
        return start.distanceTo(end)
    }

    fun shouldSaveParkedLocation(
        currentLocation: StoredLocation,
        homeLocation: StoredLocation?,
        workLocation: StoredLocation?,
        exclusionRadiusMeters: Float = PARKING_EXCLUSION_RADIUS_METERS
    ): Boolean {
        val nearHome = homeLocation?.let { distanceMeters(currentLocation, it) <= exclusionRadiusMeters } == true
        val nearWork = workLocation?.let { distanceMeters(currentLocation, it) <= exclusionRadiusMeters } == true
        return !nearHome && !nearWork
    }

    suspend fun getCurrentExactLocation(context: Context): Location? {
        return withContext(Dispatchers.IO) {
            try {
                val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                    ?: client.lastLocation.await()
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun reverseGeocode(context: Context, location: StoredLocation): String {
        val fallback = formatLatLng(location)
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val address = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
                address?.takeIf { it.isNotBlank() } ?: fallback
            } catch (_: Exception) {
                fallback
            }
        }
    }

    fun launchNavigation(context: Context, location: StoredLocation) {
        val navigationUri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}")
        val mapsIntent = Intent(Intent.ACTION_VIEW, navigationUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        try {
            context.startActivity(mapsIntent)
        } catch (_: Exception) {
            val fallbackUri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}")
            context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
        }
    }

    fun formatLatLng(location: StoredLocation): String {
        return "%.6f, %.6f".format(Locale.getDefault(), location.latitude, location.longitude)
    }
}
