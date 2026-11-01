package com.sensorpark.feature.checkin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.PrimaryButton
import com.sensorpark.core.ui.components.ReservationStatusBadge
import com.sensorpark.core.ui.components.formatAmount
import com.sensorpark.core.ui.theme.SensorParkTheme
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

    if (state.loading) {
        LoadingContent()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            state.notFound -> NoticeCard(stringResource(R.string.check_in_not_found))
            state.failure != null -> NoticeCard(stringResource(R.string.check_in_failure))
            else -> {
                val reservation = state.reservation
                val lot = state.parkingLot
                if (reservation == null) {
                    NoticeCard(stringResource(R.string.check_in_not_found))
                } else {
                    Details(reservation, lot)
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
private fun NoticeCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun Details(reservation: Reservation, lot: ParkingLot?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.check_in_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        ReservationStatusBadge(reservation.status)
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = lot?.name ?: stringResource(R.string.check_in_unknown_lot),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                lot?.address?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

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
                    text = stringResource(R.string.check_in_spot, reservation.spotCode),
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
                        R.string.check_in_slot,
                        TIME.format(reservation.arrivesAt),
                        TIME.format(reservation.endsAt),
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (opensAt != null && state.isTooEarly(now)) {
                // Sin esto, el botón aparece gris y "te quedan 11 h" se lee como si ya se pudiera.
                Text(
                    text = if (DAY.format(opensAt) == DAY.format(now)) {
                        stringResource(R.string.check_in_too_early_today, TIME.format(opensAt))
                    } else {
                        stringResource(R.string.check_in_too_early_other_day, DAY.format(opensAt), TIME.format(opensAt))
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (left.isZero) {
                Text(
                    text = stringResource(R.string.check_in_tolerance_over),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    text = stringResource(R.string.check_in_tolerance_left, format(left)),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.check_in_location_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.spotTaken) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.check_in_spot_taken_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.check_in_spot_taken_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onRequestAnotherSpot,
                    enabled = !state.working,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.check_in_request_another_spot),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }

    PrimaryButton(
        text = stringResource(if (state.working) R.string.check_in_working else R.string.check_in_action),
        onClick = onCheckIn,
        enabled = state.canCheckIn(now),
        loading = state.working,
    )
}

@Composable
private fun Stay(state: CheckInState, reservation: Reservation, onCheckOut: () -> Unit) {
    val statusColors = SensorParkTheme.colors
    val sensorColor = if (state.spotConfirmed) statusColors.positive else statusColors.warning

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.check_in_done_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Surface(
                color = sensorColor.container,
                contentColor = sensorColor.content,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, sensorColor.content.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (state.spotConfirmed) {
                        stringResource(R.string.check_in_spot_confirmed)
                    } else {
                        stringResource(R.string.check_in_spot_waiting)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Text(
                text = stringResource(R.string.check_out_until, TIME.format(reservation.endsAt)),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    PrimaryButton(
        text = stringResource(if (state.working) R.string.check_in_working else R.string.check_out_action),
        onClick = onCheckOut,
        enabled = !state.working,
        loading = state.working,
    )
}

@Composable
private fun Summary(summary: CheckOutSummary, reservation: Reservation) {
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.check_out_summary_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(R.string.check_out_summary_stay, summary.stayMinutes, reservation.durationMinutes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Formato argentino: "$2.500".
            Text(
                text = stringResource(R.string.check_out_summary_amount, formatAmount(summary.amount)),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            if (summary.stayMinutes > reservation.durationMinutes) {
                Text(
                    text = stringResource(R.string.check_out_summary_overstay),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Message(message: CheckInMessage, onDismiss: () -> Unit) {
    val isInfo = message == CheckInMessage.SPOT_REASSIGNED
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isInfo) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isInfo) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(message.text()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
