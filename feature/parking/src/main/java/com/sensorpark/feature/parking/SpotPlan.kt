package com.sensorpark.feature.parking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.sensorpark.core.ui.components.SpotStatusChip
import com.sensorpark.core.ui.components.color
import com.sensorpark.core.ui.components.icon
import com.sensorpark.core.ui.components.label
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType

private val SpotCellShape = RoundedCornerShape(12.dp)
private val ArLocale = Locale.forLanguageTag("es-AR")

/** Un lugar como lo dibuja el plano. Es inmutable para que Compose se saltee las celdas sin cambios. */
@Immutable
internal data class SpotCell(
    val id: String,
    val code: String,
    val status: SpotStatus,
    val type: SpotType,
    val category: Category?,
) {
    val isFree: Boolean get() = status == SpotStatus.FREE

    fun toSpot() = Spot(id = id, code = code, type = type, status = status, priorityCategory = category)
}

internal fun Spot.toCell() = SpotCell(id, code, status, type, priorityCategory)

private fun SpotType.icon(): ImageVector? = when (this) {
    SpotType.STANDARD -> null
    SpotType.PRIORITY -> Icons.AutoMirrored.Filled.Accessible
    SpotType.MOTORCYCLE -> Icons.Filled.TwoWheeler
    SpotType.EV_CHARGING -> Icons.Filled.EvStation
}

/** Subcategoría de un lugar preferencial. */
@Composable
private fun Category.shortLabel(): String? = when (this) {
    Category.STANDARD -> null
    Category.PREGNANT -> stringResource(R.string.plan_category_pregnant)
    Category.REDUCED_MOBILITY -> stringResource(R.string.plan_category_mobility)
}

/** Una celda del plano. El estado va también en ícono y descripción, no sólo en color. */
@Composable
internal fun SpotCellView(cell: SpotCell, onReserve: (SpotCell) -> Unit, modifier: Modifier = Modifier) {
    val color = cell.status.color(SensorParkTheme.colors)
    val categoryLabel = cell.category?.takeIf { cell.type == SpotType.PRIORITY }?.shortLabel()
    val statusLabel = cell.status.label()
    val typeLabel = if (cell.type != SpotType.STANDARD) cell.type.label() else null
    val baseDescription = stringResource(R.string.plan_spot_description, cell.code, statusLabel.lowercase(ArLocale))
    val description = remember(baseDescription, typeLabel, categoryLabel) {
        buildString {
            append(baseDescription)
            if (typeLabel != null) append(", ").append(typeLabel)
            if (categoryLabel != null) append(", ").append(categoryLabel)
        }
    }
    val borderColor = remember(color.content) { color.content.copy(alpha = 0.35f) }

    CompositionLocalProvider(LocalContentColor provides color.content) {
        Column(
            modifier = modifier
                .aspectRatio(1f)
                .clip(SpotCellShape)
                .background(color.container, SpotCellShape)
                .border(1.dp, borderColor, SpotCellShape)
                .clickable(
                    enabled = cell.isFree,
                    role = Role.Button,
                    onClick = { onReserve(cell) },
                )
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Icon(cell.status.icon(), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = cell.code,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color.content,
                maxLines = 1,
            )
            val typeIcon = cell.type.icon()
            if (typeIcon != null || categoryLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (typeIcon != null) Icon(typeIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                    if (categoryLabel != null) {
                        Text(
                            text = categoryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = color.content,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** La leyenda de colores del plano, una pastilla por estado. */
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
