package com.sensorpark.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLotDraft
import com.sensorpark.domain.model.ParkingLotField
import com.sensorpark.domain.repository.ParkingLotAdminRepository
import com.sensorpark.domain.repository.ParkingLotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeParseException
import javax.inject.Inject

/** Lo que escribió el administrador, tal cual: se convierte a [ParkingLotDraft] al validar. */
data class ParkingLotInput(
    val name: String = "",
    val address: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val opensAt: String = "",
    val closesAt: String = "",
    val toleranceMinutes: String = "15",
    val geofenceRadiusMeters: String = "300",
    val hourlyRate: String = "0",
) {
    fun toDraft() = ParkingLotDraft(
        name = name,
        address = address,
        latitude = latitude.toCoordinate(),
        longitude = longitude.toCoordinate(),
        opensAt = opensAt.toTime(),
        closesAt = closesAt.toTime(),
        toleranceMinutes = toleranceMinutes.toIntOrNull() ?: -1,
        geofenceRadiusMeters = geofenceRadiusMeters.toIntOrNull() ?: -1,
        hourlyRate = hourlyRate.toIntOrNull() ?: -1,
    )

    /** Los problemas del borrador, más una hora mal escrita, que el borrador ve como "sin horario". */
    fun problems(): Set<ParkingLotField> {
        val badTime = (opensAt.isNotBlank() && opensAt.toTime() == null) || (closesAt.isNotBlank() && closesAt.toTime() == null)
        return toDraft().problems() + if (badTime) setOf(ParkingLotField.SCHEDULE) else emptySet()
    }

    companion object {
        fun from(draft: ParkingLotDraft) = ParkingLotInput(
            name = draft.name,
            address = draft.address,
            latitude = draft.latitude?.toString().orEmpty(),
            longitude = draft.longitude?.toString().orEmpty(),
            opensAt = draft.opensAt?.toString().orEmpty(),
            closesAt = draft.closesAt?.toString().orEmpty(),
            toleranceMinutes = draft.toleranceMinutes.toString(),
            geofenceRadiusMeters = draft.geofenceRadiusMeters.toString(),
            hourlyRate = draft.hourlyRate.toString(),
        )
    }
}

// Se acepta coma decimal, como se escribe en Argentina.
private fun String.toCoordinate(): Double? = trim().replace(',', '.').toDoubleOrNull()

private fun String.toTime(): LocalTime? =
    if (isBlank()) null else try { LocalTime.parse(trim().padStart(5, '0')) } catch (e: DateTimeParseException) { null }

data class ParkingLotFormState(
    val input: ParkingLotInput = ParkingLotInput(),
    /** Los errores se muestran recién después del primer intento de guardar. */
    val showErrors: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val failure: DataErrorReason? = null,
    /** El id del estacionamiento guardado: al crear, la pantalla pasa a su panel. */
    val savedId: String? = null,
    val saved: Boolean = false,
) {
    val problems: Set<ParkingLotField> get() = if (showErrors) input.problems() else emptySet()
}

/**
 * El formulario de los datos de un estacionamiento (T22): el alta, desde "Crear", y la solapa
 * "Datos" del panel. Valida en el momento con las reglas del dominio; lo que no puede ver, como
 * los permisos, lo decide firestore.rules.
 */
@HiltViewModel
class ParkingLotFormViewModel @Inject constructor(
    private val admin: ParkingLotAdminRepository,
    private val parkingLots: ParkingLotRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParkingLotFormState())
    val state: StateFlow<ParkingLotFormState> = _state.asStateFlow()

    private var editingId: String? = null

    /** Con un id edita ese estacionamiento; sin id es un alta. */
    fun load(parkingLotId: String?) {
        if (parkingLotId == null || parkingLotId == editingId) return
        editingId = parkingLotId
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val result = parkingLots.parkingLot(parkingLotId).first()
            _state.update {
                when (result) {
                    is DataResult.Success -> it.copy(
                        loading = false,
                        input = ParkingLotInput.from(ParkingLotDraft.from(result.value)),
                    )
                    is DataResult.Failure -> it.copy(loading = false, failure = result.reason)
                }
            }
        }
    }

    fun edit(change: (ParkingLotInput) -> ParkingLotInput) =
        _state.update { it.copy(input = change(it.input), saved = false) }

    fun dismissError() = _state.update { it.copy(failure = null) }

    fun save() {
        val current = _state.value
        if (current.saving) return
        if (current.input.problems().isNotEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val draft = current.input.toDraft()
        viewModelScope.launch {
            _state.update { it.copy(saving = true, showErrors = true, failure = null) }
            val id = editingId
            val result = if (id == null) admin.create(draft) else admin.update(id, draft).let { r ->
                if (r is DataResult.Success) DataResult.Success(id) else r as DataResult.Failure
            }
            _state.update {
                when (result) {
                    is DataResult.Success -> it.copy(saving = false, savedId = result.value, saved = true)
                    is DataResult.Failure -> it.copy(saving = false, failure = result.reason)
                }
            }
        }
    }
}
