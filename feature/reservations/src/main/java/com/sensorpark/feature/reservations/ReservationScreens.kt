package com.sensorpark.feature.reservations

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sensorpark.core.ui.components.PlaceholderScreen
import com.sensorpark.core.ui.components.ScreenAction

/** Se llega desde el detalle de un estacionamiento. La completa T15. */
@Composable
fun ReserveScreen(parkingLotId: String, onOpenMyReservations: () -> Unit) {
    PlaceholderScreen(
        title = stringResource(R.string.reserve_title),
        detail = stringResource(R.string.reserve_pending),
        actions = listOf(ScreenAction(stringResource(R.string.reserve_go_to_my_reservations), onOpenMyReservations)),
    )
}

/**
 * Pestaña Mis reservas. La completan T15, T18, T19 y T21.
 * @param reservationId reserva a destacar cuando se llega por el deep link `sensorpark://reservation/{id}`.
 */
@Composable
fun MyReservationsScreen(reservationId: String?, onCheckIn: (reservationId: String) -> Unit) {
    PlaceholderScreen(
        title = stringResource(R.string.my_reservations_title),
        detail = if (reservationId == null) stringResource(R.string.my_reservations_pending)
        else stringResource(R.string.my_reservations_highlighted, reservationId),
        // Provisorio, para llegar a la pantalla de check-in hasta que existan las reservas.
        actions = listOf(
            ScreenAction(stringResource(R.string.my_reservations_check_in)) { onCheckIn(reservationId ?: "demo") },
        ),
    )
}
