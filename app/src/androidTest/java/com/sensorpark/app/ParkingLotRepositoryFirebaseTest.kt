package com.sensorpark.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.sensorpark.data.firebase.ParkingLotRepositoryFirebase
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.SpotType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El repositorio de estacionamientos contra los emuladores de Firebase, desde el emulador de Android.
 * Antes de correrlo (desde la raíz del repo):
 *   firebase emulators:start
 *   node functions/scripts/seed-emulators.js
 *   .\gradlew.bat :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ParkingLotRepositoryFirebaseTest {
    private val repository = ParkingLotRepositoryFirebase(db)

    @Before
    fun signIn() = runBlocking<Unit> {
        signInAs("driver@sensorpark.test")
    }

    @Test
    fun listaLosEstacionamientosDeLaSemilla() = runBlocking<Unit> {
        val lots = success(repository.parkingLots().first())

        assertEquals(listOf("abasto", "parque-centenario", "utn-medrano"), lots.map { it.id }.sorted())
        assertEquals(14, lots.first { it.id == "utn-medrano" }.capacity)
    }

    @Test
    fun traeElDetalleConSusContadoresPorTipo() = runBlocking<Unit> {
        val lot = success(repository.parkingLot("utn-medrano").first())

        assertEquals("UTN Medrano", lot.name)
        assertEquals(14, lot.capacity)
        assertEquals(8, lot.capacityOf(SpotType.STANDARD))
        assertEquals(2, lot.capacityOf(SpotType.EV_CHARGING))
    }

    @Test
    fun unEstacionamientoQueNoExisteDaNotFound() = runBlocking<Unit> {
        assertEquals(
            DataResult.Failure(DataErrorReason.NOT_FOUND),
            withTimeout(TIMEOUT_MS) { repository.parkingLot("no-existe").first() },
        )
    }

    @Test
    fun sinSesionDaPermissionDenied() = runBlocking<Unit> {
        auth.signOut()

        assertEquals(
            DataResult.Failure(DataErrorReason.PERMISSION_DENIED),
            withTimeout(TIMEOUT_MS) { repository.parkingLots().first() },
        )
    }

    @Test
    fun losContadoresLleganEnVivoCuandoSuEncargadoCambiaUnLugar() = runBlocking<Unit> {
        // El encargado de prueba administra utn-medrano (ver seed-emulators.js).
        signInAs("parking-admin@sensorpark.test")
        val spot = db.collection("parkingLots").document("utn-medrano").collection("spots").document("14")
        spot.update("status", "FREE").await()
        val before = success(repository.parkingLot("utn-medrano").first()).freeSpots

        spot.update("status", "OUT_OF_SERVICE").await()
        withTimeout(TIMEOUT_MS) {
            repository.parkingLot("utn-medrano").first { (it as? DataResult.Success)?.value?.freeSpots == before - 1 }
        }

        spot.update("status", "FREE").await()
        withTimeout(TIMEOUT_MS) {
            repository.parkingLot("utn-medrano").first { (it as? DataResult.Success)?.value?.freeSpots == before }
        }
    }

    private suspend fun signInAs(email: String) {
        auth.signInWithEmailAndPassword(email, PASSWORD).await()
    }

    private fun <T> success(result: DataResult<T>): T =
        (result as? DataResult.Success)?.value ?: error("Se esperaba Success y llegó $result")

    companion object {
        private const val HOST = "10.0.2.2"
        private const val PASSWORD = "sensorpark"
        private const val TIMEOUT_MS = 15_000L

        private val auth = FirebaseAuth.getInstance()
        private val db = FirebaseFirestore.getInstance()

        @BeforeClass
        @JvmStatic
        fun useEmulators() {
            auth.useEmulator(HOST, 9099)
            db.useEmulator(HOST, 8080)
            // Sin caché en disco: cada lectura va al emulador, no a lo que dejó una corrida anterior.
            db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }
}
