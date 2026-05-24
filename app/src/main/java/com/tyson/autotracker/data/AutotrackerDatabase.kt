package com.tyson.autotracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tyson.autotracker.models.TripLog
import com.tyson.autotracker.models.Vehicle
import com.tyson.autotracker.models.VehicleLog

// Updated Version to 12 due to changing currentKm column type from INTEGER to REAL for decimal odometer accuracy.
@Database(entities = [Vehicle::class, VehicleLog::class, TripLog::class], version = 12, exportSchema = false)
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
                    .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN lastParkedLatitude REAL")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN lastParkedLongitude REAL")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN lastParkedAt INTEGER")
            }
        }

        // Migrates currentKm from INTEGER to REAL so we can store fractional kilometers.
        // SQLite cannot ALTER a column type in-place, so we rebuild the table.
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE vehicles_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        make TEXT NOT NULL,
                        model TEXT NOT NULL,
                        year INTEGER NOT NULL,
                        currentKm REAL NOT NULL DEFAULT 0.0,
                        createdAt TEXT NOT NULL,
                        registrationNo TEXT,
                        engineNo TEXT,
                        puccDate TEXT,
                        insuranceDate TEXT,
                        bluetoothMacAddress TEXT,
                        wifiSsid TEXT,
                        imageUri TEXT,
                        useAndroidAutoHandover INTEGER NOT NULL DEFAULT 0,
                        lastParkedLatitude REAL,
                        lastParkedLongitude REAL,
                        lastParkedAt INTEGER
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO vehicles_new (
                        id, name, type, make, model, year, currentKm, createdAt,
                        registrationNo, engineNo, puccDate, insuranceDate,
                        bluetoothMacAddress, wifiSsid, imageUri, useAndroidAutoHandover,
                        lastParkedLatitude, lastParkedLongitude, lastParkedAt
                    )
                    SELECT
                        id, name, type, make, model, year, CAST(currentKm AS REAL), createdAt,
                        registrationNo, engineNo, puccDate, insuranceDate,
                        bluetoothMacAddress, wifiSsid, imageUri, useAndroidAutoHandover,
                        lastParkedLatitude, lastParkedLongitude, lastParkedAt
                    FROM vehicles
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE vehicles")
                db.execSQL("ALTER TABLE vehicles_new RENAME TO vehicles")
            }
        }
    }
}
