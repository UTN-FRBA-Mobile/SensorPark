package com.sensorpark.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sensorpark.core.ui.R
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.SpotType
import java.util.Locale
import kotlin.math.roundToInt

// Textos y piezas comunes a las pantallas que muestran estacionamientos.

@Composable
fun SpotType.label(): String = stringResource(
    when (this) {
        SpotType.STANDARD -> R.string.spot_type_standard
        SpotType.PRIORITY -> R.string.spot_type_priority
        SpotType.MOTORCYCLE -> R.string.spot_type_motorcycle
        SpotType.EV_CHARGING -> R.string.spot_type_ev_charging
    },
)

@Composable
fun DataErrorReason.message(): String = stringResource(
    when (this) {
        DataErrorReason.NOT_FOUND -> R.string.error_not_found
        DataErrorReason.PERMISSION_DENIED -> R.string.error_permission_denied
        DataErrorReason.NO_CONNECTION -> R.string.error_no_connection
        DataErrorReason.UNKNOWN -> R.string.error_unknown
    },
)

/** "350 m" o "1,2 km", con el separador decimal del idioma del teléfono. */
fun formatDistance(meters: Double): String =
    if (meters < 1_000) "${meters.roundToInt()} m"
    else String.format(Locale.getDefault(), "%.1f km", meters / 1_000)

/** Pastilla con los lugares libres: verde si hay, ámbar si queda un cuarto o menos, rojo si está completo. */
@Composable
fun AvailabilityBadge(free: Int, capacity: Int, modifier: Modifier = Modifier) {
    val colors = SensorParkTheme.colors
    val status = when {
        free <= 0 -> colors.negative
        free * 4 <= capacity -> colors.warning
        else -> colors.positive
    }
    Surface(
        color = status.container,
        contentColor = status.content,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text = if (free <= 0) stringResource(R.string.availability_full)
            else stringResource(R.string.availability_free, free, capacity),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/**
 * Tarjeta de un estacionamiento en una lista: nombre, una línea de detalle y la pastilla de libres.
 * [footer] es una línea opcional debajo, por ejemplo los libres del tipo filtrado.
 */
@Composable
fun ParkingLotCard(
    name: String,
    subtitle: String,
    freeSpots: Int,
    capacity: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    footer: String? = null,
) {
    ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AvailabilityBadge(freeSpots, capacity)
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (footer != null) {
                Text(footer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorContent(reason: DataErrorReason, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(reason.message(), textAlign = TextAlign.Center)
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Preview(name = "Claro")
@Preview(name = "Oscuro", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ParkingLotCardPreview() {
    SensorParkTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ParkingLotCard("UTN Medrano", "350 m · Av. Medrano 951", freeSpots = 9, capacity = 14, onClick = {})
            ParkingLotCard("Playa Abasto", "1,0 km · Av. Corrientes 3247", freeSpots = 2, capacity = 14, onClick = {})
            ParkingLotCard("Parque Centenario", "1,7 km · Av. Díaz Vélez 4800", freeSpots = 0, capacity = 14, onClick = {})
        }
    }
}
