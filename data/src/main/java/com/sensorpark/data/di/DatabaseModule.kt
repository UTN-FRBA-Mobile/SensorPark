package com.sensorpark.data.di

import android.content.Context
import androidx.room.Room
import com.sensorpark.data.local.GeofenceDao
import com.sensorpark.data.local.SensorParkDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** La base local. Hoy guarda sólo las geocercas activas; las reservas se leen de Firestore. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SensorParkDatabase =
        Room.databaseBuilder(context, SensorParkDatabase::class.java, "sensorpark.db")
            // Es una cache reconstruible: ante un cambio de esquema conviene rehacerla.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun geofenceDao(database: SensorParkDatabase): GeofenceDao = database.geofences()
}
