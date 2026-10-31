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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.sensorpark.core.ui.components.PrimaryButton
import com.sensorpark.core.ui.components.ReservationStatusBadge
import com.sensorpark.domain.model.ReservationStatus
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
import com.sensorpark.domain.model.WaitlistPosition
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
    /** El lugar que se tocó en el plano del estacionamiento, si se llegó desde ahí. */
    spot: PickedSpot? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(parkingLotId, spot) { vm.load(parkingLotId, spot) }

    val confirmation = state.confirmation
    val failure = state.failure
    when {
        state.loading -> LoadingContent()
        state.parkingLot == null && failure != null -> ErrorContent(failure, onRetry = { vm.load(parkingLotId, spot) })
        confirmation != null -> Confirmation(state, confirmation, onOpenMyReservations)
        else -> Form(state, vm, onOpenMyReservations)
    }
}

@Composable
private fun Form(state: ReserveState, vm: ReserveViewModel, onOpenMyReservations: () -> Unit) {
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

        Text(
            text = stringResource(R.string.reserve_when),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
        OutlinedButton(
            onClick = { pickingDate = true },
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.reserve_date, state.form.date.dayOfMonth, state.form.date.monthValue),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
        OutlinedButton(
            onClick = { pickingTime = true },
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.reserve_time, formatTime(state.arrivesAt)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
            }
        }

        Text(
            text = stringResource(R.string.reserve_duration),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
        Chips(
            options = state.durations,
            selected = state.form.durationMinutes,
            label = { formatDurationMinutes(it) },
            onSelect = vm::setDuration,
        )
        Text(
            text = stringResource(R.string.reserve_slot, formatTime(state.arrivesAt), formatTime(state.endsAt)),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )

        val picked = state.picked
        if (picked != null) {
            // Llegó desde el plano con un lugar elegido: se reserva ese, no un tipo.
            Text(
                text = stringResource(R.string.reserve_picked_spot),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.reserve_picked_spot_value, picked.code, picked.type.label()),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                TextButton(onClick = vm::clearPickedSpot) { Text(stringResource(R.string.reserve_change_spot)) }
            }
        } else {
            Text(
                text = stringResource(R.string.reserve_spot_type),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Chips(
                options = state.spotTypes,
                selected = state.form.spotType,
                label = { it.label() },
                onSelect = vm::setSpotType,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AvailabilityBadge(free = state.freeNow, capacity = state.capacity)
                Text(
                    text = stringResource(R.string.reserve_free_now_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.reserve_estimated_amount, formatAmount(state.estimatedAmount)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        PrimaryButton(
            text = stringResource(if (state.working) R.string.reserve_working else R.string.reserve_confirm),
            onClick = vm::confirm,
            enabled = state.parkingLot != null,
            loading = state.working,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            },
        )

        state.rejection?.let {
            Problem(
                message = it.message(),
                alternatives = state.alternatives,
                onDismiss = vm::dismissError,
                waitlistAction = if (!state.canJoinWaitlist) null else {
                    {
                        PrimaryButton(
                            text = stringResource(R.string.reserve_join_waitlist),
                            onClick = vm::joinWaitlist,
                            enabled = !state.working,
                        )
                    }
                },
            )
        }
        state.waitlist?.let { Joined(it, onOpenMyReservations) }
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

/** Anotado en la lista de espera: en qué lugar de la fila quedó y qué va a pasar. */
@Composable
private fun Joined(waitlist: WaitlistPosition, onOpenMyReservations: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.reserve_waitlist_joined_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(R.string.reserve_waitlist_joined_position, waitlist.position),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.reserve_waitlist_joined_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenMyReservations,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.reserve_go_to_my_reservations),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

/** Un error con, si hay, qué probar en vez de quedarse sin nada. */
@Composable
private fun Problem(
    message: String,
    alternatives: List<ReserveAlternative>,
    onDismiss: () -> Unit,
    waitlistAction: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            alternatives.forEach { alternative ->
                Text(
                    text = "· " + stringResource(
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            waitlistAction?.invoke()
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ReservationConfirmedContent(
            parkingLotName = state.parkingLot?.name,
            parkingLotAddress = state.parkingLot?.address,
            confirmation = confirmation,
            onOpenMyReservations = onOpenMyReservations,
        )
    }
}

@Composable
internal fun ReservationConfirmedContent(
    parkingLotName: String?,
    parkingLotAddress: String?,
    confirmation: ReservationConfirmation,
    onOpenMyReservations: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.reserve_done_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        ReservationStatusBadge(ReservationStatus.CONFIRMED)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!parkingLotName.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = parkingLotName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!parkingLotAddress.isNullOrBlank()) {
                        Text(
                            text = parkingLotAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.reserve_done_spot, confirmation.spotCode),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(
                        R.string.reserve_done_slot,
                        formatTime(confirmation.arrivesAt),
                        formatTime(confirmation.endsAt),
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.reserve_done_tolerance, formatTime(confirmation.toleranceUntil)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }

    PrimaryButton(
        text = stringResource(R.string.reserve_go_to_my_reservations),
        onClick = onOpenMyReservations,
    )
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
