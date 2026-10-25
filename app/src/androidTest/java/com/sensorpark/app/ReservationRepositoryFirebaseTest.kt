package com.sensorpark.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.sensorpark.data.firebase.ReservationRepositoryFirebase
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ReservationOutcome
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationRequest
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.SpotType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * Reservas de punta a punta: la app llama a las functions de reservas en los emuladores de Firebase.
 * Antes de correrlo (desde la raíz del repo):
 *   firebase emulators:start
 *   node functions/scripts/seed-emulators.js
 *   .\gradlew.bat :app:connectedDebugAndroidTest
 * Cada test arma su propio estacionamiento, sin horario y con un solo lugar, para no depender de la
 * hora a la que se corra.
 */
@RunWith(AndroidJUnit4::class)
class ReservationRepositoryFirebaseTest {
    private val repository = ReservationRepositoryFirebase(auth, db, functions)
    private lateinit var lotId: String

    @Before
    fun createParkingLot() = runBlocking<Unit> {
        signInAs("admin@sensorpark.test")
        lotId = "test-reservations-${System.currentTimeMillis()}"
        val lot = db.collection("parkingLots").document(lotId)
        lot.set(mapOf("name" to "Prueba de reservas", "location" to GeoPoint(-34.6, -58.4), "adminUids" to emptyList<String>(), "toleranceMinutes" to 15L, "geofenceRadiusMeters" to 300L, "hourlyRate" to 2000L)).await()
        lot.collection("spots").document("01")
            .set(mapOf("code" to "01", "type" to "STANDARD", "status" to "FREE", "updatedAt" to FieldValue.serverTimestamp())).await()
        signInAs("driver@sensorpark.test")
        releaseActiveReservations()
    }

    /**
     * El conductor de prueba es compartido: si una prueba a mano o una corrida cortada le dejó
     * reservas activas, el límite de dos haría fallar estos tests por LIMIT_REACHED. Arranca sin ninguna.
     */
    private suspend fun releaseActiveReservations() {
        val mine = (repository.myReservations().first() as? DataResult.Success)?.value.orEmpty()
        mine.filter { it.status.isActive }.forEach {
            if (it.status == ReservationStatus.IN_USE) repository.checkOut(it.id) else repository.cancel(it.id)
        }
    }

    /** El estacionamiento de cada test se borra al terminar: si no, se acumulan y ensucian la semilla. */
    @After
    fun deleteParkingLot() = runBlocking<Unit> {
        signInAs("admin@sensorpark.test")
        val lot = db.collection("parkingLots").document(lotId)
        lot.collection("spots").get().await().documents.forEach { it.reference.delete().await() }
        lot.delete().await()
    }

    private fun request(durationMinutes: Int = 60) = ReservationRequest(
        parkingLotId = lotId,
        arrivesAt = Instant.now().plus(Duration.ofHours(2)),
        durationMinutes = durationMinutes,
        spotType = SpotType.STANDARD,
    )

    @Test
    fun reservaYLaVeEnMisReservasYDespuesLaCancela() = runBlocking<Unit> {
        val created = repository.create(request())
        assertTrue("Se esperaba una reserva confirmada y llegó $created", created is ReservationOutcome.Success)
        val id = (created as ReservationOutcome.Success).value.reservationId

        val mine = withTimeout(TIMEOUT_MS) {
            repository.myReservations().first { result -> (result as? DataResult.Success)?.value?.any { it.id == id } == true }
        } as DataResult.Success
        assertEquals(ReservationStatus.CONFIRMED, mine.value.first { it.id == id }.status)

        assertEquals(ReservationOutcome.Success(Unit), repository.cancel(id))
        withTimeout(TIMEOUT_MS) {
            repository.myReservations().first { result ->
                (result as? DataResult.Success)?.value?.firstOrNull { it.id == id }?.status == ReservationStatus.CANCELLED
            }
        }
    }

    @Test
    fun conElUnicoLugarTomadoElBackendRespondeSinLugar() = runBlocking<Unit> {
        val first = repository.create(request()) as ReservationOutcome.Success
        try {
            // Otra reserva del mismo conductor para la misma franja: el único lugar ya está tomado.
            assertEquals(ReservationOutcome.Rejected(ReservationRejection.NO_SPOTS), repository.create(request()))
        } finally {
            repository.cancel(first.value.reservationId)
        }
    }

    @Test
    fun unaDuracionQueNoEsDeLaListaLaRechazaElBackend() = runBlocking<Unit> {
        assertEquals(ReservationOutcome.Rejected(ReservationRejection.INVALID_DURATION), repository.create(request(durationMinutes = 45)))
    }

    private suspend fun signInAs(email: String) {
        auth.signInWithEmailAndPassword(email, PASSWORD).await()
    }

    companion object {
        private const val HOST = "10.0.2.2"
        private const val PASSWORD = "sensorpark"
        private const val TIMEOUT_MS = 15_000L

        private val auth = FirebaseAuth.getInstance()
        private val db = FirebaseFirestore.getInstance()
        private val functions = FirebaseFunctions.getInstance("southamerica-east1")

        @BeforeClass
        @JvmStatic
        fun useEmulators() {
            // Si otra clase de test ya los configuró, Firebase no deja hacerlo dos veces.
            runCatching { auth.useEmulator(HOST, 9099) }
            runCatching {
                db.useEmulator(HOST, 8080)
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
            runCatching { functions.useEmulator(HOST, 5001) }
        }
    }
}
