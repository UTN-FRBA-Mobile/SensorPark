package com.sensorpark.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.sensorpark.data.firebase.SpotAvailabilityRepositoryFirebase
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.SpotAvailability
import com.sensorpark.domain.model.SpotStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los lugares en vivo contra los emuladores de Firebase, desde el emulador de Android.
 * Antes de correrlo (desde la raíz del repo):
 *   firebase emulators:start
 *   node functions/scripts/seed-emulators.js
 *   .\gradlew.bat :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class SpotAvailabilityRepositoryFirebaseTest {
    private val repository = SpotAvailabilityRepositoryFirebase(db)

    @Before
    fun signIn() = runBlocking<Unit> {
        auth.signInWithEmailAndPassword("driver@sensorpark.test", PASSWORD).await()
    }

    @Test
    fun traeLosLugaresDeLaSemillaOrdenadosPorCodigo() = runBlocking<Unit> {
        val availability = success(repository.observeSpots("utn-medrano").first())

        assertEquals(14, availability.spots.size)
        assertEquals(availability.spots.map { it.code }.sorted(), availability.spots.map { it.code })
        assertFalse(availability.isFromCache)
    }

    @Test
    fun unCambioDeUnLugarLlegaEnVivo() = runBlocking<Unit> {
        // El encargado de prueba administra utn-medrano (ver seed-emulators.js).
        auth.signInWithEmailAndPassword("parking-admin@sensorpark.test", PASSWORD).await()
        val spot = db.collection("parkingLots").document("utn-medrano").collection("spots").document("14")
        spot.update("status", "FREE").await()
        val before = success(repository.observeSpots("utn-medrano").first()).spots.first { it.id == "14" }
        assertEquals(SpotStatus.FREE, before.status)

        spot.update("status", "OUT_OF_SERVICE").await()
        withTimeout(TIMEOUT_MS) {
            repository.observeSpots("utn-medrano").first {
                (it as? DataResult.Success)?.value?.spots?.first { s -> s.id == "14" }?.status == SpotStatus.OUT_OF_SERVICE
            }
        }

        spot.update("status", "FREE").await()
    }

    @Test
    fun unEstacionamientoSinPermisoDaPermissionDenied() = runBlocking<Unit> {
        auth.signOut()

        assertEquals(
            DataResult.Failure(DataErrorReason.PERMISSION_DENIED),
            withTimeout(TIMEOUT_MS) { repository.observeSpots("utn-medrano").first() },
        )
    }

    private fun success(result: DataResult<SpotAvailability>): SpotAvailability =
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
            // Si otra clase de test ya los configuró, Firebase no deja hacerlo dos veces.
            runCatching { auth.useEmulator(HOST, 9099) }
            runCatching {
                db.useEmulator(HOST, 8080)
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
        }
    }
}
