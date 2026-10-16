package com.sensorpark.domain.geo

import com.sensorpark.domain.model.Coordinates
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Radio medio de la Tierra. */
private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Distancia en línea recta entre dos puntos, en metros, con la fórmula de Haversine.
 * Para las distancias de una ciudad el error es menor al 0,5 %.
 */
fun distanceMeters(from: Coordinates, to: Coordinates): Double {
    val dLat = Math.toRadians(to.latitude - from.latitude)
    val dLng = Math.toRadians(to.longitude - from.longitude)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
}
