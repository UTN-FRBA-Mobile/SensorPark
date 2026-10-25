package com.sensorpark.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.sensorpark.domain.model.Stay
import java.time.Instant
import kotlinx.coroutines.flow.Flow

// Lo que la app guarda a mano para verlo sin conexión (T19): favoritos e historial. El resto lo
// resuelve la caché de Firestore; esto es el patrón de contenido combinado de la materia.

/**
 * Un favorito. [pendingSync] marca un cambio hecho en el teléfono que todavía no subió al backend;
 * un favorito quitado sin red queda con [removed] hasta que el backend se entera, y recién ahí se
 * borra la fila.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val parkingLotId: String,
    val addedAtEpochMillis: Long,
    val pendingSync: Boolean,
    val removed: Boolean = false,
)

@Dao
interface FavoriteDao {
    @Query("SELECT parkingLotId FROM favorites WHERE removed = 0")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT * FROM favorites WHERE pendingSync = 1")
    suspend fun pending(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE parkingLotId = :parkingLotId")
    suspend fun find(parkingLotId: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE parkingLotId = :parkingLotId")
    suspend fun delete(parkingLotId: String)

    /** Los que vienen del backend y no tienen cambios pendientes acá. */
    @Query("DELETE FROM favorites WHERE pendingSync = 0")
    suspend fun deleteSynced()
}

/** Una estadía del historial. */
@Entity(tableName = "stays")
data class StayEntity(
    @PrimaryKey val reservationId: String,
    val parkingLotId: String,
    val parkingLotName: String,
    val spotCode: String,
    val arrivesAtEpochMillis: Long,
    val checkedOutAtEpochMillis: Long?,
    val stayMinutes: Int,
    val amount: Int,
) {
    fun toDomain() = Stay(
        reservationId = reservationId,
        parkingLotId = parkingLotId,
        parkingLotName = parkingLotName,
        spotCode = spotCode,
        arrivesAt = Instant.ofEpochMilli(arrivesAtEpochMillis),
        checkedOutAt = checkedOutAtEpochMillis?.let(Instant::ofEpochMilli),
        stayMinutes = stayMinutes,
        amount = amount,
    )

    companion object {
        fun from(stay: Stay) = StayEntity(
            reservationId = stay.reservationId,
            parkingLotId = stay.parkingLotId,
            parkingLotName = stay.parkingLotName,
            spotCode = stay.spotCode,
            arrivesAtEpochMillis = stay.arrivesAt.toEpochMilli(),
            checkedOutAtEpochMillis = stay.checkedOutAt?.toEpochMilli(),
            stayMinutes = stay.stayMinutes,
            amount = stay.amount,
        )
    }
}

@Dao
interface StayDao {
    @Query("SELECT * FROM stays ORDER BY arrivesAtEpochMillis DESC")
    fun observe(): Flow<List<StayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(stays: List<StayEntity>)
}
