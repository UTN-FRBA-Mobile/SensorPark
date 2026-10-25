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
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Role
import com.sensorpark.domain.model.Session
import com.sensorpark.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
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
        val uid = auth.signInWithEmailAndPassword(email, password).await().user!!.uid
        // Si la cuenta se quedó sin documento (por ejemplo, porque se borró a mano), crearlo
        // dispara `assignInitialRole` y la cuenta no queda esperando un rol para siempre.
        db.collection(Collections.USERS).document(uid)
            .set(mapOf(UserFields.EMAIL to email), SetOptions.merge())
            .await()
        Unit
    }

    /**
     * Crea la cuenta y su documento `users/{uid}`. El rol NO se escribe desde acá: se pide en
     * `requestedRole` y lo asigna la function `assignInitialRole` al ver el documento nuevo.
     */
    override suspend fun signUp(
        name: String,
        email: String,
        password: String,
        role: Role,
        category: Category,
    ) = translatingErrors {
        require(role != Role.ADMIN) { "El rol ADMIN no se puede pedir al registrarse." }
        val uid = auth.createUserWithEmailAndPassword(email, password).await().user!!.uid
        // Tiene que ser lo primero que se escribe: el trigger mira sólo la creación del documento.
        db.collection(Collections.USERS).document(uid).set(
            mapOf(
                UserFields.NAME to name,
                UserFields.EMAIL to email,
                UserFields.REQUESTED_ROLE to role.name,
                UserFields.CATEGORY to category.name,
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
        if (roleInDocument == null) null else resolveRole(roleInDocument, ::roleFromToken)
    }

    /**
     * El rol del claim: con red, refrescando el token (trae el recién asignado); sin red, el del
     * token guardado. Si tampoco hay token válido devuelve null, y [resolveRole] usa el del documento.
     */
    private suspend fun roleFromToken(): Role? {
        val user = auth.currentUser ?: return null
        val token = try {
            withTimeout(REFRESH_TIMEOUT_MS) { user.getIdToken(true).await() }
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            runCatching { withTimeout(REFRESH_TIMEOUT_MS) { user.getIdToken(false).await() } }.getOrNull()
        }
        return token?.let { Role.from(it.claims[ROLE_CLAIM]) }
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

        /** Cuánto se espera a refrescar el token antes de seguir con lo guardado (sin red no vuelve). */
        const val REFRESH_TIMEOUT_MS = 5_000L
    }
}

/**
 * Qué rol vale: el del claim, que es el que leen las reglas, o si no se puede leer (sin red y sin un
 * token vigente) el que el backend copió en `users/{uid}.role`. Con eso la app abre sin conexión en
 * vez de quedarse en "Preparando tu cuenta…" (T19: favoritos e historial con modo avión).
 */
internal suspend fun resolveRole(roleInDocument: String, fromToken: suspend () -> Role?): Role? =
    fromToken() ?: Role.from(roleInDocument)
