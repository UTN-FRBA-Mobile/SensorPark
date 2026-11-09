package com.sensorpark.feature.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorpark.core.ui.components.LoadingContent
import com.sensorpark.core.ui.components.ParkingLevelPlanBox
import com.sensorpark.core.ui.components.PrimaryButton
import com.sensorpark.core.ui.components.encodePlanImage
import com.sensorpark.core.ui.components.message
import com.sensorpark.domain.model.ParkingLevel
import com.sensorpark.domain.model.ParkingLevelDraft
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotPlanPosition
import com.sensorpark.domain.model.partitionSpotsForLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pestaña "Plano" del administrador: cada estacionamiento tiene un único plano donde ubicar los
 * lugares manteniendo consistencia estricta 1 a 1 con los lugares dados de alta.
 */
@Composable
internal fun PlanTab(vm: ParkingLotPanelViewModel) {
    val spots by vm.spots.collectAsStateWithLifecycle()
    val levels by vm.levels.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()

    val currentSpots = spots
    if (currentSpots == null) {
        LoadingContent()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPlan = remember(levels) { levels.firstOrNull() }

    var imageError by remember { mutableStateOf(false) }
    var deletingPlan by remember { mutableStateOf<ParkingLevel?>(null) }
    var editedPositions by remember { mutableStateOf<Map<String, SpotPlanPosition?>>(emptyMap()) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) { encodePlanImage(context, uri) }
                if (encoded != null) {
                    imageError = false
                    vm.saveLevel(
                        ParkingLevelDraft(
                            id = currentPlan?.id ?: "main",
                            name = "Plano",
                            order = 1,
                            imageBase64 = encoded.base64,
                            imageWidth = encoded.width,
                            imageHeight = encoded.height,
                        ),
                    ) {}
                } else {
                    imageError = true
                }
            }
        }
    }

    if (currentPlan == null) {
        EmptyPlanScreen(
            action = action,
            imageError = imageError,
            onDismissError = vm::dismissError,
            onPickImage = { imagePicker.launch("image/*") },
        )
    } else {
        SinglePlanEditorScreen(
            plan = currentPlan,
            spots = currentSpots,
            action = action,
            imageError = imageError,
            editedPositions = editedPositions,
            onUpdatePositions = { editedPositions = it },
            onChangeImage = { imagePicker.launch("image/*") },
            onDeletePlan = { deletingPlan = currentPlan },
            onSavePositions = {
                vm.saveSpotPositions(editedPositions) {
                    editedPositions = emptyMap()
                }
            },
            onDismissError = vm::dismissError,
        )
    }

    deletingPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { deletingPlan = null },
            title = { Text(stringResource(R.string.admin_plan_delete_title)) },
            text = { Text(stringResource(R.string.admin_plan_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deletedId = plan.id
                        deletingPlan = null
                        editedPositions = emptyMap()
                        vm.deleteLevel(deletedId)
                    },
                ) { Text(stringResource(R.string.admin_spots_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingPlan = null }) { Text(stringResource(R.string.admin_cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyPlanScreen(
    action: SpotAction,
    imageError: Boolean,
    onDismissError: () -> Unit,
    onPickImage: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        action.planFailure?.let { item { Problem(it.message(), onDismissError) } }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.admin_plan_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onPickImage,
                        enabled = !action.working,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null)
                        Text(
                            text = stringResource(R.string.admin_plan_pick_image),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (imageError) {
                        Text(
                            text = stringResource(R.string.admin_plan_image_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SinglePlanEditorScreen(
    plan: ParkingLevel,
    spots: List<Spot>,
    action: SpotAction,
    imageError: Boolean,
    editedPositions: Map<String, SpotPlanPosition?>,
    onUpdatePositions: (Map<String, SpotPlanPosition?>) -> Unit,
    onChangeImage: () -> Unit,
    onDeletePlan: () -> Unit,
    onSavePositions: () -> Unit,
    onDismissError: () -> Unit,
) {
    var selectedSpotId by rememberSaveable(plan.id) { mutableStateOf<String?>(null) }

    // Normaliza cualquier coordenada válida hacia el único plano del estacionamiento.
    val normalizedSpots = remember(spots, plan.id) {
        spots.map { spot ->
            val pos = spot.planPosition
            if (pos != null && pos.isValid && pos.levelId != plan.id) {
                spot.copy(planPosition = pos.copy(levelId = plan.id))
            } else {
                spot
            }
        }
    }

    val partition = remember(normalizedSpots, plan, editedPositions) {
        partitionSpotsForLevel(
            spots = normalizedSpots,
            levels = listOf(plan),
            currentLevelId = plan.id,
            overrides = editedPositions,
        )
    }

    LaunchedEffect(partition.unplaced, plan.id) {
        val allIds = normalizedSpots.map { it.id }.toSet()
        if (selectedSpotId == null || selectedSpotId !in allIds) {
            selectedSpotId = partition.unplaced.firstOrNull()?.id
        }
    }

    val selectedSpot = remember(normalizedSpots, selectedSpotId, partition) {
        (partition.onCurrentLevel + partition.unplaced)
            .firstOrNull { it.id == selectedSpotId }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.admin_plan_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onChangeImage,
                        enabled = !action.working,
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.admin_plan_change_image),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    TextButton(
                        onClick = onDeletePlan,
                        enabled = !action.working,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.admin_plan_delete),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }

        if (imageError) {
            item {
                Text(
                    text = stringResource(R.string.admin_plan_image_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        action.planFailure?.let { item { Problem(it.message(), onDismissError) } }

        item {
            ParkingLevelPlanBox(
                level = plan,
                spots = partition.onCurrentLevel,
                selectedSpotId = selectedSpot?.id,
                editable = true,
                onSpotClick = { clicked -> selectedSpotId = clicked.id },
                onTapImage = { x, y ->
                    val target = selectedSpot ?: partition.unplaced.firstOrNull()
                    if (target != null) {
                        val wasUnplaced = partition.unplaced.any { it.id == target.id }
                        onUpdatePositions(
                            editedPositions + (target.id to SpotPlanPosition(levelId = plan.id, x = x, y = y)),
                        )
                        if (wasUnplaced) {
                            val nextUnplaced = partition.unplaced.firstOrNull { it.id != target.id }
                            selectedSpotId = nextUnplaced?.id ?: target.id
                        }
                    }
                },
                onMoveSpot = { movedSpot, x, y ->
                    selectedSpotId = movedSpot.id
                    onUpdatePositions(
                        editedPositions + (movedSpot.id to SpotPlanPosition(levelId = plan.id, x = x, y = y)),
                    )
                },
            )
        }

        if (normalizedSpots.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.admin_plan_no_spots),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                SelectionHintCard(
                    selectedSpot = selectedSpot,
                    onRemoveFromPlan = { spot ->
                        onUpdatePositions(editedPositions + (spot.id to null))
                    },
                )
            }

            item {
                SpotPartitionCard(
                    title = stringResource(R.string.admin_plan_unplaced_title, partition.unplaced.size),
                    emptyText = stringResource(R.string.admin_plan_unplaced_all_done),
                    spots = partition.unplaced,
                    selectedSpotId = selectedSpot?.id,
                    onSelect = { selectedSpotId = it.id },
                )
            }

            item {
                SpotPartitionCard(
                    title = stringResource(R.string.admin_plan_placed_title, partition.onCurrentLevel.size),
                    emptyText = stringResource(R.string.admin_plan_placed_empty),
                    spots = partition.onCurrentLevel,
                    selectedSpotId = selectedSpot?.id,
                    onSelect = { selectedSpotId = it.id },
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (action.savedPositions && editedPositions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.admin_plan_positions_saved),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    PrimaryButton(
                        text = stringResource(
                            if (action.working) R.string.admin_form_saving else R.string.admin_plan_save_positions,
                        ),
                        onClick = onSavePositions,
                        enabled = editedPositions.isNotEmpty() && !action.working,
                        loading = action.working,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionHintCard(
    selectedSpot: Spot?,
    onRemoveFromPlan: (Spot) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (selectedSpot != null) {
                    stringResource(R.string.admin_plan_selected_hint, selectedSpot.code)
                } else {
                    stringResource(R.string.admin_plan_unselected_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            if (selectedSpot?.planPosition != null) {
                TextButton(onClick = { onRemoveFromPlan(selectedSpot) }) {
                    Text(
                        text = stringResource(R.string.admin_plan_remove_spot),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpotPartitionCard(
    title: String,
    emptyText: String,
    spots: List<Spot>,
    selectedSpotId: String?,
    onSelect: (Spot) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            if (spots.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    spots.forEach { spot ->
                        FilterChip(
                            selected = spot.id == selectedSpotId,
                            onClick = { onSelect(spot) },
                            label = { Text(spot.code, fontWeight = FontWeight.SemiBold) },
                        )
                    }
                }
            }
        }
    }
}
