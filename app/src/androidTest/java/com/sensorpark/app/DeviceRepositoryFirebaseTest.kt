package com.sensorpark.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.messaging.FirebaseMessaging
import com.sensorpark.data.firebase.DeviceRepositoryFirebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El registro del teléfono para recibir avisos, contra los emuladores de Firebase. El token es el
 * de FCM de verdad: el emulador de Android necesita Google Play. Antes de correrlo, igual que
 * [ParkingLotRepositoryFirebaseTest]: emuladores levantados y semilla cargada.
 */
@RunWith(AndroidJUnit4::class)
class DeviceRepositoryFirebaseTest {
    private val repository = DeviceRepositoryFirebase(auth, db, FirebaseMessaging.getInstance())

    @Test
    fun registraElTelefonoEnLaCuentaYLoSacaAlCerrarSesion() = runBlocking<Unit> {
        val uid = signInAs("driver@sensorpark.test")
        val token = FirebaseMessaging.getInstance().token.await()
        val device = db.document("users/$uid/devices/$token")

        repository.registerCurrentToken()
        val saved = device.get().await()
        assertEquals(token, saved.getString("token"))
        // El campo de las versiones anteriores ya no se usa.
        assertNull(db.document("users/$uid").get().await().getString("fcmToken"))

        repository.unregisterCurrentToken()
        assertFalse(device.get().await().exists())
    }

    @Test
    fun siOtraCuentaIniciaSesionEnElMismoTelefonoLaAnteriorDejaDeRecibirAvisos() = runBlocking<Unit> {
        val token = FirebaseMessaging.getInstance().token.await()
        val driverUid = signInAs("driver@sensorpark.test")
        repository.registerCurrentToken()

        // Cambia de cuenta sin cerrar sesión de la anterior (por ejemplo, sin conexión).
        val adminUid = signInAs("parking-admin@sensorpark.test")
        repository.registerCurrentToken()
        assertTrue(db.document("users/$adminUid/devices/$token").get().await().exists())

        // El trigger claimDevice se lo saca a la cuenta anterior. Sólo el dueño lo puede leer.
        signInAs("driver@sensorpark.test")
        val old = db.document("users/$driverUid/devices/$token")
        var tries = 0
        while (old.get().await().exists() && tries++ < 50) delay(200)
        assertFalse(old.get().await().exists())

        signInAs("parking-admin@sensorpark.test")
        repository.unregisterCurrentToken()
    }

    private suspend fun signInAs(email: String): String =
        auth.signInWithEmailAndPassword(email, PASSWORD).await().user!!.uid

    companion object {
        private const val HOST = "10.0.2.2"
        private const val PASSWORD = "sensorpark"

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
