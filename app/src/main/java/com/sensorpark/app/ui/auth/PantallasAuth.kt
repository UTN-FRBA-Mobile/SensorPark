package com.sensorpark.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PantallaLogin(irARegistro: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    FormularioAuth(titulo = "SensorPark", estado = estado) {
        CampoEmail(email) { email = it }
        CampoPassword(password) { password = it }
        Button(
            onClick = { vm.ingresar(email, password) },
            enabled = !estado.cargando && email.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Ingresar") }
        TextButton(onClick = irARegistro) { Text("Crear cuenta") }
    }
}

@Composable
fun PantallaRegistro(volver: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var nombre by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    FormularioAuth(titulo = "Crear cuenta", estado = estado) {
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        CampoEmail(email) { email = it }
        CampoPassword(password) { password = it }
        Button(
            onClick = { vm.registrar(nombre, email, password) },
            enabled = !estado.cargando && nombre.isNotBlank() && email.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Registrarme") }
        TextButton(onClick = volver) { Text("Ya tengo cuenta") }
    }
}

@Composable
private fun FormularioAuth(titulo: String, estado: EstadoAuth, campos: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(titulo, style = MaterialTheme.typography.headlineMedium)
        campos()
        estado.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun CampoEmail(valor: String, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CampoPassword(valor: String, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text("Contraseña") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
