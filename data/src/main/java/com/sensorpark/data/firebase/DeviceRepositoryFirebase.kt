package com.sensorpark.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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

    override suspend fun unregisterCurrentToken() {
        val uid = auth.currentUser?.uid ?: return
        devices(uid).document(messaging.token.await()).delete().await()
    }

    /**
     * Guarda el token en `users/{uid}/devices/{token}`: las functions mandan los avisos a todos los
     * teléfonos del usuario. De paso borra `fcmToken`, donde lo guardaban las versiones anteriores.
     */
    suspend fun saveToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        db.batch()
            .set(
                devices(uid).document(token),
                mapOf(DeviceFields.TOKEN to token, DeviceFields.UPDATED_AT to FieldValue.serverTimestamp()),
            )
            .set(
                db.collection(Collections.USERS).document(uid),
                mapOf(UserFields.FCM_TOKEN to FieldValue.delete()),
                SetOptions.merge(),
            )
            .commit()
            .await()
    }

    private fun devices(uid: String) =
        db.collection(Collections.USERS).document(uid).collection(Collections.DEVICES)
}
