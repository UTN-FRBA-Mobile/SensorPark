package com.sensorpark.domain.model

import java.time.LocalTime

/** Punto en el mapa, en grados decimales. */
data class Coordinates(val latitude: Double, val longitude: Double)

/** Horario de atención del estacionamiento. */
data class Schedule(val opensAt: LocalTime, val closesAt: LocalTime)

/**
 * Estacionamiento con sus contadores de lugares. Los contadores los mantiene el backend, así que
 * para mostrar "libres / capacidad" alcanza con este modelo, sin leer los lugares uno por uno.
 */
data class ParkingLot(
    val id: String,
    val name: String,
    val address: String,
    val location: Coordinates,
    val schedule: Schedule? = null,
    val toleranceMinutes: Int = 0,
    val geofenceRadiusMeters: Int = 0,
    /** Tarifa por hora, en pesos. Es informativa: la app no cobra. */
    val hourlyRate: Int = 0,
    val adminUids: List<String> = emptyList(),
    val capacity: Int = 0,
    val freeSpots: Int = 0,
    val capacityByType: Map<SpotType, Int> = emptyMap(),
    val freeSpotsByType: Map<SpotType, Int> = emptyMap(),
) {
    fun capacityOf(type: SpotType): Int = capacityByType[type] ?: 0

    fun freeSpotsOf(type: SpotType): Int = freeSpotsByType[type] ?: 0
}

/** Resultado de una búsqueda por cercanía: el estacionamiento y a cuántos metros está. */
data class NearbyParkingLot(val parkingLot: ParkingLot, val distanceMeters: Double)
