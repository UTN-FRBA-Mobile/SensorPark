package com.sensorpark.feature.reservations

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
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.formatCountdown
import com.sensorpark.core.ui.components.formatDateAndTime
import com.sensorpark.core.ui.components.formatTime
import com.sensorpark.core.ui.components.label
import com.sensorpark.core.ui.components.message
import com.sensorpark.domain.model.ReservationConfirmation
import com.sensorpark.domain.model.WaitlistEntry
import com.sensorpark.domain.model.WaitlistStatus
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * La oferta de un lugar liberado (T18): cuenta regresiva de los minutos que se lo guardan, con
 * Aceptar y Rechazar. Aceptar la convierte en una reserva, que sigue como cualquier otra.
 */
@Composable
fun WaitlistOfferScreen(
    entryId: String,
    onOpenMyReservations: () -> Unit,
    vm: WaitlistOfferViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(entryId) { vm.load(entryId) }

    // La cuenta regresiva necesita una hora que avance.
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

    val entry = state.entry
    val confirmation = state.confirmation
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            confirmation != null -> Accepted(state.parkingLotName, confirmation, onOpenMyReservations)
            state.declined -> Closed(stringResource(R.string.waitlist_offer_declined), onOpenMyReservations)
            entry == null -> Closed(stringResource(R.string.waitlist_offer_not_found), onOpenMyReservations)
            entry.status == WaitlistStatus.ACCEPTED ->
                Closed(stringResource(R.string.waitlist_offer_already_accepted), onOpenMyReservations)
            entry.status == WaitlistStatus.WAITING -> Closed(
                stringResource(R.string.waitlist_offer_still_waiting, entry.position ?: 1),
                onOpenMyReservations,
            )
            entry.status != WaitlistStatus.OFFERED || state.timeLeft(now).isZero ->
                Closed(stringResource(R.string.waitlist_offer_expired), onOpenMyReservations)
            else -> Offer(state, entry, now, vm::accept, vm::decline)
        }

        state.rejection?.let { Problem(it.message(), vm::dismissError) }
        state.failure?.let { Problem(it.message(), vm::dismissError) }
    }
}

@Composable
private fun Offer(
    state: WaitlistOfferState,
    entry: WaitlistEntry,
    now: Instant,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Text(stringResource(R.string.waitlist_offer_title), style = MaterialTheme.typography.headlineMedium)
    Text(state.parkingLotName, style = MaterialTheme.typography.titleMedium)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.waitlist_offer_time_left), style = MaterialTheme.typography.labelLarge)
            Text(
                formatCountdown(state.timeLeft(now)),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    Text(stringResource(R.string.waitlist_offer_spot, entry.offer?.spotCode.orEmpty(), entry.spotType.label()))
    Text(stringResource(R.string.waitlist_offer_slot, formatDateAndTime(entry.arrivesAt), formatTime(entry.endsAt)))
    Text(
        stringResource(R.string.waitlist_offer_hint),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )

    Button(onClick = onAccept, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.working) R.string.waitlist_offer_working else R.string.waitlist_offer_accept))
    }
    OutlinedButton(onClick = onDecline, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.waitlist_offer_decline))
    }
}

@Composable
private fun Accepted(parkingLotName: String, confirmation: ReservationConfirmation, onOpenMyReservations: () -> Unit) {
    Text(stringResource(R.string.reserve_done_title), style = MaterialTheme.typography.headlineMedium)
    Text(parkingLotName, style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.reserve_done_spot, confirmation.spotCode))
    Text(stringResource(R.string.reserve_done_slot, formatTime(confirmation.arrivesAt), formatTime(confirmation.endsAt)))
    Text(stringResource(R.string.reserve_done_tolerance, formatTime(confirmation.toleranceUntil)))
    Button(onClick = onOpenMyReservations, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.reserve_go_to_my_reservations))
    }
}

/** La oferta ya no se puede contestar: por qué, y a dónde seguir. */
@Composable
private fun Closed(message: String, onOpenMyReservations: () -> Unit) {
    Text(stringResource(R.string.waitlist_offer_title_closed), style = MaterialTheme.typography.headlineMedium)
    Text(message)
    Button(onClick = onOpenMyReservations, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.reserve_go_to_my_reservations))
    }
}

@Composable
private fun Problem(message: String, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reserve_dismiss)) }
        }
    }
}
