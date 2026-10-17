package com.sensorpark.domain.model

import java.time.Instant

/**
 * Los lugares de un estacionamiento, uno por uno, con el estado de la conexión de la que vienen.
 *
 * [isFromCache] es true cuando son la última copia guardada en el teléfono y todavía no se pudo
 * confirmar con el servidor: la pantalla muestra "Sin conexión" en vez de "En vivo".
 * [lastUpdate] es el cambio más reciente entre los lugares, o null si ninguno trae hora.
 */
data class SpotAvailability(
    val spots: List<Spot>,
    val isFromCache: Boolean,
    val lastUpdate: Instant?,
)
