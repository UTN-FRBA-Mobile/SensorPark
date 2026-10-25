package com.sensorpark.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.AvailabilityBadge
import com.sensorpark.core.ui.components.ErrorContent
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.ReservationStatusBadge
import com.sensorpark.core.ui.components.SpotStatusChip
import com.sensorpark.core.ui.components.formatTime
import com.sensorpark.core.ui.components.label
import com.sensorpark.core.ui.components.message
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.DaySummary
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Reservation
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotDraft
import com.sensorpark.domain.model.SpotProblem
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType

private enum class PanelTab(val title: Int) {
    SPOTS(R.string.admin_panel_tab_spots),
    TODAY(R.string.admin_panel_tab_today),
    DETAILS(R.string.admin_panel_tab_details),
}

/** Panel de un estacionamiento para su administrador (T22): lugares, el día de hoy y sus datos. */
@Composable
fun ParkingLotPanelScreen(
    parkingLotId: String,
    vm: ParkingLotPanelViewModel = hiltViewModel(),
    formVm: ParkingLotFormViewModel = hiltViewModel(),
) {
    LaunchedEffect(parkingLotId) { vm.load(parkingLotId) }
    val result by vm.result.collectAsStateWithLifecycle()

    when (val current = result) {
        null -> LoadingContent()
        is DataResult.Failure -> ErrorContent(current.reason, onRetry = vm::retry)
        is DataResult.Success -> Panel(current.value, vm, formVm)
    }
}

@Composable
private fun Panel(lot: ParkingLot, vm: ParkingLotPanelViewModel, formVm: ParkingLotFormViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(lot.name, style = MaterialTheme.typography.headlineSmall)
                Text(lot.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AvailabilityBadge(lot.freeSpots, lot.capacity)
        }
        TabRow(selectedTabIndex = selected) {
            PanelTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(stringResource(tab.title)) },
                )
            }
        }
        when (PanelTab.entries[selected]) {
            PanelTab.SPOTS -> SpotsTab(vm)
            PanelTab.TODAY -> TodayTab(vm)
            PanelTab.DETAILS -> ParkingLotDetailsTab(lot.id, formVm)
        }
    }
}

/** Los lugares: agregar uno, cambiarle el tipo, sacarlo de servicio o borrarlo. */
@Composable
private fun SpotsTab(vm: ParkingLotPanelViewModel) {
    val spots by vm.spots.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SpotEdit?>(null) }
    var deleting by remember { mutableStateOf<Spot?>(null) }

    val current = spots
    if (current == null) {
        LoadingContent()
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedButton(
                onClick = { editing = SpotEdit(SpotDraft(code = SpotDraft.nextCode(current.map { it.code })), isNew = true) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.admin_spots_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
        action.rejection?.let { item { Problem(it.message(), vm::dismissError) } }
        action.failure?.let { item { Problem(it.message(), vm::dismissError) } }
        if (current.isEmpty()) {
            item {
                Text(stringResource(R.string.admin_spots_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(current, key = { it.id }) { spot ->
            SpotRow(
                spot = spot,
                working = action.working,
                onEdit = { editing = SpotEdit(SpotDraft.from(spot), isNew = false) },
                onToggleService = { vm.toggleService(spot) },
                onDelete = { deleting = spot },
            )
        }
    }

    editing?.let { edit ->
        SpotDialog(
            edit = edit,
            takenCodes = current.map { it.code }.toSet() - if (edit.isNew) emptySet() else setOf(edit.draft.code),
            saving = action.working,
            onDismiss = { editing = null },
            onSave = { vm.saveSpot(it) { editing = null } },
        )
    }
    deleting?.let { spot ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.admin_spots_delete_title, spot.code)) },
            text = { Text(stringResource(R.string.admin_spots_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(spot)
                    deleting = null
                }) { Text(stringResource(R.string.admin_spots_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.admin_cancel)) } },
        )
    }
}

private data class SpotEdit(val draft: SpotDraft, val isNew: Boolean)

@Composable
private fun SpotRow(
    spot: Spot,
    working: Boolean,
    onEdit: () -> Unit,
    onToggleService: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.admin_spot_code, spot.code), style = MaterialTheme.typography.titleMedium)
                    Text(
                        spot.priorityCategory?.let { stringResource(R.string.admin_spot_type_for, spot.type.label(), it.adminLabel()) }
                            ?: spot.type.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SpotStatusChip(spot.status)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, enabled = !working) { Text(stringResource(R.string.admin_spots_edit)) }
                TextButton(onClick = onToggleService, enabled = !working) {
                    Text(
                        stringResource(
                            if (spot.status == SpotStatus.OUT_OF_SERVICE) R.string.admin_spots_back_in_service
                            else R.string.admin_spots_out_of_service,
                        ),
                    )
                }
                TextButton(onClick = onDelete, enabled = !working) { Text(stringResource(R.string.admin_spots_delete)) }
            }
        }
    }
}

/** Alta o edición de un lugar: código, tipo y, si es preferencial, para qué categoría. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpotDialog(
    edit: SpotEdit,
    takenCodes: Set<String>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (SpotDraft) -> Unit,
) {
    var draft by remember(edit) { mutableStateOf(edit.draft) }
    var tried by remember(edit) { mutableStateOf(false) }
    val problems = if (tried) draft.problems(takenCodes) else emptySet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (edit.isNew) R.string.admin_spots_add else R.string.admin_spots_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.code,
                    onValueChange = { draft = draft.copy(code = it) },
                    label = { Text(stringResource(R.string.admin_spot_code_label)) },
                    // El código es el id del lugar: no se cambia una vez creado.
                    enabled = edit.isNew,
                    isError = SpotProblem.CODE_REQUIRED in problems || SpotProblem.CODE_TAKEN in problems,
                    supportingText = when {
                        SpotProblem.CODE_REQUIRED in problems -> { { Text(stringResource(R.string.admin_spot_code_required)) } }
                        SpotProblem.CODE_TAKEN in problems -> { { Text(stringResource(R.string.admin_spot_code_taken)) } }
                        else -> null
                    },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpotType.entries.forEach { type ->
                        FilterChip(
                            selected = draft.type == type,
                            onClick = {
                                draft = draft.copy(type = type, priorityCategory = draft.priorityCategory.takeIf { type == SpotType.PRIORITY })
                            },
                            label = { Text(type.label()) },
                        )
                    }
                }
                if (draft.type == SpotType.PRIORITY) {
                    Text(stringResource(R.string.admin_spot_category), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Category.PREGNANT, Category.REDUCED_MOBILITY).forEach { category ->
                            FilterChip(
                                selected = draft.priorityCategory == category,
                                onClick = { draft = draft.copy(priorityCategory = category) },
                                label = { Text(category.adminLabel()) },
                            )
                        }
                    }
                    if (SpotProblem.CATEGORY_REQUIRED in problems) {
                        Text(stringResource(R.string.admin_spot_category_required), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    tried = true
                    if (draft.problems(takenCodes).isEmpty()) onSave(draft)
                },
            ) { Text(stringResource(R.string.admin_form_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_cancel)) } },
    )
}

/** El día: cómo están los lugares ahora y qué pasó con las reservas de hoy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayTab(vm: ParkingLotPanelViewModel) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val reservations by vm.todayReservations.collectAsStateWithLifecycle()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.admin_today_spots), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Count(R.string.admin_today_free, summary.free)
                Count(R.string.admin_today_occupied, summary.occupied)
                Count(R.string.admin_today_reserved, summary.reserved)
                Count(R.string.admin_today_out_of_service, summary.outOfService)
            }
        }
        item {
            Text(
                stringResource(R.string.admin_today_reservations, summary.reservations),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Count(R.string.admin_today_active, summary.active)
                Count(R.string.admin_today_finished, summary.finished)
                Count(R.string.admin_today_cancelled, summary.cancelled)
                Count(R.string.admin_today_expired, summary.expired)
            }
        }
        if (reservations.isEmpty()) {
            item { Text(stringResource(R.string.admin_today_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(reservations.sortedBy { it.arrivesAt }, key = { it.id }) { TodayReservationRow(it) }
    }
}

@Composable
private fun Count(label: Int, value: Int) {
    Card {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TodayReservationRow(reservation: Reservation) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    R.string.admin_today_reservation,
                    reservation.spotCode,
                    formatTime(reservation.arrivesAt),
                    formatTime(reservation.endsAt),
                ),
                modifier = Modifier.weight(1f),
            )
            ReservationStatusBadge(reservation.status)
        }
    }
}

@Composable
private fun Problem(message: String, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.admin_dismiss)) }
        }
    }
}

@Composable
private fun Category.adminLabel(): String = stringResource(
    when (this) {
        Category.PREGNANT -> R.string.admin_category_pregnant
        Category.REDUCED_MOBILITY -> R.string.admin_category_reduced_mobility
        Category.STANDARD -> R.string.admin_category_standard
    },
)
