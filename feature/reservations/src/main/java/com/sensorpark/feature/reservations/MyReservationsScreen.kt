package com.sensorpark.feature.reservations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sensorpark.core.ui.components.ErrorContent
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.formatDateAndTime
import com.sensorpark.core.ui.components.formatDuration
import com.sensorpark.core.ui.components.formatTime
import com.sensorpark.core.ui.components.label
import com.sensorpark.core.ui.components.message
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationStatus
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Pestaña "Mis reservas" (CU-09): arriba la reserva que está pasando con sus acciones, después las
 * próximas y el historial. El deep link `sensorpark://reservation/{id}` la abre destacando una.
 *
 * @param reservationId la reserva a destacar cuando se llega por el deep link.
 */
@Composable
fun MyReservationsScreen(
    reservationId: String?,
    onCheckIn: (reservationId: String) -> Unit,
    vm: MyReservationsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var cancelling by remember { mutableStateOf<Reservation?>(null) }

    // Las cuentas regresivas necesitan una hora que avance.
    var now by remember { mutableStateOf(vm.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = vm.now()
        }
    }

    val failure = state.failure
    when {
        state.loading -> LoadingContent()
        failure != null && state.active == null && state.history.isEmpty() ->
            ErrorContent(failure, onRetry = vm::dismissError)
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.my_reservations_title), style = MaterialTheme.typography.headlineMedium)

            if (state.isEmpty) {
                Text(
                    stringResource(R.string.my_reservations_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.active?.let { card ->
                Text(stringResource(R.string.my_reservations_active), style = MaterialTheme.typography.titleSmall)
                ActiveCard(
                    card = card,
                    now = now,
                    working = state.working,
                    highlighted = card.reservation.id == reservationId,
                    onCheckIn = { onCheckIn(card.reservation.id) },
                    onCancel = { cancelling = card.reservation },
                )
            }

            if (state.upcoming.isNotEmpty()) {
                Text(stringResource(R.string.my_reservations_upcoming), style = MaterialTheme.typography.titleSmall)
                state.upcoming.forEach { card ->
                    ReservationRow(
                        card = card,
                        highlighted = card.reservation.id == reservationId,
                        action = {
                            TextButton(
                                onClick = { cancelling = card.reservation },
                                enabled = !state.working,
                            ) { Text(stringResource(R.string.my_reservations_cancel)) }
                        },
                    )
                }
            }

            if (state.history.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.my_reservations_history), style = MaterialTheme.typography.titleSmall)
                state.history.forEach { card ->
                    ReservationRow(card = card, highlighted = card.reservation.id == reservationId)
                }
            }

            state.rejection?.let { Problem(it.message(), vm::dismissError) }
            if (state.active != null || state.history.isNotEmpty()) {
                failure?.let { Problem(it.message(), vm::dismissError) }
            }
        }
    }

    cancelling?.let { reservation ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text(stringResource(R.string.my_reservations_cancel_title)) },
            text = { Text(stringResource(R.string.my_reservations_cancel_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.cancel(reservation)
                        cancelling = null
                    },
                ) { Text(stringResource(R.string.my_reservations_cancel_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { cancelling = null }) {
                    Text(stringResource(R.string.my_reservations_cancel_keep))
                }
            },
        )
    }
}

@Composable
private fun ActiveCard(
    card: ReservationCard,
    now: Instant,
    working: Boolean,
    highlighted: Boolean,
    onCheckIn: () -> Unit,
    onCancel: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Header(card, highlighted)
            val reservation = card.reservation
            if (reservation.status == ReservationStatus.IN_USE) {
                Text(stringResource(R.string.my_reservations_time_left, formatDuration(card.timeLeft(now))))
                Button(onClick = onCheckIn, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.my_reservations_check_out))
                }
            } else {
                val left = card.toleranceLeft(now)
                if (left.isZero) {
                    Text(
                        stringResource(R.string.my_reservations_tolerance_over),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(stringResource(R.string.my_reservations_tolerance_left, formatDuration(left)))
                }
                Button(onClick = onCheckIn, enabled = !working && !left.isZero, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.my_reservations_check_in))
                }
                OutlinedButton(onClick = onCancel, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.my_reservations_cancel))
                }
            }
        }
    }
}

@Composable
private fun ReservationRow(card: ReservationCard, highlighted: Boolean, action: @Composable () -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Header(card, highlighted)
            action()
        }
    }
}

@Composable
private fun Header(card: ReservationCard, highlighted: Boolean) {
    val reservation = card.reservation
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(card.parkingLotName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(reservation.status.label(), style = MaterialTheme.typography.labelLarge)
    }
    Text(
        stringResource(
            R.string.my_reservations_spot_and_slot,
            reservation.spotCode,
            formatDateAndTime(reservation.arrivesAt),
            formatTime(reservation.endsAt),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (highlighted) {
        Text(
            stringResource(R.string.my_reservations_highlighted),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Problem(message: String, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.my_reservations_dismiss)) }
        }
    }
}
