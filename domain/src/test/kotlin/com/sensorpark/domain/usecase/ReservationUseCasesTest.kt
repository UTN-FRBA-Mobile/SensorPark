package com.sensorpark.domain.usecase

import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.CheckOutSummary
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationRequest
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotReassignment
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.model.WaitlistEntry
import com.sensorpark.domain.model.WaitlistOffer
import com.sensorpark.domain.model.WaitlistPosition
import com.sensorpark.domain.model.WaitlistStatus
import com.sensorpark.domain.repository.ReservationRepository
import com.sensorpark.domain.reservation.ReservationRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ReservationUseCasesTest {
    private val now = Instant.parse("2026-10-19T15:00:00Z") // 12:00 en Argentina
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val lot = ParkingLot("utn-medrano", "UTN Medrano", "", Coordinates(-34.5984, -58.42), toleranceMinutes = 15, geofenceRadiusMeters = 300)
    private val request = ReservationRequest("utn-medrano", now.plus(Duration.ofHours(1)), 60, SpotType.STANDARD)

    /** Repositorio falso: anota qué se le pidió al backend y responde siempre que salió bien. */
    private class FakeReservationRepository(private val mine: List<Reservation> = emptyList()) : ReservationRepository {
        val calls = mutableListOf<String>()

        override fun myReservations(): Flow<DataResult<List<Reservation>>> = flowOf(DataResult.Success(mine))

        override suspend fun create(request: ReservationRequest): ReservationOutcome<ReservationConfirmation> {
            calls += "create"
            return ReservationOutcome.Success(
                ReservationConfirmation("r1", "01", "01", request.arrivesAt, request.arrivesAt, request.arrivesAt),
            )
        }

        override suspend fun cancel(reservationId: String): ReservationOutcome<Unit> {
            calls += "cancel"
            return ReservationOutcome.Success(Unit)
        }

        override suspend fun reassignSpot(reservationId: String): ReservationOutcome<SpotReassignment> {
            calls += "reassignSpot"
            return ReservationOutcome.Success(SpotReassignment(spotId = "spot-09", spotCode = "09"))
        }

        override suspend fun checkIn(reservationId: String, location: Coordinates): ReservationOutcome<Unit> {
            calls += "checkIn"
            return ReservationOutcome.Success(Unit)
        }

        override suspend fun checkOut(reservationId: String): ReservationOutcome<CheckOutSummary> =
            ReservationOutcome.Success(CheckOutSummary(0, 0))

        override suspend fun joinWaitlist(request: ReservationRequest): ReservationOutcome<WaitlistPosition> {
            calls += "joinWaitlist"
            return ReservationOutcome.Success(WaitlistPosition("w1", 1))
        }

        override suspend fun leaveWaitlist(entryId: String): ReservationOutcome<Unit> = ReservationOutcome.Success(Unit)

        override fun myWaitlist(): Flow<DataResult<List<WaitlistEntry>>> = flowOf(DataResult.Success(emptyList()))

        override suspend fun acceptWaitlistOffer(entryId: String): ReservationOutcome<ReservationConfirmation> {
            calls += "acceptWaitlistOffer"
            return ReservationOutcome.Success(ReservationConfirmation("r2", "01", "01", Instant.EPOCH, Instant.EPOCH, Instant.EPOCH))
        }

        override suspend fun declineWaitlistOffer(entryId: String): ReservationOutcome<Unit> {
            calls += "declineWaitlistOffer"
            return ReservationOutcome.Success(Unit)
        }
    }

    private fun entry(status: WaitlistStatus, expiresAt: Instant? = null) = WaitlistEntry(
        "w1", "ana", "utn-medrano", SpotType.STANDARD, now.plus(Duration.ofHours(1)), 60, Category.STANDARD,
        now.minus(Duration.ofMinutes(10)), status,
        offer = expiresAt?.let { WaitlistOffer("01", "01", it.minus(ReservationRules.WAITLIST_OFFER_TIME), it) },
    )

    private fun reservation(status: ReservationStatus, arrivesAt: Instant = now) = Reservation(
        "r-$status", "ana", "utn-medrano", "01", "01", SpotType.STANDARD, arrivesAt, 60,
        ReservationRules.endsAt(arrivesAt, 60), arrivesAt.plus(Duration.ofMinutes(15)), status,
    )

    @Test
    fun `un pedido valido llega al backend`() = runTest {
        val repo = FakeReservationRepository()
        val result = CreateReservationUseCase(repo, clock)(request, lot, Category.STANDARD)

        assertTrue(result is ReservationOutcome.Success)
        assertEquals(listOf("create"), repo.calls)
    }

    @Test
    fun `un pedido invalido se rechaza sin llamar al backend`() = runTest {
        val repo = FakeReservationRepository()
        val result = CreateReservationUseCase(repo, clock)(request.copy(durationMinutes = 45), lot, Category.STANDARD)

        assertEquals(ReservationOutcome.Rejected(ReservationRejection.INVALID_DURATION), result)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `el limite cuenta solo las reservas activas propias`() = runTest {
        val twoActive = FakeReservationRepository(
            listOf(reservation(ReservationStatus.CONFIRMED), reservation(ReservationStatus.IN_USE), reservation(ReservationStatus.FINISHED)),
        )
        assertEquals(
            ReservationOutcome.Rejected(ReservationRejection.LIMIT_REACHED),
            CreateReservationUseCase(twoActive, clock)(request, lot, Category.STANDARD),
        )

        val oneActive = FakeReservationRepository(listOf(reservation(ReservationStatus.CONFIRMED), reservation(ReservationStatus.CANCELLED)))
        assertTrue(CreateReservationUseCase(oneActive, clock)(request, lot, Category.STANDARD) is ReservationOutcome.Success)
    }

    @Test
    fun `con el lugar tomado por otro auto se pide otro al backend`() = runTest {
        val repo = FakeReservationRepository()
        val taken = Spot("01", "01", SpotType.STANDARD, SpotStatus.OCCUPIED)

        val result = ReassignSpotUseCase(repo, clock)(reservation(ReservationStatus.CONFIRMED), taken)

        assertEquals(ReservationOutcome.Success(SpotReassignment("spot-09", "09")), result)
        assertEquals(listOf("reassignSpot"), repo.calls)
    }

    @Test
    fun `con el lugar libre no se pide otro ni se llama al backend`() = runTest {
        val repo = FakeReservationRepository()
        val free = Spot("01", "01", SpotType.STANDARD, SpotStatus.FREE)

        assertEquals(
            ReservationOutcome.Rejected(ReservationRejection.SPOT_NOT_TAKEN),
            ReassignSpotUseCase(repo, clock)(reservation(ReservationStatus.CONFIRMED), free),
        )
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `pasada la tolerancia no se pide otro lugar`() = runTest {
        val repo = FakeReservationRepository()
        val taken = Spot("01", "01", SpotType.STANDARD, SpotStatus.OCCUPIED)
        // Llegó hace una hora: la tolerancia era de 15 minutos.
        val expired = reservation(ReservationStatus.CONFIRMED, arrivesAt = now.minus(Duration.ofHours(1)))

        assertEquals(
            ReservationOutcome.Rejected(ReservationRejection.EXPIRED),
            ReassignSpotUseCase(repo, clock)(expired, taken),
        )
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `cancelar una reserva en uso se rechaza sin llamar al backend`() = runTest {
        val repo = FakeReservationRepository()
        assertEquals(
            ReservationOutcome.Rejected(ReservationRejection.CANCEL_NOT_ALLOWED),
            CancelReservationUseCase(repo)(reservation(ReservationStatus.IN_USE)),
        )
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `check-in lejos del estacionamiento se rechaza sin llamar al backend`() = runTest {
        val repo = FakeReservationRepository()
        val farAway = Coordinates(-34.6037, -58.4108)
        assertEquals(
            ReservationOutcome.Rejected(ReservationRejection.TOO_FAR),
            CheckInUseCase(repo, clock)(reservation(ReservationStatus.CONFIRMED), lot, farAway),
        )
        assertTrue(CheckInUseCase(repo, clock)(reservation(ReservationStatus.CONFIRMED), lot, lot.location) is ReservationOutcome.Success)
    }

    @Test
    fun `anotarse no mira el limite pero si la categoria`() = runTest {
        val full = FakeReservationRepository(listOf(reservation(ReservationStatus.CONFIRMED), reservation(ReservationStatus.CONFIRMED)))
        assertTrue(JoinWaitlistUseCase(full, clock)(request, lot, Category.STANDARD) is ReservationOutcome.Success)
        assertEquals(
            ReservationOutcome.Rejected(ReservationRejection.CATEGORY_REQUIRED),
            JoinWaitlistUseCase(full, clock)(request.copy(spotType = SpotType.PRIORITY), lot, Category.STANDARD),
        )
    }

    @Test
    fun `aceptar una oferta vigente llega al backend`() = runTest {
        val repo = FakeReservationRepository()
        val offered = entry(WaitlistStatus.OFFERED, expiresAt = now.plus(Duration.ofMinutes(3)))
        assertTrue(AcceptWaitlistOfferUseCase(repo, clock)(offered) is ReservationOutcome.Success)
        assertEquals(listOf("acceptWaitlistOffer"), repo.calls)
    }

    @Test
    fun `aceptar sin oferta o con la oferta vencida se rechaza sin llamar al backend`() = runTest {
        val repo = FakeReservationRepository()
        assertEquals(
            ReservationOutcome.Rejected(ReservationRejection.NO_OFFER),
            AcceptWaitlistOfferUseCase(repo, clock)(entry(WaitlistStatus.WAITING)),
        )
        assertEquals(
            ReservationOutcome.Rejected(ReservationRejection.OFFER_EXPIRED),
            AcceptWaitlistOfferUseCase(repo, clock)(entry(WaitlistStatus.OFFERED, expiresAt = now.minusSeconds(1))),
        )
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `aceptar la oferta respeta el limite de reservas activas`() = runTest {
        val full = FakeReservationRepository(listOf(reservation(ReservationStatus.CONFIRMED), reservation(ReservationStatus.IN_USE)))
        assertEquals(
            ReservationOutcome.Rejected(ReservationRejection.LIMIT_REACHED),
            AcceptWaitlistOfferUseCase(full, clock)(entry(WaitlistStatus.OFFERED, expiresAt = now.plus(Duration.ofMinutes(3)))),
        )
        assertTrue(full.calls.isEmpty())
    }

    @Test
    fun `rechazar sólo tiene sentido con una oferta, aunque esté vencida`() = runTest {
        val repo = FakeReservationRepository()
        assertEquals(
            ReservationOutcome.Rejected(ReservationRejection.NO_OFFER),
            DeclineWaitlistOfferUseCase(repo)(entry(WaitlistStatus.WAITING)),
        )
        assertTrue(DeclineWaitlistOfferUseCase(repo)(entry(WaitlistStatus.OFFERED, expiresAt = now.minusSeconds(1))) is ReservationOutcome.Success)
        assertEquals(listOf("declineWaitlistOffer"), repo.calls)
    }
}
