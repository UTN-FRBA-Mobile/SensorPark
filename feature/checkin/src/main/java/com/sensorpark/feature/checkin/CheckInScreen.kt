package com.sensorpark.feature.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.formatAmount
import com.sensorpark.domain.model.CheckOutSummary
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.reservation.ReservationRules
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ReservationRules.ZONE)
private val DAY = DateTimeFormatter.ofPattern("dd/MM").withZone(ReservationRules.ZONE)

/**
 * Check-in y check-out de una reserva (CU-06 y CU-08). Se abre desde la reserva activa en "Mis
 * reservas" o desde el aviso de llegada. Según el estado de la reserva muestra el check-in, la
 * estadía en curso o el resumen final.
 */
@Composable
fun CheckInScreen(reservationId: String, vm: CheckInViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(reservationId) { vm.load(reservationId) }

    // La tolerancia se muestra como cuenta regresiva: hace falta una hora que avance.
    var now by remember { mutableStateOf(vm.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = vm.now()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            state.loading -> Loading()
            state.notFound -> Text(stringResource(R.string.check_in_not_found))
            state.failure != null -> Text(stringResource(R.string.check_in_failure))
            else -> {
                val reservation = state.reservation
                val lot = state.parkingLot
                if (reservation == null) {
                    Text(stringResource(R.string.check_in_not_found))
                } else {
                    Details(reservation, lot)
                    HorizontalDivider()
                    val summary = state.summary
                    when {
                        summary != null -> Summary(summary, reservation)
                        state.isInUse -> Stay(state, reservation, onCheckOut = vm::checkOut)
                        else -> CheckIn(
                            state = state,
                            now = now,
                            onCheckIn = vm::checkIn,
                            onRequestAnotherSpot = vm::requestAnotherSpot,
                        )
                    }
                    state.message?.let { Message(it, onDismiss = vm::dismissMessage) }
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Details(reservation: Reservation, lot: ParkingLot?) {
    Text(stringResource(R.string.check_in_title), style = MaterialTheme.typography.headlineMedium)
    Text(lot?.name ?: stringResource(R.string.check_in_unknown_lot), style = MaterialTheme.typography.titleMedium)
    lot?.address?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text(stringResource(R.string.check_in_spot, reservation.spotCode))
    Text(
        stringResource(
            R.string.check_in_slot,
            TIME.format(reservation.arrivesAt),
            TIME.format(reservation.endsAt),
        ),
    )
}

@Composable
private fun CheckIn(
    state: CheckInState,
    now: Instant,
    onCheckIn: () -> Unit,
    onRequestAnotherSpot: () -> Unit,
) {
    val left = state.toleranceLeft(now)
    val opensAt = state.checkInOpensAt
    if (opensAt != null && state.isTooEarly(now)) {
        // Sin esto, el botón aparece gris y "te quedan 11 h" se lee como si ya se pudiera.
        Text(
            if (DAY.format(opensAt) == DAY.format(now)) {
                stringResource(R.string.check_in_too_early_today, TIME.format(opensAt))
            } else {
                stringResource(R.string.check_in_too_early_other_day, DAY.format(opensAt), TIME.format(opensAt))
            },
        )
    } else if (left.isZero) {
        Text(stringResource(R.string.check_in_tolerance_over), color = MaterialTheme.colorScheme.error)
    } else {
        Text(stringResource(R.string.check_in_tolerance_left, format(left)))
    }

    if (state.spotTaken) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.check_in_spot_taken_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.check_in_spot_taken_body))
                Button(
                    onClick = onRequestAnotherSpot,
                    enabled = !state.working,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.check_in_request_another_spot))
                }
            }
        }
    }

    Button(
        onClick = onCheckIn,
        enabled = state.canCheckIn(now),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(if (state.working) R.string.check_in_working else R.string.check_in_action))
    }
    Text(
        stringResource(R.string.check_in_location_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Stay(state: CheckInState, reservation: Reservation, onCheckOut: () -> Unit) {
    Text(stringResource(R.string.check_in_done_title), style = MaterialTheme.typography.titleMedium)
    Text(
        if (state.spotConfirmed) {
            stringResource(R.string.check_in_spot_confirmed)
        } else {
            stringResource(R.string.check_in_spot_waiting)
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.check_out_until, TIME.format(reservation.endsAt)))

    Button(onClick = onCheckOut, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.working) R.string.check_in_working else R.string.check_out_action))
    }
}

@Composable
private fun Summary(summary: CheckOutSummary, reservation: Reservation) {
    Text(stringResource(R.string.check_out_summary_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.check_out_summary_stay, summary.stayMinutes, reservation.durationMinutes))
    // Formato argentino: "$2.500".
    Text(stringResource(R.string.check_out_summary_amount, formatAmount(summary.amount)))
    if (summary.stayMinutes > reservation.durationMinutes) {
        Text(stringResource(R.string.check_out_summary_overstay), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Message(message: CheckInMessage, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(message.text()))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.check_in_dismiss)) }
        }
    }
}

private fun CheckInMessage.text(): Int = when (this) {
    CheckInMessage.TOO_FAR -> R.string.check_in_error_too_far
    CheckInMessage.TOO_FAR_LOW_ACCURACY -> R.string.check_in_error_too_far_low_accuracy
    CheckInMessage.TOO_EARLY -> R.string.check_in_error_too_early
    CheckInMessage.EXPIRED -> R.string.check_in_error_expired
    CheckInMessage.NO_LOCATION_PERMISSION -> R.string.check_in_error_no_permission
    CheckInMessage.NO_LOCATION -> R.string.check_in_error_no_location
    CheckInMessage.NO_CONNECTION -> R.string.check_in_error_no_connection
    CheckInMessage.UNKNOWN_ERROR -> R.string.check_in_error_unknown
    CheckInMessage.SPOT_TAKEN -> R.string.check_in_spot_taken_body
    CheckInMessage.NO_OTHER_SPOT -> R.string.check_in_no_other_spot
    CheckInMessage.SPOT_REASSIGNED -> R.string.check_in_spot_reassigned
}

/** "12 min" o "1 h 05 min". */
private fun format(left: Duration): String {
    val hours = left.toHours()
    val minutes = left.toMinutes() % 60
    return if (hours > 0) "$hours h ${minutes.toString().padStart(2, '0')} min" else "$minutes min"
}
