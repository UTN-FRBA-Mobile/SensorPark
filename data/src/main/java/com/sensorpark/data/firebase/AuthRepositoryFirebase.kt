package com.sensorpark.data.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sensorpark.domain.model.AuthErrorReason
import com.sensorpark.domain.model.AuthException
import com.sensorpark.domain.model.Role
import com.sensorpark.domain.model.Session
import com.sensorpark.domain.repository.AuthRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

class AuthRepositoryFirebase @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : AuthRepository {

    override val session: Flow<Session?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.let { Session(it.uid, it.email.orEmpty()) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, password: String) = translatingErrors {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    /**
     * Crea la cuenta y su documento `users/{uid}`. El rol NO se escribe desde acá:
     * lo asigna la function `assignInitialRole` al ver el documento nuevo.
     */
    override suspend fun signUp(name: String, email: String, password: String) = translatingErrors {
        val uid = auth.createUserWithEmailAndPassword(email, password).await().user!!.uid
        // merge: el registro del token FCM puede haber creado el documento antes.
        db.collection(Collections.USERS).document(uid).set(
            mapOf(
                UserFields.NAME to name,
                UserFields.EMAIL to email,
                UserFields.CREATED_AT to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        Unit
    }

    override fun signOut() = auth.signOut()

    /**
     * `users/{uid}.role` es sólo el aviso de que el claim cambió: al verlo se refresca
     * el token y el rol que vale es el del claim, que es el que leen las reglas.
     */
    override fun role(uid: String): Flow<Role?> = callbackFlow {
        val registration = db.collection(Collections.USERS).document(uid)
            .addSnapshotListener { doc, error ->
                if (error != null) close(error) else trySend(doc?.getString(UserFields.ROLE))
            }
        awaitClose { registration.remove() }
    }.distinctUntilChanged().map { roleInDocument ->
        if (roleInDocument == null) null else roleFromToken()
    }

    private suspend fun roleFromToken(): Role? {
        val token = auth.currentUser?.getIdToken(true)?.await() ?: return null
        return Role.from(token.claims[ROLE_CLAIM])
    }

    private suspend fun translatingErrors(action: suspend () -> Unit) {
        try {
            // await() espera el ack del servidor: sin tope, una red rota cuelga la UI.
            withTimeout(TIMEOUT_MS) { action() }
        } catch (e: TimeoutCancellationException) {
            throw AuthException(AuthErrorReason.NO_CONNECTION, e)
        } catch (e: FirebaseNetworkException) {
            throw AuthException(AuthErrorReason.NO_CONNECTION, e)
        } catch (e: FirebaseAuthWeakPasswordException) {
            throw AuthException(AuthErrorReason.WEAK_PASSWORD, e)
        } catch (e: FirebaseAuthUserCollisionException) {
            throw AuthException(AuthErrorReason.EMAIL_IN_USE, e)
        } catch (e: FirebaseAuthInvalidUserException) {
            throw AuthException(AuthErrorReason.INVALID_CREDENTIALS, e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw AuthException(AuthErrorReason.INVALID_CREDENTIALS, e)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val ROLE_CLAIM = "role"
    }
}
