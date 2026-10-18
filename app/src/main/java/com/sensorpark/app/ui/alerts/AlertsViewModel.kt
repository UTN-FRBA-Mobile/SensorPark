package com.sensorpark.app.ui.alerts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.data.notifications.InAppNotifications
import com.sensorpark.data.preferences.PermissionPromptStore
import com.sensorpark.domain.model.AppNotification
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.repository.GeofenceRepository
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.ReservationRepository
import com.sensorpark.domain.usecase.SyncReservationAlertsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lo que la app hace por su cuenta a partir de las reservas, mientras está abierta:
 *
 * - mantiene al día los avisos, el recordatorio y la geocerca de cada reserva (T20);
 * - pide los permisos de avisos y ubicación después de la primera reserva, no al iniciar sesión;
 * - muestra como banner los avisos que no pudieron salir porque falta el permiso.
 */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val reservations: ReservationRepository,
    private val parkingLots: ParkingLotRepository,
    private val syncAlerts: SyncReservationAlertsUseCase,
    private val geofences: GeofenceRepository,
    private val permissionPrompts: PermissionPromptStore,
    inAppNotifications: InAppNotifications,
) : ViewModel() {

    init {
        // Por si el permiso de segundo plano se dio desde los ajustes, con la app cerrada.
        registerPendingGeofences()
    }

    val banners: Flow<AppNotification> = inAppNotifications.messages

    private val hasReservations: StateFlow<Boolean> = reservations.myReservations()
        // Con cada cambio de las reservas se reconcilian geocercas, recordatorios y avisos.
        .onEach { sync(it) }
        .map { (it as? DataResult.Success)?.value?.isNotEmpty() == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Cuándo mostrar la explicación y pedir los permisos: con la primera reserva, una sola vez. */
    val shouldAskPermissions: StateFlow<Boolean> =
        combine(hasReservations, permissionPrompts.askedAfterFirstReservation) { has, asked -> has && !asked }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onPermissionsAsked() {
        viewModelScope.launch {
            runCatching { permissionPrompts.markAskedAfterFirstReservation() }
                .onFailure { Log.w(TAG, "No se pudo recordar que ya se pidieron los permisos", it) }
        }
        registerPendingGeofences()
    }

    /**
     * Los permisos se piden después de la primera reserva, así que su geocerca se guardó cuando
     * todavía no se podía registrar en el sistema. Sin esto, la primera reserva (justo la de la
     * demo) nunca tendría aviso de llegada. Volver a registrarlas es idempotente: las que ya
     * estaban se reemplazan y las vencidas se borran.
     */
    private fun registerPendingGeofences() {
        viewModelScope.launch {
            if (!geofences.hasBackgroundLocationPermission()) return@launch
            runCatching { geofences.restoreAll() }
                .onFailure { Log.w(TAG, "No se pudieron registrar las geocercas pendientes", it) }
        }
    }

    private suspend fun sync(result: DataResult<List<com.sensorpark.domain.model.Reservation>>) {
        val mine = (result as? DataResult.Success)?.value ?: return
        runCatching {
            val lots = (parkingLots.parkingLots().first() as? DataResult.Success)?.value.orEmpty()
            syncAlerts.sync(mine, lots.associateBy { it.id })
        }.onFailure { Log.w(TAG, "No se pudieron poner al día los avisos de las reservas", it) }
    }

    private companion object {
        const val TAG = "SensorParkAlerts"
    }
}
