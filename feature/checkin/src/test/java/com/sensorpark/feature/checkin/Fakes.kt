package com.sensorpark.feature.checkin

import com.sensorpark.domain.model.ActiveGeofence
import com.sensorpark.domain.model.AppNotification
import com.sensorpark.domain.model.CheckOutSummary
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.LocationReading
import com.sensorpark.domain.model.NotificationType
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRequest
import com.sensorpark.domain.model.SpotAvailability
import com.sensorpark.domain.model.WaitlistPosition
import com.sensorpark.domain.notification.ReservationAlertMessages
import com.sensorpark.domain.repository.GeofenceRepository
import com.sensorpark.domain.repository.LocationRepository
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.ReminderScheduler
import com.sensorpark.domain.repository.ReservationRepository
import com.sensorpark.domain.repository.SpotAvailabilityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

// Repositorios falsos para probar el ViewModel sin Firebase ni Android (los que pide T04 para
// esta parte). Cada uno guarda lo que recibió, así el test puede comprobarlo.

class FakeReservationRepository(reservations: List<Reservation> = emptyList()) : ReservationRepository {
    private val state = MutableStateFlow<DataResult<List<Reservation>>>(DataResult.Success(reservations))
    val checkedIn = mutableListOf<String>()
    val checkedOut = mutableListOf<String>()
    var checkOutResult: ReservationOutcome<CheckOutSummary> =
        ReservationOutcome.Success(CheckOutSummary(stayMinutes = 45, amount = 750))

    fun emit(reservations: List<Reservation>) {
        state.value = DataResult.Success(reservations)
    }

    override fun myReservations(): Flow<DataResult<List<Reservation>>> = state

    override suspend fun create(request: ReservationRequest): ReservationOutcome<ReservationConfirmation> =
        throw UnsupportedOperationException()

    override suspend fun cancel(reservationId: String): ReservationOutcome<Unit> =
        throw UnsupportedOperationException()

    override suspend fun checkIn(reservationId: String, location: Coordinates): ReservationOutcome<Unit> {
        checkedIn += reservationId
        return ReservationOutcome.Success(Unit)
    }

    override suspend fun checkOut(reservationId: String): ReservationOutcome<CheckOutSummary> {
        checkedOut += reservationId
        return checkOutResult
    }

    override suspend fun joinWaitlist(request: ReservationRequest): ReservationOutcome<WaitlistPosition> =
        throw UnsupportedOperationException()

    override suspend fun leaveWaitlist(entryId: String): ReservationOutcome<Unit> =
        throw UnsupportedOperationException()
}

class FakeParkingLotRepository(private val lots: List<ParkingLot>) : ParkingLotRepository {
    override fun parkingLots(): Flow<DataResult<List<ParkingLot>>> =
        MutableStateFlow(DataResult.Success(lots))

    override fun parkingLot(id: String): Flow<DataResult<ParkingLot>> = MutableStateFlow(
        lots.firstOrNull { it.id == id }
            ?.let { DataResult.Success(it) }
            ?: DataResult.Failure(com.sensorpark.domain.model.DataErrorReason.NOT_FOUND),
    )
}

class FakeSpotAvailabilityRepository(availability: SpotAvailability) : SpotAvailabilityRepository {
    private val state = MutableStateFlow(availability)

    fun emit(availability: SpotAvailability) {
        state.value = availability
    }

    override fun observeSpots(parkingLotId: String): Flow<DataResult<SpotAvailability>> =
        state.map { DataResult.Success(it) }
}

class FakeLocationRepository(
    var reading: LocationReading? = null,
    private var permission: Boolean = true,
) : LocationRepository {
    override suspend fun currentLocation(): LocationReading? = reading

    override fun hasLocationPermission(): Boolean = permission

    fun denyPermission() {
        permission = false
        reading = null
    }
}

class FakeGeofenceRepository : GeofenceRepository {
    private val geofences = mutableMapOf<String, ActiveGeofence>()
    val removed = mutableListOf<String>()

    override suspend fun register(geofence: ActiveGeofence): Boolean {
        geofences[geofence.reservationId] = geofence
        return true
    }

    override suspend fun remove(reservationId: String) {
        geofences -= reservationId
        removed += reservationId
    }

    override suspend fun active(): List<ActiveGeofence> = geofences.values.toList()

    override suspend fun find(reservationId: String): ActiveGeofence? = geofences[reservationId]

    override suspend fun restoreAll() = Unit

    override fun hasBackgroundLocationPermission(): Boolean = true
}

class FakeReminderScheduler : ReminderScheduler {
    val scheduled = mutableMapOf<String, Instant>()
    val cancelled = mutableListOf<String>()

    override suspend fun scheduleArrivalReminder(reservationId: String, notifyAt: Instant) {
        scheduled[reservationId] = notifyAt
    }

    override suspend fun cancel(reservationId: String) {
        cancelled += reservationId
    }
}

/** Textos de mentira: lo que importa en los tests es qué avisos se muestran, no cómo se leen. */
class FakeAlertMessages : ReservationAlertMessages {
    override fun reservationConfirmed(
        reservationId: String,
        parkingLotName: String,
        spotCode: String,
        arrivesAt: Instant,
    ) = AppNotification(NotificationType.RESERVATION, "confirmed$reservationId", "Reserva", parkingLotName)

    override fun arrivalReminder(
        reservationId: String,
        parkingLotName: String,
        spotCode: String,
        arrivesAt: Instant,
    ) = AppNotification(NotificationType.REMINDER, "reminder$reservationId", "Recordatorio", parkingLotName)

    override fun arrival(geofence: ActiveGeofence) = AppNotification(
        NotificationType.ARRIVAL,
        "arrival${geofence.reservationId}",
        "Llegaste",
        geofence.spotCode,
    )

    override fun stay(reservationId: String, spotCode: String, endsAt: Instant) = AppNotification(
        NotificationType.RESERVATION,
        stayKey(reservationId),
        "Estacionado",
        spotCode,
        ongoing = true,
    )

    override fun stayKey(reservationId: String) = "stay$reservationId"
}
