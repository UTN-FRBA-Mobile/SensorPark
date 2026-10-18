package com.sensorpark.core.testing

import com.sensorpark.domain.model.ActiveGeofence
import com.sensorpark.domain.model.AppNotification
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.CheckOutSummary
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.LocationReading
import com.sensorpark.domain.model.NotificationPreferences
import com.sensorpark.domain.model.NotificationType
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRequest
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.SpotAvailability
import com.sensorpark.domain.model.SpotReassignment
import com.sensorpark.domain.model.WaitlistPosition
import com.sensorpark.domain.repository.GeofenceRepository
import com.sensorpark.domain.repository.LocationRepository
import com.sensorpark.domain.repository.NotificationPreferencesRepository
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.ReminderScheduler
import com.sensorpark.domain.repository.ReservationRepository
import com.sensorpark.domain.repository.SpotAvailabilityRepository
import com.sensorpark.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant

/**
 * Los repositorios falsos que pide T04. Guardan todo en memoria, anotan qué se les pidió y dejan
 * que el test decida qué responder. No dependen de Android ni de Firebase.
 *
 * El de reservas imita al backend de verdad en lo que importa para una demo: al reservar asigna un
 * lugar y al hacer check-in o check-out cambia el estado de la reserva, así las pantallas que
 * escuchan el flow se actualizan solas.
 */
class FakeReservationRepository(
    reservations: List<Reservation> = emptyList(),
    private val clock: () -> Instant = { Samples.NOW },
) : ReservationRepository {

    private val state = MutableStateFlow<DataResult<List<Reservation>>>(DataResult.Success(reservations))

    val created = mutableListOf<ReservationRequest>()
    val cancelled = mutableListOf<String>()
    val checkedIn = mutableListOf<String>()
    val checkedOut = mutableListOf<String>()
    val reassigned = mutableListOf<String>()

    /** Cuando están, el falso responde esto en vez de hacer el camino feliz. */
    var createResult: ReservationOutcome<ReservationConfirmation>? = null
    var cancelResult: ReservationOutcome<Unit>? = null
    var checkInResult: ReservationOutcome<Unit>? = null
    var checkOutResult: ReservationOutcome<CheckOutSummary>? = null
    var reassignResult: ReservationOutcome<SpotReassignment>? = null

    /** El código del lugar que asigna al reservar. */
    var spotCodeToAssign: String = "07"

    val current: List<Reservation> get() = (state.value as? DataResult.Success)?.value.orEmpty()

    fun emit(reservations: List<Reservation>) {
        state.value = DataResult.Success(reservations)
    }

    fun fail(reason: DataErrorReason) {
        state.value = DataResult.Failure(reason)
    }

    override fun myReservations(): Flow<DataResult<List<Reservation>>> = state.asStateFlow()

    override suspend fun create(request: ReservationRequest): ReservationOutcome<ReservationConfirmation> {
        created += request
        createResult?.let { return it }
        val reservation = Samples.reservation(
            id = "res-${created.size}",
            arrivesAt = request.arrivesAt,
            durationMinutes = request.durationMinutes,
            spotCode = spotCodeToAssign,
        )
        update { it + reservation }
        return ReservationOutcome.Success(
            ReservationConfirmation(
                reservationId = reservation.id,
                spotId = reservation.spotId,
                spotCode = reservation.spotCode,
                arrivesAt = reservation.arrivesAt,
                endsAt = reservation.endsAt,
                toleranceUntil = reservation.toleranceUntil,
            ),
        )
    }

    override suspend fun cancel(reservationId: String): ReservationOutcome<Unit> {
        cancelled += reservationId
        cancelResult?.let { return it }
        changeStatus(reservationId, ReservationStatus.CANCELLED)
        return ReservationOutcome.Success(Unit)
    }

    override suspend fun reassignSpot(reservationId: String): ReservationOutcome<SpotReassignment> {
        reassigned += reservationId
        reassignResult?.let { return it }
        val reassignment = SpotReassignment(spotId = "spot-09", spotCode = "09")
        update { list ->
            list.map {
                if (it.id == reservationId) it.copy(spotId = reassignment.spotId, spotCode = reassignment.spotCode)
                else it
            }
        }
        return ReservationOutcome.Success(reassignment)
    }

    override suspend fun checkIn(reservationId: String, location: Coordinates): ReservationOutcome<Unit> {
        checkedIn += reservationId
        checkInResult?.let { return it }
        update { list ->
            list.map {
                if (it.id == reservationId) it.copy(status = ReservationStatus.IN_USE, checkedInAt = clock()) else it
            }
        }
        return ReservationOutcome.Success(Unit)
    }

    override suspend fun checkOut(reservationId: String): ReservationOutcome<CheckOutSummary> {
        checkedOut += reservationId
        checkOutResult?.let { return it }
        val reservation = current.firstOrNull { it.id == reservationId }
        val stayMinutes = reservation?.checkedInAt
            ?.let { Duration.between(it, clock()).toMinutes().toInt() }
            ?: 0
        val summary = CheckOutSummary(
            stayMinutes = stayMinutes,
            amount = Samples.UTN_MEDRANO.hourlyRate * stayMinutes / 60,
        )
        update { list ->
            list.map {
                if (it.id == reservationId) {
                    it.copy(
                        status = ReservationStatus.FINISHED,
                        checkedOutAt = clock(),
                        stayMinutes = summary.stayMinutes,
                        amount = summary.amount,
                    )
                } else {
                    it
                }
            }
        }
        return ReservationOutcome.Success(summary)
    }

    override suspend fun joinWaitlist(request: ReservationRequest): ReservationOutcome<WaitlistPosition> =
        ReservationOutcome.Success(WaitlistPosition(entryId = "w-1", position = 1))

    override suspend fun leaveWaitlist(entryId: String): ReservationOutcome<Unit> = ReservationOutcome.Success(Unit)

    private fun changeStatus(reservationId: String, status: ReservationStatus) =
        update { list -> list.map { if (it.id == reservationId) it.copy(status = status) else it } }

    private fun update(transform: (List<Reservation>) -> List<Reservation>) {
        state.update { DataResult.Success(transform((it as? DataResult.Success)?.value.orEmpty())) }
    }
}

class FakeParkingLotRepository(lots: List<ParkingLot> = listOf(Samples.UTN_MEDRANO)) : ParkingLotRepository {
    private val state = MutableStateFlow(lots)

    fun emit(lots: List<ParkingLot>) {
        state.value = lots
    }

    override fun parkingLots(): Flow<DataResult<List<ParkingLot>>> = state.map { DataResult.Success(it) }

    override fun parkingLot(id: String): Flow<DataResult<ParkingLot>> = state.map { lots ->
        lots.firstOrNull { it.id == id }
            ?.let { DataResult.Success(it) }
            ?: DataResult.Failure(DataErrorReason.NOT_FOUND)
    }
}

class FakeSpotAvailabilityRepository(
    availability: SpotAvailability = Samples.availability(Samples.spot()),
) : SpotAvailabilityRepository {
    private val state = MutableStateFlow(availability)
    val requested = mutableListOf<String>()

    /** Como el simulador de sensores: cambia el estado de un lugar y la pantalla se entera sola. */
    fun emit(availability: SpotAvailability) {
        state.value = availability
    }

    override fun observeSpots(parkingLotId: String): Flow<DataResult<SpotAvailability>> {
        requested += parkingLotId
        return state.map { DataResult.Success(it) }
    }
}

class FakeUserRepository(category: Category = Category.STANDARD) : UserRepository {
    private val state = MutableStateFlow(category)

    override val category: Flow<Category> = state.asStateFlow()

    override suspend fun setCategory(category: Category) {
        state.value = category
    }
}

/** Por defecto devuelve la puerta del estacionamiento de ejemplo: el check-in sale bien. */
class FakeLocationRepository(
    var reading: LocationReading? = LocationReading(Samples.UTN_MEDRANO.location, accuracyMeters = 8f),
    private var permission: Boolean = true,
) : LocationRepository {
    /** Cuántas veces se pidió la ubicación, para comprobar que no se pide de más. */
    var requests = 0
        private set

    override suspend fun currentLocation(): LocationReading? {
        requests++
        return reading
    }

    override suspend fun approximateLocation(): LocationReading? {
        requests++
        return reading
    }

    override fun hasLocationPermission(): Boolean = permission

    fun denyPermission() {
        permission = false
        reading = null
    }

    fun grantPermission(at: LocationReading = LocationReading(Samples.UTN_MEDRANO.location, accuracyMeters = 8f)) {
        permission = true
        reading = at
    }

    /** A un par de kilómetros: el check-in se rechaza por TOO_FAR. */
    fun moveFarAway() {
        reading = LocationReading(Coordinates(-34.5800, -58.4200), accuracyMeters = 8f)
    }
}

class FakeGeofenceRepository(var backgroundPermission: Boolean = true) : GeofenceRepository {
    private val geofences = mutableMapOf<String, ActiveGeofence>()
    val registered = mutableListOf<String>()
    val removed = mutableListOf<String>()

    override suspend fun register(geofence: ActiveGeofence): Boolean {
        // Como la de verdad: la fila se guarda aunque falte el permiso.
        geofences[geofence.reservationId] = geofence
        registered += geofence.reservationId
        return backgroundPermission
    }

    override suspend fun remove(reservationId: String) {
        geofences -= reservationId
        removed += reservationId
    }

    override suspend fun active(): List<ActiveGeofence> = geofences.values.toList()

    override suspend fun find(reservationId: String): ActiveGeofence? = geofences[reservationId]

    override suspend fun restoreAll() = Unit

    override fun hasBackgroundLocationPermission(): Boolean = backgroundPermission
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

class FakeNotificationPreferencesRepository(
    preferences: NotificationPreferences = NotificationPreferences(),
) : NotificationPreferencesRepository {
    private val state = MutableStateFlow(preferences)

    override val preferences: Flow<NotificationPreferences> = state.asStateFlow()

    override suspend fun setEnabled(type: NotificationType, enabled: Boolean) {
        state.update { it.with(type, enabled) }
    }
}

/**
 * Textos de los avisos sin recursos de Android, para poder probar en la JVM qué aviso se mostró.
 * El texto no importa: lo que se comprueba es el tipo y la clave.
 */
object FakeAlertMessages : com.sensorpark.domain.notification.ReservationAlertMessages {
    override fun reservationConfirmed(
        reservationId: String,
        parkingLotName: String,
        spotCode: String,
        arrivesAt: Instant,
    ) = AppNotification(
        type = NotificationType.RESERVATION,
        key = "reservationConfirmed$reservationId",
        title = "Reserva confirmada",
        body = "$parkingLotName · lugar $spotCode",
    )

    override fun arrivalReminder(
        reservationId: String,
        parkingLotName: String,
        spotCode: String,
        arrivesAt: Instant,
    ) = AppNotification(
        type = NotificationType.REMINDER,
        key = "reminder$reservationId",
        title = "Tu reserva es en 30 minutos",
        body = "$parkingLotName · lugar $spotCode",
    )

    override fun arrival(geofence: ActiveGeofence) = AppNotification(
        type = NotificationType.ARRIVAL,
        key = "arrival${geofence.reservationId}",
        title = "Llegaste a ${geofence.parkingLotName}",
        body = "Tu lugar es el ${geofence.spotCode}",
    )

    override fun stay(reservationId: String, spotCode: String, endsAt: Instant) = AppNotification(
        type = NotificationType.RESERVATION,
        key = stayKey(reservationId),
        title = "Estacionado en el lugar $spotCode",
        body = "Hasta el fin de la franja",
        ongoing = true,
    )

    override fun stayKey(reservationId: String) = "stay$reservationId"
}
