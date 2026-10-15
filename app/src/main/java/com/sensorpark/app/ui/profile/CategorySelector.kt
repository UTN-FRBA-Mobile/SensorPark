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
import androidx.compose.ui.semantics.Role
import com.sensorpark.domain.model.Category

/** Texto que ve el usuario para cada categoría de prioridad. */
fun Category.label(): String = when (this) {
    Category.STANDARD -> "Ninguna"
    Category.PREGNANT -> "Embarazo"
    Category.REDUCED_MOBILITY -> "Movilidad reducida o discapacidad"
}

/**
 * Selector de la categoría de prioridad del conductor. Se usa en el registro y en Preferencias;
 * es de buena fe y elegir una es opcional.
 */
@Composable
fun CategorySelector(selected: Category, onSelect: (Category) -> Unit, enabled: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        Text("Prioridad para estacionar (opcional)", style = MaterialTheme.typography.titleSmall)
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
