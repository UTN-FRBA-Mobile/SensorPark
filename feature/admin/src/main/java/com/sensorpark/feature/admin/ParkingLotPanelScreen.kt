package com.sensorpark.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot

/** Las solapas del panel. Cada una la completa T22. */
private enum class PanelTab(val title: Int, val pending: Int) {
    SPOTS(R.string.admin_panel_tab_spots, R.string.admin_panel_spots_pending),
    TODAY(R.string.admin_panel_tab_today, R.string.admin_panel_today_pending),
    DETAILS(R.string.admin_panel_tab_details, R.string.admin_panel_details_pending),
}

/** Panel de un estacionamiento para su administrador: lugares, el día de hoy y sus datos. */
@Composable
fun ParkingLotPanelScreen(
    parkingLotId: String,
    vm: ParkingLotPanelViewModel = hiltViewModel(),
) {
    LaunchedEffect(parkingLotId) { vm.load(parkingLotId) }
    val result by vm.result.collectAsStateWithLifecycle()

    when (val current = result) {
        null -> LoadingContent()
        is DataResult.Failure -> ErrorContent(current.reason, onRetry = vm::retry)
        is DataResult.Success -> Panel(current.value)
    }
}

@Composable
private fun Panel(lot: ParkingLot) {
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(PanelTab.entries[selected].pending),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
