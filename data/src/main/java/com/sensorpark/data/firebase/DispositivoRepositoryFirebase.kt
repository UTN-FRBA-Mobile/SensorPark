package com.sensorpark.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.sensorpark.domain.repository.DispositivoRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DispositivoRepositoryFirebase @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
) : DispositivoRepository {

    override suspend fun registrarTokenActual() = guardarToken(messaging.token.await())

    /** Guarda el token en `users/{uid}.fcmToken`; las functions lo leen de ahí para enviar. */
    suspend fun guardarToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection(Colecciones.USUARIOS).document(uid)
            .set(mapOf(CamposUsuario.FCM_TOKEN to token), SetOptions.merge())
            .await()
    }
}
