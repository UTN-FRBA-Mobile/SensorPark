package com.sensorpark.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sensorpark.app.model.Rol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    val usuarioActual: FirebaseUser? get() = auth.currentUser

    /** Emite el usuario con sesión iniciada, o null si no hay sesión. */
    val sesion: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun ingresar(email: String, password: String) {
        withTimeout(TOPE_MS) { auth.signInWithEmailAndPassword(email, password).await() }
    }

    /**
     * Crea la cuenta y su documento `users/{uid}`. El rol NO se escribe desde acá:
     * lo asigna la function `asignarRolInicial` al ver el documento nuevo.
     */
    suspend fun registrar(nombre: String, email: String, password: String) {
        withTimeout(TOPE_MS) {
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
        }
    }

    fun salir() = auth.signOut()

    /**
     * Rol del usuario, o null mientras el backend todavía no lo asignó.
     * `users/{uid}.rol` es sólo el aviso de que el claim cambió: al verlo se refresca
     * el token y el rol que vale es el del claim, que es el que leen las reglas.
     */
    fun rol(uid: String): Flow<Rol?> = callbackFlow {
        val registro = db.collection(Colecciones.USUARIOS).document(uid)
            .addSnapshotListener { doc, error ->
                if (error != null) close(error) else trySend(doc?.getString(CamposUsuario.ROL))
            }
        awaitClose { registro.remove() }
    }.distinctUntilChanged().map { rolEnDocumento ->
        if (rolEnDocumento == null) null else rolDelToken(forzarRefresco = true)
    }

    private suspend fun rolDelToken(forzarRefresco: Boolean): Rol? {
        val token = auth.currentUser?.getIdToken(forzarRefresco)?.await() ?: return null
        return Rol.desde(token.claims["rol"])
    }

    private companion object {
        // await() espera el ack del servidor: sin tope, una red rota cuelga la UI.
        const val TOPE_MS = 15_000L
    }
}
