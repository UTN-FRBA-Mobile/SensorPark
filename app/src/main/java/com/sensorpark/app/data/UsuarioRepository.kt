package com.sensorpark.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsuarioRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
) {
    /** Guarda el token FCM del dispositivo en `users/{uid}.fcmToken`; las functions lo leen para enviar. */
    suspend fun guardarTokenFcm(token: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection(Colecciones.USUARIOS).document(uid)
            .set(mapOf(CamposUsuario.FCM_TOKEN to token), SetOptions.merge())
            .await()
    }

    /** Hay que registrar el token al arrancar con sesión, no sólo en `onNewToken`. */
    suspend fun registrarTokenFcmActual() {
        guardarTokenFcm(messaging.token.await())
    }
}
