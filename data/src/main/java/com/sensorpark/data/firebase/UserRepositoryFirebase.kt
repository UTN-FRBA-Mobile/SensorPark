package com.sensorpark.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.repository.UserRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryFirebase @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : UserRepository {

    override val category: Flow<Category> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(Category.STANDARD)
            close()
            return@callbackFlow
        }
        val registration = db.collection(Collections.USERS).document(uid)
            .addSnapshotListener { doc, error ->
                if (error != null) close(error) else trySend(Category.from(doc?.getString(UserFields.CATEGORY)))
            }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    override suspend fun setCategory(category: Category) {
        val uid = auth.currentUser?.uid ?: return
        db.collection(Collections.USERS).document(uid)
            .set(mapOf(UserFields.CATEGORY to category.name), SetOptions.merge())
            .await()
    }
}
