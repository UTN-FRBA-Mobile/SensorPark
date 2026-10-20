package com.sensorpark.app.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sensorpark.app.R
import com.sensorpark.domain.model.Category

/** Texto que ve el usuario para cada categoría de prioridad. */
@Composable
fun Category.label(): String = stringResource(
    when (this) {
        Category.STANDARD -> R.string.category_standard
        Category.PREGNANT -> R.string.category_pregnant
        Category.REDUCED_MOBILITY -> R.string.category_reduced_mobility
    },
)

/**
 * Selector de la categoría de prioridad del conductor. Se usa en el registro y en Cuenta;
 * es de buena fe y elegir una es opcional.
 */
@Composable
fun CategorySelector(selected: Category, onSelect: (Category) -> Unit, enabled: Boolean = true) {
    Column(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.category_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Category.entries.forEach { category ->
            val isSelected = category == selected
            val shape = RoundedCornerShape(12.dp)
            Surface(
                shape = shape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(category) },
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    RadioButton(selected = isSelected, onClick = null, enabled = enabled)
                    Text(
                        text = category.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
