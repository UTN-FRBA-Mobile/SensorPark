package com.sensorpark.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sensorpark.data.local.FavoriteDao
import com.sensorpark.data.local.GeofenceDao
import com.sensorpark.data.local.SensorParkDatabase
import com.sensorpark.data.local.StayDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** La base local: las geocercas activas (T20) y los favoritos y el historial sin conexión (T19). */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /** Versión 3: la geocerca guarda la hora de llegada. Sin migración se borrarían también favoritos y estadías. */
    private val ADD_GEOFENCE_ARRIVAL = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE geofences ADD COLUMN arrivesAtEpochMillis INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SensorParkDatabase =
        Room.databaseBuilder(context, SensorParkDatabase::class.java, "sensorpark.db")
            .addMigrations(ADD_GEOFENCE_ARRIVAL)
            // Es una cache reconstruible: ante un cambio de esquema sin migración conviene rehacerla.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun geofenceDao(database: SensorParkDatabase): GeofenceDao = database.geofences()

    @Provides
    fun favoriteDao(database: SensorParkDatabase): FavoriteDao = database.favorites()

    @Provides
    fun stayDao(database: SensorParkDatabase): StayDao = database.stays()
}
