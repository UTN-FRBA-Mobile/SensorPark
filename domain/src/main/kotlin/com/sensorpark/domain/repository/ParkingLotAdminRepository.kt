package com.sensorpark.domain.repository

import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLevelDraft
import com.sensorpark.domain.model.ParkingLotDraft
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.SpotDraft
import com.sensorpark.domain.model.SpotPlanPosition
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Lo que hace el administrador de estacionamiento desde su panel (T22). Los datos y los lugares se
 * escriben directo en la base, donde los validan las reglas; sacar de servicio o borrar un lugar
 * pasa por el backend, que verifica que no tenga reservas activas.
 */
interface ParkingLotAdminRepository {
    /** Crea el estacionamiento con el usuario como su único administrador. Devuelve su id. */
    suspend fun create(draft: ParkingLotDraft): DataResult<String>

    suspend fun update(parkingLotId: String, draft: ParkingLotDraft): DataResult<Unit>

    /** Agrega el lugar o cambia su tipo; el código es su id. Uno nuevo arranca libre. */
    suspend fun saveSpot(parkingLotId: String, spot: SpotDraft): DataResult<Unit>

    suspend fun setSpotInService(parkingLotId: String, spotId: String, inService: Boolean): ReservationOutcome<Unit>

    suspend fun deleteSpot(parkingLotId: String, spotId: String): ReservationOutcome<Unit>

    /** Crea o actualiza un piso del plano del estacionamiento. Devuelve su id. */
    suspend fun saveLevel(parkingLotId: String, draft: ParkingLevelDraft): DataResult<String> =
        DataResult.Success(draft.id ?: "level-1")

    /** Borra un piso del plano y deja sus lugares como pendientes de ubicar. */
    suspend fun deleteLevel(parkingLotId: String, levelId: String): DataResult<Unit> =
        DataResult.Success(Unit)

    /**
     * Guarda en lote la posición en el plano de los lugares del estacionamiento. Un valor `null`
     * quita al lugar del plano (vuelve a quedar pendiente de ubicar).
     */
    suspend fun saveSpotPositions(
        parkingLotId: String,
        positions: Map<String, SpotPlanPosition?>,
    ): DataResult<Unit> = DataResult.Success(Unit)

    /** Las reservas del estacionamiento que llegan entre [from] y [until], en vivo. */
    fun reservationsBetween(parkingLotId: String, from: Instant, until: Instant): Flow<DataResult<List<Reservation>>>
}
