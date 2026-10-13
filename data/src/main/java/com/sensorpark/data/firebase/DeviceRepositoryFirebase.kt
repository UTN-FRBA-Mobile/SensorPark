package com.sensorpark.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.sensorpark.domain.repository.DeviceRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DeviceRepositoryFirebase @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
) : DeviceRepository {

    override suspend fun registerCurrentToken() = saveToken(messaging.token.await())

    /** Guarda el token en `users/{uid}.fcmToken`; las functions lo leen de ahí para enviar. */
    suspend fun saveToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection(Collections.USERS).document(uid)
            .set(mapOf(UserFields.FCM_TOKEN to token), SetOptions.merge())
            .await()
    }
}
