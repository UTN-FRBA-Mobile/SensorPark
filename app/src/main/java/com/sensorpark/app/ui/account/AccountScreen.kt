package com.sensorpark.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.app.BuildConfig
import com.sensorpark.app.R
import com.sensorpark.app.ui.profile.CategorySelector
import com.sensorpark.domain.model.NotificationPreferences
import com.sensorpark.domain.model.NotificationType
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
    val notifications by vm.notifications.collectAsStateWithLifecycle()

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
        NotificationPreferences(
            preferences = notifications,
            onToggle = vm::setNotificationEnabled,
        )

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

/**
 * Un interruptor por tipo de aviso. Apagar uno acá además se copia al perfil en Firestore, para
 * que el backend tampoco mande la push.
 */
@Composable
private fun NotificationPreferences(
    preferences: NotificationPreferences,
    onToggle: (NotificationType, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.account_notifications), style = MaterialTheme.typography.titleSmall)
        NotificationType.entries.forEach { type ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(type.label()))
                Switch(
                    checked = preferences.isEnabled(type),
                    onCheckedChange = { onToggle(type, it) },
                )
            }
        }
    }
}

private fun NotificationType.label(): Int = when (this) {
    NotificationType.RESERVATION -> R.string.account_notifications_reservation
    NotificationType.REMINDER -> R.string.account_notifications_reminder
    NotificationType.ARRIVAL -> R.string.account_notifications_arrival
    NotificationType.WAITLIST -> R.string.account_notifications_waitlist
}

@Composable
private fun Role.label(): String = stringResource(
    when (this) {
        Role.DRIVER -> R.string.account_role_driver
        Role.PARKING_ADMIN -> R.string.account_role_parking_admin
        Role.ADMIN -> R.string.account_role_admin
    },
)
