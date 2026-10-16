package com.sensorpark.feature.checkin

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sensorpark.core.ui.components.PlaceholderScreen

/** Se abre desde la reserva o desde el aviso de llegada. La completa T21. */
@Composable
fun CheckInScreen(reservationId: String) {
    PlaceholderScreen(
        title = stringResource(R.string.check_in_title),
        detail = stringResource(R.string.check_in_pending, reservationId),
    )
}
