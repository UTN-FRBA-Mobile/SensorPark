package com.sensorpark.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.app.BuildConfig
import com.sensorpark.app.R
import com.sensorpark.app.ui.profile.CategorySelector
import com.sensorpark.domain.model.Role
import com.sensorpark.domain.model.SessionState

/**
 * Pestaña Cuenta, para todos los roles: datos de la sesión, categoría de prioridad (sólo el
 * conductor), avisos y cerrar sesión. Las herramientas de desarrollo aparecen en el build debug
 * y para el rol ADMIN.
 */
@Composable
fun AccountScreen(
    state: SessionState.SignedIn,
    onSignOut: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    vm: AccountViewModel = hiltViewModel(),
) {
    val category by vm.category.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineSmall)
        Column {
            Text(state.session.email, style = MaterialTheme.typography.titleMedium)
            state.role?.let {
                Text(it.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val current = category
        if (state.role == Role.DRIVER && current != null) {
            HorizontalDivider()
            CategorySelector(selected = current, onSelect = vm::setCategory)
        }

        HorizontalDivider()
        Column {
            Text(stringResource(R.string.account_notifications), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.account_notifications_pending),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (BuildConfig.DEBUG || state.role == Role.ADMIN) {
            HorizontalDivider()
            Text(stringResource(R.string.account_dev_tools), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_diagnostics))
            }
        }

        HorizontalDivider()
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_sign_out))
        }
    }
}

@Composable
private fun Role.label(): String = stringResource(
    when (this) {
        Role.DRIVER -> R.string.account_role_driver
        Role.PARKING_ADMIN -> R.string.account_role_parking_admin
        Role.ADMIN -> R.string.account_role_admin
    },
)
