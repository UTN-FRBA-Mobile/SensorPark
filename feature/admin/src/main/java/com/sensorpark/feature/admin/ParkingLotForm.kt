package com.sensorpark.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.PrimaryButton
import com.sensorpark.core.ui.components.message
import com.sensorpark.domain.model.ParkingLotDraft
import com.sensorpark.domain.model.ParkingLotField

/**
 * Alta de un estacionamiento (T22). El que lo crea queda como su único administrador; después se
 * le agregan los lugares desde su panel.
 */
@Composable
fun CreateParkingLotScreen(
    onCreated: (parkingLotId: String) -> Unit,
    vm: ParkingLotFormViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) { state.savedId?.let(onCreated) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            stringResource(R.string.admin_create_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        ParkingLotForm(state, onEdit = vm::edit, onSave = vm::save, saveLabel = R.string.admin_form_create)
    }
}

/** La solapa "Datos" del panel: el mismo formulario, con los datos del estacionamiento cargados. */
@Composable
internal fun ParkingLotDetailsTab(parkingLotId: String, vm: ParkingLotFormViewModel = hiltViewModel()) {
    LaunchedEffect(parkingLotId) { vm.load(parkingLotId) }
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.loading) {
        LoadingContent()
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ParkingLotForm(state, onEdit = vm::edit, onSave = vm::save, saveLabel = R.string.admin_form_save)
    }
}

@Composable
internal fun ParkingLotForm(
    state: ParkingLotFormState,
    onEdit: ((ParkingLotInput) -> ParkingLotInput) -> Unit,
    onSave: () -> Unit,
    saveLabel: Int,
) {
    val input = state.input
    val problems = state.problems
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(R.string.admin_form_name, input.name, ParkingLotField.NAME in problems, R.string.admin_form_name_error) {
            onEdit { i -> i.copy(name = it) }
        }
        Field(R.string.admin_form_address, input.address, ParkingLotField.ADDRESS in problems, R.string.admin_form_address_error) {
            onEdit { i -> i.copy(address = it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val bad = ParkingLotField.LOCATION in problems
            Field(R.string.admin_form_latitude, input.latitude, bad, R.string.admin_form_location_error, KeyboardType.Decimal, Modifier.weight(1f)) {
                onEdit { i -> i.copy(latitude = it) }
            }
            Field(R.string.admin_form_longitude, input.longitude, bad, null, KeyboardType.Decimal, Modifier.weight(1f)) {
                onEdit { i -> i.copy(longitude = it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val bad = ParkingLotField.SCHEDULE in problems
            Field(R.string.admin_form_opens_at, input.opensAt, bad, R.string.admin_form_schedule_error, KeyboardType.Number, Modifier.weight(1f)) {
                onEdit { i -> i.copy(opensAt = it) }
            }
            Field(R.string.admin_form_closes_at, input.closesAt, bad, null, KeyboardType.Number, Modifier.weight(1f)) {
                onEdit { i -> i.copy(closesAt = it) }
            }
        }
        Text(
            stringResource(R.string.admin_form_schedule_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Field(
            R.string.admin_form_tolerance, input.toleranceMinutes, ParkingLotField.TOLERANCE in problems,
            R.string.admin_form_tolerance_error, KeyboardType.Number,
            errorArgs = arrayOf(ParkingLotDraft.TOLERANCE_RANGE.first, ParkingLotDraft.TOLERANCE_RANGE.last),
        ) { onEdit { i -> i.copy(toleranceMinutes = it) } }
        Field(
            R.string.admin_form_radius, input.geofenceRadiusMeters, ParkingLotField.GEOFENCE_RADIUS in problems,
            R.string.admin_form_radius_error, KeyboardType.Number,
            errorArgs = arrayOf(ParkingLotDraft.RADIUS_RANGE.first, ParkingLotDraft.RADIUS_RANGE.last),
        ) { onEdit { i -> i.copy(geofenceRadiusMeters = it) } }
        Field(
            R.string.admin_form_rate, input.hourlyRate, ParkingLotField.HOURLY_RATE in problems,
            R.string.admin_form_rate_error, KeyboardType.Number,
        ) { onEdit { i -> i.copy(hourlyRate = it) } }

        state.failure?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
        if (state.saved) {
            Text(stringResource(R.string.admin_form_saved), color = MaterialTheme.colorScheme.primary)
        }
        PrimaryButton(
            text = stringResource(if (state.saving) R.string.admin_form_saving else saveLabel),
            onClick = onSave,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun Field(
    label: Int,
    value: String,
    isError: Boolean,
    error: Int?,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    errorArgs: Array<Any> = emptyArray(),
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        supportingText = if (isError && error != null) {
            { Text(stringResource(error, *errorArgs)) }
        } else {
            null
        },
        modifier = modifier,
    )
}
