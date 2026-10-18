package com.sensorpark.feature.reservations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.AvailabilityBadge
import com.sensorpark.core.ui.components.ErrorContent
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.formatAmount
import com.sensorpark.core.ui.components.formatDurationMinutes
import com.sensorpark.core.ui.components.formatTime
import com.sensorpark.core.ui.components.label
import com.sensorpark.core.ui.components.message
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.reservation.ReservationRules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Pantalla "Reservar" (CU-04). Se llega desde el detalle de un estacionamiento. Al confirmar, la
 * reserva aparece en "Mis reservas" y el teléfono dispara el aviso, el recordatorio y la geocerca.
 */
@Composable
fun ReserveScreen(
    parkingLotId: String,
    onOpenMyReservations: () -> Unit,
    vm: ReserveViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(parkingLotId) { vm.load(parkingLotId) }

    val confirmation = state.confirmation
    val failure = state.failure
    when {
        state.loading -> LoadingContent()
        state.parkingLot == null && failure != null -> ErrorContent(failure, onRetry = { vm.load(parkingLotId) })
        confirmation != null -> Confirmation(state, confirmation, onOpenMyReservations)
        else -> Form(state, vm)
    }
}

@Composable
private fun Form(state: ReserveState, vm: ReserveViewModel) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.reserve_title), style = MaterialTheme.typography.headlineMedium)
        state.parkingLot?.let { lot ->
            Text(lot.name, style = MaterialTheme.typography.titleMedium)
            Text(lot.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HorizontalDivider()

        Text(stringResource(R.string.reserve_when), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reserve_date, state.form.date.dayOfMonth, state.form.date.monthValue))
        }
        OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reserve_time, formatTime(state.arrivesAt)))
        }

        Text(stringResource(R.string.reserve_duration), style = MaterialTheme.typography.titleSmall)
        Chips(
            options = state.durations,
            selected = state.form.durationMinutes,
            label = { formatDurationMinutes(it) },
            onSelect = vm::setDuration,
        )
        Text(stringResource(R.string.reserve_slot, formatTime(state.arrivesAt), formatTime(state.endsAt)))

        Text(stringResource(R.string.reserve_spot_type), style = MaterialTheme.typography.titleSmall)
        Chips(
            options = state.spotTypes,
            selected = state.form.spotType,
            label = { it.label() },
            onSelect = vm::setSpotType,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AvailabilityBadge(free = state.freeNow, capacity = state.capacity)
                Text(stringResource(R.string.reserve_free_now_hint))
                Text(stringResource(R.string.reserve_estimated_amount, formatAmount(state.estimatedAmount)))
            }
        }

        Button(
            onClick = vm::confirm,
            enabled = !state.working && state.parkingLot != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (state.working) R.string.reserve_working else R.string.reserve_confirm))
        }

        state.rejection?.let { Problem(it.message(), state.alternatives, vm::dismissError) }
        if (state.parkingLot != null) failureMessage(state)?.let { Problem(it, emptyList(), vm::dismissError) }
    }

    if (pickingDate) {
        DatePickerSheet(
            selected = state.form.date,
            onDismiss = { pickingDate = false },
            onSelect = {
                vm.setDate(it)
                pickingDate = false
            },
        )
    }
    if (pickingTime) {
        TimePickerSheet(
            selected = state.form.time,
            onDismiss = { pickingTime = false },
            onSelect = {
                vm.setTime(it)
                pickingTime = false
            },
        )
    }
}

@Composable
private fun failureMessage(state: ReserveState): String? = state.failure?.message()

/** Un error con, si hay, qué probar en vez de quedarse sin nada. */
@Composable
private fun Problem(message: String, alternatives: List<ReserveAlternative>, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message)
            alternatives.forEach { alternative ->
                Text(
                    "· " + stringResource(
                        when (alternative) {
                            is ReserveAlternative.ShorterDuration -> R.string.reserve_alternative_shorter
                            ReserveAlternative.AnotherTime -> R.string.reserve_alternative_another_time
                            ReserveAlternative.AnotherParkingLot -> R.string.reserve_alternative_another_lot
                        },
                        *when (alternative) {
                            is ReserveAlternative.ShorterDuration ->
                                arrayOf<Any>(formatDurationMinutes(alternative.minutes))
                            else -> emptyArray()
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reserve_dismiss)) }
        }
    }
}

@Composable
private fun Confirmation(
    state: ReserveState,
    confirmation: ReservationConfirmation,
    onOpenMyReservations: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.reserve_done_title), style = MaterialTheme.typography.headlineMedium)
        state.parkingLot?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
        Text(stringResource(R.string.reserve_done_spot, confirmation.spotCode))
        Text(
            stringResource(
                R.string.reserve_done_slot,
                formatTime(confirmation.arrivesAt),
                formatTime(confirmation.endsAt),
            ),
        )
        Text(stringResource(R.string.reserve_done_tolerance, formatTime(confirmation.toleranceUntil)))

        Button(onClick = onOpenMyReservations, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reserve_go_to_my_reservations))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(selected: LocalDate, onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now(ReservationRules.ZONE)
    val lastDay = today.plusDays(ReservationRules.MAX_ADVANCE.toDays())
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        // Sólo los días en los que se puede reservar: de hoy hasta una semana.
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return !day.isBefore(today) && !day.isAfter(lastDay)
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let {
                        onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text(stringResource(R.string.reserve_accept)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reserve_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(selected: LocalTime, onDismiss: () -> Unit, onSelect: (LocalTime) -> Unit) {
    val pickerState = rememberTimePickerState(
        initialHour = selected.hour,
        initialMinute = selected.minute,
        // Hora de 24 h, como corresponde al formato argentino.
        is24Hour = true,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSelect(LocalTime.of(pickerState.hour, pickerState.minute)) },
            ) { Text(stringResource(R.string.reserve_accept)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reserve_cancel)) } },
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            TimePicker(state = pickerState)
        }
    }
}
