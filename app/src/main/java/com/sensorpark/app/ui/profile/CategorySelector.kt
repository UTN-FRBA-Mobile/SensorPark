package com.sensorpark.app.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sensorpark.app.R
import androidx.compose.ui.semantics.Role
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
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        Text(stringResource(R.string.category_title), style = MaterialTheme.typography.titleSmall)
        Category.entries.forEach { category ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = category == selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(category) },
                    ),
            ) {
                RadioButton(selected = category == selected, onClick = null, enabled = enabled)
                Text(category.label())
            }
        }
    }
}
