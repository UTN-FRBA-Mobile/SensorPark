package com.sensorpark.app.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sensorpark.app.ui.diagnostico.Diagnostico

/** Pantalla provisoria de cada rol: reemplazar por el inicio real de cada uno. */
@Composable
fun PantallaInicio(rol: String, email: String, onSalir: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("SensorPark", style = MaterialTheme.typography.headlineMedium)
        Text("Ingresaste como $rol")
        Text(email)
        Diagnostico(modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = onSalir) { Text("Cerrar sesión") }
    }
}
