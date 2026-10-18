package com.sensorpark.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.sensorpark.domain.model.ActiveGeofence
import com.sensorpark.domain.model.Coordinates
import java.time.Instant

/**
 * Una geocerca activa, guardada en el teléfono. Un reinicio borra las geocercas del sistema, así
 * que esta tabla es la que permite volver a registrarlas sin red y sin sesión.
 */
@Entity(tableName = "geofences")
data class GeofenceEntity(
    @PrimaryKey val reservationId: String,
    val parkingLotName: String,
    val spotCode: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val expiresAtEpochMillis: Long,
) {
    fun toDomain() = ActiveGeofence(
        reservationId = reservationId,
        parkingLotName = parkingLotName,
        spotCode = spotCode,
        center = Coordinates(latitude, longitude),
        radiusMeters = radiusMeters,
        expiresAt = Instant.ofEpochMilli(expiresAtEpochMillis),
    )

    companion object {
        fun from(geofence: ActiveGeofence) = GeofenceEntity(
            reservationId = geofence.reservationId,
            parkingLotName = geofence.parkingLotName,
            spotCode = geofence.spotCode,
            latitude = geofence.center.latitude,
            longitude = geofence.center.longitude,
            radiusMeters = geofence.radiusMeters,
            expiresAtEpochMillis = geofence.expiresAt.toEpochMilli(),
        )
    }
}

@Dao
interface GeofenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(geofence: GeofenceEntity)

    @Query("SELECT * FROM geofences")
    suspend fun all(): List<GeofenceEntity>

    @Query("SELECT * FROM geofences WHERE reservationId = :reservationId")
    suspend fun find(reservationId: String): GeofenceEntity?

    @Query("DELETE FROM geofences WHERE reservationId = :reservationId")
    suspend fun delete(reservationId: String)
}

@Database(entities = [GeofenceEntity::class], version = 1, exportSchema = false)
abstract class SensorParkDatabase : RoomDatabase() {
    abstract fun geofences(): GeofenceDao
}
