package com.sensorpark.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sensorpark.core.ui.R
import com.sensorpark.core.ui.theme.SensorParkColors
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.core.ui.theme.StatusColor
import com.sensorpark.domain.model.SpotStatus

@Composable
fun SpotStatus.label(): String = stringResource(
    when (this) {
        SpotStatus.FREE -> R.string.spot_status_free
        SpotStatus.OCCUPIED -> R.string.spot_status_occupied
        SpotStatus.RESERVED -> R.string.spot_status_reserved
        SpotStatus.OUT_OF_SERVICE -> R.string.spot_status_out_of_service
    },
)

fun SpotStatus.icon(): ImageVector = when (this) {
    SpotStatus.FREE -> Icons.Filled.Check
    SpotStatus.OCCUPIED -> Icons.Filled.Close
    SpotStatus.RESERVED -> Icons.Filled.DateRange
    SpotStatus.OUT_OF_SERVICE -> Icons.Filled.Warning
}

fun SpotStatus.color(colors: SensorParkColors): StatusColor = when (this) {
    SpotStatus.FREE -> colors.positive
    SpotStatus.OCCUPIED -> colors.negative
    SpotStatus.RESERVED -> colors.warning
    SpotStatus.OUT_OF_SERVICE -> colors.neutral
}

@Composable
fun SpotStatusChip(
    status: SpotStatus,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    val colors = SensorParkTheme.colors
    val statusColor = status.color(colors)
    val shape = RoundedCornerShape(50)

    Surface(
        color = statusColor.container,
        contentColor = statusColor.content,
        shape = shape,
        border = if (selected) BorderStroke(2.dp, statusColor.content) else null,
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = status.icon(),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = status.label(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun SpotStatusChipPreview() {
    SensorParkTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpotStatus.entries.forEach { status ->
                    SpotStatusChip(status = status)
                }
            }
        }
    }
}
