package com.sensorpark.app.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.sensorpark.data.firebase.Collections
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.NearbyParkingLot
import com.sensorpark.domain.usecase.SearchNearbyParkingLotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

enum class CheckStatus { RUNNING, OK, ERROR, NOT_RUN }

data class Check(val name: String, val status: CheckStatus, val detail: String = "")

/**
 * Verifica contra el proyecto real cada pieza de la configuración de Firebase.
 * Es una herramienta de puesta en marcha: se puede borrar cuando la app tenga pantallas reales.
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
    private val functions: FirebaseFunctions,
    searchNearby: SearchNearbyParkingLotsUseCase,
) : ViewModel() {
    private val _checks = MutableStateFlow(emptyList<Check>())
    val checks: StateFlow<List<Check>> = _checks.asStateFlow()

    /** Estacionamientos cerca del Obelisco, en vivo: sirve para ver moverse los contadores. */
    val parkingLots: StateFlow<DataResult<List<NearbyParkingLot>>?> =
        searchNearby(OBELISK, radiusMeters = 10_000.0)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        runChecks()
    }

    fun runChecks() {
        _checks.value = listOf(AUTH, ROLE, FIRESTORE, FCM).map { Check(it, CheckStatus.RUNNING) } +
            Check(PUSH, CheckStatus.NOT_RUN, "Tocá «Enviar push de prueba».") +
            Check(SEED, CheckStatus.NOT_RUN, "Sólo un ADMIN: carga o restablece los estacionamientos de la semilla.")
        viewModelScope.launch {
            check(AUTH) {
                val user = auth.currentUser ?: error("No hay sesión iniciada.")
                "${user.email} · uid ${user.uid.take(6)}…"
            }
        }
        viewModelScope.launch {
            check(ROLE) {
                val claims = auth.currentUser!!.getIdToken(true).await().claims
                claims["role"]?.toString()
                    ?: error("El token no trae el claim `role`: ¿está desplegada la function assignInitialRole?")
            }
        }
        viewModelScope.launch {
            check(FIRESTORE) {
                val doc = db.collection(Collections.USERS).document(auth.currentUser!!.uid)
                doc.set(mapOf("diagnosticsAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
                val saved = doc.get(Source.SERVER).await()
                "Escritura y lectura en users/ a las ${saved.getTimestamp("diagnosticsAt")?.toDate()}"
            }
        }
        viewModelScope.launch {
            check(FCM) { "Token …${messaging.token.await().takeLast(8)}" }
        }
    }

    fun sendTestPush() {
        update(Check(PUSH, CheckStatus.RUNNING))
        viewModelScope.launch {
            check(PUSH) {
                functions.getHttpsCallable("sendTestPush").call().await()
                "Enviada: tiene que aparecer una notificación."
            }
        }
    }

    /**
     * Carga (o restablece) la semilla con la function `loadSeed`: es el paso de T23 para tener
     * estacionamientos en el proyecto real. Sólo la acepta un ADMIN; deja todos los lugares libres.
     */
    fun loadSeed() {
        update(Check(SEED, CheckStatus.RUNNING))
        viewModelScope.launch {
            check(SEED) {
                val result = functions.getHttpsCallable("loadSeed").call().await().getData() as? Map<*, *>
                "Cargados ${result?.get("parkingLots")} estacionamientos y ${result?.get("spots")} lugares."
            }
        }
    }

    private suspend fun check(name: String, test: suspend () -> String) {
        val result = try {
            Check(name, CheckStatus.OK, withTimeout(TIMEOUT_MS) { test() })
        } catch (e: Exception) {
            Check(name, CheckStatus.ERROR, e.message ?: e.javaClass.simpleName)
        }
        update(result)
    }

    private fun update(check: Check) {
        _checks.update { list -> list.map { if (it.name == check.name) check else it } }
    }

    private companion object {
        val OBELISK = Coordinates(-34.6037, -58.3816)
        const val TIMEOUT_MS = 15_000L
        const val AUTH = "Auth"
        const val ROLE = "Rol (custom claim)"
        const val FIRESTORE = "Firestore y reglas"
        const val FCM = "Token FCM"
        const val PUSH = "Functions + push"
        const val SEED = "Datos semilla"
    }
}
