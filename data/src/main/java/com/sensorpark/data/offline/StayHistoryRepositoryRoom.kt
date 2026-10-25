package com.sensorpark.data.offline

import android.util.Log
import com.sensorpark.data.local.StayDao
import com.sensorpark.data.local.StayEntity
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.Stay
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.ReservationRepository
import com.sensorpark.domain.repository.StayHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * El historial de estadías, siempre leído de Room (T19). Mientras alguien lo mira, copia a Room
 * las reservas terminadas que llegan de Firestore; sin red, se sigue viendo lo último copiado.
 */
@Singleton
class StayHistoryRepositoryRoom @Inject constructor(
    private val dao: StayDao,
    private val reservations: ReservationRepository,
    private val parkingLots: ParkingLotRepository,
) : StayHistoryRepository {

    override fun stays(): Flow<List<Stay>> = channelFlow {
        launch {
            combine(reservations.myReservations(), parkingLots.parkingLots()) { mine, lots ->
                val names = (lots as? DataResult.Success)?.value.orEmpty().associate { it.id to it.name }
                (mine as? DataResult.Success)?.value.orEmpty().mapNotNull { it.toStay(names) }
            }
                .catch { Log.w(TAG, "No se pudo poner al día el historial", it) }
                .collect { if (it.isNotEmpty()) dao.saveAll(it.map(StayEntity::from)) }
        }
        dao.observe().map { rows -> rows.map { it.toDomain() } }.collect { send(it) }
    }

    private companion object {
        const val TAG = "SensorParkHistory"
    }
}

/**
 * Sólo las terminadas son estadías: tienen check-out, duración real e importe. Sin el nombre del
 * estacionamiento no se guarda: si no, pisaría el que ya estaba con uno vacío.
 */
internal fun Reservation.toStay(names: Map<String, String>): Stay? {
    if (status != ReservationStatus.FINISHED) return null
    return Stay(
        reservationId = id,
        parkingLotId = parkingLotId,
        parkingLotName = names[parkingLotId] ?: return null,
        spotCode = spotCode,
        arrivesAt = arrivesAt,
        checkedOutAt = checkedOutAt,
        stayMinutes = stayMinutes ?: 0,
        amount = amount ?: 0,
    )
}
