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
import androidx.compose.material3.Card
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
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

/** "350 m" o "1,2 km". La app es sólo en español, con formato argentino. */
fun formatDistance(meters: Double): String =
    if (meters < 1_000) "${meters.roundToInt()} m"
    else String.format(Locale.forLanguageTag("es-AR"), "%.1f km", meters / 1_000)

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
        shape = CircleShape,
        border = BorderStroke(1.dp, status.content.copy(alpha = 0.3f)),
        modifier = modifier,
    ) {
        Text(
            text = if (free <= 0) stringResource(R.string.availability_full)
            else stringResource(R.string.availability_free, free, capacity),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/**
 * Tarjeta de un estacionamiento en una lista: nombre, una línea de detalle y la pastilla de libres.
 * [footer] es una línea opcional debajo, por ejemplo los libres del tipo filtrado. Con [favorite]
 * muestra el corazón para guardarlo en favoritos (T19).
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
    favorite: Boolean? = null,
    onToggleFavorite: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                if (favorite != null) FavoriteButton(favorite, name, onToggleFavorite)
                AvailabilityBadge(freeSpots, capacity)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (footer != null) {
                    Text(
                        text = footer,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.parking_card_action_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AvailabilityBadgePreview() {
    SensorParkTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AvailabilityBadge(free = 10, capacity = 14)
            AvailabilityBadge(free = 2, capacity = 14)
            AvailabilityBadge(free = 0, capacity = 14)
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun ErrorContentPreview() {
    SensorParkTheme {
        ErrorContent(
            reason = DataErrorReason.NO_CONNECTION,
            onRetry = {},
        )
    }
}


/** El corazón de favoritos (T19). Dice en voz alta qué hace, para TalkBack. */
@Composable
fun FavoriteButton(favorite: Boolean, parkingLotName: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(
                if (favorite) R.string.favorite_remove else R.string.favorite_add,
                parkingLotName,
            ),
            tint = if (favorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
