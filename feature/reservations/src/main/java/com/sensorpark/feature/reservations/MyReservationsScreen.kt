package com.sensorpark.feature.reservations

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.ErrorContent
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.OfflineBanner
import com.sensorpark.core.ui.components.PrimaryButton
import com.sensorpark.core.ui.components.ReservationStatusBadge
import com.sensorpark.core.ui.components.formatAmount
import com.sensorpark.core.ui.components.formatCountdown
import com.sensorpark.core.ui.components.formatDateAndTime
import com.sensorpark.core.ui.components.formatDuration
import com.sensorpark.core.ui.components.formatTime
import com.sensorpark.core.ui.components.label
import com.sensorpark.core.ui.components.message
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.model.Stay
import com.sensorpark.domain.model.WaitlistStatus
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/**
 * Pestaña "Mis reservas" (CU-09): arriba la reserva que está pasando con sus acciones, después las
 * próximas, las listas de espera y el historial. El deep link `sensorpark://reservation/{id}` la
 * abre destacando una.
 *
 * @param reservationId la reserva a destacar cuando se llega por el deep link.
 */
@Composable
fun MyReservationsScreen(
    reservationId: String?,
    onCheckIn: (reservationId: String) -> Unit,
    onOpenOffer: (entryId: String) -> Unit,
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
        failure != null && state.active == null && state.history.isEmpty() && state.savedStays.isEmpty() ->
            ErrorContent(failure, onRetry = vm::dismissError)
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.my_reservations_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )

            // Arriba, junto a las reservas que se cancelan: al final de la lista quedaba fuera de la vista.
            state.rejection?.let { Problem(it.message(), vm::dismissError) }
            if (state.active != null || state.history.isNotEmpty()) {
                failure?.let { Problem(it.message(), vm::dismissError) }
            }

            if (state.isEmpty) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = stringResource(R.string.my_reservations_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }

            state.active?.let { card ->
                SectionHeader(stringResource(R.string.my_reservations_active))
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
                SectionHeader(stringResource(R.string.my_reservations_upcoming))
                state.upcoming.forEach { card ->
                    ReservationRow(
                        card = card,
                        highlighted = card.reservation.id == reservationId,
                        action = {
                            OutlinedButton(
                                onClick = { cancelling = card.reservation },
                                enabled = !state.working,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.my_reservations_cancel),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                        },
                    )
                }
            }

            if (state.waitlist.isNotEmpty()) {
                SectionHeader(stringResource(R.string.my_reservations_waitlist))
                state.waitlist.forEach { card ->
                    WaitlistRow(
                        card = card,
                        now = now,
                        working = state.working,
                        onOpenOffer = { onOpenOffer(card.entry.id) },
                        onLeave = { vm.leaveWaitlist(card.entry) },
                    )
                }
            }

            if (state.history.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                SectionHeader(stringResource(R.string.my_reservations_history))
                state.history.forEach { card ->
                    ReservationRow(card = card, highlighted = card.reservation.id == reservationId)
                }
            }

            if (state.savedStays.isNotEmpty()) {
                OfflineBanner()
                SectionHeader(stringResource(R.string.my_reservations_saved_stays))
                state.savedStays.forEach { StayRow(it) }
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (highlighted) 1f else 0.6f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Header(card, highlighted)
            val reservation = card.reservation
            if (reservation.status == ReservationStatus.IN_USE) {
                Text(
                    text = stringResource(R.string.my_reservations_time_left, formatDuration(card.timeLeft(now))),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                PrimaryButton(
                    text = stringResource(R.string.my_reservations_check_out),
                    onClick = onCheckIn,
                    enabled = !working,
                )
            } else {
                val left = card.toleranceLeft(now)
                if (left.isZero) {
                    Text(
                        text = stringResource(R.string.my_reservations_tolerance_over),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.my_reservations_tolerance_left, formatDuration(left)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.my_reservations_check_in),
                    onClick = onCheckIn,
                    enabled = !working && !left.isZero,
                )
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !working,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.my_reservations_cancel),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReservationRow(card: ReservationCard, highlighted: Boolean, action: @Composable () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Header(card, highlighted)
            action()
        }
    }
}

@Composable
private fun Header(card: ReservationCard, highlighted: Boolean) {
    val reservation = card.reservation
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = card.parkingLotName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        ReservationStatusBadge(reservation.status)
    }
    Text(
        text = stringResource(
            R.string.my_reservations_spot_and_slot,
            reservation.spotCode,
            formatDateAndTime(reservation.arrivesAt),
            formatTime(reservation.endsAt),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val stayMinutes = reservation.stayMinutes
    val amount = reservation.amount
    if (reservation.status == ReservationStatus.FINISHED && stayMinutes != null && amount != null) {
        StayLine(stayMinutes, amount)
    }
    if (highlighted) {
        Text(
            text = stringResource(R.string.my_reservations_highlighted),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Una lista de espera: en qué lugar de la fila está o, si se liberó un lugar, la oferta. */
@Composable
private fun WaitlistRow(
    card: WaitlistCard,
    now: Instant,
    working: Boolean,
    onOpenOffer: () -> Unit,
    onLeave: () -> Unit,
) {
    val entry = card.entry
    val offer = entry.offer
    val offerLeft = card.offerTimeLeft(now)
    val offered = entry.status == WaitlistStatus.OFFERED && !offerLeft.isZero
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            width = if (offered) 1.5.dp else 1.dp,
            color = if (offered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = card.parkingLotName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.my_reservations_waitlist_slot,
                    entry.spotType.label(),
                    formatDateAndTime(entry.arrivesAt),
                    formatTime(entry.endsAt),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (offer != null && offered) {
                Text(
                    text = stringResource(R.string.my_reservations_waitlist_offer, offer.spotCode, formatCountdown(offerLeft)),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 2.dp),
                )
                PrimaryButton(
                    text = stringResource(R.string.my_reservations_waitlist_see_offer),
                    onClick = onOpenOffer,
                    enabled = !working,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                entry.position?.let {
                    Text(
                        text = stringResource(R.string.my_reservations_waitlist_position, it),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }
                TextButton(onClick = onLeave, enabled = !working) {
                    Text(stringResource(R.string.my_reservations_waitlist_leave))
                }
            }
        }
    }
}

@Composable
private fun Problem(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.my_reservations_dismiss))
            }
        }
    }
}

/** Una estadía del historial guardado en el teléfono, para verla sin conexión (T19). */
@Composable
private fun StayRow(stay: Stay) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stay.parkingLotName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                ReservationStatusBadge(ReservationStatus.FINISHED)
            }
            Text(
                text = stringResource(R.string.my_reservations_stay_spot, stay.spotCode, formatDateAndTime(stay.arrivesAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StayLine(stay.stayMinutes, stay.amount)
        }
    }
}

/** "Estadía de 1 h 05 min · $2.708". El importe es informativo: la app no cobra. */
@Composable
private fun StayLine(stayMinutes: Int, amount: Int) {
    Text(
        text = stringResource(
            R.string.my_reservations_stay_summary,
            formatDuration(Duration.ofMinutes(stayMinutes.toLong())),
            formatAmount(amount),
        ),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
    )
}
