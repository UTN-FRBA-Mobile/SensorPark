package com.sensorpark.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sensorpark.core.ui.R
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.model.ReservationRejection
import com.sensorpark.domain.model.ReservationStatus
import com.sensorpark.domain.reservation.ReservationRules
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

// Textos y formatos comunes a las pantallas de reservas y de check-in. Formato argentino:
// fechas dd/mm, hora de 24 h y pesos.

private val ES_AR = Locale.forLanguageTag("es-AR")
private val TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ReservationRules.ZONE)
private val DATE = DateTimeFormatter.ofPattern("dd/MM").withZone(ReservationRules.ZONE)
private val DATE_AND_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ReservationRules.ZONE)

fun formatTime(instant: Instant): String = TIME.format(instant)

fun formatDate(instant: Instant): String = DATE.format(instant)

fun formatDateAndTime(instant: Instant): String = DATE_AND_TIME.format(instant)

/** "$1.500". El importe es informativo: la app no cobra. */
fun formatAmount(pesos: Int): String = "$" + NumberFormat.getIntegerInstance(ES_AR).format(pesos)

/** "45 min" o "2 h 15 min". */
fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return if (hours > 0) "$hours h ${minutes.toString().padStart(2, '0')} min" else "$minutes min"
}

/** "4:59": la cuenta regresiva de los minutos que dura una oferta de la lista de espera. */
fun formatCountdown(duration: Duration): String {
    val seconds = duration.seconds.coerceAtLeast(0)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** "1 h" o "30 min", para los chips de duración. */
fun formatDurationMinutes(minutes: Int): String =
    if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60} h" else "$minutes min"

/**
 * Por qué el backend rechazó la operación, en un texto que el conductor entienda y que diga qué
 * puede hacer. Son los mismos motivos que documenta `docs/reservation-rules.md`.
 */
@Composable
fun ReservationRejection.message(): String = stringResource(
    when (this) {
        ReservationRejection.INVALID_DURATION -> R.string.rejection_invalid_duration
        ReservationRejection.IN_THE_PAST -> R.string.rejection_in_the_past
        ReservationRejection.TOO_FAR_AHEAD -> R.string.rejection_too_far_ahead
        ReservationRejection.OUTSIDE_SCHEDULE -> R.string.rejection_outside_schedule
        ReservationRejection.CATEGORY_REQUIRED -> R.string.rejection_category_required
        ReservationRejection.LIMIT_REACHED -> R.string.rejection_limit_reached
        ReservationRejection.NO_SPOTS -> R.string.rejection_no_spots
        ReservationRejection.CANCEL_NOT_ALLOWED -> R.string.rejection_cancel_not_allowed
        ReservationRejection.TOO_EARLY -> R.string.rejection_too_early
        ReservationRejection.EXPIRED -> R.string.rejection_expired
        ReservationRejection.TOO_FAR -> R.string.rejection_too_far
        ReservationRejection.INVALID_STATUS -> R.string.rejection_invalid_status
        ReservationRejection.ALREADY_IN_WAITLIST -> R.string.rejection_already_in_waitlist
        ReservationRejection.SPOT_NOT_TAKEN -> R.string.rejection_spot_not_taken
        ReservationRejection.NO_OFFER -> R.string.rejection_no_offer
        ReservationRejection.OFFER_EXPIRED -> R.string.rejection_offer_expired
    },
)

/**
 * Pastilla con el estado de una reserva, con el mismo código de colores que el resto de la app.
 * El color nunca es la única pista: la pastilla siempre lleva el texto del estado.
 */
@Composable
fun ReservationStatusBadge(status: ReservationStatus, modifier: Modifier = Modifier) {
    val colors = SensorParkTheme.colors
    val color = when (status) {
        ReservationStatus.CONFIRMED -> colors.positive
        ReservationStatus.IN_USE -> colors.warning
        ReservationStatus.EXPIRED -> colors.negative
        ReservationStatus.FINISHED, ReservationStatus.CANCELLED -> colors.neutral
    }
    Surface(
        color = color.container,
        contentColor = color.content,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun ReservationStatus.label(): String = stringResource(
    when (this) {
        ReservationStatus.CONFIRMED -> R.string.reservation_status_confirmed
        ReservationStatus.IN_USE -> R.string.reservation_status_in_use
        ReservationStatus.FINISHED -> R.string.reservation_status_finished
        ReservationStatus.CANCELLED -> R.string.reservation_status_cancelled
        ReservationStatus.EXPIRED -> R.string.reservation_status_expired
    },
)

@androidx.compose.ui.tooling.preview.Preview(name = "Claro", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(
    name = "Oscuro",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun ReservationStatusBadgePreview() {
    SensorParkTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            ReservationStatus.entries.forEach { status ->
                ReservationStatusBadge(status)
            }
        }
    }
}

