package com.sensorpark.app.ui.diagnostico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.sensorpark.data.firebase.Colecciones
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

enum class Resultado { PROBANDO, OK, ERROR, SIN_PROBAR }

data class Chequeo(val nombre: String, val resultado: Resultado, val detalle: String = "")

/**
 * Verifica contra el proyecto real cada pieza de la configuración de Firebase.
 * Es una herramienta de puesta en marcha: se puede borrar cuando la app tenga pantallas reales.
 */
@HiltViewModel
class DiagnosticoViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
    private val functions: FirebaseFunctions,
) : ViewModel() {
    private val _chequeos = MutableStateFlow(emptyList<Chequeo>())
    val chequeos: StateFlow<List<Chequeo>> = _chequeos.asStateFlow()

    init {
        probar()
    }

    fun probar() {
        _chequeos.value = listOf(AUTH, ROL, FIRESTORE, FCM).map { Chequeo(it, Resultado.PROBANDO) } +
            Chequeo(PUSH, Resultado.SIN_PROBAR, "Tocá «Enviar push de prueba».")
        viewModelScope.launch {
            chequear(AUTH) {
                val usuario = auth.currentUser ?: error("No hay sesión iniciada.")
                "${usuario.email} · uid ${usuario.uid.take(6)}…"
            }
        }
        viewModelScope.launch {
            chequear(ROL) {
                val claims = auth.currentUser!!.getIdToken(true).await().claims
                claims["rol"]?.toString()
                    ?: error("El token no trae el claim `rol`: ¿está desplegada la function asignarRolInicial?")
            }
        }
        viewModelScope.launch {
            chequear(FIRESTORE) {
                val doc = db.collection(Colecciones.USUARIOS).document(auth.currentUser!!.uid)
                doc.set(mapOf("diagnosticoEn" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
                val leido = doc.get(Source.SERVER).await()
                "Escritura y lectura en users/ a las ${leido.getTimestamp("diagnosticoEn")?.toDate()}"
            }
        }
        viewModelScope.launch {
            chequear(FCM) { "Token …${messaging.token.await().takeLast(8)}" }
        }
    }

    fun enviarPushDePrueba() {
        actualizar(Chequeo(PUSH, Resultado.PROBANDO))
        viewModelScope.launch {
            chequear(PUSH) {
                functions.getHttpsCallable("pushDePrueba").call().await()
                "Enviada: tiene que aparecer una notificación."
            }
        }
    }

    private suspend fun chequear(nombre: String, prueba: suspend () -> String) {
        val chequeo = try {
            Chequeo(nombre, Resultado.OK, withTimeout(TOPE_MS) { prueba() })
        } catch (e: Exception) {
            Chequeo(nombre, Resultado.ERROR, e.message ?: e.javaClass.simpleName)
        }
        actualizar(chequeo)
    }

    private fun actualizar(chequeo: Chequeo) {
        _chequeos.update { lista -> lista.map { if (it.nombre == chequeo.nombre) chequeo else it } }
    }

    private companion object {
        const val TOPE_MS = 15_000L
        const val AUTH = "Auth"
        const val ROL = "Rol (custom claim)"
        const val FIRESTORE = "Firestore y reglas"
        const val FCM = "Token FCM"
        const val PUSH = "Functions + push"
    }
}
