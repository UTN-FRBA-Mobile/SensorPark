package com.sensorpark.feature.parking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sensorpark.core.ui.components.SpotStatusChip
import com.sensorpark.core.ui.components.color
import com.sensorpark.core.ui.components.icon
import com.sensorpark.core.ui.components.label
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType

// El plano de lugares de T12: una celda por lugar, con su código, el color y el ícono de su estado
// y el ícono de su tipo. El estado nunca va sólo por color: el ícono y la descripción para
// TalkBack lo dicen también, para quien no distingue los colores.

/**
 * Un lugar del plano. Los libres se pueden tocar para reservar ese lugar puntual; el resto sólo
 * informa.
 */
@Composable
internal fun SpotCell(spot: Spot, onReserve: ((Spot) -> Unit)?, modifier: Modifier = Modifier) {
    val statusColor = spot.status.color(SensorParkTheme.colors)
    val description = spotDescription(spot)
    val canReserve = onReserve != null && spot.status == SpotStatus.FREE
    Surface(
        onClick = { onReserve?.invoke(spot) },
        enabled = canReserve,
        shape = RoundedCornerShape(12.dp),
        color = statusColor.container,
        contentColor = statusColor.content,
        border = BorderStroke(1.dp, statusColor.content.copy(alpha = 0.25f)),
        modifier = modifier
            .aspectRatio(0.85f)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (canReserve) role = Role.Button
            },
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(spot.status.icon(), contentDescription = null, modifier = Modifier.size(14.dp))
                spot.typeIcon()?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            }
            Text(
                text = spot.code,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = spot.priorityCategory?.let { stringResource(it.shortLabel()) } ?: spot.status.label(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/** Qué significa cada color del plano. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpotLegend(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpotStatus.entries.forEach { SpotStatusChip(status = it) }
    }
}

/** "Lugar 07, libre, moto. Tocá para reservarlo." Lo lee TalkBack. */
@Composable
private fun spotDescription(spot: Spot): String {
    val type = when (spot.type) {
        SpotType.STANDARD -> null
        SpotType.PRIORITY -> spot.priorityCategory?.let { stringResource(it.longLabel()) }
            ?: stringResource(R.string.spot_type_priority)
        else -> spot.type.label()
    }
    val parts = listOfNotNull(stringResource(R.string.spot_code, spot.code), spot.status.label().lowercase(), type)
    val text = parts.joinToString(", ")
    return if (spot.status == SpotStatus.FREE) text + ". " + stringResource(R.string.spot_tap_to_reserve) else "$text."
}

private fun Spot.typeIcon(): ImageVector? = when (type) {
    SpotType.STANDARD -> null
    SpotType.PRIORITY -> if (priorityCategory == Category.PREGNANT) Icons.Filled.PregnantWoman else Icons.Filled.Accessible
    SpotType.MOTORCYCLE -> Icons.Filled.TwoWheeler
    SpotType.EV_CHARGING -> Icons.Filled.ElectricCar
}

private fun Category.shortLabel(): Int = when (this) {
    Category.PREGNANT -> R.string.spot_category_pregnant_short
    Category.REDUCED_MOBILITY -> R.string.spot_category_reduced_mobility_short
    Category.STANDARD -> R.string.spot_type_priority
}

private fun Category.longLabel(): Int = when (this) {
    Category.PREGNANT -> R.string.spot_category_pregnant
    Category.REDUCED_MOBILITY -> R.string.spot_category_reduced_mobility
    Category.STANDARD -> R.string.spot_type_priority
}
