package com.sensorpark.feature.reservations

import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.CheckOutSummary
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRequest
import com.sensorpark.domain.model.SpotReassignment
import com.sensorpark.domain.model.WaitlistPosition
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.ReservationRepository
import com.sensorpark.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// Repositorios falsos para probar los ViewModels sin Firebase (los que pide T04 para esta parte).

class FakeReservationRepository(reservations: List<Reservation> = emptyList()) : ReservationRepository {
    private val state = MutableStateFlow<DataResult<List<Reservation>>>(DataResult.Success(reservations))

    /** Lo que devuelve createReservation; la prueba lo cambia para simular un rechazo. */
    var createResult: ReservationOutcome<ReservationConfirmation>? = null
    var cancelResult: ReservationOutcome<Unit> = ReservationOutcome.Success(Unit)
    val created = mutableListOf<ReservationRequest>()
    val cancelled = mutableListOf<String>()

    fun emit(reservations: List<Reservation>) {
        state.value = DataResult.Success(reservations)
    }

    fun fail(reason: DataErrorReason) {
        state.value = DataResult.Failure(reason)
    }

    override fun myReservations(): Flow<DataResult<List<Reservation>>> = state

    override suspend fun create(request: ReservationRequest): ReservationOutcome<ReservationConfirmation> {
        created += request
        return createResult ?: ReservationOutcome.Success(
            ReservationConfirmation(
                reservationId = "res-nueva",
                spotId = "spot-07",
                spotCode = "07",
                arrivesAt = request.arrivesAt,
                endsAt = request.arrivesAt.plusSeconds(request.durationMinutes * 60L),
                toleranceUntil = request.arrivesAt.plusSeconds(15 * 60L),
            ),
        )
    }

    override suspend fun cancel(reservationId: String): ReservationOutcome<Unit> {
        cancelled += reservationId
        return cancelResult
    }

    override suspend fun reassignSpot(reservationId: String): ReservationOutcome<SpotReassignment> =
        throw UnsupportedOperationException()

    override suspend fun checkIn(reservationId: String, location: Coordinates): ReservationOutcome<Unit> =
        throw UnsupportedOperationException()

    override suspend fun checkOut(reservationId: String): ReservationOutcome<CheckOutSummary> =
        throw UnsupportedOperationException()

    override suspend fun joinWaitlist(request: ReservationRequest): ReservationOutcome<WaitlistPosition> =
        throw UnsupportedOperationException()

    override suspend fun leaveWaitlist(entryId: String): ReservationOutcome<Unit> =
        throw UnsupportedOperationException()
}

class FakeParkingLotRepository(private val lots: List<ParkingLot>) : ParkingLotRepository {
    override fun parkingLots(): Flow<DataResult<List<ParkingLot>>> = MutableStateFlow(DataResult.Success(lots))

    override fun parkingLot(id: String): Flow<DataResult<ParkingLot>> =
        MutableStateFlow(lots.firstOrNull { it.id == id })
            .map { lot ->
                if (lot == null) DataResult.Failure(DataErrorReason.NOT_FOUND) else DataResult.Success(lot)
            }
}

class FakeUserRepository(category: Category = Category.STANDARD) : UserRepository {
    private val state = MutableStateFlow(category)

    override val category: Flow<Category> = state

    override suspend fun setCategory(category: Category) {
        state.value = category
    }
}
