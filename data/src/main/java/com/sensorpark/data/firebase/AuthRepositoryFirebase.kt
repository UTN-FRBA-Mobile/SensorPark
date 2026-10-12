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
import com.sensorpark.domain.model.AuthException
import com.sensorpark.domain.model.MotivoErrorAuth
import com.sensorpark.domain.model.Rol
import com.sensorpark.domain.model.Sesion
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

    override val sesion: Flow<Sesion?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.let { Sesion(it.uid, it.email.orEmpty()) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun ingresar(email: String, password: String) = traduciendoErrores {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    /**
     * Crea la cuenta y su documento `users/{uid}`. El rol NO se escribe desde acá:
     * lo asigna la function `asignarRolInicial` al ver el documento nuevo.
     */
    override suspend fun registrar(nombre: String, email: String, password: String) = traduciendoErrores {
        val uid = auth.createUserWithEmailAndPassword(email, password).await().user!!.uid
        // merge: el registro del token FCM puede haber creado el documento antes.
        db.collection(Colecciones.USUARIOS).document(uid).set(
            mapOf(
                CamposUsuario.NOMBRE to nombre,
                CamposUsuario.EMAIL to email,
                CamposUsuario.CREADO_EN to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        Unit
    }

    override fun salir() = auth.signOut()

    /**
     * `users/{uid}.rol` es sólo el aviso de que el claim cambió: al verlo se refresca
     * el token y el rol que vale es el del claim, que es el que leen las reglas.
     */
    override fun rol(uid: String): Flow<Rol?> = callbackFlow {
        val registro = db.collection(Colecciones.USUARIOS).document(uid)
            .addSnapshotListener { doc, error ->
                if (error != null) close(error) else trySend(doc?.getString(CamposUsuario.ROL))
            }
        awaitClose { registro.remove() }
    }.distinctUntilChanged().map { rolEnDocumento ->
        if (rolEnDocumento == null) null else rolDelToken()
    }

    private suspend fun rolDelToken(): Rol? {
        val token = auth.currentUser?.getIdToken(true)?.await() ?: return null
        return Rol.desde(token.claims["rol"])
    }

    private suspend fun traduciendoErrores(accion: suspend () -> Unit) {
        try {
            // await() espera el ack del servidor: sin tope, una red rota cuelga la UI.
            withTimeout(TOPE_MS) { accion() }
        } catch (e: TimeoutCancellationException) {
            throw AuthException(MotivoErrorAuth.SIN_CONEXION, e)
        } catch (e: FirebaseNetworkException) {
            throw AuthException(MotivoErrorAuth.SIN_CONEXION, e)
        } catch (e: FirebaseAuthWeakPasswordException) {
            throw AuthException(MotivoErrorAuth.PASSWORD_DEBIL, e)
        } catch (e: FirebaseAuthUserCollisionException) {
            throw AuthException(MotivoErrorAuth.EMAIL_EN_USO, e)
        } catch (e: FirebaseAuthInvalidUserException) {
            throw AuthException(MotivoErrorAuth.CREDENCIALES_INVALIDAS, e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw AuthException(MotivoErrorAuth.CREDENCIALES_INVALIDAS, e)
        }
    }

    private companion object {
        const val TOPE_MS = 15_000L
    }
}
