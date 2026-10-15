package com.sensorpark.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.app.ui.profile.CategorySelector
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Role

@Composable
fun LoginScreen(onSignUp: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthForm(title = "SensorPark", state = state) {
        EmailField(email) { email = it }
        PasswordField(password) { password = it }
        Button(
            onClick = { vm.signIn(email, password) },
            enabled = !state.loading && email.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Ingresar") }
        TextButton(onClick = onSignUp) { Text("Crear cuenta") }
    }
}

@Composable
fun SignUpScreen(onBack: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(Role.DRIVER) }
    var category by rememberSaveable { mutableStateOf(Category.STANDARD) }

    AuthForm(title = "Crear cuenta", state = state) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        EmailField(email) { email = it }
        PasswordField(password) { password = it }
        RoleSelector(role) { role = it }
        // La prioridad es para quien estaciona: un administrador de estacionamiento no la usa.
        if (role == Role.DRIVER) {
            CategorySelector(selected = category, onSelect = { category = it }, enabled = !state.loading)
        }
        Button(
            onClick = {
                vm.signUp(name, email, password, role, if (role == Role.DRIVER) category else Category.STANDARD)
            },
            enabled = !state.loading && name.isNotBlank() && email.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Registrarme") }
        TextButton(onClick = onBack) { Text("Ya tengo cuenta") }
    }
}

@Composable
private fun AuthForm(title: String, state: AuthUiState, fields: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        fields()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** Los dos roles que se pueden elegir al registrarse. `ADMIN` no se ofrece. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleSelector(selected: Role, onSelect: (Role) -> Unit) {
    val options = listOf(Role.DRIVER to "Conductor", Role.PARKING_ADMIN to "Administro un estacionamiento")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (role, label) ->
            SegmentedButton(
                selected = selected == role,
                onClick = { onSelect(role) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Contraseña") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
