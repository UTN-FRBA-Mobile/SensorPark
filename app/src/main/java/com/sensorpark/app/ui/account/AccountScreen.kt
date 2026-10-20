package com.sensorpark.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.sensorpark.core.ui.theme.ThemeMode
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
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()

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

        HorizontalDivider()
        ThemeSelector(
            selected = themeMode,
            onSelect = vm::setThemeMode,
        )

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.account_theme), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                ) {
                    Text(stringResource(mode.label()))
                }
            }
        }
    }
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.account_theme_system
    ThemeMode.LIGHT -> R.string.account_theme_light
    ThemeMode.DARK -> R.string.account_theme_dark
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.account_notifications),
            style = MaterialTheme.typography.titleSmall,
        )
        NotificationType.entries.forEach { type ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(type.label()),
                    style = MaterialTheme.typography.bodyMedium,
                )
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
