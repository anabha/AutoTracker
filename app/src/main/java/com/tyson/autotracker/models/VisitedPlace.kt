package com.tyson.autotracker.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "visited_places")
data class VisitedPlace(
    @PrimaryKey val placeId: String = UUID.randomUUID().toString(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeName: String = "",
    val visitCount: Int = 1,
    val lastVisitDate: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "place_visit_history",
    foreignKeys = [ForeignKey(
        entity = VisitedPlace::class,
        parentColumns = ["placeId"],
        childColumns = ["placeId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class PlaceVisitHistory(
    @PrimaryKey val historyId: String = UUID.randomUUID().toString(),
    val placeId: String = "",
    val visitTimestamp: Long = System.currentTimeMillis()
)
