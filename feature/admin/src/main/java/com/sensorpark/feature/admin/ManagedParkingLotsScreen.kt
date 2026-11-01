package com.sensorpark.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.ErrorContent
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.ParkingLotCard
import com.sensorpark.domain.model.DataResult

/**
 * Pestaña Mis estacionamientos del administrador. [seesAll] es para el rol ADMIN, que administra
 * todos. Cada tarjeta abre el panel del estacionamiento.
 */
@Composable
fun ManagedParkingLotsScreen(
    uid: String,
    seesAll: Boolean,
    onOpenParkingLot: (parkingLotId: String) -> Unit,
    onCreateParkingLot: () -> Unit,
    vm: ManagedParkingLotsViewModel = hiltViewModel(),
) {
    LaunchedEffect(uid, seesAll) { vm.load(uid, seesAll) }
    val result by vm.result.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.admin_lots_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            when (val current = result) {
                null -> LoadingContent()
                is DataResult.Failure -> ErrorContent(current.reason, onRetry = vm::retry)
                is DataResult.Success ->
                    if (current.value.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.admin_lots_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(20.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            // Abajo deja lugar para el botón flotante.
                            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(current.value, key = { it.id }) { lot ->
                                ParkingLotCard(
                                    name = lot.name,
                                    subtitle = lot.address,
                                    freeSpots = lot.freeSpots,
                                    capacity = lot.capacity,
                                    onClick = { onOpenParkingLot(lot.id) },
                                )
                            }
                        }
                    }
            }
        }
        val createLabel = stringResource(R.string.admin_lots_create)
        ExtendedFloatingActionButton(
            onClick = onCreateParkingLot,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = {
                Text(
                    text = createLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            },
            // El botón extendido no le pasa su texto a TalkBack: sin esto se anunciaría sólo "botón".
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).semantics { contentDescription = createLabel },
        )
    }
}
