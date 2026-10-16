package com.sensorpark.feature.admin

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sensorpark.core.ui.components.PlaceholderScreen

/** Alta de un estacionamiento. La completa T22: el que lo crea queda como su administrador. */
@Composable
fun CreateParkingLotScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.admin_create_title),
        detail = stringResource(R.string.admin_create_pending),
    )
}
